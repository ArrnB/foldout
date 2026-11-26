package app.foldout

import android.annotation.SuppressLint
import android.content.ActivityNotFoundException
import android.content.ContentValues
import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.util.Base64
import android.webkit.JavascriptInterface
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.widget.FrameLayout
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.addCallback
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.FileProvider
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.webkit.WebViewAssetLoader
import androidx.webkit.WebViewClientCompat
import java.io.File

/**
 * Foldout as an Android app: the same scanner page as the website, shown in a
 * WebView, with the few things a web page cannot do by itself wired to Android:
 *
 *  - "Take Photo" opens the camera app, "Choose from Library" the photo picker
 *  - saved PDFs and JPGs go to Downloads/Foldout
 *  - the Back button steps back through the scanner's screens
 *
 * Everything else, including the saved scans (IndexedDB), is the web page itself.
 */
class MainActivity : ComponentActivity() {

    private lateinit var web: WebView

    /** The page's file input waiting for a photo. It must always get an answer, even null, or it stops working. */
    private var pendingPhoto: ValueCallback<Array<Uri>>? = null

    /** Where the camera app was asked to write the photo. Kept across a restart. */
    private var cameraFile: File? = null

    /**
     * A photo that came back after Android stopped and restarted the app while the
     * camera or picker was open. The page's request died with the old page, so the
     * photo is handed to the new page by URL once it has loaded.
     */
    private var photoForPage: String? = null
    private var pageLoaded = false

