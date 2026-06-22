package com.examhelper.app.pipeline

object ScanPageFilter {

    private val FILTER_PATTERNS = listOf(
        Regex("""在线考试"""),
        Regex("""剩余时间[：:]\s*\d{2}:\d{2}:\d{2}"""),
        Regex("""gradeEvaluationPlan"""),
        Regex("""^上一页$"""),
        Regex("""^下一页$"""),
        Regex("""[-]"""),       // Unicode Private Use Area chars like  
        Regex("""^[ -~]$"""),        // single ASCII char line
        Regex("""^\d+\.\d+$""")      // decimal numbers (height/weight values, e.g. "3.5", "2.5")
    )

    private val PROGRESS_REGEX = Regex("""(\d+)\s*/\s*(\d+)""")

    /** Filter noise text from screen reading, keep only question content */
    fun filter(text: String): String {
        var result = text
        for (pattern in FILTER_PATTERNS) {
            result = pattern.replace(result, "")
        }
        // Filter exam title lines (e.g. "6月22日-D一线人员/47-全专业通用安规/35-信息安规")
        result = result.replace(Regex("""\d+月\d+日-.+/\d+-.+/"""), "")
        // Normalize blank lines (3+ → 2)
        result = result.replace(Regex("""\n{3,}"""), "\n\n")
        return result.trim()
    }

    /** Extract N/M progress from text, returns (current, total) or null on failure */
    fun extractProgress(text: String): Pair<Int, Int>? {
        val match = PROGRESS_REGEX.find(text) ?: return null
        val current = match.groupValues[1].toIntOrNull() ?: return null
        val total = match.groupValues[2].toIntOrNull() ?: return null
        return current to total
    }
}
