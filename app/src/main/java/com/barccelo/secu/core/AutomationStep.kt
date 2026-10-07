package com.barccelo.secu.core

import java.util.UUID

enum class StepType(val label: String) {
    WAIT_TEXT("Esperar texto"),
    CLICK_TEXT("Pulsar texto"),
    DELAY("Esperar tiempo"),
    BACK("Volver atrás"),
    INPUT_TEXT("Escribir texto"),
    OPEN_APP("Abrir app")
}

data class AutomationStep(
    val id: String = UUID.randomUUID().toString(),
    val type: StepType,
    val value: String = ""
) {
    fun description(): String {
        return when (type) {
            StepType.WAIT_TEXT -> "Esperar hasta ver “$value”"
            StepType.CLICK_TEXT -> "Pulsar “$value”"
            StepType.DELAY -> "Esperar ${value.toLongOrNull()?.coerceAtLeast(0L) ?: 0L} ms"
            StepType.BACK -> "Volver atrás"
            StepType.INPUT_TEXT -> "Escribir “$value”"
            StepType.OPEN_APP -> "Abrir app: $value"
        }
    }
}
