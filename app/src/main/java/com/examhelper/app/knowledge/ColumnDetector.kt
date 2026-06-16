package com.examhelper.app.knowledge

import android.util.Log
import com.examhelper.app.ExamApplication
import com.examhelper.app.network.LLMClient
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
 * 2. [detectByLLM] — LLM-based analysis of header + data rows
 */
class ColumnDetector {

    private val gson = Gson()

    companion object {
        private const val TAG = "ColumnDetector"

        private val QUESTION_KEYWORDS = setOf("题目", "问题", "试题", "考题", "question")
        private val ANSWER_KEYWORDS = setOf("答案", "回答", "answer", "key")
        private val SOURCE_KEYWORDS = setOf("来源", "source", "出处", "选项")
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

        for (i in 0..lastColIndex) {
            val cell = row.getCell(i) ?: continue
            val text = cell.toString().trim().lowercase()
            if (text.isEmpty()) continue

            if (questionCol == null && text in QUESTION_KEYWORDS) {
                questionCol = i
            }
            if (answerCol == null && text in ANSWER_KEYWORDS) {
                answerCol = i
            }
            if (sourceCol == null && text in SOURCE_KEYWORDS) {
                sourceCol = i
            }
        }

        return if (questionCol != null && answerCol != null) {
            ColumnMapping(questionCol = questionCol!!, answerCol = answerCol!!, sourceCol = sourceCol)
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

    // ── LLM-based detection ─────────────────────────────────────────────────

    /**
     * Uses an LLM to identify question/answer/source columns from header and sample data.
     * Returns `null` when the API key is unset, the LLM call fails, or the response
     * cannot be parsed / validated.
     */
    suspend fun detectByLLM(sheet: Sheet): ColumnMapping? {
        val config = ExamApplication.instance.appConfig.getSnapshot()
        if (config.apiKey.isBlank()) {
            Log.d(TAG, "detectByLLM: API key not configured, skipping")
            return null
        }

        val lastColIndex = getLastColumnIndex(sheet) ?: return null

        val headersFormatted = buildHeadersString(sheet.getRow(0), lastColIndex)
        val dataRowsFormatted = buildDataRowsString(sheet)
        val prompt = buildPrompt(headersFormatted, dataRowsFormatted)

        return try {
            val llmClient = LLMClient()
            val result = llmClient.chatSync(
                endpoint = config.apiEndpoint,
                apiKey = config.apiKey,
                model = config.modelName,
                temperature = 0.1f,
                maxTokens = config.maxTokens,
                systemPrompt = "You are a precise data extraction assistant. Respond only with JSON.",
                userMessage = prompt
            )

            val jsonStr = when (result) {
                is LLMClient.Result.Success -> result.content.trim()
                else -> {
                    Log.w(TAG, "detectByLLM: LLM call failed → $result")
                    return null
                }
            }

            val cleanJson = extractJsonFromResponse(jsonStr)
            val response = gson.fromJson(cleanJson, LLMColumnResponse::class.java)
                ?: return null.also { Log.w(TAG, "detectByLLM: JSON parse returned null") }

            // Bounds validation
            if (response.questionCol !in 0..lastColIndex) {
                Log.w(TAG, "detectByLLM: questionCol ${response.questionCol} out of bounds [0..$lastColIndex]")
                return null
            }
            if (response.answerCol !in 0..lastColIndex) {
                Log.w(TAG, "detectByLLM: answerCol ${response.answerCol} out of bounds [0..$lastColIndex]")
                return null
            }
            if (response.sourceCol != null && response.sourceCol !in 0..lastColIndex) {
                Log.w(TAG, "detectByLLM: sourceCol ${response.sourceCol} out of bounds [0..$lastColIndex]")
                return null
            }

            ColumnMapping(
                questionCol = response.questionCol,
                answerCol = response.answerCol,
                sourceCol = response.sourceCol
            ).also {
                Log.d(TAG, "detectByLLM: succeeded → $it")
            }
        } catch (e: Exception) {
            Log.e(TAG, "detectByLLM: unexpected error", e)
            null
        }
    }

    // ── Public entry point ──────────────────────────────────────────────────

    /**
     * Opens the Excel file at [filePath], reads the first sheet, and attempts
     * column detection via [detectByHeader] first, then [detectByLLM] as fallback.
     *
     * @throws ColumnDetectionException when both detection methods fail.
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

            // 1. Try header-based detection
            detectByHeader(sheet)?.let {
                Log.d(TAG, "detectColumns: header detection selected → $it")
                return@withContext it
            }

            // 2. Fall back to LLM
            detectByLLM(sheet)?.let {
                Log.d(TAG, "detectColumns: LLM detection selected → $it")
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
{"questionCol": <index>, "answerCol": <index>, "sourceCol": <index|null>}
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
        val sourceCol: Int?
    )
}
