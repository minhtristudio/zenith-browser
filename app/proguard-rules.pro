# Browser app proguard rules
-keepattributes *Annotation*
-keepattributes SourceFile,LineNumberTable
-keep public class * extends android.webkit.WebView
-keepclassmembers class * extends android.webkit.WebViewClient {
    public *;
}
-keepclassmembers class * extends android.webkit.WebChromeClient {
    public *;
}
-keep class com.zenith.browser.** { *; }
-dontwarn com.zenith.browser.extensions.**
