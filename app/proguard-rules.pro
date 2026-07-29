-keep class com.hazbu.xpoint.XPointModule { *; }
-keep class com.hazbu.xpoint.LocationProvider { *; }
-keep class de.robv.android.xposed.** { *; }
-keep interface de.robv.android.xposed.** { *; }
-keep class com.hazbu.xpoint.** { *; }
-keepclassmembers class * {
    @de.robv.android.xposed.callbacks.XC_LoadPackage$LoadPackageParam *;
}
-dontwarn de.robv.android.xposed.**
