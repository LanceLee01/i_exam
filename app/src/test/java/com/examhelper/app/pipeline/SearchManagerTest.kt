package com.examhelper.app.pipeline

import com.examhelper.app.network.Reference
import com.examhelper.app.network.SearchResult
import com.examhelper.app.network.TavilyClient
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class SearchManagerTest {

    // ═════════════════════════════════════════════════════════════════════
    // extractSearchQueries() — pure function, 5 scenarios
    // ═════════════════════════════════════════════════════════════════════

    @Test
    fun `extractSearchQueries lines with parentheses strip bracket content`() {
        val text = """
            What is the capital of (France)?
            Short
            Explain (quantum) computing
        """.trimIndent()
        val manager = SearchManager(null)
        val queries = manager.extractSearchQueries(text)

        assertTrue(queries.contains("What is the capital of ?"))
        assertTrue(queries.contains("Explain  computing"))
    }

    @Test
    fun `extractSearchQueries no brackets but length gt 8 uses fallback concatenation`() {
        val text = """
            What is the capital of France
            The sky is blue and beautiful
        """.trimIndent()
        val manager = SearchManager(null)
        val queries = manager.extractSearchQueries(text)

        // Fallback should combine stem lines into one query
        assertTrue(queries.isNotEmpty())
        assertTrue(queries.all { it.length > 8 })
        assertTrue(queries.any { it.contains("France") && it.contains("beautiful") })
    }

    @Test
    fun `extractSearchQueries empty text returns empty list`() {
        val manager = SearchManager(null)
        val queries = manager.extractSearchQueries("")
        assertEquals(emptyList<String>(), queries)
    }

    @Test
    fun `extractSearchQueries only short lines returns empty list`() {
        val text = """
            hi
            ok
            foo
        """.trimIndent()
        val manager = SearchManager(null)
        val queries = manager.extractSearchQueries(text)
        assertEquals(emptyList<String>(), queries)
    }

    @Test
    fun `extractSearchQueries mixed content deduplicates queries`() {
        val text = """
            What is (X)?
            What is (X)?
            Define (Y) in context
        """.trimIndent()
        val manager = SearchManager(null)
        val queries = manager.extractSearchQueries(text)

        // "What is ?" should appear only once despite being in input twice
        val count = queries.count { it == "What is ?" }
        assertEquals(1, count, "Duplicate queries should be deduplicated")
        assertTrue(queries.contains("Define  in context"))
    }

    // ═════════════════════════════════════════════════════════════════════
    // searchQuestions() — suspend function, 3 scenarios via MockK
    // ═════════════════════════════════════════════════════════════════════

    @Test
    fun `searchQuestions with null TavilyClient returns skipped`() {
        val manager = SearchManager(null)
        val result = runBlocking { manager.searchQuestions("What is the capital of France?") }

        assertTrue(result.skipped, "Should be skipped when client is null")
        assertFalse(result.found)
        assertFalse(result.failed)
    }

    @Test
    fun `searchQuestions when TavilyClient search succeeds returns found with summary and references`() {
        val mockClient = mockk<TavilyClient>()
        val manager = SearchManager(mockClient)

        val ref = Reference("Example Title", "https://example.com", "Example snippet")
        val searchResult = SearchResult(
            answer = "Paris is the capital of France",
            references = listOf(ref),
            source = "tavily"
        )

        coEvery { mockClient.search(any()) } returns Result.success(searchResult)

        val result = runBlocking { manager.searchQuestions("What is the capital of France?") }

        assertTrue(result.found)
        assertFalse(result.skipped)
        assertFalse(result.failed)
        assertEquals("Paris is the capital of France", result.summary)
        assertEquals(1, result.references.size)
        assertEquals("Example Title", result.references[0].title)
        assertEquals("https://example.com", result.references[0].url)
    }

    @Test
    fun `searchQuestions when TavilyClient search fails returns failed`() {
        val mockClient = mockk<TavilyClient>()
        val manager = SearchManager(mockClient)

        coEvery { mockClient.search(any()) } returns Result.failure(
            java.io.IOException("API error")
        )

        val result = runBlocking { manager.searchQuestions("What is the capital of France?") }

        assertTrue(result.failed, "Should be failed when search throws")
        assertFalse(result.skipped)
        assertFalse(result.found)
    }
}
