/*
 * SablePureBlack — by @drkmuqeett
 * Package: com.drkmuqeett.pureblack
 *
 * Live LSPosed / Zygisk Vector injection probe for the companion UI.
 *
 * A class in the companion app can only see `xposed` types when the framework
 * has injected them into *this* process — which happens only when the module is
 * enabled **and** scoped for its own package. The preference bridge is probed the
 * same way, so the header reflects real IPC health rather than a guess.
 *
 * No root, no `su`, no shell.
 */
package com.drkmuqeett.pureblack.core

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.content.pm.PackageManager

/** Result of probing the framework in the current process. */
data class InjectionProbe(
    val injected: Boolean,
    val ipcReady: Boolean,
    val managerPackage: String?,
    val managerLabel: String
) {
    val managerAvailable: Boolean
        get() = managerPackage != null
}

object Probe {

    private const val XPOSED_BRIDGE = "de.robv.android.xposed.XposedBridge"
    private const val XSHARED_PREFERENCES = "de.robv.android.xposed.XSharedPreferences"

    /** Manager packages, in preference order. */
    private val KNOWN_MANAGERS: List<Pair<String, String>> = listOf(
        "org.lsposed.manager" to "LSPosed Manager",
        "io.github.lsposed.manager" to "LSPosed Manager",
        "org.lsposed.lspatch" to "LSPatch",
        "org.vector.manager" to "Vector Manager",
        "io.github.vector.manager" to "Vector Manager",
        "org.meowcat.edxposed.manager" to "EdXposed Manager",
        "de.robv.android.xposed.installer" to "Xposed Installer",
        "com.solohsu.android.edxp.manager" to "EdXposed Manager"
    )

    /** Probes the current process and the installed managers. */
    fun inspect(context: Context?): InjectionProbe {
        val injected = isFrameworkInjected()
        val manager = findManager(context)
        return InjectionProbe(
            injected = injected,
            ipcReady = injected && canLoadXSharedPreferences(),
            managerPackage = manager?.first,
            managerLabel = manager?.second ?: "LSPosed Manager"
        )
    }

    /** True when framework classes are resolvable from this app's loader. */
    fun isFrameworkInjected(): Boolean = isLoadable(XPOSED_BRIDGE) || hasFrameworkStackFrame()

    /** True when the cross-process preference bridge is usable. */
    private fun canLoadXSharedPreferences(): Boolean = isLoadable(XSHARED_PREFERENCES)

    private fun isLoadable(className: String): Boolean = try {
        Class.forName(className, false, Probe::class.java.classLoader)
        true
    } catch (_: Throwable) {
        false
    }

    private fun hasFrameworkStackFrame(): Boolean = try {
        Throwable().stackTrace.any { frame ->
            val name = frame.className
            name.startsWith("de.robv.android.xposed") ||
                name.startsWith("org.lsposed") ||
                name.startsWith("io.github.lsposed") ||
                name.contains("XposedBridge")
        }
    } catch (_: Throwable) {
        false
    }

    /** First installed manager package + its display label. */
    private fun findManager(context: Context?): Pair<String, String>? {
        if (context == null) return null
        val pm = context.packageManager
        for ((packageName, label) in KNOWN_MANAGERS) {
            val installed = try {
                pm.getPackageInfo(packageName, 0)
                true
            } catch (_: Throwable) {
                false
            }
            if (installed) return packageName to label
        }
        return null
    }

    /**
     * Intents that open the manager's scope screen, best first, always ending
     * with a guaranteed system App-info fallback.
     */
    fun managerIntents(context: Context): List<Intent> {
        val intents = mutableListOf<Intent>()
        val manager = findManager(context)?.first

        if (manager != null) {
            try {
                context.packageManager.getLaunchIntentForPackage(manager)?.let {
                    it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    intents += it
                }
            } catch (_: Throwable) {
                // Fall through to the deep links.
            }
            // LSPosed exposes a scope editor; the explicit component is a hint
            // and the URI scheme below is the documented fallback.
            intents += Intent(Intent.ACTION_VIEW).apply {
                setClassName(manager, "org.lsposed.manager.ui.activity.ModulesActivity")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            intents += Intent(Intent.ACTION_VIEW).apply {
                data = Uri.parse("lsposed://module?package=${context.packageName}")
                setPackage(manager)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
        }

        appInfoIntent(context)?.let { intents += it }
        return intents
    }

    /** System App-info screen for [packageName] (defaults to this module). */
    fun appInfoIntent(context: Context, packageName: String = context.packageName): Intent? = try {
        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = Uri.fromParts("package", packageName, null)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
    } catch (_: Throwable) {
        null
    }

    /** Starts the first intent that resolves; false when none did. */
    fun launchFirst(context: Context, intents: List<Intent>): Boolean {
        val pm = context.packageManager
        for (intent in intents) {
            if (resolves(pm, intent)) {
                try {
                    context.startActivity(intent)
                    return true
                } catch (_: Throwable) {
                    // Try the next candidate.
                }
            }
        }
        return false
    }

    private fun resolves(pm: PackageManager, intent: Intent): Boolean = try {
        pm.resolveActivity(intent, 0) != null || pm.queryIntentActivities(intent, 0).isNotEmpty()
    } catch (_: Throwable) {
        false
    }
}
