/*
 * SPDX-FileCopyrightText: 2025 kenway214
 * SPDX-License-Identifier: Apache-2.0
 */

package com.android.gamebar

import android.app.ActivityManager
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.pm.PackageManager
import android.util.Log

object ForegroundAppDetector {

    private const val TAG = "ForegroundAppDetector"
    private const val CACHE_TIMEOUT_MS = 500L

    @Volatile
    private var lastKnownPackage = "Unknown"
    @Volatile
    private var lastUpdateTime = 0L

    fun getForegroundPackageName(context: Context): String {
        val now = System.currentTimeMillis()
        if (now - lastUpdateTime < CACHE_TIMEOUT_MS && lastKnownPackage != "Unknown") {
            return lastKnownPackage
        }

        val myPkg = context.packageName

        // 1. Try public ActivityManager running tasks
        tryGetRunningTasks(context)?.takeIf { it != myPkg }?.let {
            updateCache(it, now)
            return it
        }

        // 2. Try ActivityTaskManager via reflection
        tryReflectActivityTaskManager()?.takeIf { it != myPkg }?.let {
            updateCache(it, now)
            return it
        }

        // 3. Try UsageStatsManager
        tryUsageStats(context)?.takeIf { it != myPkg }?.let {
            updateCache(it, now)
            return it
        }

        return lastKnownPackage
    }

    private fun updateCache(pkg: String, timestamp: Long) {
        lastKnownPackage = pkg
        lastUpdateTime = timestamp
    }

    private fun tryGetRunningTasks(context: Context): String? {
        return runCatching {
            if (context.checkSelfPermission("android.permission.GET_TASKS") == PackageManager.PERMISSION_GRANTED) {
                val am = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
                val topTask = am?.getRunningTasks(1)?.firstOrNull()
                topTask?.topActivity?.packageName
            } else {
                null
            }
        }.onFailure { Log.d(TAG, "tryGetRunningTasks failed: ${it.message}") }.getOrNull()
    }

    private fun tryReflectActivityTaskManager(): String? {
        return runCatching {
            val atmClass = Class.forName("android.app.ActivityTaskManager")
            val getServiceMethod = atmClass.getDeclaredMethod("getService").apply { isAccessible = true }
            val atmService = getServiceMethod.invoke(null) ?: return null

            val getTasksMethod = atmService.javaClass.getMethod("getTasks", Int::class.javaPrimitiveType)
            val taskList = getTasksMethod.invoke(atmService, 1) as? List<*>
            val firstTask = taskList?.firstOrNull() ?: return null

            val getTopActivityMethod = firstTask.javaClass.getDeclaredMethod("getTopActivity")
            val compName = getTopActivityMethod.invoke(firstTask) ?: return null

            val getPackageNameMethod = compName.javaClass.getMethod("getPackageName")
            getPackageNameMethod.invoke(compName) as? String
        }.onFailure { Log.d(TAG, "tryReflectActivityTaskManager failed: ${it.message}") }.getOrNull()
    }

    private fun tryUsageStats(context: Context): String? {
        return runCatching {
            val usm = context.getSystemService(Context.USAGE_STATS_SERVICE) as? UsageStatsManager
                ?: return null
            val time = System.currentTimeMillis()
            val events = usm.queryEvents(time - 10_000L, time) ?: return null
            val event = UsageEvents.Event()
            var lastPkg: String? = null

            while (events.hasNextEvent()) {
                events.getNextEvent(event)
                if (event.eventType == UsageEvents.Event.ACTIVITY_RESUMED) {
                    val pkg = event.packageName
                    if (!pkg.isNullOrEmpty() && pkg != context.packageName) {
                        lastPkg = pkg
                    }
                }
            }
            lastPkg
        }.onFailure { Log.d(TAG, "tryUsageStats failed: ${it.message}") }.getOrNull()
    }
}
