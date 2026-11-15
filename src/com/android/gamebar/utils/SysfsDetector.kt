/*
 * SPDX-FileCopyrightText: 2025 kenway214
 * SPDX-FileCopyrightText: 2025 carlos 'klozz' jesus
 * SPDX-License-Identifier: Apache-2.0
 */

package com.android.gamebar.utils

import android.util.Log
import java.io.File

/**
 * Utility to automatically detect sysfs nodes for hardware performance monitoring.
 * Provides dynamic discovery across diverse SoC vendors and kernel configurations.
 */
object SysfsDetector {
    private const val TAG = "SysfsDetector"

    private val detectedPaths = mutableMapOf<String, String?>()
    private val detectedDividers = mutableMapOf<String, Int>()

    /**
     * Determines temperature divider (1000, 100, 10, or 1) based on raw sysfs integer range.
     */
    private fun detectTemperatureDivider(path: String): Int {
        return runCatching {
            val file = File(path)
            if (!file.exists() || !file.canRead()) return 1000

            val rawValue = file.readText().trim().toIntOrNull() ?: return 1000
            when (rawValue) {
                in 15000..85000 -> 1000 // Millidegrees Celsius (e.g. 36600 -> 36.6°C)
                in 1500..8500   -> 100  // Centidegrees Celsius (e.g. 3660 -> 36.6°C)
                in 150..850     -> 10   // Decidegrees Celsius (e.g. 366 -> 36.6°C)
                in 15..85       -> 1    // Degrees Celsius (e.g. 36 -> 36°C)
                else            -> 1000
            }.also { divider ->
                Log.d(TAG, "Detected divider for $path: $divider (raw: $rawValue)")
            }
        }.getOrElse {
            Log.w(TAG, "Failed detecting temperature divider for $path, defaulting to 1000")
            1000
        }
    }

    /**
     * Returns the sysfs path and divider for battery temperature.
     */
    fun getBatteryTempInfo(): Pair<String?, Int> {
        val path = getBatteryTempPath() ?: return Pair(null, 1000)
        val divider = detectedDividers.getOrPut("battery_temp") {
            detectTemperatureDivider(path)
        }
        return Pair(path, divider)
    }

    /**
     * Dynamically searches for CPU temperature node with type-based prioritization.
     */
    private fun findCpuTempPath(): String? {
        detectedPaths["cpu_temp"]?.let { return it }

        val thermalBaseDirs = arrayOf("/sys/class/thermal", "/sys/devices/virtual/thermal")
        val priorityTypes = arrayOf("cpu_therm", "cpuss", "cpu", "cluster0")

        for (baseDirPath in thermalBaseDirs) {
            val baseDir = File(baseDirPath)
            if (!baseDir.exists() || !baseDir.isDirectory()) continue

            val zones = baseDir.listFiles() ?: continue

            // Pass 1: Prioritized known CPU thermal sensor types
            for (priorityType in priorityTypes) {
                for (zone in zones) {
                    val typeFile = File(zone, "type")
                    val tempFile = File(zone, "temp")

                    if (typeFile.exists() && tempFile.exists() && tempFile.canRead()) {
                        runCatching {
                            val type = typeFile.readText().trim().lowercase()
                            val isMatch = if (priorityType == "cpuss") type.startsWith("cpuss") else type == priorityType
                            if (isMatch) {
                                val path = tempFile.absolutePath
                                detectedPaths["cpu_temp"] = path
                                Log.d(TAG, "Found CPU temp (priority): $path (type=$type)")
                                return path
                            }
                        }
                    }
                }
            }

            // Pass 2: Fallback search for any thermal zone matching CPU patterns with valid range
            for (zone in zones) {
                val typeFile = File(zone, "type")
                val tempFile = File(zone, "temp")

                if (typeFile.exists() && tempFile.exists() && tempFile.canRead()) {
                    runCatching {
                        val type = typeFile.readText().trim().lowercase()
                        if (type.contains("cpu") || type.contains("core") ||
                            type.contains("cluster") || type.contains("tsens") ||
                            type.startsWith("thermal")) {
                            val temp = tempFile.readText().trim().toIntOrNull()
                            if (temp != null && temp in 20000..90000) {
                                val path = tempFile.absolutePath
                                detectedPaths["cpu_temp"] = path
                                Log.d(TAG, "Found CPU temp (fallback): $path (type=$type)")
                                return path
                            }
                        }
                    }
                }
            }
        }

        Log.w(TAG, "No suitable CPU temperature sysfs node found")
        detectedPaths["cpu_temp"] = null
        return null
    }

