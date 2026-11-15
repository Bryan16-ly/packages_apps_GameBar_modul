/*
 * SPDX-FileCopyrightText: 2025 kenway214
 * SPDX-License-Identifier: Apache-2.0
 */


package com.android.gamebar

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.preference.PreferenceManager

class GameBarBootReceiver : BroadcastReceiver() {
    
    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action
        if (Intent.ACTION_BOOT_COMPLETED == action || Intent.ACTION_LOCKED_BOOT_COMPLETED == action) {
            restoreOverlayState(context)
        }
    }

    private fun restoreOverlayState(context: Context) {
        val prefs = PreferenceManager.getDefaultSharedPreferences(context)
        val mainEnabled = prefs.getBoolean("game_bar_enable", false)
        val autoEnabled = prefs.getBoolean("game_bar_auto_enable", false)
        
        if (mainEnabled) {
            val gameBar = GameBar.getInstance(context)
            gameBar.applyPreferences()
            gameBar.show()
        }
        
        if (autoEnabled) {
            val monitorIntent = Intent(context, GameBarMonitorService::class.java)
            context.startService(monitorIntent)
        }

        val bubbleEnabled = prefs.getBoolean("game_bar_fps_bubble_enable", false)
        if (bubbleEnabled || FpsRecordingPrefs.isPerAppEnabled(context)) {
            try {
                context.startService(Intent(context, FpsRecordingBubbleService::class.java))
            } catch (e: Exception) {
                android.util.Log.w("GameBarBootReceiver", "Unable to restore FPS recording service", e)
            }
        }
    }
}
