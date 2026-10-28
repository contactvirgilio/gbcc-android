package com.philj56.gbcc

import android.app.ActivityManager
import android.app.ApplicationExitInfo
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Typeface
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Temporary diagnostics for "app isn't responding" (ANR) reports.
 *
 * - [AnrWatchdog] runs while a game is open, notices when the main thread stops
 *   responding, and records what the main thread was doing at that moment.
 * - [showPendingReports] shows any recorded report (and Android's own ANR trace,
 *   kept when the user taps "Close app") so it can be copied or shared.
 */
object AnrDiagnostics {
    private const val TAG = "GBCC-ANR"
    private const val REPORT_FILE = "anr_report.txt"
    private const val PREFS = "anr_diagnostics"
    private const val LAST_EXIT_SEEN = "last_exit_timestamp"

    private fun reportFile(context: Context) = File(context.filesDir, REPORT_FILE)

    class AnrWatchdog(
        private val context: Context,
        private val thresholdMs: Long = 2000,
        private val onStallDetected: () -> Unit
    ) {
        private val mainHandler = Handler(Looper.getMainLooper())
        @Volatile private var lastTick = SystemClock.uptimeMillis()
        @Volatile private var running = false
        private var thread: Thread? = null
        private val tick = Runnable { lastTick = SystemClock.uptimeMillis() }

        fun start() {
            if (running) return
            running = true
            lastTick = SystemClock.uptimeMillis()
            thread = Thread({
                var reportedThisStall = false
                while (running) {
                    mainHandler.post(tick)
                    try {
                        Thread.sleep(250)
                    } catch (_: InterruptedException) {
                        return@Thread
                    }
                    val stalledFor = SystemClock.uptimeMillis() - lastTick
                    if (stalledFor >= thresholdMs) {
                        if (!reportedThisStall) {
                            reportedThisStall = true
                            record(stalledFor)
                        }
                    } else if (reportedThisStall) {
                        reportedThisStall = false
                        mainHandler.post(onStallDetected)
                    }
                }
            }, "GBCC-AnrWatchdog").apply {
                isDaemon = true
                start()
            }
        }

        fun stop() {
            running = false
            thread?.interrupt()
            thread = null
        }

        private fun record(stalledFor: Long) {
            val main = Looper.getMainLooper().thread
            val text = buildString {
                appendLine("GBCC main thread stalled for ${stalledFor} ms")
                appendLine("Time: ${timestamp(System.currentTimeMillis())}")
                appendDeviceInfo()
                appendLine()
                appendLine("Main thread (${main.state}):")
                main.stackTrace.forEach { appendLine("    at $it") }
                appendLine()
                appendLine("Other threads:")
                Thread.getAllStackTraces().forEach { (t, stack) ->
                    if (t == main || stack.isEmpty()) return@forEach
                    appendLine("\"${t.name}\" (${t.state})")
                    stack.take(15).forEach { appendLine("    at $it") }
                }
            }
            Log.e(TAG, text)
            try {
                reportFile(context).writeText(text)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to write ANR report", e)
            }
        }
    }

    /**
     * Shows a recorded watchdog report or, failing that, the trace of the most recent
     * ANR that Android recorded for this app (Android 11+), if not already shown.
     */
    fun showPendingReports(activity: AppCompatActivity) {
        val file = reportFile(activity)
        if (file.exists()) {
            val text = try { file.readText() } catch (_: Exception) { "" }
            file.delete()
            if (text.isNotBlank()) {
                showReport(activity, text)
                return
            }
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            systemAnrTrace(activity)?.let { showReport(activity, it) }
        }
    }

    private fun systemAnrTrace(context: Context): String? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return null
        return try {
            val am = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
            val exit = am.getHistoricalProcessExitReasons(context.packageName, 0, 5)
                .firstOrNull { it.reason == ApplicationExitInfo.REASON_ANR } ?: return null
            val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            if (prefs.getLong(LAST_EXIT_SEEN, 0) >= exit.timestamp) return null
            prefs.edit().putLong(LAST_EXIT_SEEN, exit.timestamp).apply()
            val trace = exit.traceInputStream?.bufferedReader()?.use { it.readText() } ?: ""
            buildString {
                appendLine("Android ANR report")
                appendLine("Time: ${timestamp(exit.timestamp)}")
                appendLine("Description: ${exit.description}")
                appendDeviceInfo()
                appendLine()
                append(trimTrace(trace))
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to read exit info", e)
            null
        }
    }

    /** Keeps the start of the trace and the app's own threads, which hold the useful parts. */
    private fun trimTrace(trace: String): String {
        if (trace.length <= 60000) return trace
        val threads = trace.split("\n\n")
        val useful = threads.filter {
            it.contains("\"main\"") || it.contains("gbcc", ignoreCase = true) ||
                it.contains("GLThread") || it.startsWith("----")
        }
        return useful.joinToString("\n\n").take(60000)
    }

    private fun StringBuilder.appendDeviceInfo() {
        appendLine("App: ${BuildConfig.APPLICATION_ID} ${BuildConfig.VERSION_NAME}")
        appendLine("Device: ${Build.MANUFACTURER} ${Build.MODEL}, Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})")
    }

    private fun timestamp(millis: Long) =
        SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date(millis))

    private fun showReport(activity: AppCompatActivity, text: String) {
        if (activity.isFinishing || activity.isDestroyed) return
        val padding = (16 * activity.resources.displayMetrics.density).toInt()
        val view = ScrollView(activity).apply {
            addView(TextView(activity).apply {
                this.text = text
                typeface = Typeface.MONOSPACE
                textSize = 11f
                setTextIsSelectable(true)
                setPadding(padding, padding, padding, padding)
            })
        }
        MaterialAlertDialogBuilder(activity)
            .setTitle("Freeze report")
            .setView(view)
            .setPositiveButton("Share") { _, _ ->
                val send = Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(Intent.EXTRA_SUBJECT, "GBCC freeze report")
                    putExtra(Intent.EXTRA_TEXT, text)
                }
                activity.startActivity(Intent.createChooser(send, "Share freeze report"))
            }
            .setNeutralButton("Copy") { _, _ ->
                val clipboard = activity.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                clipboard.setPrimaryClip(ClipData.newPlainText("GBCC freeze report", text))
                Toast.makeText(activity, "Copied", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("Close", null)
            .show()
    }
}
