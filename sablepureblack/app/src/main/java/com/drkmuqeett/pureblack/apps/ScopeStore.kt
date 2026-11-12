/*
 * SablePureBlack — by @drkmuqeett
 * Package: com.drkmuqeett.pureblack
 *
 * Persistence for the per-app scope selection.
 *
 * The selection lives in the same cross-process preference file the payload
 * reads, so toggling an app here changes hook behaviour on the next launch of
 * that app with no extra plumbing. A `StringSet` keeps writes atomic and cheap.
 */
package com.drkmuqeett.pureblack.apps

import android.content.Context
import android.content.SharedPreferences
import com.drkmuqeett.pureblack.config.PureBlackPrefs

object ScopeStore {

    /** Immutable snapshot of what the user has selected. */
    data class Selection(
        val packages: Set<String>,
        val targetMode: Int
    ) {
        val isSelectedMode: Boolean
            get() = targetMode == PureBlackPrefs.TARGET_MODE_SELECTED

        fun contains(packageName: String): Boolean = packages.contains(packageName)

        fun count(): Int = packages.size
    }

    fun read(context: Context): Selection {
        val store = PureBlackPrefs.store(context)
        return Selection(
            packages = readSet(store),
            targetMode = store.getInt(PureBlackPrefs.KEY_TARGET_MODE, PureBlackPrefs.DEFAULT_TARGET_MODE)
        )
    }

    /** Adds or removes [packageName] and persists the change immediately. */
    fun toggle(context: Context, packageName: String): Selection {
        val store = PureBlackPrefs.store(context)
        val current = readSet(store)
        val updated = LinkedHashSet(current)
        if (!updated.remove(packageName)) {
            updated.add(packageName)
        }
        store.edit().putStringSet(PureBlackPrefs.KEY_SCOPED_APPS, updated).apply()
        return Selection(
            packages = updated,
            targetMode = store.getInt(PureBlackPrefs.KEY_TARGET_MODE, PureBlackPrefs.DEFAULT_TARGET_MODE)
        )
    }

    /** Replaces the whole selection (used by "select all" / "clear"). */
    fun write(context: Context, packages: Set<String>): Selection {
        val store = PureBlackPrefs.store(context)
        store.edit().putStringSet(PureBlackPrefs.KEY_SCOPED_APPS, LinkedHashSet(packages)).apply()
        return Selection(
            packages = packages,
            targetMode = store.getInt(PureBlackPrefs.KEY_TARGET_MODE, PureBlackPrefs.DEFAULT_TARGET_MODE)
        )
    }

    /**
     * Switches between "every app in the LSPosed scope" and "only my list".
     * Selected mode starts empty, which the payload treats as "all", so flipping
     * the switch can never accidentally disable the module.
     */
    fun setTargetMode(context: Context, targetMode: Int) {
        PureBlackPrefs.store(context).edit()
            .putInt(PureBlackPrefs.KEY_TARGET_MODE, targetMode)
            .apply()
    }

    private fun readSet(store: SharedPreferences): Set<String> = try {
        store.getStringSet(PureBlackPrefs.KEY_SCOPED_APPS, null)
            ?.filterIsInstance<String>()
            ?.toSet()
            ?: emptySet()
    } catch (_: Throwable) {
        emptySet()
    }
}
