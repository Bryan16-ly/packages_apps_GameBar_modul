/*
 * SPDX-FileCopyrightText: 2025 kenway214
 * SPDX-License-Identifier: Apache-2.0
 */

package com.android.gamebar

import android.app.Activity
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.FileProvider
import androidx.fragment.app.Fragment
import com.android.gamebar.R
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

/**
 * Dashboard analisis performa untuk satu sesi rekaman:
 * - Header aplikasi/sesi + ringkasan 4x2 tanpa card
 * - FPS card + grafik
 * - Card metrik: battery, frame time, CPU, GPU, RAM, power
 *
 * Metrik yang tidak tersedia ditandai "N/A" atau tidak ditampilkan jika optional.
 */
class FpsRecordDetailFragment : Fragment() {

    companion object {
        private const val REQUEST_EXPORT_CSV = 9001
    }

    private lateinit var tvApp:          TextView
    private lateinit var tvMeta:         TextView
    private lateinit var tvResolution:   TextView
    private lateinit var statsRow1:      LinearLayout
    private lateinit var statsRow2:      LinearLayout
    private lateinit var tvAvg:          TextView
    private lateinit var tvMax:          TextView
    private lateinit var tvMin:          TextView
    private lateinit var tvLow1:         TextView
    private lateinit var fpsGraph:       FpsGraphView
    private lateinit var dashboard:      LinearLayout
    private var rootView: View? = null
    private var currentSession: FpsRecordingSession? = null
    private var pendingCsvExport: File? = null

    private val colorBatteryTemp = Color.parseColor("#FF9800")
    private val colorFrameTime   = Color.parseColor("#2196F3")
    private val colorCpuUsage    = Color.parseColor("#03A9F4")
    private val colorCpuFreq     = Color.parseColor("#FFA726")
    private val colorCpuTemp     = Color.parseColor("#00BCD4")
    private val colorGpuUsage    = Color.parseColor("#8BC34A")
    private val colorGpuClock    = Color.parseColor("#9C27B0")
    private val colorGpuTemp     = Color.parseColor("#E91E63")
    private val colorRamUsage    = Color.parseColor("#FFEB3B")
    private val colorRamFreq     = Color.parseColor("#4CAF50")
    private val colorRamTemp     = Color.parseColor("#FF9800")
    private val colorPower       = Color.parseColor("#42A5F5")

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? = inflater.inflate(R.layout.fragment_fps_record_detail, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        rootView = view

        tvApp        = view.findViewById(R.id.tv_detail_app)
        tvMeta       = view.findViewById(R.id.tv_detail_meta)
        tvResolution = view.findViewById(R.id.tv_detail_resolution)
        statsRow1    = view.findViewById(R.id.stats_row_1)
        statsRow2    = view.findViewById(R.id.stats_row_2)
        tvAvg        = view.findViewById(R.id.tv_detail_avg)
        tvMax        = view.findViewById(R.id.tv_detail_max)
        tvMin        = view.findViewById(R.id.tv_detail_min)
        tvLow1       = view.findViewById(R.id.tv_detail_low1)
        fpsGraph     = view.findViewById(R.id.fps_graph_view)
        dashboard    = view.findViewById(R.id.dashboard_container)

        val sessionId = arguments?.getString(FpsRecordDetailActivity.EXTRA_SESSION_ID)
        if (sessionId == null) {
            Toast.makeText(requireContext(), "Session not found", Toast.LENGTH_SHORT).show()
            return
        }

        loadSession(sessionId)
    }

    private fun loadSession(sessionId: String) {
        Thread {
            val session = FpsRecordingManager.getInstance(requireContext())
                .loadAllSessions()
                .find { it.id == sessionId }

            activity?.runOnUiThread {
                if (session == null) {
                    Toast.makeText(requireContext(), "Session not found", Toast.LENGTH_SHORT).show()
                    return@runOnUiThread
                }
                bindSession(session)
            }
        }.start()
    }

