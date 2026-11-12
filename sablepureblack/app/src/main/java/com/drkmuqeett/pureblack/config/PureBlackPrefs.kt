/*
 * SablePureBlack — by @drkmuqeett
 * Package: com.drkmuqeett.pureblack
 *
 * Shared preference contract between the companion UI and the Xposed payload.
 *
 * The manifest declares <meta-data android:name="xposedsharedprefs"
 * android:value="true"/>, so LSPosed / Zygisk Vector routes the module's
 * preference file into every scoped app process. Both sides therefore share a
 * single source of truth, which is what makes live config updates possible
 * without restarting the hooked app.
 */
package com.drkmuqeett.pureblack.config

import android.content.Context
import android.content.SharedPreferences

object PureBlackPrefs {

    /** Preference file name — identical in the UI process and in hooked processes. */
    const val FILE_NAME: String = "sablepureblack_config"

    /** Module package, used to resolve preferences across processes. */
    const val MODULE_PACKAGE: String = "com.drkmuqeett.pureblack"

    // ------------------------------------------------------------ palette ids
    /** True pitch black — pixels physically off on OLED. */
    const val PALETTE_PITCH_BLACK: Int = 0

    /** Deep Obsidian — soft dark contrast. */
    const val PALETTE_DEEP_OBSIDIAN: Int = 1

    /** Custom Accent Dark — user-tinted near-black surface. */
    const val PALETTE_CUSTOM_ACCENT: Int = 2

    // ---------------------------------------------------------------- keys
    const val KEY_PALETTE: String = "palette_id"
    const val KEY_CUSTOM_ACCENT_SEED: String = "custom_accent_seed"
    const val KEY_WEBVIEW_FORCE_DARK: String = "webview_force_dark"
    const val KEY_TEXT_CONTRAST_BOOST: String = "text_contrast_boost"
    const val KEY_OVERLAY_OPACITY: String = "overlay_opacity_percent"
    const val KEY_MODULE_ENABLED: String = "module_enabled"
    const val KEY_TARGET_MODE: String = "target_mode"
    const val KEY_SCOPED_APPS: String = "scoped_apps"
    const val KEY_INSTALL_STAMP: String = "install_stamp"

    // ------------------------------------------------------------- defaults
    const val DEFAULT_PALETTE: Int = PALETTE_PITCH_BLACK
    const val DEFAULT_CUSTOM_ACCENT_SEED: Int = 0xFF3DD68C.toInt()
    const val DEFAULT_WEBVIEW_FORCE_DARK: Boolean = true
    const val DEFAULT_TEXT_CONTRAST_BOOST: Boolean = true
    const val DEFAULT_OVERLAY_OPACITY: Int = 100
    const val DEFAULT_MODULE_ENABLED: Boolean = true

    /** Target mode: force every scoped process (default) or an allow-list only. */
    const val TARGET_MODE_ALL_SCOPED: Int = 0

    /** Target mode: only packages present in KEY_SCOPED_APPS. */
    const val TARGET_MODE_SELECTED: Int = 1

    const val DEFAULT_TARGET_MODE: Int = TARGET_MODE_ALL_SCOPED

    /** Brand colours used by the UI and by the palette previews. */
    const val COLOR_PITCH_BLACK: Int = 0xFF000000.toInt()
    const val COLOR_DEEP_OBSIDIAN: Int = 0xFF0A0A0A.toInt()

    /** Crisp off-white applied to text that would otherwise be unreadable. */
    const val COLOR_TEXT_ON_BLACK: Int = 0xFFEAEAEA.toInt()

    /** Opens the shared preference store for the current process. */
    fun store(context: Context): SharedPreferences =
        context.applicationContext.getSharedPreferences(FILE_NAME, Context.MODE_PRIVATE)

    /** Human-readable palette label. */
    fun paletteLabel(palette: Int): String = when (palette) {
        PALETTE_DEEP_OBSIDIAN -> "Deep Obsidian"
        PALETTE_CUSTOM_ACCENT -> "Custom Accent Dark"
        else -> "Pitch Black"
    }

    /** Human-readable palette hex for subtitles and status tiles. */
    fun paletteHex(palette: Int, accentSeed: Int): String = when (palette) {
        PALETTE_DEEP_OBSIDIAN -> "#0A0A0A"
        PALETTE_CUSTOM_ACCENT -> PaletteMath.hex(PaletteMath.customAccentColor(accentSeed))
        else -> "#000000"
    }
}
