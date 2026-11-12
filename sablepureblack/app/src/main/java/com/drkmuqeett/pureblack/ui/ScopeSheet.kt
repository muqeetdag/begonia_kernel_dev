/*
 * SablePureBlack — by @drkmuqeett
 * Package: com.drkmuqeett.pureblack
 *
 * Searchable per-app scope selector in a Material 3 bottom sheet.
 *
 * Installed apps are loaded once, off the main thread; the search box filters the
 * in-memory list on every keystroke (O(n) over a pre-built lowercase haystack, no
 * PackageManager traffic). Toggling an app writes straight to the shared
 * preference file the payload reads.
 */
package com.drkmuqeett.pureblack.ui

import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.core.os.bundleOf
// Extension import required: viewLifecycleOwner.lifecycleScope comes from here.
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.drkmuqeett.pureblack.R
import com.drkmuqeett.pureblack.apps.AppEntry
import com.drkmuqeett.pureblack.apps.AppRepository
import com.drkmuqeett.pureblack.apps.ScopeStore
import com.drkmuqeett.pureblack.config.PureBlackPrefs
import com.drkmuqeett.pureblack.databinding.SheetScopeBinding
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class ScopeSheet : BottomSheetDialogFragment() {

    private var _binding: SheetScopeBinding? = null
    private val binding: SheetScopeBinding
        get() = checkNotNull(_binding) { "ScopeSheet accessed after onDestroyView" }

    private lateinit var adapter: AppScopeAdapter

    private var allApps: List<AppEntry> = emptyList()
    private var selection: Set<String> = emptySet()
    private var selectedMode: Boolean = false

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = SheetScopeBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val context = requireContext()
        val stored = ScopeStore.read(context)
        selection = stored.packages
        selectedMode = stored.isSelectedMode

        adapter = AppScopeAdapter(onToggle = ::toggleApp)
        binding.sheetList.layoutManager = LinearLayoutManager(context)
        binding.sheetList.adapter = adapter

        binding.selectedModeSwitch.isChecked = selectedMode
        binding.selectedModeSwitch.thumbTintList =
            android.content.res.ColorStateList.valueOf(
                ContextCompat.getColor(context, R.color.sable_black)
            )
        binding.selectedModeSwitch.trackTintList =
            android.content.res.ColorStateList.valueOf(
                ContextCompat.getColor(context, R.color.sable_accent)
            )
        binding.selectedModeRow.setOnClickListener {
            selectedMode = !selectedMode
            binding.selectedModeSwitch.isChecked = selectedMode
            ScopeStore.setTargetMode(
                context,
                if (selectedMode) {
                    PureBlackPrefs.TARGET_MODE_SELECTED
                } else {
                    PureBlackPrefs.TARGET_MODE_ALL_SCOPED
                }
            )
            notifyChanged()
            refreshVisibleList()
        }

        binding.searchField.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit

            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit

            override fun afterTextChanged(s: Editable?) {
                refreshVisibleList()
            }
        })

        expandSheet()
        loadApps()
    }

    /** Loads installed apps off the main thread, then renders. */
    private fun loadApps() {
        val appContext = requireContext().applicationContext
        val selfPackage = requireContext().packageName
        binding.sheetEmpty.visibility = View.VISIBLE
        binding.sheetEmptyText.setText(R.string.scope_loading)

        viewLifecycleOwner.lifecycleScope.launch {
            val apps = withContext(Dispatchers.IO) {
                AppRepository.load(
                    context = appContext,
                    includeSystem = true,
                    selfPackage = selfPackage
                )
            }
            if (_binding == null) return@launch
            allApps = apps
            refreshVisibleList()
        }
    }

    private fun refreshVisibleList() {
        val query = binding.searchField.text?.toString().orEmpty()
        val filtered = AppRepository.filter(allApps, query)
        adapter.submit(filtered, selection)

        binding.sheetCount.text = getString(R.string.scope_count, selection.size, allApps.size)

        val empty = filtered.isEmpty()
        binding.sheetEmpty.visibility = if (empty) View.VISIBLE else View.GONE
        binding.sheetList.visibility = if (empty) View.GONE else View.VISIBLE
        if (empty) {
            binding.sheetEmptyText.text = if (allApps.isEmpty()) {
                getString(R.string.scope_loading)
            } else {
                getString(R.string.scope_empty, query)
            }
        }
    }

    private fun toggleApp(entry: AppEntry, requestedState: Boolean) {
        val updated = ScopeStore.toggle(requireContext(), entry.packageName)
        selection = updated.packages
        adapter.updateSelection(selection, entry.packageName)
        binding.sheetCount.text = getString(R.string.scope_count, selection.size, allApps.size)
        // The row is re-rendered from the persisted state, not from the tap, so
        // a failed write can never leave the checkbox lying about the config.
        if (requestedState != selection.contains(entry.packageName)) {
            refreshVisibleList()
        }
        notifyChanged()
    }

    private fun notifyChanged() {
        parentFragmentManager.setFragmentResult(
            RESULT_KEY,
            bundleOf(
                RESULT_COUNT to selection.size,
                RESULT_MODE to selectedMode
            )
        )
    }

    private fun expandSheet() {
        (dialog as? com.google.android.material.bottomsheet.BottomSheetDialog)?.let { sheet ->
            sheet.behavior.state = BottomSheetBehavior.STATE_EXPANDED
            sheet.behavior.skipCollapsed = true
            sheet.behavior.isDraggable = true
        }
    }

    override fun onDestroyView() {
        binding.sheetList.adapter = null
        _binding = null
        super.onDestroyView()
    }

    companion object {
        const val TAG = "ScopeSheet"
        const val RESULT_KEY = "scope_sheet_result"
        const val RESULT_COUNT = "selected_count"
        const val RESULT_MODE = "selected_mode"

        fun newInstance(): ScopeSheet = ScopeSheet()
    }
}
