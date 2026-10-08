package com.barccelo.secu.core

import android.content.Context

object PickerStore {
    private const val PREFS = "secu_picker"
    private const val KEY_POINT = "selected_point"

    fun savePoint(context: Context, x: Int, y: Int) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_POINT, "$x,$y")
            .apply()
    }

    fun consumePoint(context: Context): String? {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val value = prefs.getString(KEY_POINT, null)
        if (value != null) prefs.edit().remove(KEY_POINT).apply()
        return value
    }
}
