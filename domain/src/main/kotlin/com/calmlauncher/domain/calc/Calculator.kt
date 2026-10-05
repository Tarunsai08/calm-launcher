package com.calmlauncher.domain.calc

import java.math.BigDecimal
import java.math.MathContext
import java.math.RoundingMode
import kotlin.math.pow

/**
 * A tiny, safe arithmetic evaluator for the search bar ("12*3+4", "(2+3)^2", "15% of 80").
 * Supports + - * / % ^, parentheses, unary minus, decimals, and "x"/"×"/"÷" symbols.
 * Returns null for anything that is not clearly an arithmetic expression.
 */
object Calculator {

    private val allowed = Regex("^[0-9+\\-*/%^().,\\s×÷x]+$")
    private val hasOperator = Regex("[+\\-*/%^×÷x]")

    fun looksLikeExpression(input: String): Boolean {
        val t = input.trim()
        if (t.length < 3) return false
        if (!allowed.matches(t)) return false
        if (!t.any { it.isDigit() }) return false
        // Needs an operator between operands, not just a leading minus.
        return hasOperator.containsMatchIn(t.drop(1))
    }

    fun evaluate(input: String): Double? {
        val percentOf = Regex("^\\s*([0-9.]+)\\s*%\\s*of\\s*([0-9.]+)\\s*$", RegexOption.IGNORE_CASE).find(input)
        if (percentOf != null) {
            val p = percentOf.groupValues[1].toDoubleOrNull() ?: return null
            val v = percentOf.groupValues[2].toDoubleOrNull() ?: return null
            return p / 100.0 * v
        }
        if (!looksLikeExpression(input)) return null
        return try {
            val parser = Parser(tokenize(input))
            val value = parser.parseExpression()
            if (!parser.atEnd()) null else value.takeIf { it.isFinite() }
        } catch (_: IllegalArgumentException) {
            null
        } catch (_: ArithmeticException) {
            null
        }
    }

    /** Formats a result without trailing zeros, up to 10 significant decimals. */
    fun format(value: Double): String {
        if (value == value.toLong().toDouble() && kotlin.math.abs(value) < 1e15) return value.toLong().toString()
        val bd = BigDecimal(value).round(MathContext(12, RoundingMode.HALF_UP)).stripTrailingZeros()
        return bd.toPlainString()
    }

    private sealed interface Token {
        data class Num(val value: Double) : Token
        data class Op(val symbol: Char) : Token
        data object LParen : Token
        data object RParen : Token
    }

    private fun tokenize(input: String): List<Token> {
        val tokens = ArrayList<Token>()
        var i = 0
        val s = input.replace(',', '.')
        while (i < s.length) {
            val c = s[i]
            when {
                c.isWhitespace() -> i++
                c.isDigit() || c == '.' -> {
                    val start = i
                    while (i < s.length && (s[i].isDigit() || s[i] == '.')) i++
                    val number = s.substring(start, i).toDoubleOrNull()
                        ?: throw IllegalArgumentException("Bad number")
                    tokens += Token.Num(number)
                }
                c == '(' -> { tokens += Token.LParen; i++ }
                c == ')' -> { tokens += Token.RParen; i++ }
                c == '×' || c == 'x' -> { tokens += Token.Op('*'); i++ }
                c == '÷' -> { tokens += Token.Op('/'); i++ }
                c in "+-*/%^" -> { tokens += Token.Op(c); i++ }
                else -> throw IllegalArgumentException("Unexpected $c")
            }
        }
        return tokens
    }

    /** Recursive-descent parser: expr := term (('+'|'-') term)*, etc. */
    private class Parser(private val tokens: List<Token>) {
        private var pos = 0

        fun atEnd() = pos >= tokens.size

        private fun peek(): Token? = tokens.getOrNull(pos)

        fun parseExpression(): Double {
            var value = parseTerm()
            while (true) {
                val t = peek()
                if (t is Token.Op && (t.symbol == '+' || t.symbol == '-')) {
                    pos++
                    val rhs = parseTerm()
                    value = if (t.symbol == '+') value + rhs else value - rhs
                } else {
                    return value
                }
            }
        }

        private fun parseTerm(): Double {
            var value = parseFactor()
            while (true) {
                val t = peek()
                if (t is Token.Op && (t.symbol == '*' || t.symbol == '/')) {
                    pos++
                    val rhs = parseFactor()
                    if (t.symbol == '/') {
                        if (rhs == 0.0) throw ArithmeticException("Division by zero")
                        value /= rhs
                    } else {
                        value *= rhs
                    }
                } else if (t is Token.Op && t.symbol == '%') {
                    pos++
                    // "50%" alone means 0.5; "10 % 3" means modulo.
                    val next = peek()
                    if (next is Token.Num || next is Token.LParen) {
                        val rhs = parseFactor()
                        if (rhs == 0.0) throw ArithmeticException("Modulo by zero")
                        value %= rhs
                    } else {
                        value /= 100.0
                    }
                } else {
                    return value
                }
            }
        }

        private fun parseFactor(): Double {
            val base = parseUnary()
            val t = peek()
            if (t is Token.Op && t.symbol == '^') {
                pos++
                val exponent = parseFactor() // right-associative
                return base.pow(exponent)
            }
            return base
        }

        private fun parseUnary(): Double {
            val t = peek()
            if (t is Token.Op && t.symbol == '-') {
                pos++
                return -parseUnary()
            }
            if (t is Token.Op && t.symbol == '+') {
                pos++
                return parseUnary()
            }
            return parsePrimary()
        }

        private fun parsePrimary(): Double {
            return when (val t = peek()) {
                is Token.Num -> {
                    pos++
                    t.value
                }
                Token.LParen -> {
                    pos++
                    val v = parseExpression()
                    require(peek() == Token.RParen) { "Missing )" }
                    pos++
                    v
                }
                else -> throw IllegalArgumentException("Unexpected token")
            }
        }
    }
}
