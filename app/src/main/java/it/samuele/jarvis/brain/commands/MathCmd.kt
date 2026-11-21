package it.samuele.jarvis.brain.commands

import android.content.Context
import it.samuele.jarvis.brain.Command
import it.samuele.jarvis.brain.Reply
import it.samuele.jarvis.brain.Utterance
import it.samuele.jarvis.util.Text
import kotlin.math.abs
import kotlin.math.pow
import kotlin.math.sqrt

/** Calcoli a voce: "quanto fa 12 per 7", "radice quadrata di 81", "il 20 percento di 150". */
object MathCmd : Command {
    override val name = "calcoli"

    override fun handle(u: Utterance, ctx: Context): Reply? {
        var s = Text.numbers(u.n)
        val trigger = Regex("^(quanto fa|quanto e|calcola|quanto viene|fammi il calcolo|risultato di)\\s+").find(s)
        if (trigger != null) s = s.substring(trigger.range.last + 1)
        else if (!s.matches(Regex("^[0-9 .,+\\-*/x()%^a-z]*[0-9][0-9 .,+\\-*/x()%^a-z]*$")) || !s.any { it.isDigit() }) return null

        Regex("(\\d+(?:[.,]\\d+)?) ?(?:percento|per cento|%) di (\\d+(?:[.,]\\d+)?)").find(s)?.let {
            val r = num(it.groupValues[1]) * num(it.groupValues[2]) / 100.0
            return Reply("Fa ${fmt(r)}.")
        }
        val expr = s.replace("radice quadrata di", " r ").replace("radice di", " r ")
            .replace("elevato alla", "^").replace("elevato a", "^").replace("alla seconda", "^2").replace("al quadrato", "^2")
            .replace("al cubo", "^3").replace("diviso per", "/").replace("diviso", "/").replace("fratto", "/")
            .replace("piu", "+").replace("meno", "-").replace(" per ", " * ").replace(" x ", " * ")
            .replace("virgola", ".").replace(",", ".").replace("aperta parentesi", "(").replace("chiusa parentesi", ")")
        val clean = expr.replace(Regex("[^0-9.+\\-*/^()r ]"), " ")
        if (!clean.any { it.isDigit() } || !clean.any { it in "+-*/^r" }) return null
        val v = try { Parser(clean.replace(" ", "")).parse() } catch (e: Exception) { return null }
        if (v.isNaN() || v.isInfinite()) return Reply("Il risultato non è definito.")
        return Reply("Fa ${fmt(v)}.")
    }

    private fun num(s: String) = s.replace(',', '.').toDouble()

    private fun fmt(v: Double): String {
        if (abs(v - Math.round(v)) < 1e-9 && abs(v) < 1e15) return Math.round(v).toString()
        return String.format(java.util.Locale.ITALIAN, "%.4f", v).trimEnd('0').trimEnd(',')
    }

    /** Piccolo analizzatore di espressioni: + - * / ^ parentesi e radice (r). */
    private class Parser(val s: String) {
        var i = 0
        fun parse(): Double { val v = expr(); if (i != s.length) throw IllegalArgumentException(); return v }
        fun expr(): Double {
            var v = term()
            while (i < s.length && (s[i] == '+' || s[i] == '-')) { val op = s[i++]; val r = term(); v = if (op == '+') v + r else v - r }
            return v
        }
        fun term(): Double {
            var v = power()
            while (i < s.length && (s[i] == '*' || s[i] == '/')) { val op = s[i++]; val r = power(); v = if (op == '*') v * r else v / r }
            return v
        }
        fun power(): Double {
            val b = unary()
            if (i < s.length && s[i] == '^') { i++; return b.pow(power()) }
            return b
        }
        fun unary(): Double {
            if (i < s.length && s[i] == '-') { i++; return -unary() }
            if (i < s.length && s[i] == '+') { i++; return unary() }
            if (i < s.length && s[i] == 'r') { i++; return sqrt(unary()) }
            return atom()
        }
        fun atom(): Double {
            if (i < s.length && s[i] == '(') { i++; val v = expr(); if (i < s.length && s[i] == ')') i++; return v }
            val st = i
            while (i < s.length && (s[i].isDigit() || s[i] == '.')) i++
            if (st == i) throw IllegalArgumentException()
            return s.substring(st, i).toDouble()
        }
    }
}
