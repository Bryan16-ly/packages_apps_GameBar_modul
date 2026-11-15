/*
 * SPDX-FileCopyrightText: 2025 kenway214
 * SPDX-License-Identifier: Apache-2.0
 */

package com.android.gamebar

import java.io.BufferedReader
import java.io.File
import java.io.FileReader
import java.io.IOException
import kotlin.math.abs

/**
 * Pembaca metrik baterai/daya dari power_supply sysfs.
 * Tidak membuat nilai estimasi: jika node tidak ada/tidak valid -> null.
 */
object GameBarBatteryInfo {

    private const val POWER_SUPPLY_DIR = "/sys/class/power_supply"

    private var resolved = false
    private var powerNowPath: String? = null
    private var currentPath: String? = null
    private var voltagePath: String? = null

    fun getBatteryTemperatureC(): Double? {
        val (path, divider) = GameBarConfig.getBatteryTempConfig()
        if (path == null) return null

        val raw = readFirstLine(path)?.trim()?.toDoubleOrNull() ?: return null
        val celsius = raw / divider.toDouble()
        return if (celsius > -20.0 && celsius < 120.0) celsius else null
    }

    /**
     * Daya baterai dalam Watt dari power_supply.
     * Prioritas: battery current * voltage, lalu power_now.
     * Arus baterai lebih merepresentasikan konsumsi perangkat dibanding node
     * power_now yang pada sebagian perangkat menunjukkan daya input charger.
     * Nilai di luar rentang wajar diabaikan.
     */
    fun getPowerWatts(): Double? {
        resolvePaths()

        val currentRaw = currentPath?.let { readFirstLine(it)?.trim()?.toDoubleOrNull() }
        val voltageRaw = voltagePath?.let { readFirstLine(it)?.trim()?.toDoubleOrNull() }

        if (currentRaw != null && voltageRaw != null) {
            val current = abs(currentRaw)
            val voltage = abs(voltageRaw)
            if (current > 0.0 && voltage > 0.0) {
                // Kombinasi satuan yang lazim: uA/mA dan uV/mV/V.
                val candidates = listOf(
                    (current / 1_000_000.0) * (voltage / 1_000_000.0), // uA * uV
                    (current / 1_000.0) * (voltage / 1_000.0),         // mA * mV
                    (current / 1_000.0) * (voltage / 1_000_000.0),     // mA * uV
                    (current / 1_000_000.0) * (voltage / 1_000.0),     // uA * mV
                    (current / 1_000.0) * voltage                     // mA * V
                )
                candidates.firstOrNull { it in 0.01..60.0 }?.let { return it }
            }
        }

        powerNowPath?.let { path ->
            val raw = readFirstLine(path)?.trim()?.toDoubleOrNull()
            if (raw != null && raw > 0) {
                // Sebagian kernel memakai uW, sebagian mW.
                listOf(raw / 1_000_000.0, raw / 1_000.0)
                    .firstOrNull { it in 0.01..60.0 }
                    ?.let { return it }
            }
        }
        return null
    }

    @Synchronized
    private fun resolvePaths() {
        if (resolved) return
        val supplies = File(POWER_SUPPLY_DIR).listFiles()
            ?.filter { it.isDirectory }
            ?.sortedBy { dir ->
                when {
                    dir.name.contains("battery", ignoreCase = true) -> 0
                    dir.name.contains("bms", ignoreCase = true) -> 1
                    else -> 2
                }
            }
            ?: emptyList()

        // Ambil semua node dari satu power_supply yang sama agar arus dan
        // tegangan tidak tercampur antar perangkat (mis. battery vs USB).
        for (dir in supplies) {
            val powerNow = if (isReadable(dir, "power_now")) {
                File(dir, "power_now").absolutePath
            } else null
            val current = listOf("current_now", "current_avg")
                .firstOrNull { isReadable(dir, it) }
                ?.let { File(dir, it).absolutePath }
            val voltage = listOf("voltage_now", "voltage_avg")
                .firstOrNull { isReadable(dir, it) }
                ?.let { File(dir, it).absolutePath }

            if (current != null && voltage != null) {
                currentPath = current
                voltagePath = voltage
                powerNowPath = powerNow
                resolved = true
                return
            }
            if (powerNow != null) {
                powerNowPath = powerNow
                currentPath = current
                voltagePath = voltage
                resolved = true
                return
            }
        }
        resolved = true
    }

    private fun isReadable(dir: File, name: String): Boolean {
        val file = File(dir, name)
        return file.exists() && file.canRead()
    }

    private fun readFirstLine(path: String): String? {
        return try {
            BufferedReader(FileReader(path)).use { it.readLine() }
        } catch (e: IOException) {
            null
        }
    }
}
