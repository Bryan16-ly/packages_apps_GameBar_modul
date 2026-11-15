/*
 * SPDX-FileCopyrightText: 2025 kenway214
 * SPDX-License-Identifier: Apache-2.0
 */

package com.android.gamebar

import android.content.Context
import android.util.Log
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedWriter
import java.io.File
import java.io.FileWriter
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

/**
 * Singleton yang mengelola sesi rekaman FPS.
 *
 * - Menyimpan sesi aktif di memori.
 * - Mempersist setiap sesi ke file JSON di app-private internal storage
 *   (<files>/fps_recordings/<id>.json) agar tersedia setelah restart.
 * - Terpisah dari overlay GameBar dan penyimpanan sesi FPS.
 */
class FpsRecordingManager private constructor(context: Context) {

    companion object {
        private const val TAG = "FpsRecordingManager"
        private const val DIR_NAME = "fps_recordings"

        private const val CSV_HEADER =
            "DateTime,PackageName,FPS,Frame_Time,Battery_Temp,CPU_Usage,CPU_Clock," +
                "CPU_Temp,RAM_Usage_MB,RAM_Usage_Pct,RAM_Speed_MHz,RAM_Temp," +
                "GPU_Usage,GPU_Clock_MHz,GPU_Temp,Power_W"

        @Volatile
        private var instance: FpsRecordingManager? = null

        fun getInstance(context: Context): FpsRecordingManager =
            instance ?: synchronized(this) {
                instance ?: FpsRecordingManager(context.applicationContext).also { instance = it }
            }
    }

    private val ctx = context.applicationContext
    private val recordingDir: File = File(ctx.filesDir, DIR_NAME).also { it.mkdirs() }

    /** Sesi yang sedang direkam (null = tidak ada rekaman aktif). */
    @Volatile
    var activeSession: FpsRecordingSession? = null
        private set

    // ─── Public API ───────────────────────────────────────────────────────────

    /** Mulai sesi baru. Jika ada sesi aktif, akan dihentikan & disimpan dulu. */
    fun startSession(
        packageName: String,
        appName: String,
        screenWidth: Int = 0,
        screenHeight: Int = 0
    ): FpsRecordingSession {
        stopSession()   // pastikan tidak ada sesi menggantung

        val metrics = ctx.resources.displayMetrics
        val finalWidth = screenWidth.takeIf { it > 0 } ?: metrics.widthPixels
        val finalHeight = screenHeight.takeIf { it > 0 } ?: metrics.heightPixels

        val session = FpsRecordingSession(
            id           = UUID.randomUUID().toString(),
            appPackage   = packageName,
            appName      = appName,
            startTime    = System.currentTimeMillis(),
            screenWidth  = finalWidth,
            screenHeight = finalHeight
        )
        activeSession = session
        Log.d(TAG, "Recording started: ${session.id} ($appName)")
        return session
    }

    /** Tambahkan satu sampel performa ke sesi aktif. */
    fun addSample(sample: FpsRecordingSession.FpsSample) {
        activeSession?.samples?.add(sample)
    }

    /**
     * Hentikan sesi aktif, simpan ke disk, kembalikan sesi yang baru disimpan.
     * Return null jika tidak ada sesi aktif.
     */
    fun stopSession(): FpsRecordingSession? {
        val session = activeSession ?: return null
        session.endTime = System.currentTimeMillis()
        activeSession = null

        if (session.samples.isNotEmpty()) {
            session.csvPath = csvFileFor(session).absolutePath
            saveSession(session)
            saveSessionCsv(session, File(session.csvPath!!))
            Log.d(TAG, "Recording stopped & saved: ${session.id} (${session.samples.size} samples)")
        } else {
            Log.d(TAG, "Recording stopped with no samples, discarded: ${session.id}")
        }
        return session
    }

    /** True jika sedang merekam. */
    fun isRecording(): Boolean = activeSession != null

    /** Load semua sesi dari disk, diurutkan terbaru dulu. */
    fun loadAllSessions(): List<FpsRecordingSession> {
        val files = recordingDir.listFiles { f -> f.extension == "json" } ?: return emptyList()
        return files
            .mapNotNull { readSession(it) }
            .sortedByDescending { it.startTime }
    }

    /** Hapus satu sesi dari disk beserta CSV pasangannya. */
    fun deleteSession(sessionId: String) {
        val jsonFile = File(recordingDir, "$sessionId.json")
        val session = readSession(jsonFile)
        jsonFile.delete()
        session?.let { deleteCsvFile(it) }
        Log.d(TAG, "Deleted session: $sessionId")
    }

