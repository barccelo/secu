package com.barccelo.secu.accessibility

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.content.Intent
import android.graphics.Color
import android.graphics.Path
import android.graphics.PixelFormat
import android.graphics.Rect
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.Toast
import com.barccelo.secu.core.AutomationStore
import com.barccelo.secu.core.NumericEngine
import com.barccelo.secu.core.PickerStore
import com.barccelo.secu.core.SequenceStore
import com.barccelo.secu.core.StepType
import com.barccelo.secu.core.VariableStore
import java.math.BigDecimal
import java.util.ArrayDeque
import kotlin.math.abs

class SecuAccessibilityService : AccessibilityService() {

    companion object {
        @Volatile private var instance: SecuAccessibilityService? = null
        fun isConnected(): Boolean = instance != null
        fun requestProcess() { instance?.kick() }
        fun cancelPendingWork() { instance?.cancelPendingWorkInternal() }
        fun startPointCapture(delayMs: Long = 1000L): Boolean {
            val service = instance ?: return false
            service.schedulePointCapture(delayMs)
            return true
        }
    }

    private val handler = Handler(Looper.getMainLooper())
    private var delayedRunnable: Runnable? = null
    private var delayedStepIndex: Int? = null
    private var pointCaptureRunnable: Runnable? = null
    private var pointCaptureView: View? = null
    private var gestureInProgress = false
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

    override fun onInterrupt() { AutomationStore.appendLog(this, "Servicio de accesibilidad interrumpido.") }

    override fun onDestroy() {
        cancelPendingWorkInternal()
        removePointCaptureOverlay()
        if (instance === this) instance = null
        super.onDestroy()
    }

    private fun kick() { handler.post { processCurrentStep() } }

