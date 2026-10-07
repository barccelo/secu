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
import com.barccelo.secu.core.SequenceStore
import com.barccelo.secu.core.StepType

class MainActivity : Activity() {

    private lateinit var statusText: TextView
    private lateinit var executionText: TextView
    private lateinit var stepTypeSpinner: Spinner
    private lateinit var stepValueInput: EditText
    private lateinit var stepsContainer: LinearLayout
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
            text = "Motor de secuencias · 0.2"
            textSize = 16f
            setPadding(0, dp(4), 0, dp(20))
        }

        statusText = TextView(this).apply {
            textSize = 16f
            setTypeface(typeface, Typeface.BOLD)
            setPadding(0, 0, 0, dp(8))
        }

        val openSettings = Button(this).apply {
            text = "Abrir ajustes de accesibilidad"
            isAllCaps = false
            setOnClickListener {
                startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
            }
        }

        val sequenceTitle = TextView(this).apply {
            text = "Secuencia"
            textSize = 22f
            setTypeface(typeface, Typeface.BOLD)
            setPadding(0, dp(26), 0, dp(6))
        }

        val help = TextView(this).apply {
            text = "Añade pasos en el orden en que Secu debe ejecutarlos. Los pasos se guardan automáticamente en este teléfono."
            textSize = 15f
            setPadding(0, 0, 0, dp(12))
        }

        stepTypeSpinner = Spinner(this).apply {
            adapter = ArrayAdapter(
                this@MainActivity,
                android.R.layout.simple_spinner_item,
                StepType.entries.map { it.label }
            ).also { adapter ->
                adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
            }
        }

        stepValueInput = EditText(this).apply {
            hint = "Texto, milisegundos o paquete de app"
            inputType = InputType.TYPE_CLASS_TEXT
            isSingleLine = true
        }

        val addStepButton = Button(this).apply {
            text = "Añadir paso"
            isAllCaps = false
            setOnClickListener { addStep() }
        }

        stepsContainer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, dp(8), 0, dp(8))
        }

        val clearButton = Button(this).apply {
            text = "Vaciar secuencia"
            isAllCaps = false
            setOnClickListener {
                stopExecution()
                steps.clear()
                SequenceStore.saveSteps(this@MainActivity, steps)
                refreshUi()
            }
        }

        executionText = TextView(this).apply {
            textSize = 15f
            setPadding(0, dp(12), 0, dp(8))
        }

        val runButton = Button(this).apply {
            text = "Ejecutar secuencia"
            isAllCaps = false
            setOnClickListener { runSequence() }
        }

        val stopButton = Button(this).apply {
            text = "Detener"
            isAllCaps = false
            setOnClickListener {
                stopExecution()
                AutomationStore.appendLog(this@MainActivity, "Secuencia detenida por el usuario.")
                refreshUi()
            }
        }

        val logHeader = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, dp(26), 0, 0)
        }

        val logTitle = TextView(this).apply {
            text = "Registro"
            textSize = 22f
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
        content.addView(sequenceTitle)
        content.addView(help)
        content.addView(stepTypeSpinner)
        content.addView(stepValueInput)
        content.addView(addStepButton)
        content.addView(stepsContainer)
        content.addView(clearButton)
        content.addView(executionText)
        content.addView(runButton)
        content.addView(stopButton)
        content.addView(logHeader)
        content.addView(logText)
        content.addView(clearLogButton)

        scroll.addView(content)
        return scroll
    }

    private fun addStep() {
        val type = StepType.entries[stepTypeSpinner.selectedItemPosition]
        val value = stepValueInput.text.toString().trim()

        if (type != StepType.BACK && value.isBlank()) {
            Toast.makeText(this, "Este paso necesita un valor.", Toast.LENGTH_SHORT).show()
            return
        }

        if (type == StepType.DELAY && value.toLongOrNull() == null) {
            Toast.makeText(this, "Escribe el tiempo en milisegundos. Ej.: 1000", Toast.LENGTH_LONG).show()
            return
        }

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

        if (steps.isEmpty()) {
            stepsContainer.addView(TextView(this).apply {
                text = "No hay pasos todavía."
                textSize = 14f
                setPadding(0, dp(8), 0, dp(8))
            })
            return
        }

        steps.forEachIndexed { index, step ->
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(0, dp(4), 0, dp(4))
            }

            val label = TextView(this).apply {
                text = "${index + 1}. ${step.description()}"
                textSize = 14f
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            }

            val up = Button(this).apply {
                text = "↑"
                isAllCaps = false
                isEnabled = index > 0
                setOnClickListener { moveStep(index, -1) }
            }

            val down = Button(this).apply {
                text = "↓"
                isAllCaps = false
                isEnabled = index < steps.lastIndex
                setOnClickListener { moveStep(index, 1) }
            }

            val delete = Button(this).apply {
                text = "×"
                isAllCaps = false
                setOnClickListener { removeStep(index) }
            }

            row.addView(label)
            row.addView(up)
            row.addView(down)
            row.addView(delete)
            stepsContainer.addView(row)
        }
    }

    private fun moveStep(index: Int, delta: Int) {
        val destination = index + delta
        if (destination !in steps.indices) return
        stopExecution()
        val step = steps.removeAt(index)
        steps.add(destination, step)
        SequenceStore.saveSteps(this, steps)
        refreshUi()
    }

    private fun removeStep(index: Int) {
        if (index !in steps.indices) return
        stopExecution()
        steps.removeAt(index)
        SequenceStore.saveSteps(this, steps)
        refreshUi()
    }

    private fun runSequence() {
        if (!isAccessibilityServiceEnabled() || !SecuAccessibilityService.isConnected()) {
            Toast.makeText(this, "Activa primero el servicio de accesibilidad de Secu.", Toast.LENGTH_LONG).show()
            return
        }

        if (steps.isEmpty()) {
            Toast.makeText(this, "Añade al menos un paso.", Toast.LENGTH_SHORT).show()
            return
        }

        SecuAccessibilityService.cancelPendingWork()
        SequenceStore.reset(this)
        SequenceStore.saveSteps(this, steps)
        SequenceStore.start(this)
        AutomationStore.appendLog(this, "Secuencia iniciada · ${steps.size} pasos.")
        SecuAccessibilityService.requestProcess()
        refreshUi()
    }

    private fun stopExecution() {
        SecuAccessibilityService.cancelPendingWork()
        SequenceStore.stop(this)
    }

    private fun refreshUi() {
        val enabled = isAccessibilityServiceEnabled()
        statusText.text = if (enabled && SecuAccessibilityService.isConnected()) {
            "Accesibilidad: ACTIVA"
        } else {
            "Accesibilidad: DESACTIVADA"
        }

        renderSteps()

        val running = SequenceStore.isRunning(this)
        val index = SequenceStore.currentIndex(this)
        executionText.text = if (running) {
            val current = steps.getOrNull(index)
            if (current == null) {
                "Secuencia activa."
            } else {
                "Ejecutando ${index + 1}/${steps.size}: ${current.description()}"
            }
        } else {
            "Sin ejecución activa."
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