    private fun bindSession(session: FpsRecordingSession) {
        currentSession = session
        tvApp.text = session.appName
        tvMeta.text = getString(
            R.string.session_meta_format,
            formatDate(session.startTime),
            formatDuration(session.durationMs)
        )
        tvResolution.text = getString(R.string.session_resolution, displayResolution(session))

        val hasFps = session.hasFpsData
        tvAvg.text  = if (hasFps) getString(R.string.fps_detail_avg,  session.avgFps.roundToInt())  else na()
        tvMax.text  = if (hasFps) getString(R.string.fps_detail_max,  session.maxFps.roundToInt())  else na()
        tvMin.text  = if (hasFps) getString(R.string.fps_detail_min,  session.minFps.roundToInt())  else na()
        tvLow1.text = if (hasFps) getString(R.string.fps_detail_low1, session.fps1PercentLow.roundToInt()) else na()

        val fpsData = session.toGraphData()
        if (fpsData.isNotEmpty()) {
            fpsGraph.visibility = View.VISIBLE
            fpsGraph.setData(fpsData, session.avgFps, session.fps1PercentLow)
        } else {
            fpsGraph.visibility = View.GONE
        }

        bindSummary(session)
        buildDashboard(session)
    }

    private fun bindSummary(session: FpsRecordingSession) {
        statsRow1.removeAllViews()
        statsRow2.removeAllViews()

        val fps = R.string.summary_unit_fps
        val hasFps = session.hasFpsData
        val stats = listOf(
            SummaryItem(R.string.summary_label_max,      if (hasFps) formatOneDecimal(session.maxFps.toDouble()) else na(), fps),
            SummaryItem(R.string.summary_label_min,      if (hasFps) formatOneDecimal(session.minFps.toDouble()) else na(), fps),
            SummaryItem(R.string.summary_label_average,  if (hasFps) formatOneDecimal(session.avgFps) else na(), fps),
            SummaryItem(R.string.summary_label_1p_low,   if (hasFps) formatOneDecimal(session.fps1PercentLow) else na(), fps),
            SummaryItem(R.string.summary_label_01p_low,  if (hasFps) formatOneDecimal(session.fps01PercentLow) else na(), fps),
            SummaryItem(R.string.summary_label_5p_low,   if (hasFps) formatOneDecimal(session.fps5PercentLow) else na(), fps),
            SummaryItem(
                R.string.summary_label_max,
                session.maxTemperatureC?.let { formatOneDecimal(it) } ?: na(),
                R.string.summary_unit_celsius
            ),
            SummaryItem(
                R.string.summary_label_average,
                session.powerSummary?.avg?.let { formatTwoDecimals(it) } ?: na(),
                R.string.summary_unit_watt
            )
        )

        stats.take(4).forEach { addSummaryItem(statsRow1, it) }
        stats.drop(4).forEach { addSummaryItem(statsRow2, it) }
    }

    private fun addSummaryItem(row: LinearLayout, item: SummaryItem) {
        val view = layoutInflater.inflate(R.layout.item_recording_stat, row, false)
        view.layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        view.findViewById<TextView>(R.id.tv_stat_label).setText(item.labelRes)
        view.findViewById<TextView>(R.id.tv_stat_value).text = item.value
        view.findViewById<TextView>(R.id.tv_stat_unit).setText(item.unitRes)
        row.addView(view)
    }

