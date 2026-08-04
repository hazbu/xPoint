# xPoint Proguard Rules

-keep class com.hazbu.xpoint.XPointModule { *; }
-keep class com.hazbu.xpoint.LocationProvider { *; }
-keep class com.hazbu.xpoint.** { *; }

# libxposed rules
-dontwarn io.github.libxposed.annotation.**
-adaptresourcefilecontents META-INF/xposed/java_init.list
-keep,allowoptimization,allowobfuscation public class * extends io.github.libxposed.api.XposedModule {
    public <init>(io.github.libxposed.api.XposedInterface, io.github.libxposed.api.XposedModuleInterface$ModuleLoadedParam);
}
