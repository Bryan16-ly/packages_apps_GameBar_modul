/*
 * SPDX-FileCopyrightText: 2025 kenway214
 * SPDX-License-Identifier: Apache-2.0
 */


package com.android.gamebar

import java.io.BufferedReader
import java.io.File
import java.io.FileReader
import java.io.IOException

import java.util.Locale

object GameBarMemInfo {

    data class RamUsageInfo(
        val usedMb: Long,
        val usedPercent: Double
    )

    fun getRamUsage(): String = getRamUsageInfo()?.usedMb?.toString() ?: "N/A"

    fun getRamUsageInfo(): RamUsageInfo? {
        var memTotal = 0L
        var memAvailable = 0L

        try {
            BufferedReader(FileReader(GameBarConfig.procMeminfoPath)).use { br ->
                var line: String?
                while (br.readLine().also { line = it } != null) {
                    line?.let {
                        when {
                            it.startsWith("MemTotal:") -> memTotal = parseMemValue(it)
                            it.startsWith("MemAvailable:") -> memAvailable = parseMemValue(it)
                        }
                        if (memTotal > 0 && memAvailable > 0) {
                            return@use
                        }
                    }
                }
            }
        } catch (e: IOException) {
            return null
        }

        if (memTotal == 0L) return null

        val usedKb = (memTotal - memAvailable).coerceAtLeast(0L)
        val usedMb = usedKb / 1024
        val usedPercent = if (memTotal > 0) usedKb * 100.0 / memTotal else 0.0
        return RamUsageInfo(usedMb, usedPercent)
    }

    private fun parseMemValue(line: String): Long {
        val parts = line.split("\\s+".toRegex())
        if (parts.size < 3) {
            return 0L
        }
        return try {
            parts[1].toLong()
        } catch (e: NumberFormatException) {
            0L
        }
    }

    fun getRamSpeed(): String {
        val mhz = getRamFrequencyMhz() ?: return "N/A"
        return if (mhz >= 1000) {
            String.format("%.3f GHz", mhz / 1000.0)
        } else {
            String.format("%.0f MHz", mhz)
        }
    }

    /** Frekuensi RAM dalam MHz, null jika node tidak tersedia/terbaca. */
    fun getRamFrequencyMhz(): Double? {
        val khz = readFirstLine(GameBarConfig.ramFreqPath)?.trim()?.toDoubleOrNull()
        return if (khz != null && khz > 0) khz / 1000.0 else null
    }

    fun getRamTemp(): String {
        val celsius = getRamTemperatureC() ?: return "N/A"
        return String.format("%.1f°C", celsius)
    }

    /** Suhu RAM dalam °C, null jika sensor tidak valid/tidak tersedia. */
    fun getRamTemperatureC(): Double? {
        val path = GameBarConfig.ramTempPath
        if (!isRamTempCandidate(path)) return null
        val raw = readFirstLine(path)?.trim()?.toDoubleOrNull()
            ?: return null
        val celsius = raw / GameBarConfig.ramTempDivider.toDouble()
        return if (celsius > -20.0 && celsius < 120.0) celsius else null
    }

    /**
     * Tolak path yang jelas bukan sensor RAM. Jika thermal zone menyediakan file
     * "type", tipe harus mengandung ram/ddr/dram/mem. Path tanpa metadata tetap
     * dipercaya karena berasal dari konfigurasi perangkat.
     */
    private fun isRamTempCandidate(path: String): Boolean {
        val typeFile = File(File(path).parentFile, "type")
        if (!typeFile.exists() || !typeFile.canRead()) return true
        val type = readFirstLine(typeFile.absolutePath)?.trim()?.lowercase(Locale.US)
            ?: return true
        if (type.isEmpty()) return true
        return type.contains("ram") ||
            type.contains("ddr") ||
            type.contains("dram") ||
            type.contains("mem")
    }

    private fun readFirstLine(path: String): String? {
        return try {
            BufferedReader(FileReader(path)).use { it.readLine() }
        } catch (e: IOException) {
            null
        }
    }
}
