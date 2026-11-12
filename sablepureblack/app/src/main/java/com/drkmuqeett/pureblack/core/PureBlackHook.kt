/*
 * SablePureBlack — by @drkmuqeett
 * Package: com.drkmuqeett.pureblack
 *
 * Xposed / LSPosed entry point for SablePureBlack.
 *
 * Pipeline
 * ========
 *  1. Live config — XSharedPreferences bound through the `xposedsharedprefs`
 *     manifest bridge, with a `hasFileChanged()` poll on every window attach so
 *     palette / toggle changes apply without restarting the hooked app.
 *  2. Window & DecorView — Activity.onCreate / onResume / Dialog.show force a
 *     black window background, black status + navigation bars and
 *     decorView.isForceDarkAllowed = true.
 *  3. Luminance/alpha engine — setBackgroundColor, setBackground and
 *     setBackgroundResource are hooked; light surfaces (luma > 45) become the
 *     palette colour, transparent overlays (alpha < 40) survive untouched, and
 *     source alpha is re-applied so translucent white cards become translucent
 *     black cards instead of solid blocks. Resource verdicts are cached by id.
 *  4. WebView + text — FORCE_DARK_ON on every WebView; dark text (luma < 90) is
 *     lifted to #EAEAEA.
 *  5. Zero hardware interference — Settings.System.SCREEN_BRIGHTNESS,
 *     SCREEN_BRIGHTNESS_MODE, WindowManager.LayoutParams.screenBrightness,
 *     SurfaceFlinger colour matrices and display calibration are never read,
 *     never written and never hooked.
 */
package com.drkmuqeett.pureblack.core

import android.app.Activity
import android.app.Application
import android.app.Dialog
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.Drawable
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.view.WindowManager
import android.webkit.WebSettings
import android.webkit.WebView
import android.widget.EditText
import android.widget.TextView
import com.drkmuqeett.pureblack.config.PaletteMath
import com.drkmuqeett.pureblack.config.PrefSource
import com.drkmuqeett.pureblack.config.PureBlackPrefs
import com.drkmuqeett.pureblack.config.SableSettings
import de.robv.android.xposed.IXposedHookLoadPackage
import de.robv.android.xposed.IXposedHookZygoteInit
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XC_MethodHook.MethodHookParam
import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.XposedHelpers
import de.robv.android.xposed.callbacks.XC_LoadPackage
import java.io.File
import java.util.concurrent.ConcurrentHashMap

/** Payload logger — every diagnostic goes through the LSPosed log. */
internal object HookLog {

    private const val TAG = "SablePureBlack"

    fun d(message: String) {
        runCatching { XposedBridge.log("$TAG: $message") }
    }

    fun e(message: String, throwable: Throwable) {
        runCatching {
            XposedBridge.log("$TAG: $message :: ${android.util.Log.getStackTraceString(throwable)}")
        }
    }
}

@Suppress("DEPRECATION")
class PureBlackHook : IXposedHookLoadPackage, IXposedHookZygoteInit {

    // ------------------------------------------------------------------ state

    /** Current configuration snapshot for this process. */
    @Volatile
    private var settings: SableSettings = SableSettings.DEFAULT

    /** Live cross-process preference bridge, when the framework provides it. */
    @Volatile
    private var prefBridge: XPrefBridge? = null

    /** Resource-id → "is this drawable light?" verdict cache. */
    private val resourceVerdicts: MutableMap<Int, Boolean> = ConcurrentHashMap()

    /** Guards against recursion from our own setBackgroundColor calls. */
    private val selfCall: ThreadLocal<Boolean> = ThreadLocal.withInitial { false }

    /** Throttles preference re-reads so the poll stays off the frame budget. */
    @Volatile
    private var lastRefreshAt: Long = 0L

    // -------------------------------------------------------------- lifecycle

    override fun initZygote(startupParam: IXposedHookZygoteInit.StartupParam) {
        // Nothing is hooked in the zygote: the module must never leak into
        // system_server or unrelated processes. Work happens per package.
        HookLog.d("initZygote(${startupParam.modulePath})")
    }

