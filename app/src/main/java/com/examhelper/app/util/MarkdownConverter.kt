package com.examhelper.app.util

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Converts raw screen-extracted exam text into a structured Markdown document.
 *
 * Raw input is deduplicated lines from AccessibilityService node traversal.
 * The converter detects question numbers, option letters, and groups them
 * into proper Markdown sections. Unstructured text is preserved as-is.
 */
object MarkdownConverter {

    private val questionRegex = Regex("""^\s*(\d+)\s*[.、)）]""")
    private val optionRegex = Regex("""^\s*([A-Fa-f])\s*[.、:：)）]\s*(.*)""")

    /**
     * Converts raw extracted exam text into a formatted Markdown string.
     */
    fun convert(rawText: String): String {
        val lines = rawText.lines()
        val md = StringBuilder()
        val timestamp = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date())

        // Header
        md.appendLine("# 考试题目")
        md.appendLine()
        md.appendLine("> 从 i国网 考试界面自动提取 — $timestamp")
        md.appendLine()
        md.appendLine("---")
        md.appendLine()

        val blocks = parseToBlocks(lines)

        if (blocks.isEmpty()) {
            // No structured content detected; output as code block
            md.appendLine("```text")
            md.appendLine(rawText)
            md.appendLine("```")
            md.appendLine()
            return md.toString()
        }

        for (block in blocks) {
            when (block) {
                is Block.Question -> {
                    md.appendLine("## ${block.question}")
                    for (opt in block.options) {
                        md.appendLine("- ${opt}")
                    }
                    md.appendLine()
                }
                is Block.Text -> {
                    md.appendLine(block.content)
                    md.appendLine()
                }
            }
        }

        // Footer
        md.appendLine("---")
        md.appendLine("*共 ${blocks.count { it is Block.Question }} 题，由 考试助手 (ExamHelper) 生成*")

        return md.toString()
    }

    private sealed class Block {
        data class Question(val question: String, val options: List<String>) : Block()
        data class Text(val content: String) : Block()
    }

    private fun parseToBlocks(lines: List<String>): List<Block> {
        val blocks = mutableListOf<Block>()
        var currentQuestion: String? = null
        val currentOptions = mutableListOf<String>()
        val textBuffer = mutableListOf<String>()

        fun flushText() {
            if (textBuffer.isNotEmpty()) {
                val text = textBuffer.joinToString("\n")
                if (text.isNotBlank()) {
                    blocks.add(Block.Text(text))
                }
                textBuffer.clear()
            }
        }

        fun flushQuestion() {
            val q = currentQuestion ?: return
            if (currentOptions.isNotEmpty() || q.isNotBlank()) {
                blocks.add(Block.Question(q.trim(), currentOptions.toList()))
            }
            currentQuestion = null
            currentOptions.clear()
        }

        for (rawLine in lines) {
            val line = rawLine.trim()
            if (line.isEmpty()) continue

            val qMatch = questionRegex.find(line)
            if (qMatch != null) {
                // New question starts
                flushQuestion()
                flushText()
                currentQuestion = line
                currentOptions.clear()
                continue
            }

            val oMatch = optionRegex.find(line)
            if (oMatch != null) {
                // Option for current question (or standalone)
                if (currentQuestion == null) {
                    // Orphan option without question — treat as text
                    textBuffer.add(line)
                } else {
                    currentOptions.add(line)
                }
                continue
            }

            // Ordinary line
            if (currentQuestion != null && currentOptions.isEmpty()) {
                // Still part of the question text (multi-line question)
                currentQuestion += " $line"
            } else if (currentQuestion != null && currentOptions.isNotEmpty()) {
                // After options started — new text means question is done
                flushQuestion()
                textBuffer.add(line)
            } else {
                textBuffer.add(line)
            }
        }

        flushQuestion()
        flushText()

        return blocks
    }
}
