package com.alexdremov.notate.ocr

import com.alexdremov.notate.ocr.index.OcrSearchNormalizer
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class OcrSearchNormalizerTest {
    @Test
    fun normalizesWidthCaseAndWhitespace() {
        assertThat(OcrSearchNormalizer.normalize("  Ｈｅｌｌｏ   WORLD ")).isEqualTo("hello world")
    }

    @Test
    fun englishQueryUsesSafePrefixTerms() {
        assertThat(OcrSearchNormalizer.ftsQuery("hand writ")).isEqualTo("\"hand\"* \"writ\"*")
    }

    @Test
    fun chineseTokensContainUnigramsAndBigrams() {
        assertThat(OcrSearchNormalizer.tokens("手写笔记")).containsAtLeast("手", "写", "笔", "记", "手写", "写笔", "笔记")
        assertThat(OcrSearchNormalizer.ftsQuery("写笔")).isEqualTo("\"z5199\" \"z7b14\" \"z51997b14\"")
    }

    @Test
    fun astralChineseUsesWholeCodePoints() {
        val first = String(Character.toChars(0x20000))
        val second = String(Character.toChars(0x20001))

        assertThat(OcrSearchNormalizer.tokens(first + second)).containsExactly(first, second, first + second).inOrder()
        assertThat(OcrSearchNormalizer.ftsQuery(first)).isEqualTo("\"z20000\"")
    }

    @Test
    fun mixedQueriesAreSplitIntoIndependentSegments() {
        assertThat(OcrSearchNormalizer.querySegments("note 中 文").map { it.value })
            .containsExactly("note", "中", "文")
            .inOrder()
    }

    @Test
    fun punctuationCannotEscapeFtsQuery() {
        assertThat(OcrSearchNormalizer.ftsQuery("hello OR title:evil")).isEqualTo("\"hello\"* \"or\"* \"title\"* \"evil\"*")
    }
}
