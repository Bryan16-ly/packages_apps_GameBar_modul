/*
 * SPDX-FileCopyrightText: 2025 kenway214
 * SPDX-License-Identifier: Apache-2.0
 */

package com.android.gamebar

import android.app.ActivityManager
import android.content.Context
import android.content.SharedPreferences
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.Log
import android.view.WindowManager
import android.window.TaskFpsCallback
import androidx.preference.PreferenceManager
import java.io.BufferedReader
import java.io.FileReader
import java.io.IOException
import java.lang.reflect.InvocationTargetException
import java.util.concurrent.Executor

class GameBarFpsMeter private constructor(context: Context) {

    companion object {
        private const val TAG = "GameBarFpsMeter"
        private const val STALENESS_THRESHOLD_MS = 2000L
        private const val TASK_CHECK_INTERVAL_MS = 1000L

        @Volatile
        private var instance: GameBarFpsMeter? = null

        @JvmStatic
        fun getInstance(context: Context): GameBarFpsMeter =
            instance ?: synchronized(this) {
                instance ?: GameBarFpsMeter(context.applicationContext).also { instance = it }
            }
    }

    private val context: Context = context.applicationContext
    private val windowManager: WindowManager =
        context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private val prefs: SharedPreferences =
        PreferenceManager.getDefaultSharedPreferences(context)

    @Volatile
    private var currentFps = 0f
    private var taskFpsCallback: TaskFpsCallback? = null
    private var callbackRegistered = false
    private var currentTaskId = -1
    private var isRunning = false

    @Volatile
    private var lastFpsUpdateTime = System.currentTimeMillis()

    private val handler = Handler(Looper.getMainLooper())

    private val reinitRunnable = Runnable {
        if (isRunning) {
            registerFpsCallbackForFocusedTask()
        }
    }

    private val taskCheckRunnable = object : Runnable {
        override fun run() {
            if (!isRunning || Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return

            try {
                val newTaskId = getFocusedTaskId()
                if (newTaskId > 0 && newTaskId != currentTaskId) {
                    reinitCallback()
                } else if (callbackRegistered) {
                    val now = System.currentTimeMillis()
                    if (now - lastFpsUpdateTime > STALENESS_THRESHOLD_MS) {
                        reinitCallback()
                    }
                } else if (newTaskId > 0) {
                    registerFpsCallbackForFocusedTask()
                }
            } catch (t: Throwable) {
                Log.w(TAG, "Error checking focused task: ${t.message}")
            }

            if (isRunning) {
                handler.postDelayed(this, TASK_CHECK_INTERVAL_MS)
            }
        }
    }

    init {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            taskFpsCallback = object : TaskFpsCallback() {
                override fun onFpsReported(fps: Float) {
                    if (fps > 0) {
                        currentFps = fps
                        lastFpsUpdateTime = System.currentTimeMillis()
                        Log.d(TAG, "onFpsReported: $fps FPS")
                    }
                }
            }
        }
    }

