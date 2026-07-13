package com.examhelper.app.knowledge

import android.util.Log
import com.examhelper.app.ExamApplication
import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.apache.poi.ss.usermodel.Cell
import org.apache.poi.ss.usermodel.Row
import org.apache.poi.ss.usermodel.Sheet
import org.apache.poi.ss.usermodel.WorkbookFactory
import java.io.FileInputStream

/**
 * Detects which columns in an Excel sheet contain question, answer, and source data.
 *
 * Uses two strategies in order:
 * 1. [detectByHeader] — keyword matching on header rows (0-2)
 * 2. 基于启发式规则检测列
 */
class ColumnDetector {

    private val gson = Gson()

    companion object {
        private const val TAG = "ColumnDetector"

        private val QUESTION_KEYWORDS = setOf("题目", "问题", "试题", "考题", "question", "题干", "题目内容", "试题内容", "题目描述", "问题描述")
        private val ANSWER_KEYWORDS = setOf("答案", "回答", "answer", "key", "正确答案", "正确选项", "正确", "选择", "参考答案", "选项答案")
        private val SOURCE_KEYWORDS = setOf("来源", "出处", "source", "来源出处", "题目来源", "参考", "参考资料")
        private val OPTION_KEYWORDS = setOf("选项", "备选", "备选项", "option", "选择项", "备选答案", "选择")
        private val TYPE_KEYWORDS = setOf("题型", "类型", "题目类型", "type", "类别", "试题类型")
    }

    // ── Header-based detection ──────────────────────────────────────────────

    /**
     * Scans rows 0-2 for recognizable header keywords and returns a [ColumnMapping].
     * Returns `null` if insufficient columns (question + answer) are found.
     */
    fun detectByHeader(sheet: Sheet): ColumnMapping? {
        val lastColIndex = getLastColumnIndex(sheet) ?: return null

        for (rowNum in 0..2) {
            val row = sheet.getRow(rowNum) ?: continue
            val mapping = matchRow(row, lastColIndex)
            if (mapping != null) {
                Log.d(TAG, "detectByHeader: matched at row $rowNum → $mapping")
                return mapping
            }
        }
        return null
    }

    /**
     * Examines each cell in [row] against keyword tables (case-insensitive).
     * Returns a mapping when both questionCol and answerCol are identified.
     * On duplicate matches for the same type, the first cell wins.
     */
    private fun matchRow(row: Row, lastColIndex: Int): ColumnMapping? {
        var questionCol: Int? = null
        var answerCol: Int? = null
        var sourceCol: Int? = null
        var optionsCol: Int? = null
        var typeCol: Int? = null

        for (i in 0..lastColIndex) {
            val cell = row.getCell(i) ?: continue
            val text = cell.toString().trim().lowercase()
            if (text.isEmpty()) continue

            if (questionCol == null && text in QUESTION_KEYWORDS) {
                questionCol = i
            } else if (answerCol == null && text in ANSWER_KEYWORDS) {
                answerCol = i
            } else if (sourceCol == null && text in SOURCE_KEYWORDS) {
                sourceCol = i
            } else if (optionsCol == null && text in OPTION_KEYWORDS) {
                optionsCol = i
            } else if (typeCol == null && text in TYPE_KEYWORDS) {
                typeCol = i
            }
        }

        return if (questionCol != null && answerCol != null) {
            ColumnMapping(questionCol = questionCol!!, answerCol = answerCol!!, sourceCol = sourceCol, optionsCol = optionsCol, typeCol = typeCol)
        } else null
    }

    /**
     * Returns the maximum cell index across rows 0-2, or `null` if the sheet has no data.
     */
    private fun getLastColumnIndex(sheet: Sheet): Int? {
        var maxCol = -1
        for (i in 0..2) {
            val row = sheet.getRow(i) ?: continue
            if (row.lastCellNum > 0) {
                maxCol = maxOf(maxCol, row.lastCellNum - 1)
            }
        }
        return if (maxCol >= 0) maxCol else null
    }

    // ── Public entry point ──────────────────────────────────────────────────

    /**
     * 打开 Excel 文件，读取第一个 sheet，通过表头匹配检测列。
     * 不再依赖 LLM 兜底。
     *
     * @throws ColumnDetectionException 当检测失败时抛出。
     */
    suspend fun detectColumns(filePath: String): ColumnMapping = withContext(Dispatchers.IO) {
        val stream = FileInputStream(filePath)
        val workbook = try {
            WorkbookFactory.create(stream)
        } finally {
            stream.close()
        }

        try {
            val sheet = workbook.getSheetAt(0)

            // 基于表头的检测
            detectByHeader(sheet)?.let {
                Log.d(TAG, "detectColumns: header detection selected → $it")
                return@withContext it
            }

            // 3. Both failed — throw
            val reason = if (getLastColumnIndex(sheet) == null) {
                DetectionFailReason.SHEET_EMPTY
            } else {
                DetectionFailReason.NO_HEADER_MATCH
            }
            throw ColumnDetectionException(
                "无法自动检测列映射，请手动指定",
                reason
            )
        } finally {
            workbook.close()
        }
    }

    // ── Prompt helpers ──────────────────────────────────────────────────────

    private fun buildHeadersString(row: Row?, lastColIndex: Int): String {
        if (row == null) return "无标题行"
        return (0..lastColIndex).joinToString("\n") { i ->
            val value = row.getCell(i)?.toString()?.trim() ?: ""
            "Col $i: \"$value\""
        }
    }

    private fun buildDataRowsString(sheet: Sheet): String {
        return (1..5).mapNotNull { rowNum ->
            val row = sheet.getRow(rowNum) ?: return@mapNotNull null
            val cellCount = row.lastCellNum.takeIf { it > 0 } ?: return@mapNotNull null
            val cells = (0 until cellCount).joinToString(", ") { i ->
                val value = row.getCell(i)?.toString()?.trim() ?: ""
                "Col $i: \"$value\""
            }
            "Row $rowNum: $cells"
        }.joinToString("\n")
    }

    private fun buildPrompt(headers: String, dataRows: String): String {
        return """
You are analyzing a spreadsheet with exam questions.
Identify which column contains the question text, which contains the answer, and which contains the source/options.

Column headers:
$headers

First 5 data rows:
$dataRows

Respond ONLY with a JSON object (no markdown, no explanation):
{"questionCol": <index>, "answerCol": <index>, "sourceCol": <index|null>, "optionsCol": <index|null>, "typeCol": <index|null>}
        """.trimIndent()
    }

    /**
     * Extracts a JSON object from an LLM response, handling optional markdown
     * code-block fences.
     */
    private fun extractJsonFromResponse(response: String): String {
        val codeBlockRegex = Regex("```(?:json)?\\s*([\\s\\S]*?)```")
        val match = codeBlockRegex.find(response)
        if (match != null) return match.groupValues[1].trim()

        val jsonStart = response.indexOf('{')
        val jsonEnd = response.lastIndexOf('}')
        if (jsonStart >= 0 && jsonEnd > jsonStart) {
            return response.substring(jsonStart, jsonEnd + 1)
        }
        return response.trim()
    }

    // ── LLM response model ──────────────────────────────────────────────────

    private data class LLMColumnResponse(
        val questionCol: Int,
        val answerCol: Int,
        val sourceCol: Int?,
        val optionsCol: Int?,
        val typeCol: Int?
    )
}
