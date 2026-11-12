/*
 * SablePureBlack — by @drkmuqeett
 * Package: com.drkmuqeett.pureblack
 *
 * Main-screen adapter: one flat list of heterogeneous rows driven by DiffUtil.
 *
 * Payload updates keep toggles and palette selections cheap (a single row's
 * visual state changes, not the whole item), and the spring helpers are only
 * used for render-thread properties.
 */
package com.drkmuqeett.pureblack.ui

import android.content.res.ColorStateList
import android.graphics.drawable.GradientDrawable
import android.view.HapticFeedbackConstants
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import com.drkmuqeett.pureblack.R
import com.drkmuqeett.pureblack.config.PaletteMath
import com.drkmuqeett.pureblack.databinding.ItemAboutBinding
import com.drkmuqeett.pureblack.databinding.ItemAccentBinding
import com.drkmuqeett.pureblack.databinding.ItemGuideBinding
import com.drkmuqeett.pureblack.databinding.ItemHeaderBinding
import com.drkmuqeett.pureblack.databinding.ItemOpacityBinding
import com.drkmuqeett.pureblack.databinding.ItemPaletteBinding
import com.drkmuqeett.pureblack.databinding.ItemScopeEntryBinding
import com.drkmuqeett.pureblack.databinding.ItemSectionTitleBinding
import com.drkmuqeett.pureblack.databinding.ItemToggleBinding

/** Callbacks the screen implements. */
interface SableCallbacks {
    fun onPaletteSelected(option: PaletteOption)
    fun onAccentSelected(seed: Int)
    fun onToggleChanged(id: Int, checked: Boolean)
    fun onOpacityChanged(percent: Int)
    fun onOpenScope()
    fun onOpenManager()
    fun onOpenAppInfo()
}

