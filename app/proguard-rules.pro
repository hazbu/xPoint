# Xposed Module keep rules
-keep class com.hazbu.xpoint.XPointModule { *; }

# Keep any classes that might be accessed via reflection by Xposed
-keepclassmembers class * {
    @de.robv.android.xposed.callbacks.XC_LoadPackage$LoadPackageParam *;
}