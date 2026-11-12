/*
 * SablePureBlack — by @drkmuqeett
 * Package: com.drkmuqeett.pureblack
 *
 * Companion screen: true #000000 Material 3 theme, #121212 elevated cards, one
 * flat RecyclerView with DiffUtil updates and spring-driven interactions.
 *
 * Everything the user changes is written straight into the cross-process
 * preference file the payload reads, so a hook picks the new palette up on the
 * next window attach — no module restart, no device reboot.
 */
package com.drkmuqeett.pureblack

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.recyclerview.widget.DefaultItemAnimator
import androidx.recyclerview.widget.LinearLayoutManager
import com.drkmuqeett.pureblack.apps.ScopeStore
import com.drkmuqeett.pureblack.config.PaletteMath
import com.drkmuqeett.pureblack.config.PureBlackPrefs
import com.drkmuqeett.pureblack.config.SableSettings
import com.drkmuqeett.pureblack.config.SharedPrefSource
import com.drkmuqeett.pureblack.core.Probe
import com.drkmuqeett.pureblack.databinding.ActivityMainBinding
import com.drkmuqeett.pureblack.ui.HeaderState
import com.drkmuqeett.pureblack.ui.PaletteOption
import com.drkmuqeett.pureblack.ui.ProbeState
import com.drkmuqeett.pureblack.ui.Row
import com.drkmuqeett.pureblack.ui.SableAdapter
import com.drkmuqeett.pureblack.ui.SableCallbacks
import com.drkmuqeett.pureblack.ui.ScopeSheet
import com.drkmuqeett.pureblack.ui.ScopeState
import com.drkmuqeett.pureblack.ui.Springs

class MainActivity : AppCompatActivity(), SableCallbacks {

    private lateinit var binding: ActivityMainBinding
    private lateinit var adapter: SableAdapter

    private var settings: SableSettings = SableSettings.DEFAULT

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        configureWindow()

        adapter = SableAdapter(this)
        binding.contentList.apply {
            layoutManager = LinearLayoutManager(this@MainActivity)
            itemAnimator = DefaultItemAnimator().apply {
                // Cross-fades add nothing on a black background; plain add / move
                // animations keep the list cheap.
                supportsChangeAnimations = false
            }
            setHasFixedSize(false)
            adapter = this@MainActivity.adapter
        }

