package com.barccelo.secu.core

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

object SequenceStore {
    private const val PREFS = "secu_sequence"
    private const val KEY_STEPS = "steps"
    private const val KEY_RUNNING = "running"
    private const val KEY_INDEX = "current_index"

    fun loadSteps(context: Context): MutableList<AutomationStep> {
        val raw = prefs(context).getString(KEY_STEPS, "[]").orEmpty()
        val result = mutableListOf<AutomationStep>()

        runCatching {
            val array = JSONArray(raw)
            for (index in 0 until array.length()) {
                val item = array.getJSONObject(index)
                val type = StepType.valueOf(item.getString("type"))
                result += AutomationStep(
                    id = item.optString("id").ifBlank { java.util.UUID.randomUUID().toString() },
                    type = type,
                    value = item.optString("value")
                )
            }
        }

        return result
    }

    fun saveSteps(context: Context, steps: List<AutomationStep>) {
        val array = JSONArray()
        steps.forEach { step ->
            array.put(
                JSONObject()
                    .put("id", step.id)
                    .put("type", step.type.name)
                    .put("value", step.value)
            )
        }

        prefs(context).edit().putString(KEY_STEPS, array.toString()).apply()
    }

    fun isRunning(context: Context): Boolean = prefs(context).getBoolean(KEY_RUNNING, false)

    fun currentIndex(context: Context): Int = prefs(context).getInt(KEY_INDEX, 0)

    fun currentStep(context: Context): AutomationStep? {
        if (!isRunning(context)) return null
        return loadSteps(context).getOrNull(currentIndex(context))
    }

    fun start(context: Context): Boolean {
        val steps = loadSteps(context)
        if (steps.isEmpty()) return false
        prefs(context).edit().putBoolean(KEY_RUNNING, true).putInt(KEY_INDEX, 0).apply()
        return true
    }

    fun stop(context: Context) {
        prefs(context).edit().putBoolean(KEY_RUNNING, false).apply()
    }

    fun advance(context: Context): Boolean = advanceBy(context, 1)

    fun advanceBy(context: Context, delta: Int): Boolean {
        val size = loadSteps(context).size
        val target = currentIndex(context) + delta

        return when {
            target >= size -> {
                prefs(context).edit().putInt(KEY_INDEX, size).putBoolean(KEY_RUNNING, false).apply()
                false
            }
            target < 0 -> {
                prefs(context).edit().putInt(KEY_INDEX, 0).apply()
                true
            }
            else -> {
                prefs(context).edit().putInt(KEY_INDEX, target).apply()
                true
            }
        }
    }

    fun reset(context: Context) {
        prefs(context).edit().putBoolean(KEY_RUNNING, false).putInt(KEY_INDEX, 0).apply()
    }

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}
