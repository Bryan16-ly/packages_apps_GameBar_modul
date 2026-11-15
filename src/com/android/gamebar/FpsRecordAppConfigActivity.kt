/*
 * SPDX-FileCopyrightText: 2025 kenway214
 * SPDX-License-Identifier: Apache-2.0
 */

package com.android.gamebar

import android.os.Bundle
import com.android.gamebar.R
import com.android.settingslib.collapsingtoolbar.CollapsingToolbarBaseActivity

class FpsRecordAppConfigActivity : CollapsingToolbarBaseActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_game_bar_app_selector)
        title = getString(R.string.fps_record_apps_screen_title)

        if (savedInstanceState == null) {
            supportFragmentManager.beginTransaction()
                .replace(R.id.content_frame, FpsRecordAppConfigFragment())
                .commit()
        }
    }
}
