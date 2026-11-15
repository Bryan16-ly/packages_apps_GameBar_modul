/*
 * SPDX-FileCopyrightText: 2025 kenway214
 * SPDX-License-Identifier: Apache-2.0
 */

package com.android.gamebar

import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.LinearLayout
import androidx.preference.Preference
import androidx.preference.PreferenceCategory
import androidx.preference.SwitchPreferenceCompat
import com.android.settingslib.widget.SettingsBasePreferenceFragment

/**
 * App selector for automatic FPS recording. Kept separate from the per-app
 * GameBar overlay list so users can record a different set of apps.
 */
class FpsRecordAppConfigFragment : SettingsBasePreferenceFragment() {

    private var searchBar: EditText? = null
    private var category: PreferenceCategory? = null
    private var allApps: List<ApplicationInfo>? = null
    private var pm: PackageManager? = null
    private var selectedApps: Set<String> = emptySet()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val root = super.onCreateView(inflater, container, savedInstanceState)
        val context = requireContext()
        val layout = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
        }
        searchBar = EditText(context).apply {
            id = View.generateViewId()
            hint = "Search apps..."
            inputType = android.text.InputType.TYPE_CLASS_TEXT
            setBackgroundResource(R.drawable.bg_search_rounded)
            setPadding(24, 24, 24, 24)
            setTextColor(context.getColor(R.color.app_name_text_selector))
            setHintTextColor(context.getColor(R.color.app_package_text_selector))
        }
        val params = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        )
        val margin = (context.resources.displayMetrics.density * 16).toInt()
        params.setMargins(margin, 0, margin, 0)
        layout.addView(searchBar, params)
        root?.let { layout.addView(it) }

        searchBar?.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                populateAppList(s?.toString()?.lowercase() ?: "")
            }
            override fun afterTextChanged(s: Editable?) {}
        })
        return layout
    }

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        preferenceScreen = preferenceManager.createPreferenceScreen(requireContext())
        category = PreferenceCategory(requireContext()).apply {
            title = getString(R.string.fps_record_apps_screen_title)
        }
        preferenceScreen.addPreference(category!!)
        pm = requireContext().packageManager
        allApps = pm!!.getInstalledApplications(PackageManager.GET_META_DATA)
        selectedApps = FpsRecordingPrefs.getAutoApps(requireContext())
        populateAppList("")
    }

    private fun populateAppList(filter: String) {
        val target = category ?: return
        target.removeAll()
        val manager = pm ?: return

        allApps?.forEach { app ->
            if ((app.flags and ApplicationInfo.FLAG_SYSTEM) != 0) return@forEach
            if (app.packageName == requireContext().packageName) return@forEach
            val label = try {
                app.loadLabel(manager).toString()
            } catch (_: Exception) {
                app.packageName
            }
            if (filter.isNotEmpty() &&
                !label.lowercase().contains(filter) &&
                !app.packageName.lowercase().contains(filter)
            ) {
                return@forEach
            }

            val pref = SwitchPreferenceCompat(requireContext()).apply {
                title = label
                summary = app.packageName
                key = "fps_record_${app.packageName}"
                isChecked = selectedApps.contains(app.packageName)
                icon = try { app.loadIcon(manager) } catch (_: Exception) { null }
                onPreferenceChangeListener = Preference.OnPreferenceChangeListener { _, newValue ->
                    val updated = FpsRecordingPrefs.getAutoApps(requireContext()).toMutableSet()
                    if (newValue as Boolean) {
                        updated.add(app.packageName)
                    } else {
                        updated.remove(app.packageName)
                    }
                    FpsRecordingPrefs.setAutoApps(requireContext(), updated)
                    selectedApps = updated
                    true
                }
            }
            target.addPreference(pref)
        }
    }
}
