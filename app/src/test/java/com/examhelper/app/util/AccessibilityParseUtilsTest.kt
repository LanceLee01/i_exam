package com.examhelper.app.util

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class AccessibilityParseUtilsTest {

    // ── parseAnswerPairs ──

    @Test
    fun `parseAnswerPairs extracts single selection`() {
        val result = parseAnswerPairs("[1] A")
        assertEquals(listOf(1 to listOf("A")), result)
    }

    @Test
    fun `parseAnswerPairs extracts single selection with Chinese bracket`() {
        val result = parseAnswerPairs("【1】A")
        assertEquals(listOf(1 to listOf("A")), result)
    }

    @Test
    fun `parseAnswerPairs extracts multiple selections`() {
        val result = parseAnswerPairs("[1] A B C")
        assertEquals(listOf(1 to listOf("A", "B", "C")), result)
    }

    @Test
    fun `parseAnswerPairs extracts Chinese-separated selections`() {
        val result = parseAnswerPairs("[1] A、B、C")
        assertEquals(listOf(1 to listOf("A", "B", "C")), result)
    }

    @Test
    fun `parseAnswerPairs extracts 正确`() {
        val result = parseAnswerPairs("[1] 正确")
        assertEquals(listOf(1 to listOf("正确")), result)
    }

    @Test
    fun `parseAnswerPairs extracts 错误`() {
        val result = parseAnswerPairs("[1] 错误")
        assertEquals(listOf(1 to listOf("错误")), result)
    }

    @Test
    fun `parseAnswerPairs extracts 对`() {
        val result = parseAnswerPairs("[1] 对")
        assertEquals(listOf(1 to listOf("对")), result)
    }

    @Test
    fun `parseAnswerPairs extracts 错`() {
        val result = parseAnswerPairs("[1] 错")
        assertEquals(listOf(1 to listOf("错")), result)
    }

    @Test
    fun `parseAnswerPairs extracts multiple questions`() {
        val result = parseAnswerPairs("[1] A[2] B C")
        assertEquals(listOf(1 to listOf("A"), 2 to listOf("B", "C")), result)
    }

    @Test
    fun `parseAnswerPairs returns empty for no match`() {
        val result = parseAnswerPairs("no answer here")
        assertEquals(emptyList<Pair<Int, List<String>>>(), result)
    }

    @Test
    fun `parseAnswerPairs handles letters`() {
        val result = parseAnswerPairs("[1] A B")
        assertEquals(listOf(1 to listOf("A", "B")), result)
    }

    // ── countOptionsPerQuestion ──

    @Test
    fun `countOptionsPerQuestion counts 3-option question`() {
        val text = """
            1. Question one?
            A. Option A
            B. Option B
            C. Option C
        """.trimIndent()
        assertEquals(listOf(3), countOptionsPerQuestion(text))
    }

    @Test
    fun `countOptionsPerQuestion counts multiple questions`() {
        val text = """
            1. Question one?
            A. Option A
            B. Option B
            2. Question two?
            A. Option A
            B. Option B
            C. Option C
        """.trimIndent()
        assertEquals(listOf(2, 3), countOptionsPerQuestion(text))
    }

    @Test
    fun `countOptionsPerQuestion handles true-false with 正确`() {
        val text = """
            1. Is this true?
            正确
            错误
        """.trimIndent()
        assertEquals(listOf(2), countOptionsPerQuestion(text))
    }

    @Test
    fun `countOptionsPerQuestion handles mix of text and options`() {
        val text = """
            1. Question here
            Some description text
            A. First
            B. Second
            C. Third
        """.trimIndent()
        assertEquals(listOf(3), countOptionsPerQuestion(text))
    }

    @Test
    fun `countOptionsPerQuestion returns empty for no questions`() {
        val text = "Just some text without any questions"
        assertEquals(emptyList<Int>(), countOptionsPerQuestion(text))
    }

    @Test
    fun `countOptionsPerQuestion uses Japanese bracket separator`() {
        val text = """
            1. Question?
            A) Option A
            B) Option B
        """.trimIndent()
        assertEquals(listOf(2), countOptionsPerQuestion(text))
    }

    // ── matchesSelection ──

    @Test
    fun `matchesSelection matches letter followed by dot`() {
        assertTrue(matchesSelection("A. Option text", "A"))
    }

    @Test
    fun `matchesSelection matches letter followed by paren`() {
        assertTrue(matchesSelection("B) Option text", "B"))
    }

    @Test
    fun `matchesSelection matches bare letter`() {
        assertTrue(matchesSelection("A", "A"))
    }

    @Test
    fun `matchesSelection rejects wrong letter`() {
        assertFalse(matchesSelection("B. Option text", "A"))
    }

    @Test
    fun `matchesSelection matches 正确`() {
        assertTrue(matchesSelection("正确", "正确"))
    }

    @Test
    fun `matchesSelection matches 错误`() {
        assertTrue(matchesSelection("错误", "错误"))
    }

    @Test
    fun `matchesSelection any tf value matches for tf selection`() {
        assertTrue(matchesSelection("正确", "错误"))
    }

    @Test
    fun `matchesSelection does plain text fallback`() {
        assertTrue(matchesSelection("some option text", "option"))
    }

    @Test
    fun `matchesSelection is case insensitive for letters`() {
        assertTrue(matchesSelection("a. option", "A"))
    }

    @Test
    fun `matchesSelection matches 对`() {
        assertTrue(matchesSelection("对", "对"))
    }

    @Test
    fun `matchesSelection matches 错`() {
        assertTrue(matchesSelection("错", "错"))
    }
}
