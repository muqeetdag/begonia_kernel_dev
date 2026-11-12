/*
 * SablePureBlack — by @drkmuqeett
 * Package: com.drkmuqeett.pureblack
 *
 * Installed-app discovery for the per-app scope selector.
 *
 * Only launchable packages are considered (the same set a user can actually
 * open), and only those this process may see under Android 11+ package
 * visibility. No permission is required: the manifest declares an implicit
 * LAUNCHER-intent <queries> entry.
 *
 * PackageManager work is synchronous here; the caller runs [load] on
 * Dispatchers.IO so the UI thread is never blocked.
 */
package com.drkmuqeett.pureblack.apps

import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.Drawable
import com.drkmuqeett.pureblack.config.PureBlackPrefs
import java.text.Collator
import java.util.Locale

/** One installed, launchable application. */
data class AppEntry(
    val packageName: String,
    val label: String,
    val isSystem: Boolean,
    val icon: Drawable?,
    /** Pre-normalised lowercase haystack: label + package name. */
    val searchKey: String
)

object AppRepository {

    /** Packages that must never appear as a scope target. */
    private val EXCLUDED: Set<String> = setOf(
        PureBlackPrefs.MODULE_PACKAGE,
        "com.drkmuqeett.pureblack.debug",
        "android",
        "com.android.systemui"
    )

    /**
     * Returns every launchable application this process may see, sorted
     * locale-aware by label.
     *
     * @param includeSystem keep pre-installed apps (default true) — users
     *        frequently want to force black on OEM gallery / clock / mail apps.
     * @param selfPackage the caller's own package, excluded from the result.
     */
    fun load(
        context: Context,
        includeSystem: Boolean = true,
        selfPackage: String? = null
    ): List<AppEntry> {
        val pm = context.packageManager
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)

        val resolved = try {
            pm.queryIntentActivities(intent, 0)
        } catch (_: Throwable) {
            emptyList()
        }

        val seen = HashSet<String>(resolved.size)
        val entries = ArrayList<AppEntry>(resolved.size)

        for (info in resolved) {
            val activityInfo = info.activityInfo ?: continue
            val packageName = activityInfo.packageName ?: continue
            if (packageName.isBlank() || packageName in EXCLUDED) continue
            if (packageName == selfPackage) continue
            if (!seen.add(packageName)) continue

            val appInfo: ApplicationInfo = activityInfo.applicationInfo ?: continue
            val isSystem: Boolean = (appInfo.flags and ApplicationInfo.FLAG_SYSTEM) != 0 ||
                (appInfo.flags and ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) != 0
            if (isSystem && !includeSystem) continue

            val label = resolveLabel(pm, appInfo)
            entries += AppEntry(
                packageName = packageName,
                label = label,
                isSystem = isSystem,
                icon = resolveIcon(pm, appInfo),
                searchKey = (label + " " + packageName).lowercase(Locale.ROOT)
            )
        }

        return sortByLabel(entries)
    }

    /** Locale-aware sort by display label, package name as tie-breaker. */
    private fun sortByLabel(entries: List<AppEntry>): List<AppEntry> {
        val collator = try {
            Collator.getInstance(Locale.getDefault())
        } catch (_: Throwable) {
            null
        }
        return if (collator == null) {
            entries.sortedBy { it.searchKey }
        } else {
            entries.sortedWith { a, b ->
                val byLabel = collator.compare(a.label, b.label)
                if (byLabel != 0) byLabel else a.packageName.compareTo(b.packageName)
            }
        }
    }

    private fun resolveLabel(pm: PackageManager, appInfo: ApplicationInfo): String {
        val direct: String? = try {
            // loadLabel() is typed CharSequence (never null), but a malformed
            // APK can still end up with an empty string — normalise both.
            appInfo.loadLabel(pm).toString().trim()
        } catch (_: Throwable) {
            null
        }
        if (!direct.isNullOrEmpty() && direct != appInfo.packageName) return direct

        // Localised fallback: resolve the label with the app's own resources.
        return try {
            val label: String = pm.getResourcesForApplication(appInfo).getString(appInfo.labelRes)
            label.takeIf { it.isNotBlank() } ?: appInfo.packageName
        } catch (_: Throwable) {
            appInfo.packageName
        }
    }

    private fun resolveIcon(pm: PackageManager, appInfo: ApplicationInfo): Drawable? {
        try {
            appInfo.loadIcon(pm)?.let { return it }
        } catch (_: Throwable) {
            // Fall through to a resource-based lookup.
        }
        return try {
            pm.getResourcesForApplication(appInfo).getDrawable(appInfo.icon, null)
        } catch (_: Throwable) {
            null
        }
    }

    /** Filters [entries] by a free-text query, matching label and package. */
    fun filter(entries: List<AppEntry>, query: String): List<AppEntry> {
        val needle = query.trim().lowercase(Locale.ROOT)
        if (needle.isEmpty()) return entries
        return entries.filter { entry -> entry.searchKey.contains(needle) }
    }

    /** Neutral icon for apps whose drawable could not be loaded. */
    fun fallbackIcon(): Drawable = ColorDrawable(0xFF121212.toInt())
}
