# Add project specific ProGuard rules here.
-keep class dev.alacarte.hook.HookEntry

# Keep YukiHookAPI generated classes
-keep class dev.alacarte.hook.HookEntry_YukiHookXposedInit
-keep class * extends com.highcapable.yukihookapi.hook.xposed.proxy.IYukiHookXposedInit

# Xposed API
-keep class de.robv.android.xposed.** { *; }
-keepclassmembers class * {
    @de.robv.android.xposed.* <methods>;
}

# Obfuscate entry class names for Modern API
-adaptresourcefilenames
