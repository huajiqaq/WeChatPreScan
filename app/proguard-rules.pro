-dontwarn io.github.libxposed.**
-dontwarn org.luckypray.dexkit.**
-dontwarn kotlin.**
-dontwarn androidx.**

-keep,allowoptimization,allowobfuscation public class * extends io.github.libxposed.api.XposedModule {
    public <init>();
}
-keep class io.github.huajiqaq.wxprescan.MainHook { *; }

-adaptresourcefilecontents META-INF/xposed/java_init.list

-repackageclasses ''