    private fun buildDashboard(session: FpsRecordingSession) {
        dashboard.removeAllViews()

        addMetricCard(
            titleRes  = R.string.metric_title_battery_temp,
            summary   = session.batteryTempSummary,
            graphData = session.toGraphData { it.batteryTempC },
            config    = MetricGraphView.Config(
                yAxisLabel  = "Battery Temp (°C)",
                unit        = "°C",
                lineColor   = colorBatteryTemp
            ),
            formatter = ::formatTemp
        )

        addMetricCard(
            titleRes  = R.string.metric_title_frame_time,
            summary   = session.frameTimeSummary,
            graphData = session.toGraphData {
                it.frameTimeMs ?: if (it.fps > 0f) 1000.0 / it.fps else null
            },
            config    = MetricGraphView.Config(
                yAxisLabel  = "Frame Time (ms)",
                unit        = "ms",
                lineColor   = colorFrameTime,
                baselineZero = true,
                maxFloor    = 20.0
            ),
            formatter = ::formatFrameTime
        )

        addMetricCard(
            titleRes  = R.string.metric_title_cpu_usage,
            summary   = session.cpuUsageSummary,
            graphData = session.toGraphData { it.cpuUsagePct },
            config    = MetricGraphView.Config(
                yAxisLabel   = "CPU Usage (%)",
                unit         = "%",
                lineColor    = colorCpuUsage,
                baselineZero = true,
                maxFloor     = 100.0
            ),
            formatter = ::formatPercent
        )

        addMetricCard(
            titleRes  = R.string.metric_title_cpu_freq,
            summary   = session.cpuFreqSummary,
            graphData = session.toGraphData { it.cpuFreqMhz },
            config    = MetricGraphView.Config(
                yAxisLabel = "CPU Freq (MHz)",
                unit       = "MHz",
                lineColor  = colorCpuFreq
            ),
            formatter = ::formatMhz,
            perCore   = session.cpuCoreSummaries()
        )

        addMetricCard(
            titleRes  = R.string.metric_title_cpu_temp,
            summary   = session.cpuTempSummary,
            graphData = session.toGraphData { it.cpuTempC },
            config    = MetricGraphView.Config(
                yAxisLabel = "CPU Temp (°C)",
                unit       = "°C",
                lineColor  = colorCpuTemp
            ),
            formatter = ::formatTemp
        )

        if (session.gpuUsageSummary != null) {
            addMetricCard(
                titleRes  = R.string.metric_title_gpu_usage,
                summary   = session.gpuUsageSummary,
                graphData = session.toGraphData { it.gpuUsagePct },
                config    = MetricGraphView.Config(
                    yAxisLabel   = "GPU Usage (%)",
                    unit         = "%",
                    lineColor    = colorGpuUsage,
                    baselineZero = true,
                    maxFloor     = 100.0
                ),
                formatter = ::formatPercent
            )
        }

        if (session.gpuClockSummary != null) {
            addMetricCard(
                titleRes  = R.string.metric_title_gpu_clock,
                summary   = session.gpuClockSummary,
                graphData = session.toGraphData { it.gpuClockMhz },
                config    = MetricGraphView.Config(
                    yAxisLabel = "GPU Clock (MHz)",
                    unit       = "MHz",
                    lineColor  = colorGpuClock
                ),
                formatter = ::formatMhz
            )
        }

        if (session.gpuTempSummary != null) {
            addMetricCard(
                titleRes  = R.string.metric_title_gpu_temp,
                summary   = session.gpuTempSummary,
                graphData = session.toGraphData { it.gpuTempC },
                config    = MetricGraphView.Config(
                    yAxisLabel = "GPU Temp (°C)",
                    unit       = "°C",
                    lineColor  = colorGpuTemp
                ),
                formatter = ::formatTemp
            )
        }

        addMetricCard(
            titleRes  = R.string.metric_title_ram_usage,
            summary   = session.ramUsageSummary,
            graphData = session.toGraphData { it.ramUsageMb?.toDouble() },
            config    = MetricGraphView.Config(
                yAxisLabel = "RAM Usage (MB)",
                unit       = "MB",
                lineColor  = colorRamUsage
            ),
            formatter = ::formatMb,
            extra     = session.ramUsagePctSummary?.let {
                getString(
                    R.string.metric_ram_percent,
                    String.format(Locale.getDefault(), "%.1f", it.last),
                    String.format(Locale.getDefault(), "%.1f", it.avg)
                )
            }
        )

        if (session.ramFreqSummary != null) {
            addMetricCard(
                titleRes  = R.string.metric_title_ram_freq,
                summary   = session.ramFreqSummary,
                graphData = session.toGraphData { it.ramFreqMhz },
                config    = MetricGraphView.Config(
                    yAxisLabel = "RAM Freq (MHz)",
                    unit       = "MHz",
                    lineColor  = colorRamFreq
                ),
                formatter = ::formatMhz
            )
        }

        if (session.ramTempSummary != null) {
            addMetricCard(
                titleRes  = R.string.metric_title_ram_temp,
                summary   = session.ramTempSummary,
                graphData = session.toGraphData { it.ramTempC },
                config    = MetricGraphView.Config(
                    yAxisLabel = "RAM Temp (°C)",
                    unit       = "°C",
                    lineColor  = colorRamTemp
                ),
                formatter = ::formatTemp
            )
        }

        addMetricCard(
            titleRes  = R.string.metric_title_power,
            summary   = session.powerSummary,
            graphData = session.toGraphData { it.powerW },
            config    = MetricGraphView.Config(
                yAxisLabel   = "Power (W)",
                unit         = "W",
                lineColor    = colorPower,
                baselineZero = true
            ),
            formatter = ::formatWatts
        )
    }

