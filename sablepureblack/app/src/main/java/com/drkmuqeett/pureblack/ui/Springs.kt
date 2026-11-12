/*
 * SablePureBlack — by @drkmuqeett
 * Package: com.drkmuqeett.pureblack
 *
 * Spring physics helpers built on androidx.dynamicanimation.
 *
 * Only render-thread properties (alpha, scale, translation) are animated, so
 * every effect here is a zero-layout-pass operation and holds 60fps+ on modest
 * hardware. Each helper cancels the previous animation it started on that view.
 */
package com.drkmuqeett.pureblack.ui

import android.view.View
import androidx.dynamicanimation.animation.DynamicAnimation
import androidx.dynamicanimation.animation.SpringAnimation
import androidx.dynamicanimation.animation.SpringForce

object Springs {

    /** Snappy, no overshoot — for appearance and press feedback. */
    private const val STIFFNESS_SNAPPY = 1_500f
    private const val DAMPING_SNAPPY = 0.85f

    /** Gentle float used by the header status pill. */
    private const val STIFFNESS_SOFT = 900f
    private const val DAMPING_SOFT = 0.8f

    private val ALPHA = DynamicAnimation.ALPHA
    private val SCALE_X = DynamicAnimation.SCALE_X
    private val SCALE_Y = DynamicAnimation.SCALE_Y

    /** Maps a view to the running animations started by this helper. */
    private val running = java.util.WeakHashMap<View, MutableList<SpringAnimation>>()

    /** Fades [view] in with a spring, keeping its translation intact. */
    fun fadeIn(view: View, stiffness: Float = STIFFNESS_SNAPPY) {
        animate(view, ALPHA, 1f, stiffness, DAMPING_SNAPPY)
    }

    /** Scales [view] to [value] on both axes. */
    fun scaleTo(view: View, value: Float, stiffness: Float = STIFFNESS_SNAPPY) {
        animate(view, SCALE_X, value, stiffness, DAMPING_SNAPPY)
        animate(view, SCALE_Y, value, stiffness, DAMPING_SNAPPY)
    }

    /** Springs [view] back to its resting presentation. */
    fun settle(view: View) {
        animate(view, ALPHA, 1f, STIFFNESS_SNAPPY, DAMPING_SNAPPY)
        animate(view, SCALE_X, 1f, STIFFNESS_SNAPPY, DAMPING_SNAPPY)
        animate(view, SCALE_Y, 1f, STIFFNESS_SNAPPY, DAMPING_SNAPPY)
    }

    /** A soft attention pulse: settle → slight lift → settle. */
    fun pulse(view: View) {
        animate(view, SCALE_X, 1.06f, STIFFNESS_SOFT, DAMPING_SOFT, onEnd = {
            animate(view, SCALE_X, 1f, STIFFNESS_SOFT, DAMPING_SOFT)
            animate(view, SCALE_Y, 1f, STIFFNESS_SOFT, DAMPING_SOFT)
        })
        animate(view, SCALE_Y, 1.06f, STIFFNESS_SOFT, DAMPING_SOFT)
    }

    /** Press-in / press-out feedback for a tappable row. */
    fun press(view: View, pressed: Boolean) {
        scaleTo(view, if (pressed) 0.985f else 1f, STIFFNESS_SNAPPY)
    }

    /** Cancels every animation this helper started on [view]. */
    fun cancel(view: View) {
        running.remove(view)?.forEach { animation ->
            runCatching { animation.cancel() }
        }
    }

    private fun animate(
        view: View,
        property: DynamicAnimation.ViewProperty,
        target: Float,
        stiffness: Float,
        damping: Float,
        onEnd: (() -> Unit)? = null
    ) {
        val animation = SpringAnimation(view, property).apply {
            spring = SpringForce(target).apply {
                this.stiffness = stiffness
                this.dampingRatio = damping
            }
        }
        if (onEnd != null) {
            animation.addEndListener { _, canceled, _ ->
                if (!canceled) onEnd()
            }
        }
        // Replace any in-flight animation for the same property on this view.
        running[view]?.filter { it != animation }?.let { list ->
            list.forEach { runCatching { it.cancel() } }
        }
        running.getOrPut(view) { mutableListOf() }.add(animation)
        runCatching { animation.start() }
    }
}
