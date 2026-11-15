/*
 * SPDX-FileCopyrightText: 2025 kenway214
 * SPDX-License-Identifier: Apache-2.0
 */

package com.android.gamebar

import android.content.Context
import androidx.preference.PreferenceManager

/**
 * Preferences shared by the FPS recording bubble service and app selector.
 */
object FpsRecordingPrefs {

    const val PREF_PER_APP_ENABLED = "fps_record_per_app_enabled"
    const val PREF_AUTO_APPS = "fps_record_auto_apps"

    fun isPerAppEnabled(context: Context): Boolean =
        PreferenceManager.getDefaultSharedPreferences(context)
            .getBoolean(PREF_PER_APP_ENABLED, false)

    fun getAutoApps(context: Context): Set<String> =
        PreferenceManager.getDefaultSharedPreferences(context)
            .getStringSet(PREF_AUTO_APPS, emptySet()) ?: emptySet()

    fun setAutoApps(context: Context, apps: Set<String>) {
        PreferenceManager.getDefaultSharedPreferences(context)
            .edit()
            .putStringSet(PREF_AUTO_APPS, apps)
            .apply()
    }
}
