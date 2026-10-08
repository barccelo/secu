package com.barccelo.secu.accessibility

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import com.barccelo.secu.core.AutomationStore
import com.barccelo.secu.core.NumericEngine
import com.barccelo.secu.core.SequenceStore
import com.barccelo.secu.core.StepType
import com.barccelo.secu.core.VariableStore
import java.math.BigDecimal
import java.util.ArrayDeque

class SecuAccessibilityService : AccessibilityService() {

    companion object {
        @Volatile private var instance: SecuAccessibilityService? = null
        fun isConnected(): Boolean = instance != null
        fun requestProcess() { instance?.kick() }
        fun cancelPendingWork() { instance?.cancelScheduledDelay() }
    }

    private val handler = Handler(Looper.getMainLooper())
    private var delayedRunnable: Runnable? = null
    private var delayedStepIndex: Int? = null
    private var processing = false
    private var lastPackageName: String? = null

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        AutomationStore.appendLog(this, "Servicio de accesibilidad conectado.")
        kick()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        event ?: return
        val eventPackage = event.packageName?.toString()
        if (!eventPackage.isNullOrBlank() && eventPackage != lastPackageName) {
            lastPackageName = eventPackage
            AutomationStore.appendLog(this, "Pantalla activa: $eventPackage · ${AccessibilityEvent.eventTypeToString(event.eventType)}")
        }
        if (SequenceStore.isRunning(this)) kick()
    }

    override fun onInterrupt() {
        AutomationStore.appendLog(this, "Servicio de accesibilidad interrumpido.")
    }

    override fun onDestroy() {
        cancelScheduledDelay()
        if (instance === this) instance = null
        super.onDestroy()
    }

    private fun kick() { handler.post { processCurrentStep() } }

    private fun processCurrentStep() {
        if (processing || !SequenceStore.isRunning(this)) return
        val step = SequenceStore.currentStep(this) ?: return
        val index = SequenceStore.currentIndex(this)
        processing = true

        try {
            when (step.type) {
                StepType.WAIT_TEXT -> {
                    val root = rootInActiveWindow ?: return
                    if (findBestMatch(root, step.value) != null) completeStep("Texto encontrado: “${step.value}”.")
                }

                StepType.CLICK_TEXT -> {
                    val root = rootInActiveWindow ?: return
                    val candidate = findBestMatch(root, step.value) ?: return
                    val clickable = findClickableNode(candidate)
                    if (clickable == null) {
                        AutomationStore.appendLog(this, "Encontrado “${step.value}”, pero aún no hay un nodo pulsable.")
                        return
                    }
                    if (clickable.performAction(AccessibilityNodeInfo.ACTION_CLICK)) {
                        completeStep("Pulsado: “${step.value}”.")
                    } else {
                        AutomationStore.appendLog(this, "No se pudo pulsar “${step.value}”.")
                    }
                }

                StepType.DELAY -> {
                    val millis = step.value.toLongOrNull()?.coerceAtLeast(0L) ?: 0L
                    scheduleDelay(index, millis)
                }

                StepType.BACK -> {
                    if (performGlobalAction(GLOBAL_ACTION_BACK)) completeStep("Acción Atrás ejecutada.")
                    else AutomationStore.appendLog(this, "No se pudo ejecutar Atrás.")
                }

                StepType.INPUT_TEXT -> {
                    val root = rootInActiveWindow ?: return
                    val editable = findEditableNode(root) ?: return
                    if (setNodeText(editable, step.value)) completeStep("Texto escrito.")
                    else AutomationStore.appendLog(this, "No se pudo escribir en el campo editable.")
                }

                StepType.OPEN_APP -> {
                    val targetPackage = step.value.trim()
                    val launchIntent = packageManager.getLaunchIntentForPackage(targetPackage)
                    if (launchIntent == null) {
                        failStep("No se encontró una app lanzable para $targetPackage.")
                        return
                    }
                    launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    runCatching { startActivity(launchIntent) }
                        .onSuccess { completeStep("App abierta: $targetPackage.") }
                        .onFailure { failStep("No se pudo abrir $targetPackage: ${it.message ?: "error desconocido"}.") }
                }

                StepType.READ_NUMBER -> {
                    val spec = NumericEngine.parseReadSpec(step.value)
                    if (spec == null) {
                        failStep("Formato de lectura inválido. Usa: Etiqueta -> variable")
                        return
                    }
                    val root = rootInActiveWindow ?: return
                    val number = findNumberNearLabel(root, spec.label) ?: return
                    if (!VariableStore.setNumber(this, spec.variable, number)) {
                        failStep("Nombre de variable inválido: ${spec.variable}")
                        return
                    }
                    completeStep("${spec.variable} = ${number.stripTrailingZeros().toPlainString()}")
                }

                StepType.CALCULATE -> {
                    val spec = NumericEngine.parseAssignment(step.value)
                    if (spec == null) {
                        failStep("Formato de cálculo inválido. Usa: resultado = min(a, b)")
                        return
                    }
                    val result = NumericEngine.evaluateExpression(spec.expression) { name ->
                        VariableStore.getNumber(this, name)
                    }
                    if (result == null) {
                        failStep("No se pudo calcular: ${spec.expression}")
                        return
                    }
                    VariableStore.setNumber(this, spec.variable, result)
                    completeStep("${spec.variable} = ${result.stripTrailingZeros().toPlainString()}")
                }

                StepType.IF_NUMERIC -> {
                    val spec = NumericEngine.parseConditional(step.value)
                    if (spec == null) {
                        failStep("Formato IF inválido. Usa: a >= b ? +0 : +1")
                        return
                    }
                    val condition = NumericEngine.evaluateCondition(spec.condition) { name ->
                        VariableStore.getNumber(this, name)
                    }
                    if (condition == null) {
                        failStep("No se pudo evaluar: ${spec.condition}")
                        return
                    }
                    val offset = if (condition) spec.trueOffset else spec.falseOffset
                    completeStep("IF ${spec.condition} = $condition · salto adicional $offset.", offset)
                }

                StepType.WRITE_VARIABLE -> {
                    val variableName = step.value.trim()
                    val number = VariableStore.getNumber(this, variableName)
                    if (number == null) {
                        failStep("Variable inexistente o no numérica: $variableName")
                        return
                    }
                    val root = rootInActiveWindow ?: return
                    val editable = findEditableNode(root) ?: return
                    val textValue = number.stripTrailingZeros().toPlainString()
                    if (setNodeText(editable, textValue)) {
                        completeStep("Variable $variableName escrita: $textValue.")
                    } else {
                        AutomationStore.appendLog(this, "No se pudo escribir la variable $variableName.")
                    }
                }
            }
        } finally {
            processing = false
        }
    }

    private fun scheduleDelay(stepIndex: Int, millis: Long) {
        if (delayedStepIndex == stepIndex && delayedRunnable != null) return
        cancelScheduledDelay()
        delayedStepIndex = stepIndex
        AutomationStore.appendLog(this, "Esperando $millis ms.")
        val runnable = Runnable {
            delayedRunnable = null
            delayedStepIndex = null
            if (SequenceStore.isRunning(this) && SequenceStore.currentIndex(this) == stepIndex) {
                completeStep("Espera de $millis ms completada.")
            }
        }
        delayedRunnable = runnable
        handler.postDelayed(runnable, millis)
    }

    private fun cancelScheduledDelay() {
        delayedRunnable?.let(handler::removeCallbacks)
        delayedRunnable = null
        delayedStepIndex = null
    }

    private fun completeStep(message: String, extraSkip: Int = 0) {
        cancelScheduledDelay()
        val stepNumber = SequenceStore.currentIndex(this) + 1
        AutomationStore.appendLog(this, "Paso $stepNumber OK · $message")
        if (SequenceStore.advanceBy(this, 1 + extraSkip)) {
            handler.postDelayed({ processCurrentStep() }, 40L)
        } else {
            AutomationStore.appendLog(this, "Secuencia completada.")
        }
    }

    private fun failStep(message: String) {
        SequenceStore.stop(this)
        AutomationStore.appendLog(this, "ERROR · $message · Secuencia detenida.")
    }

    private fun setNodeText(node: AccessibilityNodeInfo, value: String): Boolean {
        node.performAction(AccessibilityNodeInfo.ACTION_FOCUS)
        val args = Bundle().apply {
            putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, value)
        }
        return node.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)
    }

    private fun findBestMatch(root: AccessibilityNodeInfo, target: String): AccessibilityNodeInfo? {
        val normalizedTarget = target.trim()
        if (normalizedTarget.isBlank()) return null
        val queue = ArrayDeque<AccessibilityNodeInfo>()
        queue.add(root)
        var partialMatch: AccessibilityNodeInfo? = null

        while (queue.isNotEmpty()) {
            val node = queue.removeFirst()
            val text = node.text?.toString()?.trim()
            val description = node.contentDescription?.toString()?.trim()
            val exact = text.equals(normalizedTarget, ignoreCase = true) || description.equals(normalizedTarget, ignoreCase = true)
            if (exact) return node
            if (partialMatch == null) {
                val partial = text?.contains(normalizedTarget, ignoreCase = true) == true ||
                    description?.contains(normalizedTarget, ignoreCase = true) == true
                if (partial) partialMatch = node
            }
            for (childIndex in 0 until node.childCount) node.getChild(childIndex)?.let(queue::addLast)
        }
        return partialMatch
    }

    private fun findNumberNearLabel(root: AccessibilityNodeInfo, label: String): BigDecimal? {
        val entries = mutableListOf<Pair<AccessibilityNodeInfo, String>>()
        flattenReadableNodes(root, entries)
        val matchIndex = entries.indexOfFirst { (_, text) ->
            text.equals(label, ignoreCase = true) || text.contains(label, ignoreCase = true)
        }
        if (matchIndex < 0) return null

        val matchedNode = entries[matchIndex].first
        val parent = matchedNode.parent
        if (parent != null) {
            val localEntries = mutableListOf<Pair<AccessibilityNodeInfo, String>>()
            flattenReadableNodes(parent, localEntries)
            val localIndex = localEntries.indexOfFirst { it.first == matchedNode }
            if (localIndex >= 0) {
                for (i in (localIndex + 1)..minOf(localIndex + 8, localEntries.lastIndex)) {
                    NumericEngine.extractNumber(localEntries[i].second)?.let { return it }
                }
            }
        }

        for (i in (matchIndex + 1)..minOf(matchIndex + 14, entries.lastIndex)) {
            NumericEngine.extractNumber(entries[i].second)?.let { return it }
        }
        return null
    }

    private fun flattenReadableNodes(
        node: AccessibilityNodeInfo,
        output: MutableList<Pair<AccessibilityNodeInfo, String>>
    ) {
        val text = node.text?.toString()?.trim().orEmpty()
        val description = node.contentDescription?.toString()?.trim().orEmpty()
        val readable = when {
            text.isNotBlank() -> text
            description.isNotBlank() -> description
            else -> ""
        }
        if (readable.isNotBlank()) output += node to readable
        for (childIndex in 0 until node.childCount) {
            node.getChild(childIndex)?.let { child -> flattenReadableNodes(child, output) }
        }
    }

    private fun findClickableNode(start: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        var current: AccessibilityNodeInfo? = start
        while (current != null) {
            if (current.isClickable && current.isEnabled) return current
            current = current.parent
        }
        return null
    }

    private fun findEditableNode(root: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        val queue = ArrayDeque<AccessibilityNodeInfo>()
        queue.add(root)
        var firstEditable: AccessibilityNodeInfo? = null
        while (queue.isNotEmpty()) {
            val node = queue.removeFirst()
            if (node.isEditable && node.isEnabled) {
                if (node.isFocused) return node
                if (firstEditable == null) firstEditable = node
            }
            for (childIndex in 0 until node.childCount) node.getChild(childIndex)?.let(queue::addLast)
        }
        return firstEditable
    }
}