    override fun handleLoadPackage(lpparam: XC_LoadPackage.LoadPackageParam) {
        val target = lpparam.packageName ?: return
        if (target == PureBlackPrefs.MODULE_PACKAGE || target == DEBUG_PACKAGE) {
            // The companion UI is already true black; never re-hook ourselves.
            return
        }

        try {
            attachPreferences(lpparam)
            if (!settings.enabled) {
                HookLog.d("disabled in prefs — skipping $target")
                return
            }
            if (!settings.shouldProcess(target)) {
                HookLog.d("not in selected scope — skipping $target")
                return
            }

            hookActivityLifecycle()
            hookBackgroundColor()
            hookBackgroundDrawable()
            hookBackgroundResource()
            hookTextColors()
            hookWebView()
            hookDialogs()
            hookApplicationCreate()

            HookLog.d(
                "hooks attached for $target " +
                    "(palette=${PureBlackPrefs.paletteLabel(settings.palette)}, " +
                    "opacity=${settings.overlayOpacityPercent}%, " +
                    "webview=${settings.webViewForceDark}, text=${settings.textContrastBoost})"
            )
        } catch (t: Throwable) {
            HookLog.e("failed to attach hooks for $target", t)
        }
    }

    /**
     * Binds the cross-process preference bridge.
     *
     * With `xposedsharedprefs=true` LSPosed resolves
     * XSharedPreferences(modulePackage, fileName) to the module's own file, so
     * the payload always observes exactly what the companion UI wrote.
     */
    private fun attachPreferences(lpparam: XC_LoadPackage.LoadPackageParam) {
        val bridge = XPrefBridge.create(lpparam.classLoader)
        prefBridge = bridge
        settings = SableSettings.from(bridge ?: fallbackPrefSource(lpparam))
        lastRefreshAt = System.currentTimeMillis()
    }

    /**
     * Last-resort backend: read the module's XML preference file directly.
     * Works on setups where the framework class is not injected but the file is
     * readable; otherwise degrades to defaults.
     */
    private fun fallbackPrefSource(lpparam: XC_LoadPackage.LoadPackageParam): PrefSource? = try {
        val context = android.app.AndroidAppHelper.currentApplication()
        if (context == null) {
            null
        } else {
            @Suppress("UNCHECKED_CAST")
            val clazz = Class.forName(
                "com.drkmuqeett.pureblack.config.SharedPrefSource",
                false,
                lpparam.classLoader
            )
            val constructor = clazz.getConstructor(android.content.SharedPreferences::class.java)
            constructor.newInstance(
                context.getSharedPreferences(PureBlackPrefs.FILE_NAME, android.content.Context.MODE_PRIVATE)
            ) as? PrefSource
        }
    } catch (_: Throwable) {
        null
    }

    /**
     * Re-reads configuration at most once every [REFRESH_INTERVAL_MS].
     * Called from every window attach, which is frequent enough to feel instant
     * and rare enough to be free.
     */
    private fun refreshSettingsIfStale() {
        val now = System.currentTimeMillis()
        if (now - lastRefreshAt < REFRESH_INTERVAL_MS) return
        lastRefreshAt = now
        val bridge = prefBridge ?: return
        try {
            if (!bridge.hasFileChanged()) return
            bridge.reload()
            val updated = SableSettings.from(bridge)
            if (updated != settings) {
                settings = updated
                resourceVerdicts.clear()
                HookLog.d("live config updated: palette=${PureBlackPrefs.paletteLabel(updated.palette)}")
            }
        } catch (t: Throwable) {
            HookLog.e("refreshSettings", t)
        }
    }

    // ------------------------------------------------ 2. window / decor view

    private fun hookActivityLifecycle() {
        val onCreate = object : XC_MethodHook() {
            override fun afterHookedMethod(param: MethodHookParam) {
                val activity = param.thisObject as? Activity ?: return
                applyToActivity(activity, sweepTree = true)
            }
        }
        val onResume = object : XC_MethodHook() {
            override fun afterHookedMethod(param: MethodHookParam) {
                val activity = param.thisObject as? Activity ?: return
                applyToActivity(activity, sweepTree = false)
            }
        }
        safeHook("Activity.onCreate") {
            XposedHelpers.findAndHookMethod(Activity::class.java, "onCreate", Bundle::class.java, onCreate)
        }
        safeHook("Activity.onResume") {
            XposedHelpers.findAndHookMethod(Activity::class.java, "onResume", onResume)
        }
    }

