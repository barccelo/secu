package com.barccelo.secu.accessibility

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import com.barccelo.secu.core.AutomationStore
import com.barccelo.secu.core.SequenceStore
import com.barccelo.secu.core.StepType
import java.util.ArrayDeque

class SecuAccessibilityService : AccessibilityService() {

    companion object {
        @Volatile
        private var instance: SecuAccessibilityService? = null

        fun isConnected(): Boolean = instance != null

        fun requestProcess() {
            instance?.kick()
        }

        fun cancelPendingWork() {
            instance?.cancelScheduledDelay()
        }
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
            AutomationStore.appendLog(
                this,
                "Pantalla activa: $eventPackage · ${AccessibilityEvent.eventTypeToString(event.eventType)}"
            )
        }

        if (SequenceStore.isRunning(this)) {
            kick()
        }
    }

    override fun onInterrupt() {
        AutomationStore.appendLog(this, "Servicio de accesibilidad interrumpido.")
    }

    override fun onDestroy() {
        cancelScheduledDelay()
        if (instance === this) instance = null
        super.onDestroy()
    }

    private fun kick() {
        handler.post { processCurrentStep() }
    }

    private fun processCurrentStep() {
        if (processing || !SequenceStore.isRunning(this)) return
        val step = SequenceStore.currentStep(this) ?: return
        val index = SequenceStore.currentIndex(this)

        processing = true
        try {
            when (step.type) {
                StepType.WAIT_TEXT -> {
                    val root = rootInActiveWindow ?: return
                    val found = findBestMatch(root, step.value)
                    if (found != null) {
                        completeStep("Texto encontrado: “${step.value}”.")
                    }
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
                    if (performGlobalAction(GLOBAL_ACTION_BACK)) {
                        completeStep("Acción Atrás ejecutada.")
                    } else {
                        AutomationStore.appendLog(this, "No se pudo ejecutar Atrás.")
                    }
                }

                StepType.INPUT_TEXT -> {
                    val root = rootInActiveWindow ?: return
                    val editable = findEditableNode(root) ?: return
                    editable.performAction(AccessibilityNodeInfo.ACTION_FOCUS)
                    val args = Bundle().apply {
                        putCharSequence(
                            AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE,
                            step.value
                        )
                    }
                    if (editable.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)) {
                        completeStep("Texto escrito.")
                    } else {
                        AutomationStore.appendLog(this, "No se pudo escribir en el campo editable.")
                    }
                }

                StepType.OPEN_APP -> {
                    val packageNameToOpen = step.value.trim()
                    val launchIntent = packageManager.getLaunchIntentForPackage(packageNameToOpen)
                    if (launchIntent == null) {
                        AutomationStore.appendLog(this, "No se encontró una app lanzable para $packageNameToOpen.")
                        return
                    }

                    launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    runCatching { startActivity(launchIntent) }
                        .onSuccess { completeStep("App abierta: $packageNameToOpen.") }
                        .onFailure { error ->
                            AutomationStore.appendLog(
                                this,
                                "No se pudo abrir $packageNameToOpen: ${error.message ?: "error desconocido"}."
                            )
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

    private fun completeStep(message: String) {
        cancelScheduledDelay()
        val stepNumber = SequenceStore.currentIndex(this) + 1
        AutomationStore.appendLog(this, "Paso $stepNumber OK · $message")

        if (SequenceStore.advance(this)) {
            handler.postDelayed({ processCurrentStep() }, 250L)
        } else {
            AutomationStore.appendLog(this, "Secuencia completada.")
        }
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

            val exact =
                text.equals(normalizedTarget, ignoreCase = true) ||
                    description.equals(normalizedTarget, ignoreCase = true)

            if (exact) return node

            if (partialMatch == null) {
                val partial =
                    text?.contains(normalizedTarget, ignoreCase = true) == true ||
                        description?.contains(normalizedTarget, ignoreCase = true) == true
                if (partial) partialMatch = node
            }

            for (childIndex in 0 until node.childCount) {
                node.getChild(childIndex)?.let(queue::addLast)
            }
        }

        return partialMatch
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

            for (childIndex in 0 until node.childCount) {
                node.getChild(childIndex)?.let(queue::addLast)
            }
        }

        return firstEditable
    }
}