    fun start() {
        val method = prefs.getString("game_bar_fps_method", "new")
        if (method != "new") return

        stop()
        isRunning = true
        Log.d(TAG, "FPS meter started")

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerFpsCallbackForFocusedTask()
            handler.removeCallbacks(taskCheckRunnable)
            handler.postDelayed(taskCheckRunnable, TASK_CHECK_INTERVAL_MS)
        }
    }

    fun stop() {
        isRunning = false
        handler.removeCallbacks(taskCheckRunnable)
        handler.removeCallbacks(reinitRunnable)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            unregisterFpsCallback()
        }

        currentTaskId = -1
        currentFps = 0f
        Log.d(TAG, "FPS meter stopped")
    }

    private fun registerFpsCallbackForFocusedTask() {
        if (!isRunning || Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return

        val taskId = getFocusedTaskId()
        if (taskId <= 0) {
            Log.d(TAG, "No focused task ID available ($taskId)")
            return
        }

        if (callbackRegistered && taskId == currentTaskId) {
            return
        }

        unregisterFpsCallback()
        currentTaskId = taskId

        try {
            taskFpsCallback?.let { callback ->
                val registerMethod = windowManager.javaClass.getMethod(
                    "registerTaskFpsCallback",
                    Int::class.javaPrimitiveType,
                    Executor::class.java,
                    TaskFpsCallback::class.java
                )
                registerMethod.invoke(windowManager, currentTaskId, Executor { it.run() }, callback)
                callbackRegistered = true
                lastFpsUpdateTime = System.currentTimeMillis()
                Log.i(TAG, "Registered TaskFpsCallback for taskId: $currentTaskId")
            }
        } catch (e: Exception) {
            val cause = (e as? InvocationTargetException)?.targetException ?: e
            Log.w(TAG, "Failed to register TaskFpsCallback for taskId $currentTaskId: ${cause.message}")
            callbackRegistered = false
            currentTaskId = -1
        }
    }

    private fun unregisterFpsCallback() {
        if (callbackRegistered && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            try {
                taskFpsCallback?.let { callback ->
                    val unregisterMethod = windowManager.javaClass.getMethod(
                        "unregisterTaskFpsCallback",
                        TaskFpsCallback::class.java
                    )
                    unregisterMethod.invoke(windowManager, callback)
                    Log.d(TAG, "Unregistered TaskFpsCallback for taskId: $currentTaskId")
                }
            } catch (e: Exception) {
                val cause = (e as? InvocationTargetException)?.targetException ?: e
                Log.w(TAG, "Failed to unregister TaskFpsCallback: ${cause.message}")
            } finally {
                callbackRegistered = false
            }
        }
    }

    fun getFps(): Float {
        val method = prefs.getString("game_bar_fps_method", "new")
        val fps = if (method == "legacy") {
            readLegacyFps()
        } else {
            if (currentFps > 0f) currentFps else readLegacyFps().coerceAtLeast(0f)
        }
        Log.d(TAG, "getFps (method=$method): $fps (currentFps=$currentFps)")
        return fps
    }

    private fun readLegacyFps(): Float {
        val path = GameBarConfig.fpsSysfsPath ?: return -1f
        try {
            BufferedReader(FileReader(path)).use { br ->
                val line = br.readLine()
                if (line != null && line.startsWith("fps:")) {
                    val parts = line.split("\\s+".toRegex())
                    if (parts.size >= 2) {
                        return parts[1].trim().toFloat()
                    }
                }
            }
        } catch (e: IOException) {
            Log.d(TAG, "readLegacyFps IOException ($path): ${e.message}")
        } catch (e: NumberFormatException) {
            Log.d(TAG, "readLegacyFps NumberFormatException: ${e.message}")
        }
        return -1f
    }

    private fun getFocusedTaskId(): Int {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return -1

        // Strategy 1: IActivityTaskManager.getFocusedRootTaskInfo() via Binder service
        getIActivityTaskManager()?.let { iAtm ->
            runCatching {
                val getFocusedMethod = iAtm.javaClass.getMethod("getFocusedRootTaskInfo")
                val rootTask = getFocusedMethod.invoke(iAtm)
                if (rootTask != null) {
                    val taskId = getTaskIdFromObject(rootTask)
                    if (taskId > 0) return taskId
                }
            }

            // Strategy 2: IActivityTaskManager.getTasks(...)
            runCatching {
                for (method in iAtm.javaClass.methods) {
                    if (method.name == "getTasks") {
                        val tasks = when (method.parameterTypes.size) {
                            4 -> method.invoke(iAtm, 5, false, false, 0)
                            1 -> method.invoke(iAtm, 5)
                            else -> null
                        }
                        if (tasks is List<*>) {
                            val focused = tasks.firstOrNull { it != null && isTaskFocused(it) } ?: tasks.firstOrNull()
                            if (focused != null) {
                                val taskId = getTaskIdFromObject(focused)
                                if (taskId > 0) return taskId
                            }
                        }
                    }
                }
            }
        }

        // Strategy 3: ActivityManager.getRunningTasks(5) via SDK API
        runCatching {
            val am = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
            val runningTasks = am?.getRunningTasks(5)
            if (!runningTasks.isNullOrEmpty()) {
                val focused = runningTasks.firstOrNull { isTaskFocused(it) } ?: runningTasks.firstOrNull()
                if (focused != null && focused.taskId > 0) {
                    return focused.taskId
                }
            }
        }

        return -1
    }

    private fun getIActivityTaskManager(): Any? {
        runCatching {
            val atmClass = Class.forName("android.app.ActivityTaskManager")
            return atmClass.getMethod("getService").invoke(null)
        }

        runCatching {
            val smClass = Class.forName("android.os.ServiceManager")
            val binder = smClass.getMethod("getService", String::class.java).invoke(null, "activity_task") as? IBinder
            if (binder != null) {
                val stubClass = Class.forName("android.app.IActivityTaskManager\$Stub")
                return stubClass.getMethod("asInterface", IBinder::class.java).invoke(null, binder)
            }
        }

        return null
    }

    private fun isTaskFocused(task: Any): Boolean {
        var currentClass: Class<*>? = task.javaClass
        while (currentClass != null && currentClass != Any::class.java) {
            runCatching {
                val field = currentClass.getDeclaredField("isFocused")
                field.isAccessible = true
                return field.getBoolean(task)
            }
            currentClass = currentClass.superclass
        }

        runCatching {
            val method = task.javaClass.getMethod("isFocused")
            (method.invoke(task) as? Boolean)?.let { return it }
        }

        return false
    }

    private fun getTaskIdFromObject(task: Any): Int {
        if (task is ActivityManager.RunningTaskInfo) {
            return task.taskId
        }

        val fieldNames = arrayOf("taskId", "mTaskId")
        var currentClass: Class<*>? = task.javaClass
        while (currentClass != null && currentClass != Any::class.java) {
            for (name in fieldNames) {
                runCatching {
                    val field = currentClass.getDeclaredField(name)
                    field.isAccessible = true
                    val value = field.getInt(task)
                    if (value > 0) return value
                }
            }
            currentClass = currentClass.superclass
        }

        runCatching {
            val method = task.javaClass.getMethod("getTaskId")
            (method.invoke(task) as? Int)?.let { if (it > 0) return it }
        }

        return -1
    }

    private fun reinitCallback() {
        if (!isRunning) return
        unregisterFpsCallback()

        handler.removeCallbacks(reinitRunnable)
        handler.postDelayed(reinitRunnable, 500)
    }
}