    // ─── Export / Share ───────────────────────────────────────────────────────

    fun exportCsv() {
        val session = currentSession ?: run {
            toast(R.string.fps_no_session_ready)
            return
        }
        val file = FpsRecordingManager.getInstance(requireContext()).ensureCsv(session)
        if (file == null || !file.isFile) {
            toast(R.string.fps_csv_not_ready)
            return
        }

        pendingCsvExport = file
        val intent = Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "text/csv"
            putExtra(Intent.EXTRA_TITLE, file.name)
        }
        try {
            startActivityForResult(intent, REQUEST_EXPORT_CSV)
        } catch (e: Exception) {
            pendingCsvExport = null
            toast(getString(R.string.fps_export_csv_failed, e.message ?: "error"))
        }
    }

    fun shareCsv() {
        val session = currentSession ?: run {
            toast(R.string.fps_no_session_ready)
            return
        }
        val file = FpsRecordingManager.getInstance(requireContext()).ensureCsv(session)
        if (file == null || !file.isFile) {
            toast(R.string.fps_csv_not_ready)
            return
        }
        shareFile(file, "text/csv", "FPS Recording CSV")
    }

    fun exportPng() {
        val bitmap = captureDetailBitmap() ?: run {
            toast(R.string.fps_no_session_ready)
            return
        }
        try {
            val stamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
            val session = currentSession
            val app = session?.appName?.replace(Regex("[^A-Za-z0-9._-]"), "_") ?: "FPS"
            val fileName = "FPS_Record_${app}_$stamp.png"
            val uri = GameBarStorage.savePngToPictures(requireContext(), fileName, bitmap)
            if (uri == null) {
                toast(getString(R.string.fps_png_failed, "Pictures/GameBar is not writable"))
            } else {
                toast(getString(R.string.fps_png_saved, "Pictures/GameBar/$fileName"))
            }
        } catch (e: Exception) {
            toast(getString(R.string.fps_png_failed, e.message ?: "error"))
        }
    }

    fun sharePng() {
        val bitmap = captureDetailBitmap() ?: run {
            toast(R.string.fps_no_session_ready)
            return
        }
        try {
            val stamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
            val session = currentSession
            val app = session?.appName?.replace(Regex("[^A-Za-z0-9._-]"), "_") ?: "FPS"
            val fileName = "FPS_Record_${app}_${stamp}.png"
            val uri = GameBarStorage.savePngToPictures(requireContext(), fileName, bitmap)
            if (uri == null) {
                toast(getString(R.string.fps_png_failed, "Pictures/GameBar is not writable"))
                return
            }
            shareUri(uri, "image/png", "FPS Recording")
        } catch (e: Exception) {
            toast(getString(R.string.fps_share_failed, e.message ?: "error"))
        }
    }

    private fun shareFile(file: File, mimeType: String, subject: String) {
        try {
            val uri = FileProvider.getUriForFile(
                requireContext(),
                "${requireContext().packageName}.fileprovider",
                file
            )
            shareUri(uri, mimeType, subject)
        } catch (e: Exception) {
            toast(getString(R.string.fps_share_failed, e.message ?: "error"))
        }
    }

    private fun shareUri(uri: android.net.Uri, mimeType: String, subject: String) {
        try {
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = mimeType
                putExtra(Intent.EXTRA_STREAM, uri)
                putExtra(Intent.EXTRA_SUBJECT, subject)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            startActivity(Intent.createChooser(intent, subject))
        } catch (e: Exception) {
            toast(getString(R.string.fps_share_failed, e.message ?: "error"))
        }
    }

    private fun captureDetailBitmap(): Bitmap? {
        val root = rootView ?: return null
        val content = (root as? ScrollView)?.getChildAt(0) ?: root
        val width = if (content.width > 0) {
            content.width
        } else {
            resources.displayMetrics.widthPixels - dp(32)
        }
        if (width <= 0) return null

        content.measure(
            View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
        )
        val height = content.measuredHeight
        if (height <= 0) return null

        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(Color.parseColor("#121212"))
        content.layout(0, 0, width, height)
        content.draw(canvas)
        return bitmap
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode != REQUEST_EXPORT_CSV) return

        val source = pendingCsvExport
        pendingCsvExport = null
        if (resultCode != Activity.RESULT_OK || source == null) return

        val uri = data?.data ?: return
        try {
            requireContext().contentResolver.openOutputStream(uri)?.use { output ->
                source.inputStream().use { input -> input.copyTo(output) }
            }
            toast(R.string.fps_export_csv_success)
        } catch (e: Exception) {
            toast(getString(R.string.fps_export_csv_failed, e.message ?: "error"))
        }
    }

    private fun toast(messageRes: Int) {
        Toast.makeText(requireContext(), messageRes, Toast.LENGTH_SHORT).show()
    }

    private fun toast(message: String) {
        Toast.makeText(requireContext(), message, Toast.LENGTH_LONG).show()
    }

    private fun addMetricCard(
        titleRes: Int,
        summary: FpsRecordingSession.MetricSummary?,
        graphData: List<Pair<Long, Double>>,
        config: MetricGraphView.Config,
        formatter: (Double) -> String,
        extra: String? = null,
        perCore: Map<Int, FpsRecordingSession.MetricSummary>? = null
    ) {
        val card = layoutInflater.inflate(R.layout.item_metric_card, dashboard, false)
        card.layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply {
            bottomMargin = dp(12)
        }

        val titleView   = card.findViewById<TextView>(R.id.tv_metric_title)
        val statusView  = card.findViewById<TextView>(R.id.tv_metric_status)
        val statsView   = card.findViewById<LinearLayout>(R.id.ll_metric_stats)
        val latestView  = card.findViewById<TextView>(R.id.tv_metric_latest)
        val avgView     = card.findViewById<TextView>(R.id.tv_metric_avg)
        val minView     = card.findViewById<TextView>(R.id.tv_metric_min)
        val maxView     = card.findViewById<TextView>(R.id.tv_metric_max)
        val extraView   = card.findViewById<TextView>(R.id.tv_metric_extra)
        val coreView    = card.findViewById<LinearLayout>(R.id.ll_core_container)
        val graphView   = card.findViewById<MetricGraphView>(R.id.metric_graph)

        titleView.text = getString(titleRes)

        if (summary == null || graphData.isEmpty()) {
            statusView.visibility = View.VISIBLE
            statusView.text = na()
            statsView.visibility = View.GONE
            extraView.visibility = View.GONE
            coreView.visibility = View.GONE
            graphView.visibility = View.GONE
        } else {
            statusView.visibility = View.GONE
            statsView.visibility = View.VISIBLE
            latestView.text = formatter(summary.last)
            avgView.text = formatter(summary.avg)
            minView.text = formatter(summary.min)
            maxView.text = formatter(summary.max)

            if (extra.isNullOrBlank()) {
                extraView.visibility = View.GONE
            } else {
                extraView.visibility = View.VISIBLE
                extraView.text = extra
            }

            if (perCore.isNullOrEmpty()) {
                coreView.visibility = View.GONE
            } else {
                coreView.visibility = View.VISIBLE
                populateCoreGrid(coreView, perCore)
            }

            graphView.visibility = View.VISIBLE
            graphView.setData(graphData, summary.avg, config)
        }

        dashboard.addView(card)
    }

    private fun populateCoreGrid(container: LinearLayout, cores: Map<Int, FpsRecordingSession.MetricSummary>) {
        container.removeAllViews()
        val entries = cores.entries.sortedBy { it.key }
        var index = 0
        while (index < entries.size) {
            val row = LinearLayout(requireContext()).apply {
                orientation = LinearLayout.HORIZONTAL
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply { bottomMargin = dp(2) }
            }

            addCoreCell(row, entries[index])
            if (index + 1 < entries.size) {
                addCoreCell(row, entries[index + 1])
            } else {
                row.addView(View(requireContext()), LinearLayout.LayoutParams(0, 1, 1f))
            }

            container.addView(row)
            index += 2
        }
    }

    private fun addCoreCell(row: LinearLayout, entry: Map.Entry<Int, FpsRecordingSession.MetricSummary>) {
        val cell = TextView(requireContext()).apply {
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            text = getString(
                R.string.metric_core_name,
                entry.key
            ) + "  " + getString(R.string.metric_core_value, entry.value.avg.roundToInt())
            setTextColor(Color.parseColor("#B0B0B0"))
            textSize = 12f
            setPadding(0, dp(2), 0, dp(2))
            maxLines = 1
        }
        row.addView(cell)
    }

    private fun formatTemp(value: Double): String =
        String.format(Locale.getDefault(), "%.1f°C", value)

    private fun formatFrameTime(value: Double): String =
        String.format(Locale.getDefault(), "%.2f ms", value)

    private fun formatPercent(value: Double): String =
        String.format(Locale.getDefault(), "%.1f%%", value)

    private fun formatMhz(value: Double): String =
        String.format(Locale.getDefault(), "%.0f MHz", value)

    private fun formatMb(value: Double): String =
        String.format(Locale.getDefault(), "%.0f MB", value)

    private fun formatWatts(value: Double): String =
        String.format(Locale.getDefault(), "%.2f W", value)

    private fun formatOneDecimal(value: Double): String =
        String.format(Locale.getDefault(), "%.1f", value)

    private fun formatTwoDecimals(value: Double): String =
        String.format(Locale.getDefault(), "%.2f", value)

    private fun na(): String = getString(R.string.summary_na)

    private fun displayResolution(session: FpsRecordingSession): String {
        if (session.screenWidth > 0 && session.screenHeight > 0) {
            return session.resolutionText
        }
        val metrics = resources.displayMetrics
        return if (metrics.widthPixels > 0 && metrics.heightPixels > 0) {
            "${metrics.widthPixels}x${metrics.heightPixels}"
        } else {
            session.resolutionText
        }
    }

    private data class SummaryItem(
        val labelRes: Int,
        val value: String,
        val unitRes: Int
    )

    private fun dp(value: Int): Int =
        (value * resources.displayMetrics.density).roundToInt()

    private fun formatDate(ts: Long): String =
        SimpleDateFormat("MMM dd, yyyy HH:mm", Locale.getDefault()).format(Date(ts))

    private fun formatDuration(ms: Long): String {
        val sec = ms / 1000
        return if (sec >= 60) "${sec / 60}m ${sec % 60}s" else "${sec}s"
    }
}
