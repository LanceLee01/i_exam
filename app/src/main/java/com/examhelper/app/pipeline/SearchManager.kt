package com.examhelper.app.pipeline

import com.examhelper.app.network.Reference
import com.examhelper.app.network.TavilyClient

data class SearchEnhancement(
    val skipped: Boolean = false,
    val failed: Boolean = false,
    val found: Boolean = false,
    val summary: String = "",
    val references: List<Reference> = emptyList()
)

class SearchManager(private val tavilyClient: TavilyClient?) {

    suspend fun searchQuestions(extractedText: String): SearchEnhancement {
        if (tavilyClient == null) {
            return SearchEnhancement(skipped = true)
        }

        val queries = extractSearchQueries(extractedText).take(3)
        if (queries.isEmpty()) {
            return SearchEnhancement(failed = true)
        }

        val allSummaries = mutableListOf<String>()
        val allReferences = mutableListOf<Reference>()
        var successCount = 0

        for (query in queries) {
            val result = tavilyClient.search(query)
            if (result.isSuccess) {
                val searchResult = result.getOrThrow()
                searchResult.answer?.let { allSummaries.add(it) }
                allReferences.addAll(searchResult.references)
                successCount++
                if (successCount >= 2) break
            }
        }

        if (allReferences.isEmpty() && allSummaries.isEmpty()) {
            return SearchEnhancement(failed = true)
        }

        return SearchEnhancement(
            found = true,
            summary = allSummaries.joinToString("\n"),
            references = allReferences.distinctBy { it.url }
        )
    }

    fun extractSearchQueries(text: String): List<String> {
        val lines = text.lines().map { it.trim() }.filter { it.isNotBlank() }
        val queries = mutableListOf<String>()

        // Pattern 1: Lines containing parentheses — likely question stems
        for (line in lines) {
            if (line.contains("(") || line.contains("（")) {
                val cleaned = line
                    .replace(Regex("\\([^)]*\\)"), "")
                    .replace(Regex("（[^）]*）"), "")
                    .trim()
                if (cleaned.length > 8) {
                    queries.add(cleaned.take(80))
                }
            }
        }

        // Pattern 2 (fallback): First 3 stem-like lines concatenated
        val stemLines = lines.filter { it.length > 8 }
        if (stemLines.isNotEmpty()) {
            val combined = stemLines.take(3).joinToString("").take(120)
            if (combined.length > 8) {
                queries.add(combined)
            }
        }

        return queries.distinct().filter { it.length > 8 }
    }
}
