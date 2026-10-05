package com.calmlauncher.domain.search

import java.text.Normalizer
import java.util.Locale

/** Case- and diacritic-insensitive text normalisation used by search. */
object TextNormalizer {
    private val combiningMarks = Regex("\\p{Mn}+")
    private val separators = Regex("[\\s\\-_.:,/&+()\\[\\]'’!?|]+")
    private val camelHump = Regex("(?<=[a-z0-9])(?=[A-Z])")
    private val whitespace = Regex("\\s+")

    /** Lower-cases, strips diacritics, and collapses whitespace. "Café  Ünö" -> "cafe uno". */
    fun normalize(input: String): String {
        if (input.isEmpty()) return input
        return stripMarks(input)
            .lowercase(Locale.ROOT)
            .trim()
            .replace(whitespace, " ")
    }

    private fun stripMarks(input: String): String {
        val decomposed = Normalizer.normalize(input, Normalizer.Form.NFD)
        return combiningMarks.replace(decomposed, "")
            .replace("ß", "ss")
            .replace('ø', 'o')
            .replace('Ø', 'O')
            .replace('ł', 'l')
            .replace('Ł', 'L')
            .replace('đ', 'd')
            .replace('Đ', 'D')
    }

    /** Normalised words: "YouTube Music" -> [youtube, music]. */
    fun words(original: String): List<String> =
        separators.split(normalize(original)).filter { it.isNotEmpty() }

    /**
     * Every position a user might start typing from: whole words plus camel-case humps.
     * "YouTube Music" -> [youtube, tube, music].
     */
    fun wordStarts(original: String): List<String> {
        val result = LinkedHashSet<String>()
        val rawWords = separators.split(stripMarks(original).trim()).filter { it.isNotEmpty() }
        for (raw in rawWords) {
            result += raw.lowercase(Locale.ROOT)
            val humps = camelHump.split(raw)
            if (humps.size > 1) {
                var offset = humps[0].length
                for (i in 1 until humps.size) {
                    result += raw.substring(offset).lowercase(Locale.ROOT)
                    offset += humps[i].length
                }
            }
        }
        return result.toList()
    }

    /** First letter of each word: "YouTube Music" -> "ym". */
    fun initials(original: String): String = words(original).mapNotNull { it.firstOrNull() }.joinToString("")
}