    private val pickPhoto = registerForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        when {
            pendingPhoto != null -> answerPhoto(uri)
            uri != null -> copyToCameraDir(uri)?.let { handToPage(it) }
        }
    }
    private val takePhoto = registerForActivityResult(ActivityResultContracts.TakePicture()) { saved ->
        val file = cameraFile
        when {
            pendingPhoto != null -> answerPhoto(if (saved && file != null) uriFor(file) else null)
            saved && file != null && file.length() > 0 -> handToPage(file)
        }
    }

    private val cameraDir by lazy { File(cacheDir, "camera").apply { mkdirs() } }

    private val assets by lazy {
        WebViewAssetLoader.Builder()
            .addPathHandler("/assets/", WebViewAssetLoader.AssetsPathHandler(this))
            .addPathHandler("/camera/", WebViewAssetLoader.InternalStoragePathHandler(this, cameraDir))
            .build()
    }

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Light icons on the dark page, whatever the phone's own theme is.
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
        )
        if (savedInstanceState == null) {
            clearOldCameraPhotos()
        } else {
            cameraFile = savedInstanceState.getString(STATE_CAMERA_FILE)?.let { File(cameraDir, it) }
        }

        web = WebView(this).apply {
            setBackgroundColor(Color.parseColor("#14161A"))
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            // The page comes from the app's own assets through the asset loader;
            // it never needs to read files or content:// URLs directly.
            settings.allowFileAccess = false
            settings.allowContentAccess = true // the picked photo arrives as a content:// URL
            webViewClient = PageClient()
            webChromeClient = ChromeClient()
            addJavascriptInterface(Bridge(), "FoldoutAndroid")
        }

        // Keep the page clear of the status bar, navigation bar and keyboard.
        val frame = FrameLayout(this).apply {
            setBackgroundColor(Color.parseColor("#14161A"))
            addView(web, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))
        }
        ViewCompat.setOnApplyWindowInsetsListener(frame) { view, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.ime() or WindowInsetsCompat.Type.displayCutout())
            view.setPadding(bars.left, bars.top, bars.right, bars.bottom)
            WindowInsetsCompat.CONSUMED
        }
        setContentView(frame)

        onBackPressedDispatcher.addCallback(this) {
            web.evaluateJavascript("window.foldoutBack ? window.foldoutBack() : false") { handled ->
                if (handled != "true") finish()
            }
        }

        if (savedInstanceState == null || web.restoreState(savedInstanceState) == null) web.loadUrl(START_URL)
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        web.saveState(outState)
        cameraFile?.let { outState.putString(STATE_CAMERA_FILE, it.name) }
    }

    override fun onDestroy() {
        answerPhoto(null)
        web.destroy()
        super.onDestroy()
    }

    // ------------------------------------------------------------------ photos

    private fun answerPhoto(uri: Uri?) {
        pendingPhoto?.onReceiveValue(uri?.let { arrayOf(it) })
        pendingPhoto = null
    }

    private fun uriFor(file: File): Uri = FileProvider.getUriForFile(this, "$packageName.files", file)

    private fun openCamera() {
        val file = File(cameraDir, "scan-${System.currentTimeMillis()}.jpg")
        cameraFile = file
        try {
            takePhoto.launch(uriFor(file))
        } catch (e: ActivityNotFoundException) {
            openPicker() // no camera app: offer the library instead
        }
    }

    private fun openPicker() {
        pickPhoto.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
    }

    /** Camera photos are only needed until the page has read them. */
    private fun clearOldCameraPhotos() {
        cameraDir.listFiles()?.forEach { it.delete() }
    }

    /** A picked photo (a content:// URL) copied to where the page can fetch it. */
    private fun copyToCameraDir(uri: Uri): File? = try {
        val file = File(cameraDir, "picked-${System.currentTimeMillis()}.jpg")
        contentResolver.openInputStream(uri)!!.use { input -> file.outputStream().use { input.copyTo(it) } }
        file
    } catch (e: Exception) {
        null
    }

    private fun handToPage(file: File) {
        photoForPage = file.name
        if (pageLoaded) sendPhotoToPage()
    }

    private fun sendPhotoToPage() {
        val name = photoForPage ?: return
        photoForPage = null
        web.evaluateJavascript("window.foldoutReceivePhoto && window.foldoutReceivePhoto('https://$ASSET_HOST/camera/$name')", null)
    }

    // ------------------------------------------------------------------ the page

    private inner class PageClient : WebViewClientCompat() {
        override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest): WebResourceResponse? =
            assets.shouldInterceptRequest(request.url)

        override fun onPageFinished(view: WebView, url: String) {
            pageLoaded = true
            sendPhotoToPage()
        }

        /** The page never leaves its own address; anything else would open in the browser. */
        override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
            if (request.url.host == ASSET_HOST) return false
            try {
                startActivity(Intent(Intent.ACTION_VIEW, request.url))
            } catch (_: ActivityNotFoundException) {
            }
            return true
        }
    }

    private inner class ChromeClient : WebChromeClient() {
        override fun onShowFileChooser(
            webView: WebView,
            callback: ValueCallback<Array<Uri>>,
            params: FileChooserParams,
        ): Boolean {
            answerPhoto(null) // a chooser that was never answered
            pendingPhoto = callback
            // "Take Photo" is the input with capture="environment".
            if (params.isCaptureEnabled) openCamera() else openPicker()
            return true
        }
    }

    /** What the page can call, as window.FoldoutAndroid. */
    private inner class Bridge {
        /** Saves a file the page made (a PDF or JPG, as a data: URL) to Downloads/Foldout. */
        @JavascriptInterface
        fun saveFile(dataUrl: String, fileName: String, mimeType: String) {
            val safeName = fileName.replace(Regex("[^A-Za-z0-9._ -]"), "_").ifBlank { "scan" }
            val ok = try {
                val bytes = Base64.decode(dataUrl.substringAfter(','), Base64.DEFAULT)
                saveToDownloads(bytes, safeName, mimeType)
                true
            } catch (e: Exception) {
                false
            }
            runOnUiThread {
                val message = getString(if (ok) R.string.saved_to else R.string.save_failed, safeName)
                Toast.makeText(this@MainActivity, message, Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun saveToDownloads(bytes: ByteArray, name: String, mimeType: String) {
        val values = ContentValues().apply {
            put(MediaStore.Downloads.DISPLAY_NAME, name)
            put(MediaStore.Downloads.MIME_TYPE, mimeType)
            put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/Foldout")
            put(MediaStore.Downloads.IS_PENDING, 1)
        }
        val resolver = contentResolver
        val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
            ?: error("could not create $name")
        try {
            resolver.openOutputStream(uri)!!.use { it.write(bytes) }
            values.clear()
            values.put(MediaStore.Downloads.IS_PENDING, 0)
            resolver.update(uri, values, null, null)
        } catch (e: Exception) {
            resolver.delete(uri, null, null)
            throw e
        }
    }

    private companion object {
        const val ASSET_HOST = "appassets.androidplatform.net"
        const val START_URL = "https://$ASSET_HOST/assets/foldout-scanner.html"
        const val STATE_CAMERA_FILE = "camera_file"
    }
}
