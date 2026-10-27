package com.cc.floconlite

import android.content.Context
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.io.File
import java.util.UUID

/**
 * Flocon `CrashReporter` plugin.
 *
 * Crashes are persisted to a local file (the app may die right after a crash,
 * before the socket message can be sent) and flushed to the desktop on the
 * next successful connection.
 */
object FloconCrashReporter {

    private const val FILE_NAME = "flocon_lite_crashes.json"
    private val gson = Gson()
    private val listType = object : TypeToken<MutableList<Map<String, Any>>>() {}.type

    /**
     * Installs an uncaught exception handler that records crashes for later
     * reporting, then delegates to the previously installed handler (if any)
     * so existing crash reporting / crash screens keep working.
     */
    @JvmStatic
    fun install(context: Context) {
        val previousHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            try {
                recordCrash(context, throwable)
            } catch (e: Exception) {
                // never let crash recording itself crash the crash handler
            }
            previousHandler?.uncaughtException(thread, throwable)
        }
    }

    private fun recordCrash(context: Context, throwable: Throwable) {
        val report = mapOf(
            "crashId" to UUID.randomUUID().toString(),
            "timestamp" to System.currentTimeMillis(),
            "exceptionType" to (throwable::class.java.simpleName ?: "Throwable"),
            "exceptionMessage" to (throwable.message ?: "No message"),
            "stackTrace" to throwable.stackTraceToString()
        )

        val file = crashFile(context)
        val existing = readCrashes(file)
        existing.add(report)
        file.writeText(gson.toJson(existing))
    }

    /**
     * Sends all pending crashes to the desktop then clears local storage.
     * Call this once the websocket connection is established.
     */
    internal fun flushPendingCrashes(context: Context) {
        val file = crashFile(context)
        if (!file.exists()) return

        val crashes = readCrashes(file)
        if (crashes.isEmpty()) {
            file.delete()
            return
        }

        FloconLite.send(
            plugin = "CrashReporter",
            method = "reportCrash",
            bodyJson = gson.toJson(crashes)
        )
        file.delete()
    }

    private fun readCrashes(file: File): MutableList<Map<String, Any>> {
        if (!file.exists()) return mutableListOf()
        return try {
            gson.fromJson(file.readText(), listType) ?: mutableListOf()
        } catch (e: Exception) {
            mutableListOf()
        }
    }

    private fun crashFile(context: Context): File = File(context.filesDir, FILE_NAME)
}
