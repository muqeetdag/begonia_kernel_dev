/*
 * SablePureBlack — by @drkmuqeett
 * Package: com.drkmuqeett.pureblack
 *
 * Immutable snapshot of the user's configuration, resolved from a [PrefSource]
 * (XSharedPreferences in the payload, SharedPreferences in the UI). Resolving
 * once keeps the hot view-hook paths free of preference I/O.
 */
package com.drkmuqeett.pureblack.config

import android.content.SharedPreferences

/** Minimal read surface implemented by every preference backend. */
interface PrefSource {
    fun getInt(key: String, defaultValue: Int): Int
    fun getBoolean(key: String, defaultValue: Boolean): Boolean
    fun getString(key: String, defaultValue: String?): String?
    fun getStringSet(key: String, defaultValue: MutableSet<String>?): MutableSet<String>?
}

/** SharedPreferences-backed source (companion UI process). */
class SharedPrefSource(
    private val preferences: SharedPreferences
) : PrefSource {

    override fun getInt(key: String, defaultValue: Int): Int =
        try {
            preferences.getInt(key, defaultValue)
        } catch (_: Throwable) {
            defaultValue
        }

    override fun getBoolean(key: String, defaultValue: Boolean): Boolean =
        try {
            preferences.getBoolean(key, defaultValue)
        } catch (_: Throwable) {
            defaultValue
        }

    override fun getString(key: String, defaultValue: String?): String? =
        try {
            preferences.getString(key, defaultValue)
        } catch (_: Throwable) {
            defaultValue
        }

    override fun getStringSet(key: String, defaultValue: MutableSet<String>?): MutableSet<String>? =
        try {
            @Suppress("UNCHECKED_CAST")
            (preferences.getStringSet(key, defaultValue) as? MutableSet<String>) ?: defaultValue
        } catch (_: Throwable) {
            defaultValue
        }
}

data class SableSettings(
    /** One of PureBlackPrefs.PALETTE_*. */
    val palette: Int,
    /** Resolved override colour for [palette]. */
    val overrideColor: Int,
    /** User accent seed used by PALETTE_CUSTOM_ACCENT and by status accents. */
    val accentSeed: Int,
    /** Force WebSettings.FORCE_DARK_ON on every WebView. */
    val webViewForceDark: Boolean,
    /** Lift unreadable dark text to #EAEAEA. */
    val textContrastBoost: Boolean,
    /** Alpha applied to forced surfaces, 100 = fully opaque. */
    val overlayOpacityPercent: Int,
    /** Master switch. */
    val enabled: Boolean,
    /** TARGET_MODE_ALL_SCOPED or TARGET_MODE_SELECTED. */
    val targetMode: Int,
    /** Allow-list used when [targetMode] is TARGET_MODE_SELECTED. */
    val scopedPackages: Set<String>
) {

    /** Alpha (0..255) the engine applies to forced surfaces. */
    val forcedAlpha: Int
        get() = (overlayOpacityPercent.coerceIn(0, 100) * 255) / 100

    /**
     * True when [packageName] should be processed.
     *
     * LSPosed already restricts hooking to the module's scope, so the default
     * mode trusts the manager. Selected mode adds a finer allow-list on top.
     */
    fun shouldProcess(packageName: String): Boolean {
        if (!enabled) return false
        if (targetMode == PureBlackPrefs.TARGET_MODE_ALL_SCOPED) return true
        if (scopedPackages.isEmpty()) return true
        return scopedPackages.contains(packageName)
    }

    companion object {

        /** Below this alpha an overlay is considered part of an icon/gradient. */
        const val ALPHA_KEEP_THRESHOLD: Int = 40

        /** Surfaces above this luma are considered "light" and forced dark. */
        const val LIGHT_LUMINANCE_THRESHOLD: Int = 45

        /** Text below this luma is lifted so it stays readable on black. */
        const val DARK_TEXT_LUMINANCE_THRESHOLD: Int = 90

        val DEFAULT: SableSettings = SableSettings(
            palette = PureBlackPrefs.DEFAULT_PALETTE,
            overrideColor = PureBlackPrefs.COLOR_PITCH_BLACK,
            accentSeed = PureBlackPrefs.DEFAULT_CUSTOM_ACCENT_SEED,
            webViewForceDark = PureBlackPrefs.DEFAULT_WEBVIEW_FORCE_DARK,
            textContrastBoost = PureBlackPrefs.DEFAULT_TEXT_CONTRAST_BOOST,
            overlayOpacityPercent = PureBlackPrefs.DEFAULT_OVERLAY_OPACITY,
            enabled = PureBlackPrefs.DEFAULT_MODULE_ENABLED,
            targetMode = PureBlackPrefs.DEFAULT_TARGET_MODE,
            scopedPackages = emptySet()
        )

        /** Resolves a settings snapshot from any backend, never throwing. */
        fun from(source: PrefSource?): SableSettings {
            if (source == null) return DEFAULT
            return try {
                val palette = source.getInt(PureBlackPrefs.KEY_PALETTE, PureBlackPrefs.DEFAULT_PALETTE)
                val accentSeed = source.getInt(
                    PureBlackPrefs.KEY_CUSTOM_ACCENT_SEED,
                    PureBlackPrefs.DEFAULT_CUSTOM_ACCENT_SEED
                )
                SableSettings(
                    palette = palette,
                    overrideColor = colorFor(palette, accentSeed),
                    accentSeed = accentSeed,
                    webViewForceDark = source.getBoolean(
                        PureBlackPrefs.KEY_WEBVIEW_FORCE_DARK,
                        PureBlackPrefs.DEFAULT_WEBVIEW_FORCE_DARK
                    ),
                    textContrastBoost = source.getBoolean(
                        PureBlackPrefs.KEY_TEXT_CONTRAST_BOOST,
                        PureBlackPrefs.DEFAULT_TEXT_CONTRAST_BOOST
                    ),
                    overlayOpacityPercent = source.getInt(
                        PureBlackPrefs.KEY_OVERLAY_OPACITY,
                        PureBlackPrefs.DEFAULT_OVERLAY_OPACITY
                    ),
                    enabled = source.getBoolean(
                        PureBlackPrefs.KEY_MODULE_ENABLED,
                        PureBlackPrefs.DEFAULT_MODULE_ENABLED
                    ),
                    targetMode = source.getInt(
                        PureBlackPrefs.KEY_TARGET_MODE,
                        PureBlackPrefs.DEFAULT_TARGET_MODE
                    ),
                    scopedPackages = source.getStringSet(PureBlackPrefs.KEY_SCOPED_APPS, null)
                        ?: emptySet()
                )
            } catch (_: Throwable) {
                DEFAULT
            }
        }

        /** Resolves the palette colour for [palette]. */
        fun colorFor(palette: Int, accentSeed: Int): Int = when (palette) {
            PureBlackPrefs.PALETTE_DEEP_OBSIDIAN -> PureBlackPrefs.COLOR_DEEP_OBSIDIAN
            PureBlackPrefs.PALETTE_CUSTOM_ACCENT -> PaletteMath.customAccentColor(accentSeed)
            else -> PureBlackPrefs.COLOR_PITCH_BLACK
        }
    }
}
