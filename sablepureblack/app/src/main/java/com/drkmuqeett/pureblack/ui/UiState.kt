/*
 * SablePureBlack — by @drkmuqeett
 * Package: com.drkmuqeett.pureblack
 *
 * Immutable UI state for the companion screen. Every value the screen renders
 * lives here, so a rebind is a pure function of state and DiffUtil can compare
 * rows cheaply.
 */
package com.drkmuqeett.pureblack.ui

import androidx.annotation.ColorInt
import com.drkmuqeett.pureblack.apps.AppEntry

/** Live LSPosed injection probe result. */
data class ProbeState(
    /** Framework classes are injected in this process. */
    val injected: Boolean,
    /** Manager package label resolved on this device. */
    val managerLabel: String,
    /** True when a manager app is installed and could be launched. */
    val managerAvailable: Boolean,
    /** Preference bridge readable from this process (IPC healthy). */
    val ipcReady: Boolean
) {
    val statusLabel: String
        get() = if (injected) "Active" else "Not Activated in LSPosed"
}

/** Header banner content. */
data class HeaderState(
    val probe: ProbeState,
    val paletteLabel: String,
    val paletteHex: String
)

/** One palette row in the theme switcher. */
data class PaletteOption(
    val id: Int,
    val title: String,
    val subtitle: String,
    @ColorInt val swatch: Int,
    val selected: Boolean
)

/** Scope selector content. */
data class ScopeState(
    val apps: List<AppEntry>,
    val query: String,
    val selection: Set<String>,
    val selectedMode: Boolean,
    val loading: Boolean
) {
    val totalCount: Int
        get() = apps.size

    val selectedCount: Int
        get() = selection.size
}

/** Sealed row model consumed by [SableAdapter]. */
sealed interface Row {
    data class Header(val state: HeaderState) : Row
    data class Section(val title: String) : Row
    data class Palette(val option: PaletteOption) : Row
    data class Toggle(val id: Int, val title: String, val subtitle: String, val checked: Boolean) : Row
    data class Accent(val seeds: List<Int>, val selectedSeed: Int) : Row
    data class Opacity(val percent: Int) : Row
    data class ScopeHeader(val state: ScopeState) : Row
    data object Guide : Row
    data object About : Row
}