    /** Hapus semua sesi. */
    fun deleteAllSessions() {
        val sessions = loadAllSessions()
        sessions.forEach { deleteSession(it.id) }
        recordingDir.listFiles()?.forEach { it.delete() }
        Log.d(TAG, "All sessions deleted")
    }

    /**
     * Pastikan CSV untuk sesi tersedia. Sesi lama yang belum punya CSV akan
     * dibuatkan saat pertama kali diekspor.
     */
    fun ensureCsv(session: FpsRecordingSession): File? {
        val existing = session.csvPath?.let { File(it) }?.takeIf { it.isFile }
        if (existing != null) return existing

        val file = csvFileFor(session)
        if (!writeSessionCsv(session, file)) return null
        session.csvPath = file.absolutePath
        saveSession(session)
        return file
    }

    // ─── Persistence ─────────────────────────────────────────────────────────

    private fun JSONObject.optDoubleOrNull(key: String): Double? {
        if (!has(key)) return null
        val value = optDouble(key, Double.NaN)
        return if (value.isNaN()) null else value
    }

    private fun saveSession(session: FpsRecordingSession) {
        try {
            val samplesArray = JSONArray().apply {
                session.samples.forEach { s ->
                    put(JSONObject().apply {
                        put("t", s.timestamp)
                        put("f", s.fps.toDouble())
                        s.frameTimeMs?.let { put("ft", it) }
                        s.cpuUsagePct?.let { put("cu", it) }
                        s.cpuFreqMhz?.let { put("cf", it) }
                        s.cpuFreqCoresMhz?.takeIf { it.isNotEmpty() }?.let { cores ->
                            put("cc", JSONObject().apply {
                                cores.forEach { (core, mhz) -> put(core.toString(), mhz) }
                            })
                        }
                        s.cpuTempC?.let { put("ct", it) }
                        s.gpuUsagePct?.let { put("gu", it) }
                        s.gpuClockMhz?.let { put("gc", it) }
                        s.gpuTempC?.let { put("gt", it) }
                        s.ramUsageMb?.let { put("ru", it) }
                        s.ramUsagePct?.let { put("rp", it) }
                        s.ramFreqMhz?.let { put("rf", it) }
                        s.ramTempC?.let { put("rt", it) }
                        s.batteryTempC?.let { put("bt", it) }
                        s.powerW?.let { put("pw", it) }
                    })
                }
            }
            val json = JSONObject().apply {
                put("id",         session.id)
                put("pkg",        session.appPackage)
                put("app",        session.appName)
                put("start",      session.startTime)
                put("sw",         session.screenWidth)
                put("sh",         session.screenHeight)
                put("end",        session.endTime)
                session.csvPath?.let { put("csv", it) }
                put("samples",    samplesArray)
            }
            File(recordingDir, "${session.id}.json").writeText(json.toString())
        } catch (e: Exception) {
            Log.e(TAG, "Failed to save session ${session.id}: ${e.message}")
        }
    }

    private fun readSession(file: File): FpsRecordingSession? {
        return try {
            val json    = JSONObject(file.readText())
            val samples = json.getJSONArray("samples")
            val list    = mutableListOf<FpsRecordingSession.FpsSample>()
            for (i in 0 until samples.length()) {
                val s = samples.getJSONObject(i)
                val cores = s.optJSONObject("cc")?.let { obj ->
                    val map = mutableMapOf<Int, Int>()
                    val keys = obj.keys()
                    while (keys.hasNext()) {
                        val key = keys.next()
                        val core = key.toIntOrNull() ?: continue
                        val mhz = obj.optInt(key, 0)
                        if (mhz > 0) map[core] = mhz
                    }
                    map.takeIf { it.isNotEmpty() }
                }
                list.add(
                    FpsRecordingSession.FpsSample(
                        timestamp        = s.getLong("t"),
                        fps              = s.optDouble("f", 0.0).toFloat(),
                        frameTimeMs      = s.optDoubleOrNull("ft"),
                        cpuUsagePct      = s.optDoubleOrNull("cu"),
                        cpuFreqMhz       = s.optDoubleOrNull("cf"),
                        cpuFreqCoresMhz  = cores,
                        cpuTempC         = s.optDoubleOrNull("ct"),
                        gpuUsagePct      = s.optDoubleOrNull("gu"),
                        gpuClockMhz      = s.optDoubleOrNull("gc"),
                        gpuTempC         = s.optDoubleOrNull("gt"),
                        ramUsageMb       = if (s.has("ru")) s.optLong("ru") else null,
                        ramUsagePct      = s.optDoubleOrNull("rp"),
                        ramFreqMhz       = s.optDoubleOrNull("rf"),
                        ramTempC         = s.optDoubleOrNull("rt"),
                        batteryTempC     = s.optDoubleOrNull("bt"),
                        powerW           = s.optDoubleOrNull("pw")
                    )
                )
            }
            FpsRecordingSession(
                id         = json.getString("id"),
                appPackage   = json.getString("pkg"),
                appName      = json.getString("app"),
                startTime    = json.getLong("start"),
                screenWidth  = json.optInt("sw", 0),
                screenHeight = json.optInt("sh", 0),
                endTime      = json.getLong("end"),
                samples      = list,
                csvPath      = if (json.isNull("csv")) {
                    null
                } else {
                    json.optString("csv").takeIf { it.isNotBlank() && it != "null" }
                }
            )
        } catch (e: Exception) {
            Log.e(TAG, "Failed to read session ${file.name}: ${e.message}")
            null
        }
    }

