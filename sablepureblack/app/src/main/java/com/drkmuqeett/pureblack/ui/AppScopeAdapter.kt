/*
 * SablePureBlack — by @drkmuqeett
 * Package: com.drkmuqeett.pureblack
 *
 * Adapter for the per-app scope selector bottom sheet.
 *
 * The full installed-app list is held here and filtered incrementally, so typing
 * in the search box never re-queries PackageManager. Rows are bound in a single
 * pass and the check state is applied without allocating.
 */
package com.drkmuqeett.pureblack.ui

import android.view.HapticFeedbackConstants
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import com.drkmuqeett.pureblack.R
import com.drkmuqeett.pureblack.apps.AppEntry
import com.drkmuqeett.pureblack.apps.AppRepository
import com.drkmuqeett.pureblack.databinding.ItemAppBinding

class AppScopeAdapter(
    private val onToggle: (AppEntry, Boolean) -> Unit
) : RecyclerView.Adapter<AppScopeAdapter.AppHolder>() {

    private val items = mutableListOf<AppEntry>()
    private val selected = HashSet<String>()

    /** Replaces the visible list (already filtered by the caller). */
    fun submit(newItems: List<AppEntry>, selection: Set<String>) {
        val diff = DiffUtil.calculateDiff(Differ(items.toList(), newItems), true)
        items.clear()
        items.addAll(newItems)
        selected.clear()
        selected.addAll(selection)
        diff.dispatchUpdatesTo(this)
    }

    /** Updates check marks in place after a tap (no list diff needed). */
    fun updateSelection(selection: Set<String>, packageName: String) {
        selected.clear()
        selected.addAll(selection)
        val index = items.indexOfFirst { it.packageName == packageName }
        if (index >= 0) notifyItemChanged(index, PAYLOAD_CHECK)
    }

    fun isSelected(packageName: String): Boolean = selected.contains(packageName)

    override fun getItemCount(): Int = items.size

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): AppHolder =
        AppHolder(ItemAppBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    override fun onBindViewHolder(holder: AppHolder, position: Int) {
        val entry = items[position]
        holder.bind(entry, selected.contains(entry.packageName))
    }

    override fun onBindViewHolder(
        holder: AppHolder,
        position: Int,
        payloads: MutableList<Any>
    ) {
        if (payloads.isEmpty()) {
            super.onBindViewHolder(holder, position, payloads)
            return
        }
        val entry = items[position]
        holder.bindChecked(selected.contains(entry.packageName))
    }

    inner class AppHolder(
        private val binding: ItemAppBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        private var entry: AppEntry? = null

        init {
            binding.appRow.setOnClickListener {
                val current = entry ?: return@setOnClickListener
                val next = !isSelected(current.packageName)
                binding.appRow.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                onToggle(current, next)
            }
        }

        fun bind(value: AppEntry, isSelected: Boolean) {
            entry = value
            val context = binding.root.context
            binding.appLabel.text = value.label
            binding.appPackage.text = value.packageName
            binding.appSystemBadge.visibility =
                if (value.isSystem) android.view.View.VISIBLE else android.view.View.GONE
            binding.appIcon.setImageDrawable(value.icon ?: AppRepository.fallbackIcon())
            binding.appLabel.setTextColor(
                ContextCompat.getColor(context, R.color.sable_text_primary)
            )
            bindChecked(isSelected)
        }

        fun bindChecked(isSelected: Boolean) {
            val context = binding.root.context
            binding.appCheck.isChecked = isSelected
            binding.appCheck.buttonTintList = android.content.res.ColorStateList.valueOf(
                ContextCompat.getColor(
                    context,
                    if (isSelected) R.color.sable_accent else R.color.sable_stroke_bright
                )
            )
            binding.appRow.setBackgroundColor(
                if (isSelected) 0x0FFFFFFF else android.graphics.Color.TRANSPARENT
            )
        }
    }

    private class Differ(
        private val oldItems: List<AppEntry>,
        private val newItems: List<AppEntry>
    ) : DiffUtil.Callback() {

        override fun getOldListSize(): Int = oldItems.size

        override fun getNewListSize(): Int = newItems.size

        override fun areItemsTheSame(oldItemPosition: Int, newItemPosition: Int): Boolean =
            oldItems[oldItemPosition].packageName == newItems[newItemPosition].packageName

        override fun areContentsTheSame(oldItemPosition: Int, newItemPosition: Int): Boolean {
            val a = oldItems[oldItemPosition]
            val b = newItems[newItemPosition]
            return a.packageName == b.packageName &&
                a.label == b.label &&
                a.isSystem == b.isSystem
        }
    }

    private companion object {
        const val PAYLOAD_CHECK = "payload_check"
    }
}
