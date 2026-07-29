-keep class com.hazbu.xpoint.XPointModule { *; }
-keep class de.robv.android.xposed.** { *; }
-keep interface de.robv.android.xposed.** { *; }
-keep class com.hazbu.xpoint.** { *; }
-keepclassmembers class * {
    @de.robv.android.xposed.callbacks.XC_LoadPackage$LoadPackageParam *;
}

