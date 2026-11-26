# The page calls these methods by name through window.FoldoutAndroid.
-keepclassmembers class * {
    @android.webkit.JavascriptInterface <methods>;
}