        supportFragmentManager.setFragmentResultListener(
            ScopeSheet.RESULT_KEY,
            this
        ) { _, _ ->
            // Re-render the scope summary row after the sheet closes.
            reloadSettings()
            render()
        }
    }

    override fun onResume() {
        super.onResume()
        reloadSettings()
        render()
    }

    private fun configureWindow() {
        // Edge-to-edge, pure black bars: colours and appearance flags only —
        // never brightness, never a colour matrix.
        WindowCompat.setDecorFitsSystemWindows(window, false)
        window.statusBarColor = Color.BLACK
        window.navigationBarColor = Color.BLACK
        WindowInsetsControllerCompat(window, window.decorView).apply {
            isAppearanceLightStatusBars = false
            isAppearanceLightNavigationBars = false
        }
    }

    private fun reloadSettings() {
        settings = SableSettings.from(SharedPrefSource(PureBlackPrefs.store(this)))
    }

    // ------------------------------------------------------------------- render

    private fun render() {
        val probe = Probe.inspect(this)
        val scope = ScopeStore.read(this)

        val paletteHex = PureBlackPrefs.paletteHex(settings.palette, settings.accentSeed)
        val header = HeaderState(
            probe = ProbeState(
                injected = probe.injected,
                managerLabel = probe.managerLabel,
                managerAvailable = true,
                ipcReady = probe.ipcReady
            ),
            paletteLabel = PureBlackPrefs.paletteLabel(settings.palette),
            paletteHex = paletteHex
        )

        val rows = buildList {
            add(Row.Header(header))
            add(Row.Section(getString(R.string.section_palette)))
            palettes().forEach { add(Row.Palette(it)) }
            if (settings.palette == PureBlackPrefs.PALETTE_CUSTOM_ACCENT) {
                add(Row.Accent(PaletteMath.ACCENT_PRESETS, settings.accentSeed))
            }
            add(Row.Section(getString(R.string.section_toggles)))
            add(
                Row.Toggle(
                    id = SableAdapter.TOGGLE_WEBVIEW,
                    title = getString(R.string.toggle_webview),
                    subtitle = getString(R.string.toggle_webview_sub),
                    checked = settings.webViewForceDark
                )
            )
            add(
                Row.Toggle(
                    id = SableAdapter.TOGGLE_TEXT,
                    title = getString(R.string.toggle_text),
                    subtitle = getString(R.string.toggle_text_sub),
                    checked = settings.textContrastBoost
                )
            )
            add(Row.Opacity(settings.overlayOpacityPercent))
            add(
                Row.ScopeHeader(
                    ScopeState(
                        apps = emptyList(),
                        query = "",
                        selection = scope.packages,
                        selectedMode = scope.isSelectedMode,
                        loading = false
                    )
                )
            )
            add(Row.Guide)
            add(Row.About)
        }
        adapter.submit(rows)
    }

    private fun palettes(): List<PaletteOption> = listOf(
        PaletteOption(
            id = PureBlackPrefs.PALETTE_PITCH_BLACK,
            title = getString(R.string.palette_pitch_black),
            subtitle = getString(R.string.palette_pitch_black_sub),
            swatch = PureBlackPrefs.COLOR_PITCH_BLACK,
            selected = settings.palette == PureBlackPrefs.PALETTE_PITCH_BLACK
        ),
        PaletteOption(
            id = PureBlackPrefs.PALETTE_DEEP_OBSIDIAN,
            title = getString(R.string.palette_obsidian),
            subtitle = getString(R.string.palette_obsidian_sub),
            swatch = PureBlackPrefs.COLOR_DEEP_OBSIDIAN,
            selected = settings.palette == PureBlackPrefs.PALETTE_DEEP_OBSIDIAN
        ),
        PaletteOption(
            id = PureBlackPrefs.PALETTE_CUSTOM_ACCENT,
            title = getString(R.string.palette_custom),
            subtitle = getString(R.string.palette_custom_sub),
            swatch = PaletteMath.customAccentColor(settings.accentSeed),
            selected = settings.palette == PureBlackPrefs.PALETTE_CUSTOM_ACCENT
        )
    )

    // ----------------------------------------------------------------- callbacks

    override fun onPaletteSelected(option: PaletteOption) {
        if (settings.palette == option.id) {
            // Re-tapping the active custom palette cycles its accent seed.
            if (option.id == PureBlackPrefs.PALETTE_CUSTOM_ACCENT) {
                cycleAccentSeed()
            }
            return
        }
        PureBlackPrefs.store(this).edit()
            .putInt(PureBlackPrefs.KEY_PALETTE, option.id)
            .putBoolean(PureBlackPrefs.KEY_MODULE_ENABLED, true)
            .apply()
        reloadSettings()
        render()
        pulseActivePalette(option)
        toast(getString(R.string.toast_saved))
    }

    override fun onAccentSelected(seed: Int) {
        PureBlackPrefs.store(this).edit()
            .putInt(PureBlackPrefs.KEY_CUSTOM_ACCENT_SEED, seed)
            .putInt(PureBlackPrefs.KEY_PALETTE, PureBlackPrefs.PALETTE_CUSTOM_ACCENT)
            .apply()
        reloadSettings()
        render()
    }

    override fun onToggleChanged(id: Int, checked: Boolean) {
        val editor = PureBlackPrefs.store(this).edit()
        when (id) {
            SableAdapter.TOGGLE_WEBVIEW ->
                editor.putBoolean(PureBlackPrefs.KEY_WEBVIEW_FORCE_DARK, checked)
            SableAdapter.TOGGLE_TEXT ->
                editor.putBoolean(PureBlackPrefs.KEY_TEXT_CONTRAST_BOOST, checked)
        }
        editor.putBoolean(PureBlackPrefs.KEY_MODULE_ENABLED, true).apply()
        reloadSettings()
        render()
    }

    override fun onOpacityChanged(percent: Int) {
        PureBlackPrefs.store(this).edit()
            .putInt(PureBlackPrefs.KEY_OVERLAY_OPACITY, percent.coerceIn(0, 100))
            .apply()
        settings = settings.copy(overlayOpacityPercent = percent.coerceIn(0, 100))
    }

    override fun onOpenScope() {
        if (supportFragmentManager.findFragmentByTag(ScopeSheet.TAG) != null) return
        ScopeSheet.newInstance().show(supportFragmentManager, ScopeSheet.TAG)
    }

    override fun onOpenManager() {
        val launched = Probe.launchFirst(this, Probe.managerIntents(this))
        if (!launched) {
            toast(getString(R.string.toast_manager_missing))
        }
    }

    override fun onOpenAppInfo() {
        val intent: Intent? = Probe.appInfoIntent(this)
        if (intent == null || !Probe.launchFirst(this, listOf(intent))) {
            toast(getString(R.string.toast_app_not_found))
        }
    }

    // ------------------------------------------------------------------- accents

    /** Cycles the custom accent seed through the built-in presets. */
    private fun cycleAccentSeed() {
        val presets = PaletteMath.ACCENT_PRESETS
        val currentIndex = presets.indexOf(settings.accentSeed).coerceAtLeast(0)
        val next = presets[(currentIndex + 1) % presets.size]
        PureBlackPrefs.store(this).edit()
            .putInt(PureBlackPrefs.KEY_CUSTOM_ACCENT_SEED, next)
            .putInt(PureBlackPrefs.KEY_PALETTE, PureBlackPrefs.PALETTE_CUSTOM_ACCENT)
            .apply()
        reloadSettings()
        render()
    }

    /** Soft spring pulse on the newly selected palette row. */
    private fun pulseActivePalette(option: PaletteOption) {
        // header + "Theme palette" section come first, then the three palettes.
        val index = 2 + palettes().indexOfFirst { it.id == option.id }
        if (index < 0) return
        binding.contentList.post {
            val holder = binding.contentList.findViewHolderForAdapterPosition(index) ?: return@post
            Springs.pulse(holder.itemView)
        }
    }

    private fun toast(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) {
            // Re-assert pure black bars after rotation or a return from the
            // manager. Colour only — brightness is never touched.
            window.statusBarColor = Color.BLACK
            window.navigationBarColor = Color.BLACK
            binding.contentList.post { animateHeaderIn() }
        }
    }

    /** Header fades in with a spring once per focus gain. */
    private fun animateHeaderIn() {
        val holder = binding.contentList.findViewHolderForAdapterPosition(0) ?: return
        val view: View = holder.itemView
        if (view.alpha < 1f) {
            view.alpha = 0f
            Springs.fadeIn(view)
        }
    }

}