class SableAdapter(
    private val callbacks: SableCallbacks
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    private val rows = mutableListOf<Row>()

    fun submit(newRows: List<Row>) {
        val diff = DiffUtil.calculateDiff(Differ(rows.toList(), newRows), true)
        rows.clear()
        rows.addAll(newRows)
        diff.dispatchUpdatesTo(this)
    }

    override fun getItemCount(): Int = rows.size

    override fun getItemViewType(position: Int): Int = when (rows[position]) {
        is Row.Header -> TYPE_HEADER
        is Row.Section -> TYPE_SECTION
        is Row.Palette -> TYPE_PALETTE
        is Row.Toggle -> TYPE_TOGGLE
        is Row.Accent -> TYPE_ACCENT
        is Row.Opacity -> TYPE_OPACITY
        is Row.ScopeHeader -> TYPE_SCOPE_ENTRY
        else -> TYPE_GUIDE
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return when (viewType) {
            TYPE_HEADER -> HeaderHolder(ItemHeaderBinding.inflate(inflater, parent, false))
            TYPE_SECTION -> SectionHolder(ItemSectionTitleBinding.inflate(inflater, parent, false))
            TYPE_PALETTE -> PaletteHolder(ItemPaletteBinding.inflate(inflater, parent, false))
            TYPE_TOGGLE -> ToggleHolder(ItemToggleBinding.inflate(inflater, parent, false))
            TYPE_ACCENT -> AccentHolder(ItemAccentBinding.inflate(inflater, parent, false))
            TYPE_OPACITY -> OpacityHolder(ItemOpacityBinding.inflate(inflater, parent, false))
            TYPE_SCOPE_ENTRY -> ScopeEntryHolder(ItemScopeEntryBinding.inflate(inflater, parent, false))
            TYPE_GUIDE -> GuideHolder(ItemGuideBinding.inflate(inflater, parent, false))
            else -> AboutHolder(ItemAboutBinding.inflate(inflater, parent, false))
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (val row = rows[position]) {
            is Row.Header -> (holder as HeaderHolder).bind(row.state)
            is Row.Section -> (holder as SectionHolder).bind(row.title)
            is Row.Palette -> (holder as PaletteHolder).bind(row.option)
            is Row.Toggle -> (holder as ToggleHolder).bind(row)
            is Row.Accent -> (holder as AccentHolder).bind(row)
            is Row.Opacity -> (holder as OpacityHolder).bind(row.percent)
            is Row.ScopeHeader -> (holder as ScopeEntryHolder).bind(row.state)
            is Row.Guide -> (holder as GuideHolder).bind(callbacks)
            is Row.About -> (holder as AboutHolder).bind(callbacks)
        }
    }

    override fun onBindViewHolder(
        holder: RecyclerView.ViewHolder,
        position: Int,
        payloads: MutableList<Any>
    ) {
        if (payloads.isEmpty()) {
            super.onBindViewHolder(holder, position, payloads)
            return
        }
        when (val row = rows[position]) {
            is Row.Palette -> (holder as? PaletteHolder)?.bindSelection(row.option)
            is Row.Toggle -> (holder as? ToggleHolder)?.bindChecked(row.checked)
            is Row.Accent -> (holder as? AccentHolder)?.bind(row)
            is Row.ScopeHeader -> (holder as? ScopeEntryHolder)?.bind(row.state)
            else -> super.onBindViewHolder(holder, position, payloads)
        }
    }

    // ------------------------------------------------------------------ header

    private class HeaderHolder(
        private val binding: ItemHeaderBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(state: HeaderState) {
            val context = binding.root.context
            val accent = ContextCompat.getColor(
                context,
                if (state.probe.injected) R.color.sable_active else R.color.sable_inactive
            )
            binding.statusText.text = state.probe.statusLabel
            binding.statusText.setTextColor(accent)
            binding.statusDot.backgroundTintList = ColorStateList.valueOf(accent)
            binding.statusPill.backgroundTintList = ColorStateList.valueOf(withAlpha(accent, 0x26))

            binding.statusDetail.text = if (state.probe.injected) {
                context.getString(
                    R.string.probe_active_detail,
                    state.probe.managerLabel,
                    state.paletteLabel
                )
            } else {
                context.getString(R.string.probe_inactive_detail, state.probe.managerLabel)
            }

            binding.ipcBadge.text = context.getString(
                if (state.probe.ipcReady) R.string.probe_ipc_ok else R.string.probe_ipc_missing
            )
            binding.ipcBadge.setTextColor(
                ContextCompat.getColor(
                    context,
                    if (state.probe.ipcReady) R.color.sable_text_secondary else R.color.sable_warning
                )
            )
        }

        private fun withAlpha(color: Int, alpha: Int): Int =
            (color and 0x00FFFFFF) or ((alpha and 0xFF) shl 24)
    }

    private class SectionHolder(
        private val binding: ItemSectionTitleBinding
    ) : RecyclerView.ViewHolder(binding.root) {
        fun bind(title: String) {
            binding.sectionTitle.text = title
        }
    }

    // ----------------------------------------------------------------- palette

    private inner class PaletteHolder(
        private val binding: ItemPaletteBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        private var option: PaletteOption? = null

        init {
            binding.paletteRow.setOnClickListener {
                option?.let {
                    binding.paletteRow.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                    Springs.press(binding.paletteRow, true)
                    binding.paletteRow.postDelayed(
                        { Springs.press(binding.paletteRow, false) },
                        PRESS_SETTLE_MS
                    )
                    callbacks.onPaletteSelected(it)
                }
            }
        }

        fun bind(value: PaletteOption) {
            option = value
            binding.paletteTitle.text = value.title
            binding.paletteSubtitle.text = value.subtitle
            bindSelection(value)
        }

        fun bindSelection(value: PaletteOption) {
            option = value
            val context = binding.root.context
            binding.paletteSwatch.background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(value.swatch)
                setStroke(dp(context, 1), ContextCompat.getColor(context, R.color.sable_stroke_bright))
            }
            binding.paletteCheck.visibility = if (value.selected) View.VISIBLE else View.INVISIBLE
            binding.paletteRow.setBackgroundResource(
                if (value.selected) R.drawable.bg_card_selected else R.drawable.bg_card_ripple
            )
            binding.paletteSubtitle.setTextColor(
                ContextCompat.getColor(
                    context,
                    if (value.selected) R.color.sable_text_secondary else R.color.sable_text_tertiary
                )
            )
        }
    }

    // ------------------------------------------------------------------ toggle

    private inner class ToggleHolder(
        private val binding: ItemToggleBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        private var row: Row.Toggle? = null

        init {
            binding.toggleRow.setOnClickListener { row?.let { emit(it) } }
        }

        fun bind(value: Row.Toggle) {
            row = value
            binding.toggleTitle.text = value.title
            binding.toggleSubtitle.text = value.subtitle
            bindChecked(value.checked)
        }

        fun bindChecked(checked: Boolean) {
            val context = binding.root.context
            binding.toggleSwitch.isChecked = checked
            binding.toggleSwitch.thumbTintList = ColorStateList.valueOf(
                ContextCompat.getColor(
                    context,
                    if (checked) R.color.sable_black else R.color.sable_accent_dim
                )
            )
            binding.toggleSwitch.trackTintList = ColorStateList.valueOf(
                ContextCompat.getColor(
                    context,
                    if (checked) R.color.sable_accent else R.color.sable_stroke_bright
                )
            )
        }

        private fun emit(value: Row.Toggle) {
            binding.toggleRow.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
            Springs.press(binding.toggleRow, true)
            binding.toggleRow.postDelayed(
                { Springs.press(binding.toggleRow, false) },
                PRESS_SETTLE_MS
            )
            callbacks.onToggleChanged(value.id, !value.checked)
        }
    }

    // ------------------------------------------------------------------ accent

    private inner class AccentHolder(
        private val binding: ItemAccentBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(value: Row.Accent) {
            val context = binding.root.context
            binding.accentRow.removeAllViews()
            val size = dp(context, 38)
            val gap = dp(context, 10)
            value.seeds.forEach { seed ->
                val dot = android.view.View(context)
                val params = android.widget.LinearLayout.LayoutParams(size, size)
                params.marginEnd = gap
                dot.layoutParams = params
                dot.background = GradientDrawable().apply {
                    shape = GradientDrawable.OVAL
                    setColor(seed)
                    setStroke(
                        dp(context, if (seed == value.selectedSeed) 3 else 1),
                        ContextCompat.getColor(
                            context,
                            if (seed == value.selectedSeed) R.color.sable_accent else R.color.sable_stroke_bright
                        )
                    )
                }
                dot.contentDescription = PaletteMath.hex(seed)
                dot.setOnClickListener {
                    dot.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                    callbacks.onAccentSelected(seed)
                }
                binding.accentRow.addView(dot)
            }
        }
    }

    // ----------------------------------------------------------------- opacity

    private inner class OpacityHolder(
        private val binding: ItemOpacityBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        private var suppress = false

        init {
            binding.opacitySeek.setOnSeekBarChangeListener(
                object : android.widget.SeekBar.OnSeekBarChangeListener {
                    override fun onProgressChanged(
                        seekBar: android.widget.SeekBar,
                        progress: Int,
                        fromUser: Boolean
                    ) {
                        binding.opacityValue.text =
                            binding.root.context.getString(R.string.opacity_value, progress)
                        if (fromUser && !suppress) {
                            callbacks.onOpacityChanged(progress)
                        }
                    }

                    override fun onStartTrackingTouch(seekBar: android.widget.SeekBar) = Unit

                    override fun onStopTrackingTouch(seekBar: android.widget.SeekBar) = Unit
                }
            )
        }

        fun bind(percent: Int) {
            suppress = true
            binding.opacitySeek.progress = percent.coerceIn(0, 100)
            binding.opacityValue.text = binding.root.context.getString(R.string.opacity_value, percent)
            suppress = false
        }
    }

    // -------------------------------------------------------------- scope entry

    private inner class ScopeEntryHolder(
        private val binding: ItemScopeEntryBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        init {
            binding.scopeEntryRow.setOnClickListener { callbacks.onOpenScope() }
        }

        fun bind(state: ScopeState) {
            val context = binding.root.context
            val mode = if (state.selectedMode) "only selected apps" else "all scoped apps"
            binding.scopeEntrySummary.text = context.getString(
                R.string.scope_entry_summary,
                state.selectedCount,
                state.totalCount,
                mode
            )
        }
    }

    // ------------------------------------------------------------------- guide

    private inner class GuideHolder(
        private val binding: ItemGuideBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        init {
            binding.openManagerButton.setOnClickListener { callbacks.onOpenManager() }
        }

        fun bind(callbacks: SableCallbacks) {
            binding.openManagerButton.setOnClickListener { callbacks.onOpenManager() }
        }
    }

    private inner class AboutHolder(
        private val binding: ItemAboutBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(callbacks: SableCallbacks) {
            binding.openAppInfo.setOnClickListener { callbacks.onOpenAppInfo() }
        }
    }

    // -------------------------------------------------------------------- diff

    private class Differ(
        private val oldRows: List<Row>,
        private val newRows: List<Row>
    ) : DiffUtil.Callback() {

        override fun getOldListSize(): Int = oldRows.size

        override fun getNewListSize(): Int = newRows.size

        override fun areItemsTheSame(oldItemPosition: Int, newItemPosition: Int): Boolean {
            val a = oldRows[oldItemPosition]
            val b = newRows[newItemPosition]
            return when {
                a is Row.Header && b is Row.Header -> true
                a is Row.Section && b is Row.Section -> a.title == b.title
                a is Row.Palette && b is Row.Palette -> a.option.id == b.option.id
                a is Row.Toggle && b is Row.Toggle -> a.id == b.id
                a is Row.Opacity && b is Row.Opacity -> true
                a is Row.ScopeHeader && b is Row.ScopeHeader -> true
                a is Row.Guide && b is Row.Guide -> true
                a is Row.About && b is Row.About -> true
                else -> false
            }
        }

        override fun areContentsTheSame(oldItemPosition: Int, newItemPosition: Int): Boolean =
            oldRows[oldItemPosition] == newRows[newItemPosition]

        override fun getChangePayload(oldItemPosition: Int, newItemPosition: Int): Any? {
            val a = oldRows[oldItemPosition]
            val b = newRows[newItemPosition]
            return when {
                a is Row.Palette && b is Row.Palette && a.option.selected != b.option.selected ->
                    PAYLOAD_SELECTION
                a is Row.Toggle && b is Row.Toggle && a.checked != b.checked -> PAYLOAD_CHECKED
                else -> null
            }
        }
    }

    companion object {
        const val TYPE_HEADER = 0
        const val TYPE_SECTION = 1
        const val TYPE_PALETTE = 2
        const val TYPE_TOGGLE = 3
        const val TYPE_ACCENT = 4
        const val TYPE_OPACITY = 5
        const val TYPE_SCOPE_ENTRY = 6
        const val TYPE_GUIDE = 7
        const val TYPE_ABOUT = 8

        /** How long a pressed row stays slightly scaled down. */
        const val PRESS_SETTLE_MS = 90L

        const val PAYLOAD_SELECTION = "payload_selection"
        const val PAYLOAD_CHECKED = "payload_checked"

        /** Toggle identifiers used by [Row.Toggle]. */
        const val TOGGLE_WEBVIEW = 0
        const val TOGGLE_TEXT = 1

        fun dp(context: android.content.Context, value: Int): Int =
            (value * context.resources.displayMetrics.density).toInt()
    }
}
