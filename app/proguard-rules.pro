# WebView JS interface targets — keep for future JS bridge expansion.
-keepclassmembers class * {
    @android.webkit.JavascriptInterface <methods>;
}
