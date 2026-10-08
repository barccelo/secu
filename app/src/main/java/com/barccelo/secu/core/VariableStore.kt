package com.barccelo.secu.core

import android.content.Context
import java.math.BigDecimal

object VariableStore {
    private const val PREFS = "secu_variables"
    private val validName = Regex("[A-Za-z_][A-Za-z0-9_]*")

    fun isValidName(name: String): Boolean = validName.matches(name.trim())

    fun setNumber(context: Context, name: String, value: BigDecimal): Boolean {
        val cleanName = name.trim()
        if (!isValidName(cleanName)) return false
        prefs(context).edit()
            .putString(cleanName, value.stripTrailingZeros().toPlainString())
            .apply()
        return true
    }

    fun getNumber(context: Context, name: String): BigDecimal? {
        val cleanName = name.trim()
        if (!isValidName(cleanName)) return null
        return prefs(context).getString(cleanName, null)?.toBigDecimalOrNull()
    }

    fun clear(context: Context) {
        prefs(context).edit().clear().apply()
    }

    fun summary(context: Context): String {
        val entries = prefs(context).all
            .mapNotNull { (key, value) ->
                val number = value?.toString()?.toBigDecimalOrNull() ?: return@mapNotNull null
                key to number.stripTrailingZeros().toPlainString()
            }
            .sortedBy { it.first.lowercase() }

        return if (entries.isEmpty()) {
            "Sin variables."
        } else {
            entries.joinToString("\n") { (name, value) -> "$name = $value" }
        }
    }

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}
