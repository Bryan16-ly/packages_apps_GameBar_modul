/*
 * SPDX-FileCopyrightText: 2025 kenway214
 * SPDX-License-Identifier: Apache-2.0
 */

package com.android.gamebar

/**
 * Data model untuk satu sesi rekaman performa.
 *
 * @param id           UUID string unik per sesi
 * @param appPackage   package name aplikasi yang direkam
 * @param appName      label tampilan aplikasi
 * @param startTime    epoch-ms saat rekaman dimulai
 * @param screenWidth  lebar layar aktual saat sesi dimulai (0 = tidak diketahui)
 * @param screenHeight tinggi layar aktual saat sesi dimulai (0 = tidak diketahui)
 * @param endTime      epoch-ms saat rekaman dihentikan (0 = masih berjalan)
 * @param samples      list sampel performa dengan timestamp yang sama untuk semua metrik
 */
data class FpsRecordingSession(
    val id: String,
    val appPackage: String,
    val appName: String,
    val startTime: Long,
    val screenWidth: Int = 0,
    val screenHeight: Int = 0,
    var endTime: Long = 0L,
    val samples: MutableList<FpsSample> = mutableListOf(),
    var csvPath: String? = null
) {
    /**
     * Satu sampel performa. Semua metrik memakai satu [timestamp] agar grafik
     * dapat dibandingkan pada sumbu waktu yang sama. Nilai null berarti metrik
     * tidak tersedia pada perangkat/sesi tersebut (bukan nilai palsu).
     */
    data class FpsSample(
        val timestamp: Long,                                  // epoch-ms
        val fps: Float,
        val frameTimeMs: Double? = null,
        val cpuUsagePct: Double? = null,
        val cpuFreqMhz: Double? = null,
        val cpuFreqCoresMhz: Map<Int, Int>? = null,
        val cpuTempC: Double? = null,
        val gpuUsagePct: Double? = null,
        val gpuClockMhz: Double? = null,
        val gpuTempC: Double? = null,
        val ramUsageMb: Long? = null,
        val ramUsagePct: Double? = null,
        val ramFreqMhz: Double? = null,
        val ramTempC: Double? = null,
        val batteryTempC: Double? = null,
        val powerW: Double? = null
    )

    data class MetricSummary(
        val min: Double,
        val max: Double,
        val avg: Double,
        val last: Double,
        val count: Int
    )

    /** Durasi sesi dalam milidetik. */
    val durationMs: Long
        get() = if (endTime > startTime) endTime - startTime else 0L

    /** Resolusi layar saat sesi dimulai, atau N/A untuk sesi lama. */
    val resolutionText: String
        get() = if (screenWidth > 0 && screenHeight > 0) {
            "${screenWidth}x${screenHeight}"
        } else {
            "N/A"
        }

    private fun validFpsSamples(): List<FpsSample> = samples.filter { it.fps > 0f }

    val hasFpsData: Boolean
        get() = validFpsSamples().isNotEmpty()

    /** Rata-rata FPS dari seluruh sampel FPS yang valid. */
    val avgFps: Double
        get() {
            val valid = validFpsSamples()
            return if (valid.isEmpty()) 0.0
            else valid.sumOf { it.fps.toDouble() } / valid.size
        }

    /** FPS minimum. */
    val minFps: Float
        get() = validFpsSamples().minOfOrNull { it.fps } ?: 0f

    /** FPS maksimum. */
    val maxFps: Float
        get() = validFpsSamples().maxOfOrNull { it.fps } ?: 0f

    /**
     * Low FPS: rata-rata sampel FPS terendah sebanyak persentase tertentu.
     * Metode ini sama dengan perhitungan analitik GameBar yang sudah ada.
     */
    private fun lowFps(percent: Double): Double {
        val valid = validFpsSamples()
        if (valid.isEmpty()) return 0.0
        val count = (valid.size * percent).toInt().coerceAtLeast(1)
        return valid.sortedBy { it.fps }
            .take(count)
            .sumOf { it.fps.toDouble() } / count
    }

    /** 1% low FPS. */
    val fps1PercentLow: Double
        get() = lowFps(0.01)

    /** 0.1% low FPS. */
    val fps01PercentLow: Double
        get() = lowFps(0.001)

    /** 5% low FPS. */
    val fps5PercentLow: Double
        get() = lowFps(0.05)

    /** Konversi sampel FPS ke list Pair<Long, Double> yang dibutuhkan FpsGraphView.setData(). */
    fun toGraphData(): List<Pair<Long, Double>> =
        samples.filter { it.fps > 0f }.map { Pair(it.timestamp, it.fps.toDouble()) }

    /** Konversi metrik arbitrer ke data grafik berbasis timestamp. */
    fun toGraphData(selector: (FpsSample) -> Double?): List<Pair<Long, Double>> =
        samples.mapNotNull { sample ->
            selector(sample)?.let { Pair(sample.timestamp, it) }
        }

    /** Hitung min/max/avg/last untuk metrik yang tersedia saja. */
    private fun summarize(values: List<Double>): MetricSummary? {
        if (values.isEmpty()) return null
        return MetricSummary(
            min = values.min(),
            max = values.max(),
            avg = values.average(),
            last = values.last(),
            count = values.size
        )
    }

    private fun summarizeBy(selector: (FpsSample) -> Double?): MetricSummary? =
        summarize(samples.mapNotNull(selector))

    // Frame time: gunakan nilai tersimpan, atau turunkan dari FPS untuk sesi lama.
    val frameTimeSummary: MetricSummary?
        get() = summarizeBy {
            it.frameTimeMs ?: if (it.fps > 0f) 1000.0 / it.fps else null
        }

    val cpuUsageSummary: MetricSummary?
        get() = summarizeBy { it.cpuUsagePct }

    val cpuFreqSummary: MetricSummary?
        get() = summarizeBy { it.cpuFreqMhz }

    val cpuTempSummary: MetricSummary?
        get() = summarizeBy { it.cpuTempC }

    val gpuUsageSummary: MetricSummary?
        get() = summarizeBy { it.gpuUsagePct }

    val gpuClockSummary: MetricSummary?
        get() = summarizeBy { it.gpuClockMhz }

    val gpuTempSummary: MetricSummary?
        get() = summarizeBy { it.gpuTempC }

    /**
     * Suhu maksimum baterai pada sesi.
     * Ringkasan "MAX Temperature" sengaja hanya memakai sensor battery temp.
     */
    val maxTemperatureC: Double?
        get() = batteryTempSummary?.max

    val ramUsageSummary: MetricSummary?
        get() = summarizeBy { it.ramUsageMb?.toDouble() }

    val ramUsagePctSummary: MetricSummary?
        get() = summarizeBy { it.ramUsagePct }

    val ramFreqSummary: MetricSummary?
        get() = summarizeBy { it.ramFreqMhz }

    val ramTempSummary: MetricSummary?
        get() = summarizeBy { it.ramTempC }

    val batteryTempSummary: MetricSummary?
        get() = summarizeBy { it.batteryTempC }

    val powerSummary: MetricSummary?
        get() = summarizeBy { it.powerW }

    /** Ringkasan rata-rata frekuensi per-core bila perangkat menyediakannya. */
    fun cpuCoreSummaries(): Map<Int, MetricSummary> {
        val coreIds = samples
            .flatMap { it.cpuFreqCoresMhz?.keys ?: emptySet() }
            .toSortedSet()

        return coreIds.mapNotNull { core ->
            val values = samples.mapNotNull { it.cpuFreqCoresMhz?.get(core)?.toDouble() }
            summarize(values)?.let { core to it }
        }.toMap()
    }
}
