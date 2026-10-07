package com.barccelo.secu.core

import android.content.Context
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object AutomationStore {
    private const val PREFS = "secu_automation"
    private const val KEY_PENDING_TEXT = "pending_text"
    private const val KEY_LOG = "event_log"
    private const val MAX_LOG_LINES = 80

    fun armTextClick(context: Context, target: String) {
        val cleanTarget = target.trim()
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_PENDING_TEXT, cleanTarget)
            .apply()

        appendLog(context, "Armado: buscar y pulsar “$cleanTarget”.")
    }

    fun pendingText(context: Context): String? {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY_PENDING_TEXT, null)
            ?.takeIf { it.isNotBlank() }
    }

    fun clearPending(context: Context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .remove(KEY_PENDING_TEXT)
            .apply()
    }

    @Synchronized
    fun appendLog(context: Context, message: String) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val oldLines = prefs.getString(KEY_LOG, "")
            .orEmpty()
            .lineSequence()
            .filter { it.isNotBlank() }
            .toMutableList()

        val time = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date())
        oldLines += "$time · $message"

        val trimmed = oldLines.takeLast(MAX_LOG_LINES).joinToString("\n")
        prefs.edit().putString(KEY_LOG, trimmed).apply()
    }

    fun readLog(context: Context): String {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY_LOG, "")
            .orEmpty()
    }

    fun clearLog(context: Context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .remove(KEY_LOG)
            .apply()
    }
}
