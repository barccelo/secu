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
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import com.barccelo.secu.accessibility.SecuAccessibilityService
import com.barccelo.secu.core.AutomationStep
import com.barccelo.secu.core.AutomationStore
import com.barccelo.secu.core.NumericEngine
import com.barccelo.secu.core.PickerStore
import com.barccelo.secu.core.SequenceStore
import com.barccelo.secu.core.StepType
import com.barccelo.secu.core.VariableStore

class MainActivity : Activity() {
    private lateinit var statusText: TextView
    private lateinit var executionText: TextView
    private lateinit var stepTypeSpinner: Spinner
    private lateinit var stepValueInput: EditText
    private lateinit var stepsContainer: LinearLayout
    private lateinit var variablesText: TextView
    private lateinit var logText: TextView
    private var steps = mutableListOf<AutomationStep>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        steps = SequenceStore.loadSteps(this)
        setContentView(buildContent())
    }

    override fun onResume() {
        super.onResume()
        steps = SequenceStore.loadSteps(this)
        PickerStore.consumePoint(this)?.let { point ->
            val index = StepType.entries.indexOf(StepType.TAP_COORDINATE)
            if (index >= 0) stepTypeSpinner.setSelection(index)
            stepValueInput.setText(point)
            Toast.makeText(this, "Punto recuperado: $point", Toast.LENGTH_SHORT).show()
        }
        refreshUi()
    }

    private fun buildContent(): ScrollView {
        val density = resources.displayMetrics.density
        fun dp(value: Int) = (value * density).toInt()
        val scroll = ScrollView(this)
        val content = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(20), dp(24), dp(20), dp(32)) }

        content.addView(TextView(this).apply { text = "Secu"; textSize = 32f; setTypeface(typeface, Typeface.BOLD) })
        content.addView(TextView(this).apply { text = "Motor de secuencias · 0.4.1"; textSize = 16f; setPadding(0, dp(4), 0, dp(20)) })
        statusText = TextView(this).apply { textSize = 16f; setTypeface(typeface, Typeface.BOLD); setPadding(0, 0, 0, dp(8)) }
        content.addView(statusText)
        content.addView(Button(this).apply { text = "Abrir ajustes de accesibilidad"; isAllCaps = false; setOnClickListener { startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) } })

        content.addView(TextView(this).apply { text = "Secuencia"; textSize = 22f; setTypeface(typeface, Typeface.BOLD); setPadding(0, dp(26), 0, dp(6)) })
        content.addView(TextView(this).apply {
            text = "Campos: Pulsar campo = etiqueta/hint/id · Escribir en campo = Campo -> texto. Coordenadas: usa Seleccionar punto para tocar directamente sobre la pantalla objetivo."
            textSize = 14f; setPadding(0, 0, 0, dp(12))
        })

        stepTypeSpinner = Spinner(this).apply {
            adapter = ArrayAdapter(this@MainActivity, android.R.layout.simple_spinner_item, StepType.entries.map { it.label }).also { it.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item) }
        }
        content.addView(stepTypeSpinner)
        stepValueInput = EditText(this).apply { hint = "Valor del paso"; inputType = InputType.TYPE_CLASS_TEXT; isSingleLine = true }
        content.addView(stepValueInput)
        content.addView(Button(this).apply { text = "Seleccionar punto en pantalla"; isAllCaps = false; setOnClickListener { startPointPicker() } })
        content.addView(Button(this).apply { text = "Añadir paso"; isAllCaps = false; setOnClickListener { addStep() } })

        stepsContainer = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(0, dp(8), 0, dp(8)) }
        content.addView(stepsContainer)
        content.addView(Button(this).apply { text = "Vaciar secuencia"; isAllCaps = false; setOnClickListener { stopExecution(); steps.clear(); SequenceStore.saveSteps(this@MainActivity, steps); refreshUi() } })

        executionText = TextView(this).apply { textSize = 15f; setPadding(0, dp(12), 0, dp(8)) }
        content.addView(executionText)
        content.addView(Button(this).apply { text = "Ejecutar secuencia"; isAllCaps = false; setOnClickListener { runSequence() } })
        content.addView(Button(this).apply { text = "Detener"; isAllCaps = false; setOnClickListener { stopExecution(); AutomationStore.appendLog(this@MainActivity, "Secuencia detenida por el usuario."); refreshUi() } })

        content.addView(TextView(this).apply { text = "Variables"; textSize = 22f; setTypeface(typeface, Typeface.BOLD); setPadding(0, dp(26), 0, dp(6)) })
        variablesText = TextView(this).apply { textSize = 14f; typeface = Typeface.MONOSPACE; setTextIsSelectable(true) }
        content.addView(variablesText)
        content.addView(Button(this).apply { text = "Limpiar variables"; isAllCaps = false; setOnClickListener { VariableStore.clear(this@MainActivity); AutomationStore.appendLog(this@MainActivity, "Variables limpiadas."); refreshUi() } })

        val logHeader = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL; setPadding(0, dp(26), 0, 0) }
        logHeader.addView(TextView(this).apply { text = "Registro"; textSize = 22f; setTypeface(typeface, Typeface.BOLD); layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f) })
        logHeader.addView(Button(this).apply { text = "Actualizar"; isAllCaps = false; setOnClickListener { refreshUi() } })
        content.addView(logHeader)
        logText = TextView(this).apply { textSize = 13f; typeface = Typeface.MONOSPACE; setTextIsSelectable(true); setPadding(0, dp(8), 0, 0) }
        content.addView(logText)
        content.addView(Button(this).apply { text = "Limpiar registro"; isAllCaps = false; setOnClickListener { AutomationStore.clearLog(this@MainActivity); refreshUi() } })
        scroll.addView(content)
        return scroll
    }

    private fun startPointPicker() {
        if (!isAccessibilityServiceEnabled() || !SecuAccessibilityService.isConnected()) {
            Toast.makeText(this, "Activa primero el servicio de accesibilidad de Secu.", Toast.LENGTH_LONG).show(); return
        }
        stopExecution()
        if (!SecuAccessibilityService.startPointCapture(1000L)) {
            Toast.makeText(this, "No se pudo iniciar el selector.", Toast.LENGTH_LONG).show(); return
        }
        Toast.makeText(this, "Volviendo a la app anterior. Toca el punto que quieres guardar.", Toast.LENGTH_LONG).show()
        moveTaskToBack(true)
    }

    private fun addStep() {
        val type = StepType.entries[stepTypeSpinner.selectedItemPosition]
        val value = stepValueInput.text.toString().trim()
        if (type != StepType.BACK && value.isBlank()) { Toast.makeText(this, "Este paso necesita un valor.", Toast.LENGTH_SHORT).show(); return }
        if (type == StepType.DELAY && value.toLongOrNull() == null) { Toast.makeText(this, "Escribe el tiempo en milisegundos. Ej.: 1000", Toast.LENGTH_LONG).show(); return }
        val formatError = when (type) {
            StepType.READ_NUMBER -> NumericEngine.parseReadSpec(value) == null
            StepType.CALCULATE -> NumericEngine.parseAssignment(value) == null
            StepType.IF_NUMERIC -> NumericEngine.parseConditional(value) == null
            StepType.WRITE_VARIABLE -> !VariableStore.isValidName(value)
            StepType.INPUT_FIELD -> value.split("->", limit = 2).let { it.size != 2 || it[0].trim().isBlank() }
            StepType.TAP_COORDINATE -> value.split(",", limit = 2).let { it.size != 2 || it[0].trim().toIntOrNull() == null || it[1].trim().toIntOrNull() == null }
            else -> false
        }
        if (formatError) { Toast.makeText(this, "Revisa el formato de este paso.", Toast.LENGTH_LONG).show(); return }
        stopExecution()
        steps += AutomationStep(type = type, value = value)
        SequenceStore.saveSteps(this, steps)
        stepValueInput.setText("")
        refreshUi()
    }

    private fun renderSteps() {
        stepsContainer.removeAllViews()
        val density = resources.displayMetrics.density
        fun dp(value: Int) = (value * density).toInt()
        if (steps.isEmpty()) { stepsContainer.addView(TextView(this).apply { text = "No hay pasos todavía."; textSize = 14f; setPadding(0, dp(8), 0, dp(8)) }); return }
        steps.forEachIndexed { index, step ->
            val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL; setPadding(0, dp(4), 0, dp(4)) }
            row.addView(TextView(this).apply { text = "${index + 1}. ${step.description()}"; textSize = 14f; layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f) })
            row.addView(Button(this).apply { text = "↑"; isAllCaps = false; isEnabled = index > 0; setOnClickListener { moveStep(index, -1) } })
            row.addView(Button(this).apply { text = "↓"; isAllCaps = false; isEnabled = index < steps.lastIndex; setOnClickListener { moveStep(index, 1) } })
            row.addView(Button(this).apply { text = "×"; isAllCaps = false; setOnClickListener { removeStep(index) } })
            stepsContainer.addView(row)
        }
    }

    private fun moveStep(index: Int, delta: Int) {
        val destination = index + delta; if (destination !in steps.indices) return
        stopExecution(); val step = steps.removeAt(index); steps.add(destination, step); SequenceStore.saveSteps(this, steps); refreshUi()
    }
    private fun removeStep(index: Int) {
        if (index !in steps.indices) return
        stopExecution(); steps.removeAt(index); SequenceStore.saveSteps(this, steps); refreshUi()
    }
    private fun runSequence() {
        if (!isAccessibilityServiceEnabled() || !SecuAccessibilityService.isConnected()) { Toast.makeText(this, "Activa primero el servicio de accesibilidad de Secu.", Toast.LENGTH_LONG).show(); return }
        if (steps.isEmpty()) { Toast.makeText(this, "Añade al menos un paso.", Toast.LENGTH_SHORT).show(); return }
        SecuAccessibilityService.cancelPendingWork(); SequenceStore.reset(this); SequenceStore.saveSteps(this, steps); SequenceStore.start(this)
        AutomationStore.appendLog(this, "Secuencia iniciada · ${steps.size} pasos.")
        Toast.makeText(this, "Secuencia iniciada. Volviendo a la pantalla objetivo.", Toast.LENGTH_SHORT).show()
        moveTaskToBack(true)
        SecuAccessibilityService.requestProcess()
    }
    private fun stopExecution() { SecuAccessibilityService.cancelPendingWork(); SequenceStore.stop(this) }
    private fun refreshUi() {
        val enabled = isAccessibilityServiceEnabled()
        statusText.text = if (enabled && SecuAccessibilityService.isConnected()) "Accesibilidad: ACTIVA" else "Accesibilidad: DESACTIVADA"
        renderSteps()
        val running = SequenceStore.isRunning(this); val index = SequenceStore.currentIndex(this)
        executionText.text = if (running) steps.getOrNull(index)?.let { "Ejecutando ${index + 1}/${steps.size}: ${it.description()}" } ?: "Secuencia activa." else "Sin ejecución activa."
        variablesText.text = VariableStore.summary(this)
        logText.text = AutomationStore.readLog(this).ifBlank { "Todavía no hay eventos registrados." }
    }
    private fun isAccessibilityServiceEnabled(): Boolean {
        val manager = getSystemService(AccessibilityManager::class.java)
        return manager.getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_ALL_MASK).any { service ->
            val info = service.resolveInfo.serviceInfo
            info.packageName == packageName && info.name == SecuAccessibilityService::class.java.name
        }
    }
}
