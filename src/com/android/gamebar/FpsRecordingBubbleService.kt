/*
 * SPDX-FileCopyrightText: 2025 kenway214
 * SPDX-License-Identifier: Apache-2.0
 */

package com.android.gamebar

import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.view.Gravity
import android.view.MotionEvent
import android.view.WindowManager
import android.widget.TextView
import android.widget.Toast
import androidx.preference.PreferenceManager

/**
 * FpsRecordingBubbleService
 *
 * Service yang menampilkan floating bubble di atas semua aplikasi.
 * - Tap bubble → mulai/stop rekaman FPS.
 * - Bubble bisa digeser.
 * - Warna bubble: hijau (idle) / merah (recording).
 * - Sampling mengikuti interval update overlay (game_bar_update_interval).
 * - Bisa merekam otomatis untuk app yang dipilih di FPS Recording Apps.
 *
 * Start/stop service dikendalikan dari GameBarFragment melalui SwitchPreference.
 */
class FpsRecordingBubbleService : Service() {

    companion object {
        private const val TAG               = "FpsBubbleService"
        private const val PREF_KEY_X        = "fps_bubble_x"
        private const val PREF_KEY_Y        = "fps_bubble_y"
        private const val TOUCH_SLOP        = 20f

        const val ACTION_START = "com.android.gamebar.FPS_BUBBLE_START"
        const val ACTION_STOP  = "com.android.gamebar.FPS_BUBBLE_STOP"
    }

    private lateinit var windowManager: WindowManager
    private lateinit var bubbleView: TextView
    private lateinit var layoutParams: WindowManager.LayoutParams
    private val handler = Handler(Looper.getMainLooper())
    private var isViewAdded = false
    private var startedFpsMeter = false
    private var autoMonitorStarted = false
    private var autoRecordingPackage: String? = null
    private var autoSuppressedPackage: String? = null

    // Drag state
    private var initialX = 0;    private var initialY = 0
    private var initialTouchX = 0f; private var initialTouchY = 0f
    private var isDragging = false

    // ─── Lifecycle ────────────────────────────────────────────────────────────

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        createBubbleView()
        syncBubbleVisibility()
        startAutoMonitor()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        syncBubbleVisibility()
        startAutoMonitor()

        if (!isBubbleEnabled() && !isAutoRecordingEnabled()) {
            stopSelf()
            return START_NOT_STICKY
        }

