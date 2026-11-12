/*
 * SablePureBlack — by @drkmuqeett
 * Package: com.drkmuqeett.pureblack
 *
 * Pure colour maths shared by the payload and the UI. No Android types on the
 * hot path, so the same functions that decide "is this surface light?" at
 * runtime also drive the palette previews in the companion app.
 */
package com.drkmuqeett.pureblack.config

/** Immutable ARGB helpers plus the custom-accent derivation. */
object PaletteMath {

    private const val RED_WEIGHT = 299
    private const val GREEN_WEIGHT = 587
    private const val BLUE_WEIGHT = 114
    private const val WEIGHT_SUM = RED_WEIGHT + GREEN_WEIGHT + BLUE_WEIGHT

    /** Rec. 601 luma of [color] in 0..255, alpha ignored. */
    fun luminance(color: Int): Int {
        val r = (color shr 16) and 0xFF
        val g = (color shr 8) and 0xFF
        val b = color and 0xFF
        return (RED_WEIGHT * r + GREEN_WEIGHT * g + BLUE_WEIGHT * b) / WEIGHT_SUM
    }

    fun alpha(color: Int): Int = (color ushr 24) and 0xFF

    fun red(color: Int): Int = (color shr 16) and 0xFF

    fun green(color: Int): Int = (color shr 8) and 0xFF

    fun blue(color: Int): Int = color and 0xFF

    /** Replaces the alpha channel of [color], keeping RGB. */
    fun withAlpha(color: Int, alpha: Int): Int =
        (color and 0x00FFFFFF) or ((alpha.coerceIn(0, 255)) shl 24)

    /** Linear interpolation between two colours, [fraction] in 0f..1f. */
    fun blend(from: Int, to: Int, fraction: Float): Int {
        val f = fraction.coerceIn(0f, 1f)
        val r = (red(from) + (red(to) - red(from)) * f).toInt()
        val g = (green(from) + (green(to) - green(from)) * f).toInt()
        val b = (blue(from) + (blue(to) - blue(from)) * f).toInt()
        return withAlpha((r shl 16) or (g shl 8) or b, alpha(from))
    }

    /**
     * Derives the "Custom Accent Dark" surface from a user accent seed.
     *
     * The seed only tints the surface: brightness is pinned into 10..22 luma so
     * the result stays a near-black AMOLED surface (pixels still effectively
     * off) while carrying the user's hue. A 12 % blend keeps red/green/blue
     * within 0x0A..0x16, which is #0A0A0A-ish with a tint.
     */
    fun customAccentColor(accentSeed: Int): Int {
        val tinted = blend(COLOR_DEEP_OBSIDIAN, accentSeed, TINT_FRACTION)
        // Clamp every channel into the AMOLED-safe band so a bright accent
        // (white, yellow) cannot lift the surface into visible grey.
        val r = red(tinted).coerceIn(MIN_CHANNEL, MAX_CHANNEL)
        val g = green(tinted).coerceIn(MIN_CHANNEL, MAX_CHANNEL)
        val b = blue(tinted).coerceIn(MIN_CHANNEL, MAX_CHANNEL)
        return (0xFF shl 24) or (r shl 16) or (g shl 8) or b
    }

    /** "#RRGGBB" for the given ARGB value. */
    fun hex(color: Int): String = "#%06X".format(color and 0xFFFFFF)

    private const val TINT_FRACTION = 0.12f
    private const val MIN_CHANNEL = 0x0A
    private const val MAX_CHANNEL = 0x16
    private const val COLOR_DEEP_OBSIDIAN = 0xFF0A0A0A.toInt()

    /** Accent choices offered by the palette switcher. */
    val ACCENT_PRESETS: List<Int> = listOf(
        0xFF3DD68C.toInt(), // Sable green (default)
        0xFF5AA9FF.toInt(), // Ice blue
        0xFFB388FF.toInt(), // Violet
        0xFFFF8A65.toInt(), // Ember
        0xFFFFD166.toInt(), // Amber
        0xFFEAEAEA.toInt()  // Neutral white
    )
}
