/*
 * SPDX-FileCopyrightText: 2025 kenway214
 * SPDX-License-Identifier: Apache-2.0
 */

package com.android.gamebar

import android.app.ActivityThread
import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import java.io.File
import java.io.IOException

/**
 * Central storage resolver for GameBar recordings and screenshots.
 *
 * Preferred locations:
 * - FPS recordings: /sdcard/Documents/GameBar/FPSRecordings/{package}/
 * - Screenshots: /sdcard/Pictures/GameBar/
 *
 * Standalone installs may not be able to write to shared storage. In that case
 * the resolver falls back to app-specific external storage and finally app
 * internal storage, so recordings still work for both module and standalone use.
 */
object GameBarStorage {

    private const val TAG = "GameBarStorage"
    private const val APP_DIR = "GameBar"
    private const val FPS_RECORD_DIR = "FPSRecordings"
    private const val PROBE_PREFIX = ".gamebar_write_probe_"

    @Suppress("DEPRECATION")
    private fun appContext(): Context? {
        return try {
            ActivityThread.currentApplication()
        } catch (e: Exception) {
            Log.w(TAG, "Unable to resolve application context", e)
            null
        }
    }

    private fun resolveContext(context: Context?): Context? = context ?: appContext()

    fun publicDocumentsDir(context: Context? = null): File? {
        val ctx = resolveContext(context) ?: return null
        return File(
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS),
            APP_DIR
        )
    }

    fun fallbackDocumentsDir(context: Context? = null): File? {
        val ctx = resolveContext(context) ?: return null
        val base = ctx.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS)
            ?: File(ctx.filesDir, Environment.DIRECTORY_DOCUMENTS)
        return File(base, APP_DIR)
    }

    private fun internalDocumentsDir(context: Context): File {
        return File(File(context.filesDir, Environment.DIRECTORY_DOCUMENTS), APP_DIR)
    }

    /**
     * Simpan PNG langsung ke Pictures/GameBar memakai MediaStore.
     * Tidak memakai folder data aplikasi sebagai fallback.
     */
    fun savePngToPictures(context: Context, fileName: String, bitmap: Bitmap): Uri? {
        val resolver = context.contentResolver
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, fileName)
            put(MediaStore.Images.Media.MIME_TYPE, "image/png")
            put(
                MediaStore.Images.Media.RELATIVE_PATH,
                "${Environment.DIRECTORY_PICTURES}/$APP_DIR"
            )
            put(MediaStore.Images.Media.IS_PENDING, 1)
        }

        val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
            ?: return null

        return try {
            resolver.openOutputStream(uri)?.use { output ->
                if (!bitmap.compress(Bitmap.CompressFormat.PNG, 100, output)) {
                    throw IOException("PNG compression failed")
                }
            } ?: throw IOException("Unable to open MediaStore output")

            values.clear()
            values.put(MediaStore.Images.Media.IS_PENDING, 0)
            resolver.update(uri, values, null, null)
            uri
        } catch (e: Exception) {
            Log.e(TAG, "Failed to save PNG to Pictures/GameBar", e)
            try { resolver.delete(uri, null, null) } catch (_: Exception) {}
            null
        }
    }

    fun createFpsRecordCsvFile(
        context: Context? = null,
        packageName: String,
        fileName: String
    ): File {
        val ctx = resolveContext(context)
        val targets = mutableListOf<File>()
        if (ctx != null) {
            publicDocumentsDir(ctx)?.let {
                targets.add(File(File(it, FPS_RECORD_DIR), "$packageName/$fileName"))
            }
            fallbackDocumentsDir(ctx)?.let {
                targets.add(File(File(it, FPS_RECORD_DIR), "$packageName/$fileName"))
            }
            targets.add(
                File(File(internalDocumentsDir(ctx), FPS_RECORD_DIR), "$packageName/$fileName")
            )
        } else {
            targets.add(File(Environment.getExternalStorageDirectory(), fileName))
        }
        return chooseWritableTarget(targets)
    }

    fun findFpsRecordCsvFile(
        context: Context? = null,
        packageName: String,
        fileName: String
    ): File? {
        val ctx = resolveContext(context) ?: return null
        return fpsRecordPackageDirs(ctx, packageName)
            .map { File(it, fileName) }
            .firstOrNull { it.isFile }
    }

    private fun fpsRecordPackageDirs(context: Context, packageName: String): List<File> {
        val dirs = mutableListOf<File>()
        publicDocumentsDir(context)?.let { dirs.add(File(File(it, FPS_RECORD_DIR), packageName)) }
        fallbackDocumentsDir(context)?.let { dirs.add(File(File(it, FPS_RECORD_DIR), packageName)) }
        dirs.add(File(File(internalDocumentsDir(context), FPS_RECORD_DIR), packageName))
        return dirs
    }

    private fun chooseWritableTarget(targets: List<File>): File {
        for (target in targets) {
            val parent = target.parentFile ?: continue
            if (ensureWritableDir(parent)) {
                return target
            }
        }

        // Last resort: still return a path so callers can report a useful error.
        val fallback = targets.last()
        fallback.parentFile?.mkdirs()
        Log.w(TAG, "No writable GameBar storage target; using ${fallback.absolutePath}")
        return fallback
    }

    private fun ensureWritableDir(dir: File): Boolean {
        return try {
            if (!dir.exists() && !dir.mkdirs()) return false
            val probe = File(dir, "$PROBE_PREFIX${System.nanoTime()}")
            if (!probe.createNewFile()) return false
            probe.delete()
            true
        } catch (e: Exception) {
            Log.d(TAG, "Directory not writable: ${dir.absolutePath}", e)
            false
        }
    }
}