    // ─── CSV export ──────────────────────────────────────────────────────────

    private fun csvFileFor(session: FpsRecordingSession): File {
        return GameBarStorage.createFpsRecordCsvFile(
            context = ctx,
            packageName = session.appPackage.ifBlank { "unknown" },
            fileName = csvFileName(session)
        )
    }

    private fun csvFileName(session: FpsRecordingSession): String {
        val stamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date(session.startTime))
        val app = session.appName
            .replace(Regex("[^A-Za-z0-9._-]"), "_")
            .trim('_')
            .ifBlank { session.appPackage.ifBlank { "unknown" } }
        return "${app}_${stamp}_${session.id.take(8)}.csv"
    }

    private fun saveSessionCsv(session: FpsRecordingSession, file: File) {
        if (!writeSessionCsv(session, file)) {
            Log.w(TAG, "Failed to save CSV for session ${session.id}")
        }
    }

    private fun writeSessionCsv(session: FpsRecordingSession, file: File): Boolean {
        var writer: BufferedWriter? = null
        return try {
            file.parentFile?.mkdirs()
            writer = BufferedWriter(FileWriter(file, false))
            writer.write(CSV_HEADER)
            writer.newLine()
            session.samples.forEach { sample ->
                writer.write(csvRow(session, sample))
                writer.newLine()
            }
            writer.flush()
            true
        } catch (e: IOException) {
            Log.e(TAG, "Failed to write CSV for ${session.id}", e)
            false
        } finally {
            writer?.let {
                try { it.close() } catch (_: IOException) {}
            }
        }
    }

    private fun csvRow(session: FpsRecordingSession, sample: FpsRecordingSession.FpsSample): String {
        val dateTime = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US)
            .format(Date(sample.timestamp))
        val cpuClock = sample.cpuFreqCoresMhz
            ?.entries
            ?.joinToString("; ") { "cpu${it.key}:${it.value}" }
            ?: number(sample.cpuFreqMhz)

        return listOf(
            csv(dateTime),
            csv(session.appPackage),
            number(sample.fps.toDouble()),
            number(sample.frameTimeMs),
            number(sample.batteryTempC),
            number(sample.cpuUsagePct),
            csv(cpuClock),
            number(sample.cpuTempC),
            sample.ramUsageMb?.toString() ?: "N/A",
            number(sample.ramUsagePct),
            number(sample.ramFreqMhz),
            number(sample.ramTempC),
            number(sample.gpuUsagePct),
            number(sample.gpuClockMhz),
            number(sample.gpuTempC),
            number(sample.powerW)
        ).joinToString(",")
    }

    private fun number(value: Double?): String {
        if (value == null || value.isNaN() || value.isInfinite()) return "N/A"
        return String.format(Locale.US, "%.3f", value)
    }

    private fun csv(value: String): String {
        return if (value.contains(',') || value.contains('"') || value.contains('\n')) {
            "\"${value.replace("\"", "\"\"")}\""
        } else {
            value
        }
    }

    private fun deleteCsvFile(session: FpsRecordingSession) {
        val known = session.csvPath?.let { File(it) }
        if (known != null && known.isFile) {
            known.delete()
            return
        }
        GameBarStorage.findFpsRecordCsvFile(
            context = ctx,
            packageName = session.appPackage.ifBlank { "unknown" },
            fileName = csvFileName(session)
        )?.delete()
    }
}
