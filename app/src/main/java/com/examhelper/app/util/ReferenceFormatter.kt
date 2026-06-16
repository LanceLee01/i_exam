package com.examhelper.app.util

import com.examhelper.app.network.Reference

object ReferenceFormatter {

    fun formatSingleReference(references: List<Reference>, llmQuestionNumbers: List<Int>): String? {
        if (references.isEmpty()) return null

        for (ref in references) {
            val cleaned = stripHtml(ref.snippet)
            if (cleaned.length < 100) continue

            val truncated = truncateToSentenceEnd(cleaned)
            val prefix = buildPrefix(llmQuestionNumbers)

            return prefix + truncated
        }

        return null
    }

    private fun buildPrefix(llmQuestionNumbers: List<Int>): String {
        return when (llmQuestionNumbers.size) {
            0 -> "🔍 参考: "
            1 -> "🔍 参考（题 ${llmQuestionNumbers[0]}）: "
            else -> "🔍 参考（题 ${llmQuestionNumbers.joinToString(", ")}）: "
        }
    }

    internal fun stripHtml(input: String): String {
        var text = Regex("<[^>]+>").replace(input, "")
        text = text.replace("&nbsp;", " ")
        text = text.replace("&amp;", "&")
        text = text.replace("&lt;", "<")
        text = text.replace("&gt;", ">")
        text = text.replace("&quot;", "\"")
        text = text.replace("&#39;", "'")
        text = Regex("\\s+").replace(text, " ")
        return text.trim()
    }

    internal fun truncateToSentenceEnd(text: String, minLen: Int = 100, maxLen: Int = 150): String {
        if (text.length <= maxLen) return text

        for (i in maxLen downTo minLen) {
            val ch = text[i - 1]
            when (ch) {
                '。', '！', '？' -> return text.substring(0, i)
                '.', '!', '?' -> {
                    if (i >= 2 && isCJK(text[i - 2])) {
                        return text.substring(0, i)
                    }
                }
            }
        }

        return text.substring(0, maxLen) + "..."
    }

    private fun isCJK(ch: Char): Boolean {
        return ch.code in 0x4E00..0x9FFF || ch.code in 0x3400..0x4DBF
    }
}
