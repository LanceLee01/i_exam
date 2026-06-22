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

    @Test
    fun `parseAnswerPairs extracts from multi-line combined format`() {
        val result = parseAnswerPairs("[1] A\n[2] B C\n[3] 正确")
        assertEquals(listOf(1 to listOf("A"), 2 to listOf("B", "C"), 3 to listOf("正确")), result)
    }

    @Test
    fun `parseAnswerPairs handles mixed letters and true-false`() {
        val result = parseAnswerPairs("[1] A\n[2] 错误\n[3] C D")
        assertEquals(listOf(1 to listOf("A"), 2 to listOf("错误"), 3 to listOf("C", "D")), result)
    }

    @Test
    fun `parseAnswerPairs handles non-sequential question numbers`() {
        val result = parseAnswerPairs("[3] A\n[1] B\n[2] C")
        assertEquals(3, result.size)
        assertEquals(listOf(3 to listOf("A"), 1 to listOf("B"), 2 to listOf("C")), result)
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

    // ── extractQuestionBlock ──

    @Test
    fun `extractQuestionBlock returns correct block for single question with options`() {
        val text = """
            15、\n保命题...办理电力通信工作票延期手续...由（ ）提出申请...
            A.工作负责人向工作票签发人
            B.工作许可人向运维负责人
            C.工作负责人向工作许可人
            D.工作许可人向工作票签发人
            16、\n保命题...拆接负载电缆前...
        """.trimIndent()
        val block = extractQuestionBlock(text, 15)
        assertTrue(block.contains("A.工作负责人向工作票签发人"))
        assertTrue(block.contains("D.工作许可人向工作票签发人"))
        assertFalse(block.contains("16、"))
    }

    @Test
    fun `extractQuestionBlock returns correct block for 判断题 without letter options`() {
        val text = """
            12、\n判断题\n保命题...可临时移开或越过遮栏，事后应立即恢复。（ ）
            正确
            错误
            13、\n保命题...装设接地线应由两人进行...（ ）
            14、\n保命题...怀疑可能存在有害气体时...（ ）
            15、\n保命题...由（ ）提出申请...
            A.工作负责人向工作票签发人
        """.trimIndent()
        val block13 = extractQuestionBlock(text, 13)
        assertTrue(block13.contains("装设接地线"))
        assertFalse(block13.contains("14、"))
        assertFalse(block13.contains("A."))
    }

    @Test
    fun `extractQuestionBlock returns empty for non-existent question`() {
        val text = """
            1、\nQuestion 1
            A. Option A
            2、\nQuestion 2
            B. Option B
        """.trimIndent()
        val block = extractQuestionBlock(text, 99)
        assertEquals("", block)
    }

    @Test
    fun `hasLetterOptions detects A-dot format`() {
        val block = "保命题...\nA.选项一\nB.选项二\nC.选项三\nD.选项四"
        val regex = Regex("""^[A-F]\s*[.、:：)）]""", RegexOption.MULTILINE)
        assertTrue(regex.containsMatchIn(block))
    }

    @Test
    fun `hasLetterOptions returns false for 判断题 without letter options`() {
        val block = "保命题...装设接地线应由两人进行...（ ）"
        val regex = Regex("""^[A-F]\s*[.、:：)）]""", RegexOption.MULTILINE)
        assertFalse(regex.containsMatchIn(block))
    }

    @Test
    fun `hasLetterOptions returns false for 正确-错误 only block`() {
        val block = "判断题\n保命题...（ ）\n正确\n错误"
        val regex = Regex("""^[A-F]\s*[.、:：)）]""", RegexOption.MULTILINE)
        assertFalse(regex.containsMatchIn(block))
    }

    @Test
    fun `hasLetterOptions returns true for Chinese bracket separator`() {
        val block = "保命题...\nA：选项一\nB：选项二"
        val regex = Regex("""^[A-F]\s*[.、:：)）]""", RegexOption.MULTILINE)
        assertTrue(regex.containsMatchIn(block))
    }
}
