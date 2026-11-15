/*
 * SPDX-FileCopyrightText: 2025 kenway214
 * SPDX-License-Identifier: Apache-2.0
 */


package com.android.gamebar

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.view.Menu
import android.view.MenuItem
import android.widget.Toast
import com.android.settingslib.collapsingtoolbar.CollapsingToolbarBaseActivity
import com.android.gamebar.R

class GameBarSettingsActivity : CollapsingToolbarBaseActivity() {
    
    companion object {
        private const val OVERLAY_PERMISSION_REQUEST_CODE = 1234
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_game_bar)
        title = getString(R.string.game_bar_title)

        if (!Settings.canDrawOverlays(this)) {
            val intent = Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:$packageName")
            )
            startActivityForResult(intent, OVERLAY_PERMISSION_REQUEST_CODE)
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        
        when (requestCode) {
            OVERLAY_PERMISSION_REQUEST_CODE -> {
                if (Settings.canDrawOverlays(this)) {
                    Toast.makeText(this, R.string.overlay_permission_granted, Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(this, R.string.overlay_permission_denied, Toast.LENGTH_SHORT).show()
                }
            }
        }
    }
    
    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.gamebar_settings_menu, menu)
        return true
    }
    
    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.menu_user_guide -> {
                try {
                    val url = getString(R.string.game_bar_user_guide_url)
                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
                    startActivity(intent)
                } catch (e: Exception) {
                    Toast.makeText(this, "Unable to open user guide: ${e.message}", Toast.LENGTH_LONG).show()
                    e.printStackTrace()
                }
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }
}
