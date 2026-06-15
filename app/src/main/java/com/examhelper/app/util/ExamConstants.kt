package com.examhelper.app.util

/**
 * Central source of truth for exam option letter ranges.
 *
 * OPTION_LETTERS is a `var` (not val) to allow runtime extension to A-G or beyond.
 * Both OPTION_RANGE_REGEX and ANSWER_PARSE_REGEX dynamically derive from it.
 */
object ExamConstants {
    var OPTION_LETTERS = 'A'..'F'

    val OPTION_RANGE_REGEX: Regex
        get() = Regex("""^[${OPTION_LETTERS.first}-${OPTION_LETTERS.last}]\s*[.、:：)）]""")

    val ANSWER_PARSE_REGEX: Regex
        get() = Regex("""[\[【]?(\d+)[\]】]?\s*([${OPTION_LETTERS.first}-${OPTION_LETTERS.last}\s、,，;]+|正确|错误|对|错)""")
}
