/*
 * SablePureBlack — by @drkmuqeett
 * Package: com.drkmuqeett.pureblack
 */
package com.drkmuqeett.pureblack

import android.app.Application
import com.drkmuqeett.pureblack.config.PureBlackPrefs

class SableApplication : Application() {

    override fun onCreate() {
        super.onCreate()
        seedDefaults()
    }

    /**
     * Writes every preference with its default on first launch, so the payload
     * (which reads this same file from other processes) never has to guess and
     * a fresh install behaves exactly like a configured one.
     */
    private fun seedDefaults() {
        try {
            val store = PureBlackPrefs.store(this)
            if (store.contains(PureBlackPrefs.KEY_INSTALL_STAMP)) return
            store.edit()
                .putInt(PureBlackPrefs.KEY_PALETTE, PureBlackPrefs.DEFAULT_PALETTE)
                .putInt(
                    PureBlackPrefs.KEY_CUSTOM_ACCENT_SEED,
                    PureBlackPrefs.DEFAULT_CUSTOM_ACCENT_SEED
                )
                .putBoolean(
                    PureBlackPrefs.KEY_WEBVIEW_FORCE_DARK,
                    PureBlackPrefs.DEFAULT_WEBVIEW_FORCE_DARK
                )
                .putBoolean(
                    PureBlackPrefs.KEY_TEXT_CONTRAST_BOOST,
                    PureBlackPrefs.DEFAULT_TEXT_CONTRAST_BOOST
                )
                .putInt(
                    PureBlackPrefs.KEY_OVERLAY_OPACITY,
                    PureBlackPrefs.DEFAULT_OVERLAY_OPACITY
                )
                .putBoolean(
                    PureBlackPrefs.KEY_MODULE_ENABLED,
                    PureBlackPrefs.DEFAULT_MODULE_ENABLED
                )
                .putInt(PureBlackPrefs.KEY_TARGET_MODE, PureBlackPrefs.DEFAULT_TARGET_MODE)
                .putStringSet(PureBlackPrefs.KEY_SCOPED_APPS, emptySet())
                .putLong(PureBlackPrefs.KEY_INSTALL_STAMP, System.currentTimeMillis())
                .apply()
        } catch (_: Throwable) {
            // Never block app startup on preferences.
        }
    }
}
