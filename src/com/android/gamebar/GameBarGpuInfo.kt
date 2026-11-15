/*
 * SPDX-FileCopyrightText: 2025 kenway214
 * SPDX-License-Identifier: Apache-2.0
 */


package com.android.gamebar

import java.io.BufferedReader
import java.io.FileReader
import java.io.IOException

import java.util.Locale

object GameBarGpuInfo {

    fun getGpuUsage(): String {
        val line = readLine(GameBarConfig.gpuUsagePath) ?: return "N/A"
        val cleanLine = line.replace("%", "").trim()
        return try {
            val value = cleanLine.toInt()
            value.toString()
        } catch (e: NumberFormatException) {
            "N/A"
        }
    }

    fun getGpuClock(): String {
        val line = readLine(GameBarConfig.gpuClockPath) ?: return "N/A"
        val cleanLine = line.trim()
        try {
            val hz = line.trim().toLong()
            val mhz = hz / GameBarConfig.gpuClockDivider
            return "$mhz".toString()
        } catch (e: NumberFormatException) {
            return "N/A"
        }
    }

    fun getGpuTemp(): String {
        val celsius = getGpuTempC() ?: return "N/A"
        return String.format(Locale.getDefault(), "%.1f", celsius)
    }

    /** Suhu GPU dalam °C, null jika node tidak tersedia/nilai tidak valid. */
    fun getGpuTempC(): Double? {
        val line = readLine(GameBarConfig.gpuTempPath) ?: return null
        val raw = line.trim().toDoubleOrNull() ?: return null
        val celsius = raw / GameBarConfig.gpuTempDivider.toDouble()
        return if (celsius > -20.0 && celsius < 150.0) celsius else null
    }

    private fun readLine(path: String): String? {
        return try {
            BufferedReader(FileReader(path)).use { it.readLine() }
        } catch (e: IOException) {
            null
        }
    }
}