    private fun applyToActivity(activity: Activity, sweepTree: Boolean) {
        if (!settings.enabled) return
        try {
            refreshSettingsIfStale()

            val window: Window = activity.window ?: return
            paintWindow(window)

            val decor: View = window.decorView
            forceDarkOn(decor)

            if (sweepTree) {
                // After inflation, then again for async content (lists, ads,
                // image loaders) that lands a few frames later.
                decor.post { sweep(decor) }
                decor.postDelayed({ sweep(decor) }, LATE_SWEEP_DELAY_MS)
            }
        } catch (t: Throwable) {
            HookLog.e("applyToActivity(${activity.javaClass.name})", t)
        }
    }

    /** Window-level black: background, status bar, navigation bar. */
    private fun paintWindow(window: Window) {
        val surface = settings.overrideColor
        try {
            window.setBackgroundDrawable(ColorDrawable(surface))
        } catch (_: Throwable) {
            // Some OEM windows reject a background swap; the decor sweep still
            // guarantees a dark result.
        }
        try {
            window.addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS)
            window.clearFlags(WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS)
            window.clearFlags(WindowManager.LayoutParams.FLAG_TRANSLUCENT_NAVIGATION)
            window.statusBarColor = Color.BLACK
            window.navigationBarColor = Color.BLACK
        } catch (t: Throwable) {
            HookLog.e("paintWindow bars", t)
        }
        try {
            // Light bar icons on black bars. Appearance flags only — no
            // brightness and no colour-matrix involvement whatsoever.
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                window.insetsController?.setSystemBarsAppearance(
                    0,
                    android.view.WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS or
                        android.view.WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS
                )
            } else {
                @Suppress("DEPRECATION")
                window.decorView.systemUiVisibility =
                    window.decorView.systemUiVisibility and
                    View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR.inv() and
                    View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR.inv()
            }
        } catch (_: Throwable) {
            // Cosmetic only — never fatal.
        }
    }

    /** Enables the framework's own force-dark on a subtree root. */
    private fun forceDarkOn(view: View) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return
        try {
            view.isForceDarkAllowed = true
        } catch (t: Throwable) {
            HookLog.e("forceDarkOn", t)
        }
    }

    // ------------------------------------- 3. luminance / alpha-aware engine

    /** Hooks View.setBackgroundColor(int). */
    private fun hookBackgroundColor() {
        val hook = object : XC_MethodHook() {
            override fun beforeHookedMethod(param: MethodHookParam) {
                if (!settings.enabled || selfCall.get() == true) return
                val original = param.args.firstOrNull() as? Int ?: return
                val replacement = remapBackgroundColor(original) ?: return
                param.args[0] = replacement
            }
        }
        safeHook("View.setBackgroundColor") {
            XposedHelpers.findAndHookMethod(
                View::class.java,
                "setBackgroundColor",
                Int::class.javaPrimitiveType,
                hook
            )
        }
    }

    /** Hooks View.setBackground(Drawable) — flat coloured drawables only. */
    private fun hookBackgroundDrawable() {
        val hook = object : XC_MethodHook() {
            override fun beforeHookedMethod(param: MethodHookParam) {
                if (!settings.enabled || selfCall.get() == true) return
                val drawable = param.args.firstOrNull() as? Drawable ?: return
                if (!isLightDrawable(drawable)) return
                param.args[0] = ColorDrawable(surfaceColorForDrawable(drawable))
            }
        }
        safeHook("View.setBackground") {
            XposedHelpers.findAndHookMethod(View::class.java, "setBackground", Drawable::class.java, hook)
        }
    }

    /** Hooks View.setBackgroundResource(int) with a cached resource verdict. */
    private fun hookBackgroundResource() {
        val hook = object : XC_MethodHook() {
            override fun beforeHookedMethod(param: MethodHookParam) {
                if (!settings.enabled || selfCall.get() == true) return
                val view = param.thisObject as? View ?: return
                val resId = param.args.firstOrNull() as? Int ?: return
                if (resId == 0 || resId == View.NO_ID) return
                val context = view.context ?: return
                if (!isLightResource(context, resId)) return

                // Skip the framework call entirely and paint our surface.
                param.setResult(null)
                selfCall.set(true)
                try {
                    view.setBackgroundColor(settings.overrideColor)
                } finally {
                    selfCall.set(false)
                }
            }
        }
        safeHook("View.setBackgroundResource") {
            XposedHelpers.findAndHookMethod(
                View::class.java,
                "setBackgroundResource",
                Int::class.javaPrimitiveType,
                hook
            )
        }
    }

    /**
     * Decides whether a background colour must be replaced.
     *
     * @return the replacement colour, or null to leave the call untouched.
     */
    private fun remapBackgroundColor(color: Int): Int? {
        val alpha = PaletteMath.alpha(color)

        // Transparent / near-transparent overlays are part of icon, stroke and
        // image composition — never touch alpha < 40.
        if (alpha < SableSettings.ALPHA_KEEP_THRESHOLD) return null

        // Already dark: keep the app's authored contrast.
        val luma = PaletteMath.luminance(color)
        if (luma <= SableSettings.LIGHT_LUMINANCE_THRESHOLD) return null

        // Alpha-aware scaling: a 60 % white card becomes a 60 % black card,
        // scaled by the user's global opacity, never a solid opaque slab.
        val scaled = ((alpha.toLong() * settings.forcedAlpha) / 255L).toInt()
        val target = PaletteMath.withAlpha(settings.overrideColor, scaled.coerceIn(0, 255))
        return if (target == color) null else target
    }

    /** Surface colour for a light drawable, honouring the drawable's own alpha. */
    private fun surfaceColorForDrawable(drawable: Drawable): Int {
        val sourceAlpha = drawable.alpha.takeIf { it in 1..255 } ?: 255
        val scaled = ((sourceAlpha.toLong() * settings.forcedAlpha) / 255L).toInt()
        return PaletteMath.withAlpha(settings.overrideColor, scaled.coerceIn(0, 255))
    }

    /** True when the drawable paints a flat, light, non-transparent colour. */
    private fun isLightDrawable(drawable: Drawable): Boolean {
        val color = (drawable as? ColorDrawable)?.color ?: return false
        if (PaletteMath.alpha(color) < SableSettings.ALPHA_KEEP_THRESHOLD) return false
        return PaletteMath.luminance(color) > SableSettings.LIGHT_LUMINANCE_THRESHOLD
    }

    /**
     * Resolves and classifies a background resource. Resource ids are stable for
     * the lifetime of the process, so the verdict is cached: a recycling list of
     * 200 rows resolves one drawable, not two hundred.
     */
    private fun isLightResource(context: android.content.Context, resId: Int): Boolean {
        resourceVerdicts[resId]?.let { return it }
        val verdict = try {
            val drawable = context.getDrawable(resId)
            drawable != null && isLightDrawable(drawable)
        } catch (_: Throwable) {
            false
        }
        resourceVerdicts[resId] = verdict
        return verdict
    }

    /** Recursively normalises backgrounds and text colour under [root]. */
    private fun sweep(root: View) {
        if (!settings.enabled) return
        try {
            visit(root, 0)
        } catch (t: Throwable) {
            HookLog.e("sweep", t)
        }
    }

    private fun visit(view: View, depth: Int) {
        if (depth > MAX_TREE_DEPTH) return

        val background = view.background
        if (background != null && isLightDrawable(background)) {
            try {
                view.setBackgroundColor(settings.overrideColor)
            } catch (t: Throwable) {
                HookLog.e("sweep setBackgroundColor", t)
            }
        }

        liftText(view)
        if (view is WebView) applyWebViewDark(view)

        if (view is ViewGroup) {
            for (index in 0 until view.childCount) {
                val child = view.getChildAt(index) ?: continue
                visit(child, depth + 1)
            }
        }
    }

    // ------------------------------------------ 4. WebView & text elevation

    /** Hooks TextView.setTextColor(int) so post-sweep text is lifted too. */
    private fun hookTextColors() {
        val hook = object : XC_MethodHook() {
            override fun beforeHookedMethod(param: MethodHookParam) {
                if (!settings.enabled || !settings.textContrastBoost) return
                val color = param.args.firstOrNull() as? Int ?: return
                if (PaletteMath.alpha(color) < TEXT_ALPHA_FLOOR) return
                if (PaletteMath.luminance(color) >= SableSettings.DARK_TEXT_LUMINANCE_THRESHOLD) return
                param.args[0] = PureBlackPrefs.COLOR_TEXT_ON_BLACK
            }
        }
        safeHook("TextView.setTextColor") {
            XposedHelpers.findAndHookMethod(
                TextView::class.java,
                "setTextColor",
                Int::class.javaPrimitiveType,
                hook
            )
        }
    }

    /** Lifts genuinely unreadable text on a black surface to #EAEAEA. */
    private fun liftText(view: View) {
        if (!settings.textContrastBoost || view !is TextView) return
        try {
            val current = view.currentTextColor
            if (PaletteMath.alpha(current) < TEXT_ALPHA_FLOOR) return
            if (PaletteMath.luminance(current) >= SableSettings.DARK_TEXT_LUMINANCE_THRESHOLD) return

            if (view is EditText) {
                runCatching { view.setHintTextColor(PureBlackPrefs.COLOR_TEXT_ON_BLACK) }
            }
            view.setTextColor(PureBlackPrefs.COLOR_TEXT_ON_BLACK)
            view.setLinkTextColor(PureBlackPrefs.COLOR_TEXT_ON_BLACK)
        } catch (t: Throwable) {
            HookLog.e("liftText", t)
        }
    }

    /** Forces FORCE_DARK_ON on WebViews and their settings objects. */
    private fun hookWebView() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            safeHook("WebSettings.setForceDark") {
                XposedHelpers.findAndHookMethod(
                    WebSettings::class.java,
                    "setForceDark",
                    Int::class.javaPrimitiveType,
                    object : XC_MethodHook() {
                        override fun beforeHookedMethod(param: MethodHookParam) {
                            if (!settings.enabled || !settings.webViewForceDark) return
                            // FORCE_DARK_ON == 2: a page-level opt-out is
                            // overridden so light HTML can never stay white.
                            param.args[0] = WebSettings.FORCE_DARK_ON
                        }
                    }
                )
            }
        }

        safeHook("WebView.onAttachedToWindow") {
            XposedHelpers.findAndHookMethod(
                WebView::class.java,
                "onAttachedToWindow",
                object : XC_MethodHook() {
                    override fun afterHookedMethod(param: MethodHookParam) {
                        if (!settings.enabled) return
                        val webView = param.thisObject as? WebView ?: return
                        applyWebViewDark(webView)
                    }
                }
            )
        }
    }

    private fun applyWebViewDark(webView: WebView) {
        if (!settings.webViewForceDark) return
        try {
            webView.setBackgroundColor(settings.overrideColor)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                webView.settings.forceDark = WebSettings.FORCE_DARK_ON
            }
        } catch (t: Throwable) {
            HookLog.e("applyWebViewDark", t)
        }
    }

    // ----------------------------------------------------- dialogs / application

    /** Dialogs and popups own their Window — paint those as well. */
    private fun hookDialogs() {
        safeHook("Dialog.show") {
            XposedHelpers.findAndHookMethod(
                Dialog::class.java,
                "show",
                object : XC_MethodHook() {
                    override fun afterHookedMethod(param: MethodHookParam) {
                        if (!settings.enabled) return
                        val dialog = param.thisObject as? Dialog ?: return
                        try {
                            val window = dialog.window ?: return
                            paintWindow(window)
                            val decor = window.decorView
                            forceDarkOn(decor)
                            sweep(decor)
                        } catch (t: Throwable) {
                            HookLog.e("Dialog.show", t)
                        }
                    }
                }
            )
        }
    }

    /** Re-reads configuration when the host app starts. */
    private fun hookApplicationCreate() {
        safeHook("Application.onCreate") {
            XposedHelpers.findAndHookMethod(
                Application::class.java,
                "onCreate",
                object : XC_MethodHook() {
                    override fun afterHookedMethod(param: MethodHookParam) {
                        lastRefreshAt = 0L
                        refreshSettingsIfStale()
                    }
                }
            )
        }
    }

    // ------------------------------------------------------------ diagnostics

    private inline fun safeHook(label: String, block: () -> Unit) {
        try {
            block()
        } catch (t: Throwable) {
            HookLog.e("hook $label", t)
        }
    }

    // ------------------------------------------------ XSharedPreferences bridge

    /**
     * Thin reflective wrapper over `de.robv.android.xposed.XSharedPreferences`.
     *
     * Reflection is deliberate: `:core` must not require the framework class at
     * link time, so a build without the bridge (or a future API change) degrades
     * to the fallback source or to defaults instead of crashing the host app.
     */
    private class XPrefBridge private constructor(
        private val delegate: Any
    ) : PrefSource {

        override fun getInt(key: String, defaultValue: Int): Int =
            invoke("getInt", key, defaultValue) { (it as? Number)?.toInt() }

        override fun getBoolean(key: String, defaultValue: Boolean): Boolean =
            invoke("getBoolean", key, defaultValue) { it as? Boolean }

        override fun getString(key: String, defaultValue: String?): String? {
            // The framework signature takes a non-null default, so a null caller
            // default is widened to "" and converted back to null on miss.
            val requested = defaultValue ?: ""
            val result = invoke("getString", key, requested) { it as? String }
            return if (result == requested && defaultValue == null) null else result
        }

        override fun getStringSet(
            key: String,
            defaultValue: MutableSet<String>?
        ): MutableSet<String>? = try {
            // java.util.Set is the framework's own signature (XSharedPreferences
            // implements SharedPreferences), so it must stay java-typed here.
            @Suppress("PLATFORM_CLASS_MAPPED_TO_KOTLIN")
            val setType = java.util.Set::class.java
            val method = delegate.javaClass.getMethod("getStringSet", String::class.java, setType)
            @Suppress("UNCHECKED_CAST")
            val result = method.invoke(delegate, key, defaultValue ?: emptySet<String>())
            (result as? Set<*>)?.filterIsInstance<String>()?.toMutableSet() ?: defaultValue
        } catch (_: Throwable) {
            defaultValue
        }

        /** True when the framework reports the file changed since last read. */
        fun hasFileChanged(): Boolean = try {
            delegate.javaClass.getMethod("hasFileChanged").invoke(delegate) as? Boolean ?: false
        } catch (_: Throwable) {
            false
        }

        /** Forces the bridge to re-read the preference file. */
        fun reload() {
            runCatching { delegate.javaClass.getMethod("reload").invoke(delegate) }
        }

        private fun <T : Any> invoke(
            methodName: String,
            key: String,
            fallback: T,
            convert: (Any?) -> T?
        ): T = try {
            val parameterType = when (fallback) {
                is Int -> Int::class.javaPrimitiveType
                is Boolean -> Boolean::class.javaPrimitiveType
                else -> String::class.java
            }
            val raw = delegate.javaClass
                .getMethod(methodName, String::class.java, parameterType)
                .invoke(delegate, key, fallback)
            convert(raw) ?: fallback
        } catch (_: Throwable) {
            fallback
        }

        companion object {

            private const val XSHARED_PREFERENCES = "de.robv.android.xposed.XSharedPreferences"

            /** Builds a bridge, or null when the framework class is unavailable. */
            fun create(classLoader: ClassLoader?): XPrefBridge? {
                val clazz = load(classLoader) ?: return null
                val attempts: List<() -> Any?> = listOf(
                    {
                        clazz.getConstructor(String::class.java, String::class.java)
                            .newInstance(PureBlackPrefs.MODULE_PACKAGE, PureBlackPrefs.FILE_NAME)
                    },
                    {
                        clazz.getConstructor(File::class.java)
                            .newInstance(modulePrefsFile(PureBlackPrefs.FILE_NAME))
                    },
                    {
                        clazz.getConstructor(String::class.java)
                            .newInstance(PureBlackPrefs.MODULE_PACKAGE)
                    }
                )
                for (build in attempts) {
                    try {
                        val instance = build() ?: continue
                        runCatching { clazz.getMethod("reload").invoke(instance) }
                        return XPrefBridge(instance)
                    } catch (_: Throwable) {
                        // Try the next constructor shape.
                    }
                }
                return null
            }

            private fun load(classLoader: ClassLoader?): Class<*>? {
                if (classLoader != null) {
                    try {
                        return Class.forName(XSHARED_PREFERENCES, false, classLoader)
                    } catch (_: Throwable) {
                        // Fall through to our own loader.
                    }
                }
                return try {
                    Class.forName(XSHARED_PREFERENCES, false, PureBlackHook::class.java.classLoader)
                } catch (_: Throwable) {
                    null
                }
            }

            /** /data/data/<module>/shared_prefs/<file>.xml */
            private fun modulePrefsFile(fileName: String): File = File(
                File(File(Environment.getDataDirectory(), "data"), PureBlackPrefs.MODULE_PACKAGE),
                "shared_prefs/$fileName.xml"
            )
        }
    }

    companion object {
        /** Debug variant package — matched so a debug build never hooks itself. */
        private const val DEBUG_PACKAGE = "com.drkmuqeett.pureblack.debug"
        private const val MAX_TREE_DEPTH = 96
        private const val LATE_SWEEP_DELAY_MS = 350L
        private const val REFRESH_INTERVAL_MS = 600L
        private const val TEXT_ALPHA_FLOOR = 0x40
    }
}
