package com.barccelo.secu

import android.accessibilityservice.AccessibilityServiceInfo
import android.app.Activity
import android.content.Intent
import android.graphics.Typeface
import android.os.Bundle
import android.provider.Settings
import android.text.InputType
import android.view.Gravity
import android.view.ViewGroup
import android.view.accessibility.AccessibilityManager
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import com.barccelo.secu.accessibility.SecuAccessibilityService
import com.barccelo.secu.core.AutomationStore

class MainActivity : Activity() {

    private lateinit var statusText: TextView
    private lateinit var targetInput: EditText
    private lateinit var pendingText: TextView
    private lateinit var logText: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(buildContent())
    }

    override fun onResume() {
        super.onResume()
        refreshUi()
    }

    private fun buildContent(): ScrollView {
        val density = resources.displayMetrics.density
        fun dp(value: Int) = (value * density).toInt()

        val scroll = ScrollView(this)
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(24), dp(20), dp(32))
        }

        val title = TextView(this).apply {
            text = "Secu"
            textSize = 32f
            setTypeface(typeface, Typeface.BOLD)
        }

        val subtitle = TextView(this).apply {
            text = "Motor de automatización · prototipo 0.1"
            textSize = 16f
            setPadding(0, dp(4), 0, dp(22))
        }

        statusText = TextView(this).apply {
            textSize = 17f
            setTypeface(typeface, Typeface.BOLD)
            setPadding(0, 0, 0, dp(10))
        }

        val openSettings = Button(this).apply {
            text = "Abrir ajustes de accesibilidad"
            isAllCaps = false
            setOnClickListener {
                startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
            }
        }

        val section = TextView(this).apply {
            text = "Primera prueba"
            textSize = 21f
            setTypeface(typeface, Typeface.BOLD)
            setPadding(0, dp(26), 0, dp(6))
        }

        val help = TextView(this).apply {
            text = "Escribe el texto visible de un botón, arma la prueba y luego abre la aplicación objetivo. Secu esperará hasta encontrarlo y tratará de pulsarlo."
            textSize = 15f
            setPadding(0, 0, 0, dp(10))
        }

        targetInput = EditText(this).apply {
            hint = "Ej.: Aceptar"
            inputType = InputType.TYPE_CLASS_TEXT
            isSingleLine = true
        }

        val armButton = Button(this).apply {
            text = "Armar: buscar y pulsar"
            isAllCaps = false
            setOnClickListener {
                val target = targetInput.text.toString().trim()
                if (target.isBlank()) {
                    Toast.makeText(this@MainActivity, "Escribe el texto que Secu debe buscar.", Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }

                if (!isAccessibilityServiceEnabled()) {
                    Toast.makeText(
                        this@MainActivity,
                        "Activa primero el servicio de accesibilidad de Secu.",
                        Toast.LENGTH_LONG
                    ).show()
                    return@setOnClickListener
                }

                AutomationStore.armTextClick(this@MainActivity, target)
                refreshUi()
                Toast.makeText(
                    this@MainActivity,
                    "Secu está armado. Abre ahora la app objetivo.",
                    Toast.LENGTH_LONG
                ).show()
            }
        }

        val cancelButton = Button(this).apply {
            text = "Cancelar prueba pendiente"
            isAllCaps = false
            setOnClickListener {
                AutomationStore.clearPending(this@MainActivity)
                AutomationStore.appendLog(this@MainActivity, "Prueba pendiente cancelada.")
                refreshUi()
            }
        }

        pendingText = TextView(this).apply {
            textSize = 14f
            setPadding(0, dp(8), 0, dp(18))
        }

        val logHeader = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }

        val logTitle = TextView(this).apply {
            text = "Registro"
            textSize = 21f
            setTypeface(typeface, Typeface.BOLD)
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        }

        val refreshButton = Button(this).apply {
            text = "Actualizar"
            isAllCaps = false
            setOnClickListener { refreshUi() }
        }

        logHeader.addView(logTitle)
        logHeader.addView(refreshButton)

        logText = TextView(this).apply {
            textSize = 13f
            typeface = Typeface.MONOSPACE
            setTextIsSelectable(true)
            setPadding(0, dp(8), 0, 0)
        }

        val clearLogButton = Button(this).apply {
            text = "Limpiar registro"
            isAllCaps = false
            setOnClickListener {
                AutomationStore.clearLog(this@MainActivity)
                refreshUi()
            }
        }

        content.addView(title)
        content.addView(subtitle)
        content.addView(statusText)
        content.addView(openSettings)
        content.addView(section)
        content.addView(help)
        content.addView(targetInput)
        content.addView(armButton)
        content.addView(cancelButton)
        content.addView(pendingText)
        content.addView(logHeader)
        content.addView(logText)
        content.addView(clearLogButton)

        scroll.addView(content)
        return scroll
    }

    private fun refreshUi() {
        val enabled = isAccessibilityServiceEnabled()
        statusText.text = if (enabled) {
            "Servicio de accesibilidad: ACTIVO"
        } else {
            "Servicio de accesibilidad: DESACTIVADO"
        }

        val pending = AutomationStore.pendingText(this)
        pendingText.text = if (pending == null) {
            "Sin acción pendiente."
        } else {
            "Pendiente: encontrar y pulsar “$pending”."
        }

        logText.text = AutomationStore.readLog(this).ifBlank {
            "Todavía no hay eventos registrados."
        }
    }

    private fun isAccessibilityServiceEnabled(): Boolean {
        val manager = getSystemService(AccessibilityManager::class.java)
        val enabledServices = manager.getEnabledAccessibilityServiceList(
            AccessibilityServiceInfo.FEEDBACK_ALL_MASK
        )

        return enabledServices.any { service ->
            val info = service.resolveInfo.serviceInfo
            info.packageName == packageName &&
                info.name == SecuAccessibilityService::class.java.name
        }
    }
}
