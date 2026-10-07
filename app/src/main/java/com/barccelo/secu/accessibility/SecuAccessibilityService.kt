package com.barccelo.secu.accessibility

import android.accessibilityservice.AccessibilityService
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import com.barccelo.secu.core.AutomationStore
import java.util.ArrayDeque

class SecuAccessibilityService : AccessibilityService() {

    private var lastPackageName: String? = null

    override fun onServiceConnected() {
        super.onServiceConnected()
        AutomationStore.appendLog(this, "Servicio de accesibilidad conectado.")
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

        val target = AutomationStore.pendingText(this) ?: return

        if (eventPackage == packageName) return

        val root = rootInActiveWindow ?: return
        val candidate = findBestMatch(root, target) ?: return
        val clickable = findClickableNode(candidate)

        if (clickable == null) {
            AutomationStore.appendLog(this, "Encontrado “$target”, pero no hay un nodo pulsable.")
            return
        }

        val clicked = clickable.performAction(AccessibilityNodeInfo.ACTION_CLICK)
        if (clicked) {
            AutomationStore.clearPending(this)
            AutomationStore.appendLog(
                this,
                "OK: “$target” encontrado y pulsado en ${eventPackage ?: "app desconocida"}."
            )
        } else {
            AutomationStore.appendLog(this, "No se pudo pulsar “$target”.")
        }
    }

    override fun onInterrupt() {
        AutomationStore.appendLog(this, "Servicio de accesibilidad interrumpido.")
    }

    private fun findBestMatch(root: AccessibilityNodeInfo, target: String): AccessibilityNodeInfo? {
        val normalizedTarget = target.trim()
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

            for (index in 0 until node.childCount) {
                node.getChild(index)?.let(queue::addLast)
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
}
