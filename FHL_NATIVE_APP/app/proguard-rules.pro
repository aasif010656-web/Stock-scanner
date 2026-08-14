# ProGuard rules for FHL ELECTRONICS
-keep class com.abdulasif.pdtstockscanner.** { *; }
-keepclassmembers class com.abdulasif.pdtstockscanner.** {
    @android.webkit.JavascriptInterface <methods>;
}
