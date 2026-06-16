package com.examhelper.app.util

import com.examhelper.app.network.Reference
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class ReferenceFormatterTest {

    @Test
    fun `formatSingleReference returns null for empty reference list`() {
        val result = ReferenceFormatter.formatSingleReference(emptyList(), emptyList())
        assertNull(result)
    }

    @Test
    fun `formatSingleReference returns null when all snippets are shorter than 100 chars`() {
        val refs = listOf(
            Reference("t1", "u1", "a".repeat(50)),
            Reference("t2", "u2", "a".repeat(80)),
            Reference("t3", "u3", "a".repeat(95))
        )
        val result = ReferenceFormatter.formatSingleReference(refs, emptyList())
        assertNull(result)
    }

    @Test
    fun `formatSingleReference truncates at sentence end within 100-150 range`() {
        // 200 chars with 。at position 130 (within [100,150]), truncation at 130
        val snippet = "中".repeat(129) + "。结束" + "中".repeat(68)  // total ~200
        val expectedPrefix = "🔍 参考（题 3）: "
        val expectedTruncated = snippet.substring(0, 130)
        val result = ReferenceFormatter.formatSingleReference(
            listOf(Reference("t", "u", snippet)),
            listOf(3)
        )
        assertEquals(expectedPrefix + expectedTruncated, result)
    }

    @Test
    fun `formatSingleReference returns full text when snippet is between 100-150 chars`() {
        val snippet = "中".repeat(120)
        val expectedPrefix = "🔍 参考: "
        val result = ReferenceFormatter.formatSingleReference(
            listOf(Reference("t", "u", snippet)),
            emptyList()
        )
        assertEquals(expectedPrefix + snippet, result)
    }

    @Test
    fun `formatSingleReference skips short snippet and uses next valid one`() {
        val refs = listOf(
            Reference("t1", "u1", "a".repeat(50)),
            Reference("t2", "u2", "a".repeat(200))
        )
        val result = ReferenceFormatter.formatSingleReference(refs, emptyList())
        val expected = "🔍 参考: " + "a".repeat(150) + "..."
        assertEquals(expected, result)
    }

    @Test
    fun `stripHtml removes HTML tags and decodes entities`() {
        val input = "<b>Hello</b>&nbsp;World&amp;Test&lt;Tag&gt;<br/>"
        val expected = "Hello World&Test<Tag>"
        assertEquals(expected, ReferenceFormatter.stripHtml(input))
    }

    @Test
    fun `truncateToSentenceEnd does not treat decimal point as sentence end`() {
        val text = "中".repeat(105) + "经测试3.14mg/kg符合标准" + "中".repeat(75)
        val result = ReferenceFormatter.truncateToSentenceEnd(text)
        assertEquals(153, result.length)  // 150 chars + "..."
        assertTrue(result.endsWith("..."))
    }

    @Test
    fun `truncateToSentenceEnd falls back to take(150) plus ellipsis when no sentence boundary`() {
        val text = "a".repeat(200)
        val expected = "a".repeat(150) + "..."
        assertEquals(expected, ReferenceFormatter.truncateToSentenceEnd(text))
    }

    @Test
    fun `formatSingleReference shows single question number prefix`() {
        val snippet = "a".repeat(150)
        val result = ReferenceFormatter.formatSingleReference(
            listOf(Reference("t", "u", snippet)),
            listOf(3)
        )
        assertTrue(result!!.startsWith("🔍 参考（题 3）: "))
    }

    @Test
    fun `formatSingleReference shows multiple question numbers prefix`() {
        val snippet = "a".repeat(150)
        val result = ReferenceFormatter.formatSingleReference(
            listOf(Reference("t", "u", snippet)),
            listOf(3, 4, 5)
        )
        assertTrue(result!!.startsWith("🔍 参考（题 3, 4, 5）: "))
    }

    @Test
    fun `formatSingleReference shows generic prefix when no LLM question numbers`() {
        val snippet = "a".repeat(150)
        val result = ReferenceFormatter.formatSingleReference(
            listOf(Reference("t", "u", snippet)),
            emptyList()
        )
        assertTrue(result!!.startsWith("🔍 参考: "))
    }
}