    private fun processCurrentStep() {
        if (processing || gestureInProgress || !SequenceStore.isRunning(this)) return
        val step = SequenceStore.currentStep(this) ?: return
        val index = SequenceStore.currentIndex(this)

        if (stepNeedsExternalWindow(step.type)) {
            val activePackage = rootInActiveWindow?.packageName?.toString()
            if (activePackage.isNullOrBlank() || activePackage == packageName) return
        }

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
                    if (clickable.performAction(AccessibilityNodeInfo.ACTION_CLICK)) completeStep("Pulsado: “${step.value}”.")
                    else AutomationStore.appendLog(this, "No se pudo pulsar “${step.value}”.")
                }
                StepType.CLICK_FIELD -> {
                    val root = rootInActiveWindow ?: return
                    val field = findField(root, step.value) ?: return
                    if (field.performAction(AccessibilityNodeInfo.ACTION_CLICK) || field.performAction(AccessibilityNodeInfo.ACTION_FOCUS)) {
                        completeStep("Campo seleccionado: ${step.value}.")
                    } else {
                        tapNodeCenter(field, "Campo seleccionado: ${step.value}.")
                    }
                }
                StepType.INPUT_FIELD -> {
                    val spec = parseFieldInput(step.value)
                    if (spec == null) { failStep("Formato inválido. Usa: Campo -> texto"); return }
                    val root = rootInActiveWindow ?: return
                    val field = findField(root, spec.first) ?: return
                    if (setNodeText(field, spec.second)) completeStep("Texto escrito en ${spec.first}.")
                    else AutomationStore.appendLog(this, "No se pudo escribir en el campo ${spec.first}.")
                }
                StepType.TAP_COORDINATE -> {
                    val point = parsePoint(step.value)
                    if (point == null) { failStep("Coordenada inválida. Usa: x,y"); return }
                    dispatchTap(point.first.toFloat(), point.second.toFloat(), "Toque ejecutado en ${point.first},${point.second}.")
                }
                StepType.DELAY -> scheduleDelay(index, step.value.toLongOrNull()?.coerceAtLeast(0L) ?: 0L)
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
                    if (launchIntent == null) { failStep("No se encontró una app lanzable para $targetPackage."); return }
                    launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    runCatching { startActivity(launchIntent) }
                        .onSuccess { completeStep("App abierta: $targetPackage.") }
                        .onFailure { failStep("No se pudo abrir $targetPackage: ${it.message ?: "error desconocido"}.") }
                }
                StepType.READ_NUMBER -> {
                    val spec = NumericEngine.parseReadSpec(step.value)
                    if (spec == null) { failStep("Formato de lectura inválido. Usa: Etiqueta -> variable"); return }
                    val root = rootInActiveWindow ?: return
                    val number = findNumberNearLabel(root, spec.label) ?: return
                    if (!VariableStore.setNumber(this, spec.variable, number)) { failStep("Nombre de variable inválido: ${spec.variable}"); return }
                    completeStep("${spec.variable} = ${number.stripTrailingZeros().toPlainString()}")
                }
                StepType.CALCULATE -> {
                    val spec = NumericEngine.parseAssignment(step.value)
                    if (spec == null) { failStep("Formato de cálculo inválido. Usa: resultado = min(a, b)"); return }
                    val result = NumericEngine.evaluateExpression(spec.expression) { name -> VariableStore.getNumber(this, name) }
                    if (result == null) { failStep("No se pudo calcular: ${spec.expression}"); return }
                    VariableStore.setNumber(this, spec.variable, result)
                    completeStep("${spec.variable} = ${result.stripTrailingZeros().toPlainString()}")
                }
                StepType.IF_NUMERIC -> {
                    val spec = NumericEngine.parseConditional(step.value)
                    if (spec == null) { failStep("Formato IF inválido. Usa: a >= b ? +0 : +1"); return }
                    val condition = NumericEngine.evaluateCondition(spec.condition) { name -> VariableStore.getNumber(this, name) }
                    if (condition == null) { failStep("No se pudo evaluar: ${spec.condition}"); return }
                    val offset = if (condition) spec.trueOffset else spec.falseOffset
                    completeStep("IF ${spec.condition} = $condition · salto adicional $offset.", offset)
                }
                StepType.WRITE_VARIABLE -> {
                    val variableName = step.value.trim()
                    val number = VariableStore.getNumber(this, variableName)
                    if (number == null) { failStep("Variable inexistente o no numérica: $variableName"); return }
                    val root = rootInActiveWindow ?: return
                    val editable = findEditableNode(root) ?: return
                    val textValue = number.stripTrailingZeros().toPlainString()
                    if (setNodeText(editable, textValue)) completeStep("Variable $variableName escrita: $textValue.")
                    else AutomationStore.appendLog(this, "No se pudo escribir la variable $variableName.")
                }
            }
        } finally { processing = false }
    }

    private fun parseFieldInput(raw: String): Pair<String, String>? {
        val parts = raw.split("->", limit = 2)
        if (parts.size != 2) return null
        val selector = parts[0].trim()
        val value = parts[1].trim()
        if (selector.isBlank()) return null
        return selector to value
    }

    private fun parsePoint(raw: String): Pair<Int, Int>? {
        val parts = raw.split(",", limit = 2)
        if (parts.size != 2) return null
        val x = parts[0].trim().toIntOrNull() ?: return null
        val y = parts[1].trim().toIntOrNull() ?: return null
        if (x < 0 || y < 0) return null
        return x to y
    }

    private fun schedulePointCapture(delayMs: Long) {
        pointCaptureRunnable?.let(handler::removeCallbacks)
        removePointCaptureOverlay()
        val runnable = Runnable { showPointCaptureOverlay() }
        pointCaptureRunnable = runnable
        handler.postDelayed(runnable, delayMs.coerceAtLeast(0L))
        AutomationStore.appendLog(this, "Selector de punto armado.")
    }

    private fun showPointCaptureOverlay() {
        pointCaptureRunnable = null
        val windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        val overlay = View(this).apply {
            setBackgroundColor(Color.argb(22, 0, 0, 0))
            setOnTouchListener { _, event ->
                if (event.action == MotionEvent.ACTION_DOWN) {
                    val x = event.rawX.toInt()
                    val y = event.rawY.toInt()
                    PickerStore.savePoint(this@SecuAccessibilityService, x, y)
                    AutomationStore.appendLog(this@SecuAccessibilityService, "Punto capturado: $x,$y.")
                    Toast.makeText(this@SecuAccessibilityService, "Punto guardado: $x,$y", Toast.LENGTH_SHORT).show()
                    removePointCaptureOverlay()
                }
                true
            }
        }
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        )
        pointCaptureView = overlay
        windowManager.addView(overlay, params)
        Toast.makeText(this, "Toca el punto que Secu debe recordar", Toast.LENGTH_SHORT).show()
    }

    private fun removePointCaptureOverlay() {
        val overlay = pointCaptureView ?: return
        pointCaptureView = null
        runCatching { (getSystemService(WINDOW_SERVICE) as WindowManager).removeView(overlay) }
    }

    private fun dispatchTap(x: Float, y: Float, successMessage: String) {
        gestureInProgress = true
        val path = Path().apply { moveTo(x, y) }
        val gesture = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(path, 0L, 60L))
            .build()
        val accepted = dispatchGesture(gesture, object : GestureResultCallback() {
            override fun onCompleted(gestureDescription: GestureDescription?) {
                gestureInProgress = false
                completeStep(successMessage, resumeDelayMs = 120L)
            }
            override fun onCancelled(gestureDescription: GestureDescription?) {
                gestureInProgress = false
                AutomationStore.appendLog(this@SecuAccessibilityService, "El toque fue cancelado.")
            }
        }, null)
        if (!accepted) {
            gestureInProgress = false
            AutomationStore.appendLog(this, "Android rechazó el gesto de toque.")
        }
    }

    private fun tapNodeCenter(node: AccessibilityNodeInfo, successMessage: String) {
        val bounds = Rect()
        node.getBoundsInScreen(bounds)
        if (bounds.isEmpty) { AutomationStore.appendLog(this, "El campo no tiene coordenadas utilizables."); return }
        dispatchTap(bounds.exactCenterX(), bounds.exactCenterY(), successMessage)
    }

    private fun scheduleDelay(stepIndex: Int, millis: Long) {
        if (delayedStepIndex == stepIndex && delayedRunnable != null) return
        cancelScheduledDelay()
        delayedStepIndex = stepIndex
        AutomationStore.appendLog(this, "Esperando $millis ms.")
        val runnable = Runnable {
            delayedRunnable = null
            delayedStepIndex = null
            if (SequenceStore.isRunning(this) && SequenceStore.currentIndex(this) == stepIndex) completeStep("Espera de $millis ms completada.")
        }
        delayedRunnable = runnable
        handler.postDelayed(runnable, millis)
    }

    private fun cancelScheduledDelay() {
        delayedRunnable?.let(handler::removeCallbacks)
        delayedRunnable = null
        delayedStepIndex = null
    }

    private fun cancelPendingWorkInternal() {
        cancelScheduledDelay()
        pointCaptureRunnable?.let(handler::removeCallbacks)
        pointCaptureRunnable = null
        removePointCaptureOverlay()
    }

    private fun completeStep(message: String, extraSkip: Int = 0, resumeDelayMs: Long = 40L) {
        cancelScheduledDelay()
        val stepNumber = SequenceStore.currentIndex(this) + 1
        AutomationStore.appendLog(this, "Paso $stepNumber OK · $message")
        if (SequenceStore.advanceBy(this, 1 + extraSkip)) handler.postDelayed({ processCurrentStep() }, resumeDelayMs)
        else AutomationStore.appendLog(this, "Secuencia completada.")
    }

    private fun stepNeedsExternalWindow(type: StepType): Boolean {
        return when (type) {
            StepType.WAIT_TEXT,
            StepType.CLICK_TEXT,
            StepType.CLICK_FIELD,
            StepType.INPUT_FIELD,
            StepType.TAP_COORDINATE,
            StepType.BACK,
            StepType.INPUT_TEXT,
            StepType.READ_NUMBER,
            StepType.WRITE_VARIABLE -> true

            StepType.DELAY,
            StepType.OPEN_APP,
            StepType.CALCULATE,
            StepType.IF_NUMERIC -> false
        }
    }

    private fun failStep(message: String) {
        SequenceStore.stop(this)
        AutomationStore.appendLog(this, "ERROR · $message · Secuencia detenida.")
    }

    private fun setNodeText(node: AccessibilityNodeInfo, value: String): Boolean {
        node.performAction(AccessibilityNodeInfo.ACTION_FOCUS)
        val args = Bundle().apply { putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, value) }
        return node.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)
    }

    private fun findField(root: AccessibilityNodeInfo, selector: String): AccessibilityNodeInfo? {
        val target = selector.trim()
        if (target.isBlank()) return null
        val editables = mutableListOf<AccessibilityNodeInfo>()
        val labels = mutableListOf<AccessibilityNodeInfo>()
        val queue = ArrayDeque<AccessibilityNodeInfo>()
        queue.add(root)
        while (queue.isNotEmpty()) {
            val node = queue.removeFirst()
            if (node.isEditable && node.isEnabled) editables += node
            if (nodeMatchesSelector(node, target)) labels += node
            for (i in 0 until node.childCount) node.getChild(i)?.let(queue::addLast)
        }

        editables.firstOrNull { nodeMatchesSelector(it, target, exactOnly = true) }?.let { return it }
        editables.firstOrNull { nodeMatchesSelector(it, target) }?.let { return it }

        labels.forEach { label ->
            label.labelFor?.let { if (it.isEditable && it.isEnabled) return it }
        }

        editables.forEach { field ->
            val labeledBy = field.labeledBy
            if (labeledBy != null && nodeMatchesSelector(labeledBy, target)) return field
        }

        if (labels.isEmpty() || editables.isEmpty()) return null
        var best: AccessibilityNodeInfo? = null
        var bestScore = Long.MAX_VALUE
        labels.forEach { label ->
            val lb = Rect().also(label::getBoundsInScreen)
            editables.forEach { field ->
                val fb = Rect().also(field::getBoundsInScreen)
                val dx = abs(fb.centerX() - lb.centerX()).toLong()
                val dy = abs(fb.centerY() - lb.centerY()).toLong()
                val sameParentBonus = if (field.parent == label.parent) 100000L else 0L
                val score = dx + dy * 2L - sameParentBonus
                if (score < bestScore) { bestScore = score; best = field }
            }
        }
        return best
    }

    private fun nodeMatchesSelector(node: AccessibilityNodeInfo, target: String, exactOnly: Boolean = false): Boolean {
        val candidates = listOfNotNull(
            node.text?.toString(),
            node.hintText?.toString(),
            node.contentDescription?.toString(),
            node.viewIdResourceName
        ).map { it.trim() }.filter { it.isNotBlank() }
        return if (exactOnly) candidates.any { it.equals(target, ignoreCase = true) }
        else candidates.any { it.equals(target, ignoreCase = true) || it.contains(target, ignoreCase = true) }
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
                val partial = text?.contains(normalizedTarget, ignoreCase = true) == true || description?.contains(normalizedTarget, ignoreCase = true) == true
                if (partial) partialMatch = node
            }
            for (i in 0 until node.childCount) node.getChild(i)?.let(queue::addLast)
        }
        return partialMatch
    }

    private fun findNumberNearLabel(root: AccessibilityNodeInfo, label: String): BigDecimal? {
        val entries = mutableListOf<Pair<AccessibilityNodeInfo, String>>()
        flattenReadableNodes(root, entries)
        val matchIndex = entries.indexOfFirst { (_, text) -> text.equals(label, ignoreCase = true) || text.contains(label, ignoreCase = true) }
        if (matchIndex < 0) return null
        val matchedNode = entries[matchIndex].first
        val parent = matchedNode.parent
        if (parent != null) {
            val localEntries = mutableListOf<Pair<AccessibilityNodeInfo, String>>()
            flattenReadableNodes(parent, localEntries)
            val localIndex = localEntries.indexOfFirst { it.first == matchedNode }
            if (localIndex >= 0) for (i in (localIndex + 1)..minOf(localIndex + 8, localEntries.lastIndex)) NumericEngine.extractNumber(localEntries[i].second)?.let { return it }
        }
        for (i in (matchIndex + 1)..minOf(matchIndex + 14, entries.lastIndex)) NumericEngine.extractNumber(entries[i].second)?.let { return it }
        return null
    }

    private fun flattenReadableNodes(node: AccessibilityNodeInfo, output: MutableList<Pair<AccessibilityNodeInfo, String>>) {
        val text = node.text?.toString()?.trim().orEmpty()
        val description = node.contentDescription?.toString()?.trim().orEmpty()
        val readable = when { text.isNotBlank() -> text; description.isNotBlank() -> description; else -> "" }
        if (readable.isNotBlank()) output += node to readable
        for (i in 0 until node.childCount) node.getChild(i)?.let { flattenReadableNodes(it, output) }
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
            for (i in 0 until node.childCount) node.getChild(i)?.let(queue::addLast)
        }
        return firstEditable
    }
}
