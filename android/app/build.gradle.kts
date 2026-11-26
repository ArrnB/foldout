import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

// Release signing for the Play Store. The upload key and its passwords live in
// android/keystore.properties, which is never committed (see .gitignore and
// PLAY_STORE.md). Without that file, release builds are simply left unsigned.
val keystoreFile = rootProject.file("keystore.properties")
val keystore = Properties().apply { if (keystoreFile.exists()) keystoreFile.inputStream().use { load(it) } }

// The app shows the very same page as the website: foldout-scanner.html and
// its icons are copied in from the repository root at build time, so there
// is only ever one copy of the scanner to change.
val webDir = layout.buildDirectory.dir("generated/web")
val copyWeb by tasks.registering(Copy::class) {
    from(rootProject.file("../foldout-scanner.html"))
    from(rootProject.file("../icons")) { into("icons") }
    into(webDir)
}

android {
    namespace = "app.foldout"
    compileSdk = 36

    defaultConfig {
        applicationId = "app.foldout"
        // Android 10+: saving to Downloads then needs no storage permission.
        minSdk = 29
        targetSdk = 36
        // Play needs versionCode to go up with every upload.
        versionCode = 2
        versionName = "1.1.0"
    }

    sourceSets["main"].assets.srcDir(webDir)

    signingConfigs {
        if (keystoreFile.exists()) {
            create("upload") {
                storeFile = rootProject.file(keystore.getProperty("storeFile"))
                storePassword = keystore.getProperty("storePassword")
                keyAlias = keystore.getProperty("keyAlias")
                keyPassword = keystore.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        release {
            if (keystoreFile.exists()) signingConfig = signingConfigs.getByName("upload")
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
}

tasks.named("preBuild") { dependsOn(copyWeb) }

dependencies {
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.activity:activity-ktx:1.9.3")
    // Serves the page from https://appassets.androidplatform.net, a proper
    // origin, so IndexedDB (saved scans) and fetch() behave as on the web.
    implementation("androidx.webkit:webkit:1.12.1")
}