    /**
     * Returns CPU temperature path and divider.
     */
    fun getCpuTempInfo(): Pair<String?, Int> {
        val path = getCpuTempPath() ?: return Pair(null, 1000)
        val divider = detectedDividers.getOrPut("cpu_temp") {
            detectTemperatureDivider(path)
        }
        return Pair(path, divider)
    }

    private val BATTERY_TEMP_PATHS = arrayOf(
        "/sys/class/power_supply/battery/temp",
        "/sys/class/power_supply/battery/batt_temp",
        "/sys/class/power_supply/bms/temp",
        "/sys/class/oplus_chg/battery/temp",
        "/sys/class/oplus_chg/battery/batt_temp",
        "/sys/class/oplus_chg/battery/temperature",
        "/sys/class/oplus_chg/bq27541/temp"
    )

    private fun findBatteryTempPath(): String? {
        detectedPaths["battery_temp"]?.let { return it }

        // Pass 1: Direct battery supply paths
        for (path in BATTERY_TEMP_PATHS) {
            val file = File(path)
            if (file.exists() && file.canRead()) {
                val raw = file.readText().trim().toIntOrNull()
                if (raw != null && raw > 0) {
                    detectedPaths["battery_temp"] = path
                    Log.d(TAG, "Found battery temp path: $path (raw: $raw)")
                    return path
                }
            }
        }

        // Pass 2: Thermal zones labeled for battery
        val thermalBaseDirs = arrayOf("/sys/class/thermal", "/sys/devices/virtual/thermal")
        val batteryKeywords = arrayOf("battery", "batt", "bms")

        for (baseDirPath in thermalBaseDirs) {
            val baseDir = File(baseDirPath)
            if (!baseDir.exists() || !baseDir.isDirectory()) continue

            baseDir.listFiles()?.forEach { zone ->
                val typeFile = File(zone, "type")
                val tempFile = File(zone, "temp")

                if (typeFile.exists() && tempFile.exists() && tempFile.canRead()) {
                    runCatching {
                        val type = typeFile.readText().trim().lowercase()
                        if (batteryKeywords.any { type.contains(it) }) {
                            val raw = tempFile.readText().trim().toIntOrNull()
                            if (raw != null && raw > 0) {
                                val path = tempFile.absolutePath
                                detectedPaths["battery_temp"] = path
                                Log.d(TAG, "Found battery thermal zone: $path (type=$type, raw=$raw)")
                                return path
                            }
                        }
                    }
                }
            }
        }

        Log.w(TAG, "No valid battery temp sysfs node found")
        detectedPaths["battery_temp"] = null
        return null
    }

    private val FPS_PATHS = arrayOf(
        "/sys/class/drm/card0/sde_crtc_fps",
        "/sys/class/graphics/fb0/fps",
        "/sys/class/graphics/fb0/measured_fps",
        "/sys/class/drm/sde-crtc-0/measured_fps"
    )

    private fun detectPath(pathType: String, possiblePaths: Array<String>): String? {
        detectedPaths[pathType]?.let { return it }

        for (path in possiblePaths) {
            val file = File(path)
            if (file.exists() && file.canRead()) {
                Log.d(TAG, "Detected $pathType: $path")
                detectedPaths[pathType] = path
                return path
            }
        }

        Log.w(TAG, "No valid path found for $pathType")
        detectedPaths[pathType] = null
        return null
    }

    fun getFpsPath(): String? = detectPath("fps", FPS_PATHS)

    fun getBatteryTempPath(): String? = findBatteryTempPath()

    fun getCpuTempPath(): String? = findCpuTempPath()

    fun getCpuBasePath(): String? {
        detectedPaths["cpu_base"]?.let { return it }

        val path = "/sys/devices/system/cpu"
        val file = File(path)
        if (file.exists() && file.isDirectory) {
            detectedPaths["cpu_base"] = path
            Log.d(TAG, "Using CPU base path: $path")
            return path
        }

        Log.w(TAG, "CPU base path not found: $path")
        detectedPaths["cpu_base"] = null
        return null
    }

    fun resetDetection() {
        detectedPaths.clear()
        detectedDividers.clear()
        Log.d(TAG, "Sysfs detection cache cleared")
    }

    fun isComponentSupported(component: String): Boolean {
        return when (component) {
            "fps" -> getFpsPath() != null
            "battery_temp" -> getBatteryTempPath() != null
            "cpu_temp" -> getCpuTempPath() != null
            "cpu_base" -> getCpuBasePath() != null
            else -> false
        }
    }
}