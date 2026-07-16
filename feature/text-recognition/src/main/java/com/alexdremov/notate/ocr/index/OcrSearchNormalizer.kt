package com.alexdremov.notate.ocr.index

import java.text.Normalizer
import java.util.Locale

object OcrSearchNormalizer {
    data class QuerySegment(
        val value: String,
        val isCjk: Boolean,
    )

    fun normalize(value: String): String =
        Normalizer
            .normalize(value, Normalizer.Form.NFKC)
            .lowercase(Locale.ROOT)
            .replace(Regex("\\s+"), " ")
            .trim()

    fun tokens(value: String): List<String> {
        val result = linkedSetOf<String>()
        querySegments(value).forEach { segment ->
            if (segment.isCjk) {
                val codePoints = segment.value.codePoints().toArray().map(::codePointString)
                result.addAll(codePoints)
                codePoints.zipWithNext { first, second -> first + second }.forEach(result::add)
            } else {
                result.add(segment.value)
            }
        }
        return result.toList()
    }

    fun searchableTokens(value: String): String = tokens(value).joinToString(" ", transform = ::ftsToken)

    fun containsCjk(value: String): Boolean = normalize(value).codePoints().anyMatch(::isCjk)

    fun querySegments(value: String): List<QuerySegment> {
        val segments = mutableListOf<QuerySegment>()
        Regex("[\\p{L}\\p{N}]+").findAll(normalize(value)).forEach { match ->
            val codePoints = match.value.codePoints().toArray()
            var start = 0
            while (start < codePoints.size) {
                val cjk = isCjk(codePoints[start])
                var end = start + 1
                while (end < codePoints.size && isCjk(codePoints[end]) == cjk) end++
                segments += QuerySegment(codePoints.copyOfRange(start, end).joinToString("", transform = ::codePointString), cjk)
                start = end
            }
        }
        return segments
    }

    fun ftsQuery(value: String): String =
        // Whitespace is FTS4's portable implicit-AND syntax. Some Android SQLite
        // builds parse the enhanced `AND` operator as a literal token.
        tokens(value).joinToString(" ") { token ->
            val encoded = ftsToken(token)
            val escaped = encoded.replace("\"", "\"\"")
            if (token.codePoints().anyMatch(::isCjk)) "\"$escaped\"" else "\"$escaped\"*"
        }

    private fun ftsToken(token: String): String =
        if (token.codePoints().anyMatch(::isCjk)) {
            "z" + token.codePoints().toArray().joinToString("") { it.toString(16).padStart(4, '0') }
        } else {
            token
        }

    private fun codePointString(codePoint: Int): String = String(Character.toChars(codePoint))

    private fun isCjk(codePoint: Int): Boolean = Character.UnicodeScript.of(codePoint) in CJK_SCRIPTS

    private val CJK_SCRIPTS =
        setOf(
            Character.UnicodeScript.HAN,
            Character.UnicodeScript.HIRAGANA,
            Character.UnicodeScript.KATAKANA,
            Character.UnicodeScript.HANGUL,
        )
}
