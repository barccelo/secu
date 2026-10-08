package com.barccelo.secu.core

import java.math.BigDecimal
import java.math.MathContext
import java.math.RoundingMode
import kotlin.math.max

object NumericEngine {
    private val numberCandidate = Regex("[-+]?\\d[\\d\\s.,]*")
    private val comparisonOperators = listOf(">=", "<=", "==", "!=", ">", "<")

    data class ReadSpec(val label: String, val variable: String)
    data class AssignmentSpec(val variable: String, val expression: String)
    data class ConditionalSpec(
        val condition: String,
        val trueOffset: Int,
        val falseOffset: Int
    )

    fun parseReadSpec(raw: String): ReadSpec? {
        val parts = raw.split("->", limit = 2)
        if (parts.size != 2) return null
        val label = parts[0].trim()
        val variable = parts[1].trim()
        if (label.isBlank() || !VariableStore.isValidName(variable)) return null
        return ReadSpec(label, variable)
    }

    fun parseAssignment(raw: String): AssignmentSpec? {
        val parts = raw.split("=", limit = 2)
        if (parts.size != 2) return null
        val variable = parts[0].trim()
        val expression = parts[1].trim()
        if (!VariableStore.isValidName(variable) || expression.isBlank()) return null
        return AssignmentSpec(variable, expression)
    }

    fun parseConditional(raw: String): ConditionalSpec? {
        val question = raw.indexOf("?")
        val colon = raw.indexOf(":", startIndex = question + 1)
        if (question <= 0 || colon <= question) return null

        val condition = raw.substring(0, question).trim()
        val trueOffset = raw.substring(question + 1, colon).trim().toIntOrNull() ?: return null
        val falseOffset = raw.substring(colon + 1).trim().toIntOrNull() ?: return null
        if (condition.isBlank()) return null
        return ConditionalSpec(condition, trueOffset, falseOffset)
    }

    fun extractNumber(text: String): BigDecimal? {
        val candidate = numberCandidate.find(text)?.value ?: return null
        return parseFlexibleNumber(candidate)
    }

    fun parseFlexibleNumber(raw: String): BigDecimal? {
        var value = raw.trim()
            .replace("\u00A0", "")
            .replace(" ", "")

        if (value.isBlank()) return null

        val commaCount = value.count { it == ',' }
        val dotCount = value.count { it == '.' }
        val lastComma = value.lastIndexOf(',')
        val lastDot = value.lastIndexOf('.')
        val lastSeparator = max(lastComma, lastDot)

        val useDecimalSeparator = when {
            lastSeparator < 0 -> false
            commaCount > 0 && dotCount > 0 -> true
            commaCount + dotCount == 1 -> {
                val trailing = value.length - lastSeparator - 1
                trailing in 1..6 && trailing != 3
            }
            else -> {
                val trailing = value.length - lastSeparator - 1
                trailing in 1..2
            }
        }

        value = if (useDecimalSeparator) {
            val integerPart = value.substring(0, lastSeparator)
                .replace(",", "")
                .replace(".", "")
            val fractionPart = value.substring(lastSeparator + 1)
                .replace(",", "")
                .replace(".", "")
            "$integerPart.$fractionPart"
        } else {
            value.replace(",", "").replace(".", "")
        }

        return value.toBigDecimalOrNull()
    }

    fun evaluateExpression(
        expression: String,
        resolver: (String) -> BigDecimal?
    ): BigDecimal? {
        return runCatching { ExpressionParser(expression, resolver).parse() }.getOrNull()
    }

    fun evaluateCondition(
        condition: String,
        resolver: (String) -> BigDecimal?
    ): Boolean? {
        val operator = comparisonOperators.firstOrNull { condition.contains(it) } ?: return null
        val parts = condition.split(operator, limit = 2)
        if (parts.size != 2) return null
        val left = evaluateExpression(parts[0].trim(), resolver) ?: return null
        val right = evaluateExpression(parts[1].trim(), resolver) ?: return null
        val comparison = left.compareTo(right)

        return when (operator) {
            ">=" -> comparison >= 0
            "<=" -> comparison <= 0
            "==" -> comparison == 0
            "!=" -> comparison != 0
            ">" -> comparison > 0
            "<" -> comparison < 0
            else -> null
        }
    }

    private class ExpressionParser(
        private val source: String,
        private val resolver: (String) -> BigDecimal?
    ) {
        private var index = 0
        private val mathContext = MathContext.DECIMAL128

        fun parse(): BigDecimal {
            val value = parseExpression()
            skipWhitespace()
            require(index == source.length) { "Expresión inválida cerca de: ${source.substring(index)}" }
            return value
        }

        private fun parseExpression(): BigDecimal {
            var value = parseTerm()
            while (true) {
                skipWhitespace()
                value = when {
                    consume('+') -> value.add(parseTerm(), mathContext)
                    consume('-') -> value.subtract(parseTerm(), mathContext)
                    else -> return value
                }
            }
        }

        private fun parseTerm(): BigDecimal {
            var value = parseFactor()
            while (true) {
                skipWhitespace()
                value = when {
                    consume('*') -> value.multiply(parseFactor(), mathContext)
                    consume('/') -> {
                        val divisor = parseFactor()
                        require(divisor.compareTo(BigDecimal.ZERO) != 0) { "División entre cero" }
                        value.divide(divisor, 12, RoundingMode.HALF_UP).stripTrailingZeros()
                    }
                    else -> return value
                }
            }
        }

        private fun parseFactor(): BigDecimal {
            skipWhitespace()
            if (consume('+')) return parseFactor()
            if (consume('-')) return parseFactor().negate()

            if (consume('(')) {
                val value = parseExpression()
                skipWhitespace()
                require(consume(')')) { "Falta )" }
                return value
            }

            if (index < source.length && source[index].isDigit()) {
                return parseLiteral()
            }

            val identifier = parseIdentifier()
            require(identifier.isNotBlank()) { "Se esperaba número o variable" }
            skipWhitespace()

            if (consume('(')) {
                val first = parseExpression()
                skipWhitespace()
                require(consume(',')) { "Falta coma en función" }
                val second = parseExpression()
                skipWhitespace()
                require(consume(')')) { "Falta ) en función" }
                return when (identifier.lowercase()) {
                    "min" -> if (first <= second) first else second
                    "max" -> if (first >= second) first else second
                    else -> error("Función desconocida: $identifier")
                }
            }

            return resolver(identifier) ?: error("Variable desconocida: $identifier")
        }

        private fun parseLiteral(): BigDecimal {
            val start = index
            while (index < source.length && (source[index].isDigit() || source[index] == '.')) {
                index++
            }
            return source.substring(start, index).toBigDecimal()
        }

        private fun parseIdentifier(): String {
            skipWhitespace()
            val start = index
            if (index < source.length && (source[index].isLetter() || source[index] == '_')) {
                index++
                while (index < source.length && (source[index].isLetterOrDigit() || source[index] == '_')) {
                    index++
                }
            }
            return source.substring(start, index)
        }

        private fun skipWhitespace() {
            while (index < source.length && source[index].isWhitespace()) index++
        }

        private fun consume(expected: Char): Boolean {
            if (index < source.length && source[index] == expected) {
                index++
                return true
            }
            return false
        }
    }
}
