/*
 * SPDX-FileCopyrightText: 2025 kenway214
 * SPDX-License-Identifier: Apache-2.0
 */

package com.android.gamebar

import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import com.android.settingslib.collapsingtoolbar.CollapsingToolbarBaseActivity
import com.android.gamebar.R

class FpsRecordDetailActivity : CollapsingToolbarBaseActivity() {

    companion object {
        const val EXTRA_SESSION_ID = "fps_session_id"

        private const val MENU_EXPORT_CSV = 1
        private const val MENU_EXPORT_PNG = 2
        private const val MENU_SHARE_CSV = 3
        private const val MENU_SHARE_PNG = 4
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_gamebar_log)
        title = getString(R.string.fps_record_detail_title)

        val sessionId = intent.getStringExtra(EXTRA_SESSION_ID) ?: run {
            finish(); return
        }

        if (savedInstanceState == null) {
            val fragment = FpsRecordDetailFragment().apply {
                arguments = Bundle().apply { putString(EXTRA_SESSION_ID, sessionId) }
            }
            supportFragmentManager.beginTransaction()
                .replace(R.id.content_frame, fragment)
                .commit()
        }
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        super.onCreateOptionsMenu(menu)
        menu.add(Menu.NONE, MENU_EXPORT_CSV, Menu.NONE, R.string.fps_action_export_csv)
        menu.add(Menu.NONE, MENU_EXPORT_PNG, Menu.NONE, R.string.fps_action_export_png)
        menu.add(Menu.NONE, MENU_SHARE_CSV, Menu.NONE, R.string.fps_action_share_csv)
        menu.add(Menu.NONE, MENU_SHARE_PNG, Menu.NONE, R.string.fps_action_share_png)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        val fragment = supportFragmentManager.findFragmentById(R.id.content_frame)
            as? FpsRecordDetailFragment

        when (item.itemId) {
            MENU_EXPORT_CSV -> {
                fragment?.exportCsv()
                return true
            }
            MENU_EXPORT_PNG -> {
                fragment?.exportPng()
                return true
            }
            MENU_SHARE_CSV -> {
                fragment?.shareCsv()
                return true
            }
            MENU_SHARE_PNG -> {
                fragment?.sharePng()
                return true
            }
        }
        return super.onOptionsItemSelected(item)
    }
}
