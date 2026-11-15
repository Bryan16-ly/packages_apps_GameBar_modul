/*
 * SPDX-FileCopyrightText: 2025 kenway214
 * SPDX-License-Identifier: Apache-2.0
 */


package com.android.gamebar

import java.io.BufferedReader
import java.io.FileReader
import java.io.IOException
import java.io.File
import java.util.*

object GameBarCpuInfo {

    private var prevIdle = -1L
    private var prevTotal = -1L

    fun getCpuUsage(): String =
        getCpuUsagePercent()?.toInt()?.toString() ?: "N/A"

    @Synchronized
    fun getCpuUsagePercent(): Double? {
        val line = readLine(GameBarConfig.procStatPath)
        if (line == null || !line.startsWith("cpu ")) return null
        val parts = line.split("\\s+".toRegex())
        if (parts.size < 8) return null

        return try {
            val user = parts[1].toLong()
            val nice = parts[2].toLong()
            val system = parts[3].toLong()
            val idle = parts[4].toLong()
            val iowait = parts[5].toLong()
            val irq = parts[6].toLong()
            val softirq = parts[7].toLong()
            val steal = if (parts.size > 8) parts[8].toLong() else 0L

            val total = user + nice + system + idle + iowait + irq + softirq + steal

            if (prevTotal != -1L && total != prevTotal) {
                val diffTotal = total - prevTotal
                val diffIdle = idle - prevIdle
                prevTotal = total
                prevIdle = idle
                if (diffTotal <= 0) return null
                (100.0 * (diffTotal - diffIdle) / diffTotal).coerceIn(0.0, 100.0)
            } else {
                prevTotal = total
                prevIdle = idle
                null
            }
        } catch (e: NumberFormatException) {
            null
        }
    }

    fun getCpuFrequencies(): List<String> {
        val result = mutableListOf<String>()
        val basePath = GameBarConfig.cpuBasePath
        if (basePath == null) {
            return result
        }

        val cpuDir = File(basePath)
        val files = cpuDir.listFiles { _, name -> name.matches(Regex("cpu\\d+")) }
        if (files.isNullOrEmpty()) {
            return result
        }

        val cpuFolders = files.toMutableList()
        cpuFolders.sortBy { extractCpuNumber(it) }

        for (cpu in cpuFolders) {
            val freqPath = "${cpu.absolutePath}/cpufreq/scaling_cur_freq"
            val freqStr = readLine(freqPath)
            if (!freqStr.isNullOrEmpty()) {
                try {
                    val khz = freqStr.trim().toInt()
                    val mhz = khz / 1000
                    result.add("${cpu.name}: $mhz MHz")
                } catch (e: NumberFormatException) {
                    result.add("${cpu.name}: N/A")
                }
            } else {
                result.add("${cpu.name}: offline or frequency not available")
            }
        }
        return result
    }

    /**
     * Frekuensi tiap core dalam MHz. Mengembalikan map core-index -> MHz untuk
     * core yang online dan terbaca. Map kosong berarti tidak tersedia.
     */
    fun getCpuFrequenciesMhz(): Map<Int, Int> {
        val basePath = GameBarConfig.cpuBasePath ?: return emptyMap()
        val cpuDir = File(basePath)
        val files = cpuDir.listFiles { _, name -> name.matches(Regex("cpu\\d+")) }
            ?: return emptyMap()

        val result = sortedMapOf<Int, Int>()
        files.sortedBy { extractCpuNumber(it) }.forEach { cpu ->
            val index = extractCpuNumber(cpu)
            if (index < 0) return@forEach
            val freqStr = readLine("${cpu.absolutePath}/cpufreq/scaling_cur_freq")
            val khz = freqStr?.trim()?.toLongOrNull() ?: return@forEach
            if (khz > 0) {
                val mhz = (khz / 1000).toInt()
                if (mhz > 0) result[index] = mhz
            }
        }
        return result
    }

    fun getCpuTemp(): String {
        val celsius = getCpuTemperatureC() ?: return "N/A"
        return String.format(Locale.getDefault(), "%.1f", celsius)
    }

    fun getCpuTemperatureC(): Double? {
        val (path, divider) = GameBarConfig.getCpuTempConfig()
        if (path == null) return null

        val raw = readLine(path)?.trim()?.toFloatOrNull() ?: return null
        val celsius = raw / divider.toFloat()
        // Sanity check: CPU temp should be between 0 and 150°C
        return if (celsius > 0f && celsius < 150f) celsius.toDouble() else null
    }

    private fun extractCpuNumber(cpuFolder: File): Int {
        val name = cpuFolder.name.replace("cpu", "")
        return try {
            name.toInt()
        } catch (e: NumberFormatException) {
            -1
        }
    }

    private fun readLine(path: String): String? {
        return try {
            BufferedReader(FileReader(path)).use { it.readLine() }
        } catch (e: IOException) {
            null
        }
    }
}