        // Service stays running until explicitly stopped
        return START_STICKY
    }

    override fun onDestroy() {
        stopRecordingIfActive()
        autoMonitorStarted = false
        handler.removeCallbacksAndMessages(null)
        removeBubbleFromWindow()
        super.onDestroy()
    }

    // ─── Bubble View ─────────────────────────────────────────────────────────

    private fun createBubbleView() {
        bubbleView = TextView(this).apply {
            text = "▶"
            textSize = 18f
            setTextColor(Color.WHITE)
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            setPadding(20, 12, 20, 12)
            background = buildBubbleBackground(idle = true)
            elevation = 8f
        }

        layoutParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            val prefs = PreferenceManager.getDefaultSharedPreferences(this@FpsRecordingBubbleService)
            x = prefs.getInt(PREF_KEY_X, 20)
            y = prefs.getInt(PREF_KEY_Y, 300)
        }

        bubbleView.setOnTouchListener { _, event ->
            handleTouch(event)
        }
    }

    private fun isBubbleEnabled(): Boolean =
        PreferenceManager.getDefaultSharedPreferences(this)
            .getBoolean("game_bar_fps_bubble_enable", false)

    private fun isAutoRecordingEnabled(): Boolean =
        PreferenceManager.getDefaultSharedPreferences(this)
            .getBoolean(FpsRecordingPrefs.PREF_PER_APP_ENABLED, false)

    private fun syncBubbleVisibility() {
        if (isBubbleEnabled()) {
            addBubbleToWindow()
        } else {
            removeBubbleFromWindow()
        }
    }

    private fun addBubbleToWindow() {
        if (!isViewAdded) {
            try {
                windowManager.addView(bubbleView, layoutParams)
                isViewAdded = true
            } catch (e: Exception) {
                android.util.Log.e(TAG, "Failed to add bubble: ${e.message}")
                if (!isAutoRecordingEnabled()) {
                    stopSelf()
                }
            }
        }
    }

    private fun removeBubbleFromWindow() {
        if (isViewAdded) {
            try {
                windowManager.removeView(bubbleView)
            } catch (_: Exception) {}
            isViewAdded = false
        }
    }

    // ─── Touch handling (drag + tap) ─────────────────────────────────────────

    private fun handleTouch(event: MotionEvent): Boolean {
        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                initialX      = layoutParams.x
                initialY      = layoutParams.y
                initialTouchX = event.rawX
                initialTouchY = event.rawY
                isDragging    = false
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                val dx = event.rawX - initialTouchX
                val dy = event.rawY - initialTouchY
                if (!isDragging && (Math.abs(dx) > TOUCH_SLOP || Math.abs(dy) > TOUCH_SLOP)) {
                    isDragging = true
                }
                if (isDragging) {
                    layoutParams.x = (initialX + dx).toInt()
                    layoutParams.y = (initialY + dy).toInt()
                    if (isViewAdded) windowManager.updateViewLayout(bubbleView, layoutParams)
                }
                return true
            }
            MotionEvent.ACTION_UP -> {
                if (isDragging) {
                    // Simpan posisi
                    PreferenceManager.getDefaultSharedPreferences(this)
                        .edit()
                        .putInt(PREF_KEY_X, layoutParams.x)
                        .putInt(PREF_KEY_Y, layoutParams.y)
                        .apply()
                } else {
                    // Tap → toggle recording
                    toggleRecording()
                }
                return true
            }
        }
        return false
    }

    // ─── Recording control ────────────────────────────────────────────────────

    private fun toggleRecording() {
        val manager = FpsRecordingManager.getInstance(this)
        if (manager.isRecording()) {
            if (autoRecordingPackage != null) {
                autoSuppressedPackage = autoRecordingPackage
            }
            stopRecordingSession()
        } else {
            autoSuppressedPackage = null
            startRecordingSession()
        }
    }

    private fun startRecordingSession(packageName: String? = null, auto: Boolean = false) {
        val targetPackage = packageName
            ?: ForegroundAppDetector.getForegroundPackageName(this) ?: "unknown"
        val appName = try {
            packageManager.getApplicationLabel(
                packageManager.getApplicationInfo(targetPackage, 0)
            ).toString()
        } catch (_: Exception) { targetPackage }

        // Pastikan FPS meter aktif juga saat overlay GameBar tidak ditampilkan.
        if (!GameBar.isShowing()) {
            GameBarFpsMeter.getInstance(this).start()
            startedFpsMeter = true
        }

        val (screenWidth, screenHeight) = currentScreenResolution()
        FpsRecordingManager.getInstance(this)
            .startSession(targetPackage, appName, screenWidth, screenHeight)
        autoRecordingPackage = if (auto) targetPackage else null
        updateBubbleState(recording = true)
        handler.post(sampleRunnable)
        Toast.makeText(
            this,
            if (auto) "Auto FPS recording started: $appName" else "FPS Recording started: $appName",
            Toast.LENGTH_SHORT
        ).show()
        android.util.Log.d(TAG, "Recording started for $appName (auto=$auto)")
    }

    private fun stopRecordingSession() {
        handler.removeCallbacks(sampleRunnable)
        stopOwnedFpsMeter()
        val session = FpsRecordingManager.getInstance(this).stopSession()
        autoRecordingPackage = null
        updateBubbleState(recording = false)
        if (session != null && session.samples.isNotEmpty()) {
            Toast.makeText(
                this,
                "Recording saved: ${session.samples.size} samples (${formatDuration(session.durationMs)})",
                Toast.LENGTH_SHORT
            ).show()
        } else {
            Toast.makeText(this, "Recording stopped (no data)", Toast.LENGTH_SHORT).show()
        }
    }

    private fun stopRecordingIfActive() {
        if (FpsRecordingManager.getInstance(this).isRecording()) {
            handler.removeCallbacks(sampleRunnable)
            FpsRecordingManager.getInstance(this).stopSession()
        }
        autoRecordingPackage = null
        stopOwnedFpsMeter()
    }

    private fun stopOwnedFpsMeter() {
        if (startedFpsMeter) {
            // Jangan hentikan meter jika overlay GameBar masih membutuhkannya.
            if (!GameBar.isShowing()) {
                GameBarFpsMeter.getInstance(this).stop()
            }
            startedFpsMeter = false
        }
    }

    // ─── FPS Sampling ─────────────────────────────────────────────────────────

    private val sampleRunnable = object : Runnable {
        override fun run() {
            val manager = FpsRecordingManager.getInstance(this@FpsRecordingBubbleService)
            if (!manager.isRecording()) return

            // Overlay bisa ditutup saat rekaman berjalan; pastikan meter tetap aktif.
            if (!startedFpsMeter && !GameBar.isShowing()) {
                GameBarFpsMeter.getInstance(this@FpsRecordingBubbleService).start()
                startedFpsMeter = true
            }

            val sample = capturePerformanceSample()
            manager.addSample(sample)

            handler.postDelayed(this, sampleIntervalMs())
        }
    }

    private fun sampleIntervalMs(): Long {
        val raw = PreferenceManager.getDefaultSharedPreferences(this)
            .getString("game_bar_update_interval", "1000")
        return raw?.toLongOrNull()?.coerceIn(250L, 10_000L) ?: 1000L
    }

    // ─── Per-App Auto Recording ──────────────────────────────────────────────

    private fun startAutoMonitor() {
        if (autoMonitorStarted) return
        autoMonitorStarted = true
        handler.post(autoMonitorRunnable)
    }

    private val autoMonitorRunnable = object : Runnable {
        override fun run() {
            if (!autoMonitorStarted) return
            monitorForegroundApp()
            handler.postDelayed(this, 1000L)
        }
    }

    private fun monitorForegroundApp() {
        if (!isAutoRecordingEnabled()) {
            autoSuppressedPackage = null
            if (autoRecordingPackage != null) {
                stopRecordingSession()
            }
            return
        }

        val packageName = ForegroundAppDetector.getForegroundPackageName(this)
        if (packageName.isBlank() || packageName == "Unknown" || packageName == this.packageName) {
            return
        }

        if (autoSuppressedPackage != null && autoSuppressedPackage != packageName) {
            autoSuppressedPackage = null
        }

        val autoPackage = autoRecordingPackage
        val manager = FpsRecordingManager.getInstance(this)
        if (autoPackage != null) {
            if (packageName != autoPackage) {
                stopRecordingSession()
            }
            return
        }

        if (manager.isRecording()) return

        val enabledApps = FpsRecordingPrefs.getAutoApps(this)
        if (packageName in enabledApps && packageName != autoSuppressedPackage) {
            startRecordingSession(packageName, auto = true)
        }
    }

    private fun capturePerformanceSample(): FpsRecordingSession.FpsSample {
        val now = System.currentTimeMillis()
        val fps = GameBarFpsMeter.getInstance(this).getFps().takeIf { it > 0f } ?: 0f

        val cpuUsage = GameBarCpuInfo.getCpuUsagePercent()
        val cpuFreqs = GameBarCpuInfo.getCpuFrequenciesMhz()
        val cpuFreqAvg = if (cpuFreqs.isNotEmpty()) cpuFreqs.values.average() else null
        val cpuTemp = GameBarCpuInfo.getCpuTemperatureC()
        val gpuUsage = GameBarGpuInfo.getGpuUsage().toDoubleOrNull()
        val gpuClock = GameBarGpuInfo.getGpuClock().toDoubleOrNull()
        val gpuTemp = GameBarGpuInfo.getGpuTempC()

        val ramInfo = GameBarMemInfo.getRamUsageInfo()
        val ramFreq = GameBarMemInfo.getRamFrequencyMhz()
        val ramTemp = GameBarMemInfo.getRamTemperatureC()

        val batteryTemp = GameBarBatteryInfo.getBatteryTemperatureC()
        val power = GameBarBatteryInfo.getPowerWatts()

        return FpsRecordingSession.FpsSample(
            timestamp         = now,
            fps               = fps,
            frameTimeMs       = if (fps > 0f) 1000.0 / fps else null,
            cpuUsagePct       = cpuUsage,
            cpuFreqMhz        = cpuFreqAvg,
            cpuFreqCoresMhz   = cpuFreqs.ifEmpty { null },
            cpuTempC          = cpuTemp,
            gpuUsagePct       = gpuUsage,
            gpuClockMhz       = gpuClock,
            gpuTempC          = gpuTemp,
            ramUsageMb        = ramInfo?.usedMb,
            ramUsagePct       = ramInfo?.usedPercent,
            ramFreqMhz        = ramFreq,
            ramTempC          = ramTemp,
            batteryTempC      = batteryTemp,
            powerW            = power
        )
    }

    // ─── UI helpers ───────────────────────────────────────────────────────────

    private fun currentScreenResolution(): Pair<Int, Int> {
        val metrics = resources.displayMetrics
        if (metrics.widthPixels > 0 && metrics.heightPixels > 0) {
            return metrics.widthPixels to metrics.heightPixels
        }

        val maximumBounds = windowManager.maximumWindowMetrics.bounds
        if (maximumBounds.width() > 0 && maximumBounds.height() > 0) {
            return maximumBounds.width() to maximumBounds.height()
        }

        val currentBounds = windowManager.currentWindowMetrics.bounds
        if (currentBounds.width() > 0 && currentBounds.height() > 0) {
            return currentBounds.width() to currentBounds.height()
        }

        return 0 to 0
    }

    private fun updateBubbleState(recording: Boolean) {
        bubbleView.background = buildBubbleBackground(idle = !recording)
        bubbleView.text = if (recording) "⏹" else "▶"
    }

    private fun buildBubbleBackground(idle: Boolean): GradientDrawable {
        return GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = 40f
            setColor(if (idle) Color.parseColor("#CC1B5E20") else Color.parseColor("#CCB71C1C"))
            setStroke(2, if (idle) Color.parseColor("#4CAF50") else Color.parseColor("#F44336"))
        }
    }

    private fun formatDuration(ms: Long): String {
        val sec = ms / 1000
        return if (sec >= 60) "${sec / 60}m ${sec % 60}s" else "${sec}s"
    }
}
