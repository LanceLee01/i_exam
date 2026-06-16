package com.examhelper.app.knowledge

/**
 * Maps Excel column indices for question, answer, and optional source/options columns.
 */
data class ColumnMapping(
    val questionCol: Int,
    val answerCol: Int,
    val sourceCol: Int? = null,
    val optionsCol: Int? = null
)

/**
 * Reason why column detection failed.
 */
enum class DetectionFailReason {
    NO_HEADER_MATCH,
    LLM_FAILED,
    LLM_NOT_CONFIGURED,
    LLM_INVALID_RESPONSE,
    SHEET_EMPTY
}

/**
 * Thrown when automated column detection fails (both header and LLM methods).
 */
class ColumnDetectionException(
    message: String,
    val reason: DetectionFailReason
) : Exception(message)
