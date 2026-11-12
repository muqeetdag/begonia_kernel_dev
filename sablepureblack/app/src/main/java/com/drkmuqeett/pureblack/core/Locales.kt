/*
 * SablePureBlack — by @drkmuqeett
 * Package: com.drkmuqeett.pureblack
 *
 * Locale extraction across API levels. Kept in the payload (not :config) so the
 * config layer compiles in both the payload and the UI without hidden APIs.
 *
 * Reflection is used for `LocaleList.get(index)` (hidden before API 34) and
 * always guarded, so a failure simply yields null.
 */
package com.drkmuqeett.pureblack.core

import android.content.res.Configuration
import android.content.res.Resources
import android.os.Build
import android.os.LocaleList
import java.util.Locale

internal object Locales {

    /** Locale applied to [configuration], or null when unavailable. */
    fun primary(configuration: Configuration): Locale? =
        list(configuration.locales).firstOrNull() ?: defaultLocale()

    /** Locales applied to [resources], or null when unavailable. */
    fun primary(resources: Resources?): Locale? {
        if (resources == null) return null
        val locales = try {
            resources.configuration.locales
        } catch (_: Throwable) {
            null
        }
        return locales?.let { list(it) }?.firstOrNull() ?: defaultLocale()
    }

    /** Fully expanded LocaleList as a Kotlin list. */
    fun list(locales: LocaleList?): List<Locale> {
        if (locales == null) return emptyList()
        val size = try {
            locales.size()
        } catch (_: Throwable) {
            0
        }
        val result = ArrayList<Locale>(size)
        for (index in 0 until size) {
            indexLocale(locales, index)?.let(result::add)
        }
        return result
    }

    private fun indexLocale(locales: LocaleList, index: Int): Locale? = try {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            // Public on API 34+, hidden before it — resolve reflectively.
            val method = LocaleList::class.java.getMethod("get", Int::class.javaPrimitiveType)
            method.invoke(locales, index) as? Locale
        } else {
            null
        }
    } catch (_: Throwable) {
        null
    }

    private fun defaultLocale(): Locale? = try {
        LocaleList.getDefault().let { list(it) }.firstOrNull() ?: Locale.getDefault()
    } catch (_: Throwable) {
        null
    }

    /** Two-letter language tag, or "??" when unknown. */
    fun languageTag(locale: Locale?): String =
        locale?.language?.takeIf { it.isNotBlank() }?.lowercase(Locale.ROOT) ?: "??"
}
