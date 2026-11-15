/*
 * SPDX-FileCopyrightText: 2025 kenway214
 * SPDX-License-Identifier: Apache-2.0
 */

package com.android.gamebar

import android.os.Bundle
import com.android.settingslib.collapsingtoolbar.CollapsingToolbarBaseActivity
import com.android.gamebar.R

class FpsRecordHistoryActivity : CollapsingToolbarBaseActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_gamebar_log)
        title = getString(R.string.fps_record_title)

        if (savedInstanceState == null) {
            supportFragmentManager.beginTransaction()
                .replace(R.id.content_frame, FpsRecordHistoryFragment())
                .commit()
        }
    }
}
