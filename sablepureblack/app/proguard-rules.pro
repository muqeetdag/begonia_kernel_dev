# SablePureBlack — by @drkmuqeett
# R8 / ProGuard rules for the Xposed payload.

# The entry point is loaded reflectively from assets/xposed_init — never rename,
# shrink or remove it, or the framework silently skips the module.
-keep class com.drkmuqeett.pureblack.core.PureBlackHook { *; }
-keep class com.drkmuqeett.pureblack.config.PureBlackPrefs { *; }
-keep class com.drkmuqeett.pureblack.config.PureBlackPalette { *; }
-keep class com.drkmuqeett.pureblack.config.SableSettings { *; }
-keepnames class com.drkmuqeett.pureblack.core.** { *; }
-keepnames class com.drkmuqeett.pureblack.config.** { *; }

# Xposed API is compileOnly — silence the expected missing-class notes.
-dontwarn de.robv.android.xposed.**
-dontwarn de.robv.android.xposed.callbacks.**

# Kotlin runtime metadata is not needed inside the payload.
-dontwarn kotlin.**
-keepclassmembers class ** {
    @androidx.annotation.Keep *;
}

# Manifest components.
-keep class com.drkmuqeett.pureblack.SableApplication { *; }
-keep class com.drkmuqeett.pureblack.MainActivity { *; }

# View binding generated classes.
-keep class com.drkmuqeett.pureblack.databinding.** { *; }

# Keep data classes used by DiffUtil intact (equals/hashCode/componentN).
-keepclassmembers class com.drkmuqeett.pureblack.** {
    synthetic *** component*();
    synthetic *** copy*(...);
}
