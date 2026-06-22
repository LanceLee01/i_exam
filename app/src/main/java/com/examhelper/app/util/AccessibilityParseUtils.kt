package com.examhelper.app.util

/** Special marker for uncertain answers — auto-click will skip this question. */
const val ANSWER_UNCERTAIN = "__UNCERTAIN__"

/** Extract (questionNumber, selections) pairs from an answer string. */
internal fun parseAnswerPairs(answer: String): List<Pair<Int, List<String>>> {
    return ExamConstants.ANSWER_PARSE_REGEX.findAll(answer).mapNotNull { match ->
        val qNum = match.groupValues[1].toIntOrNull() ?: return@mapNotNull null
        val raw = match.groupValues[2].trim()
        // Handle "不确定" — return special marker so auto-click can skip
        if (raw.contains("不确定")) {
            return@mapNotNull qNum to listOf(ANSWER_UNCERTAIN)
        }
        val filtered = raw.uppercase().filter { it in ExamConstants.OPTION_LETTERS || "正确错误对".indexOf(it) >= 0 }
        if (filtered.isEmpty()) return@mapNotNull null
        val selections = if (filtered.all { it in 'A'..'F' }) {
            filtered.map { it.toString() }
        } else {
            listOf(filtered)
        }
        qNum to selections
    }.toList()
}

/** Extract question type (单选题/多选题/判断题) for each question number. */
internal fun extractQuestionTypes(sourceText: String): Map<Int, String> {
    val result = mutableMapOf<Int, String>()
    val lines = sourceText.lines()
    var currentNum: Int? = null
    val qRegex = Regex("""^\s*(\d+)\s*[.、]""")

    for (i in lines.indices) {
        val trimmed = lines[i].trim()
        // Check for question number on current or next line
        val qMatch = qRegex.find(trimmed)
        if (qMatch != null) {
            currentNum = qMatch.groupValues[1].toIntOrNull()
            continue
        }
        if (currentNum != null && trimmed.isNotEmpty()) {
            when {
                trimmed.contains("单选题") -> result[currentNum] = "单选"
                trimmed.contains("多选题") -> result[currentNum] = "多选"
                trimmed.contains("判断题") -> result[currentNum] = "判断"
            }
            if (currentNum in result) currentNum = null // type found, reset
        }
    }
    return result
}

/** Count the number of options per question from source text. */
internal fun countOptionsPerQuestion(sourceText: String): List<Int> {
    val lines = sourceText.lines()
    val counts = mutableListOf<Int>()
    var currentCount = 0
    val questionRegex = Regex("""^\s*\d+\s*[.、]""")
    val optionRegex = Regex("""^\s*[A-Fa-f]\s*[.、:：)）]""")

    for (line in lines) {
        val trimmed = line.trim()
        if (questionRegex.containsMatchIn(trimmed)) {
            if (currentCount > 0) counts.add(currentCount)
            currentCount = 0
        } else if (optionRegex.containsMatchIn(trimmed) || trimmed.contains("正确") || trimmed.contains("错误")) {
            currentCount++
        }
    }
    if (currentCount > 0) counts.add(currentCount)
    return counts
}

/** Check whether a node's text matches a selection letter or special string. */
internal fun matchesSelection(nodeText: String, selection: String): Boolean {
    val letter = selection.first().uppercaseChar()
    if (letter in 'A'..'F') {
        val t = nodeText.uppercase().trim()
        return t.firstOrNull() == letter && (
            t.length == 1 || Regex("""^[A-F]\s*[.、:：)）]""").containsMatchIn(t)
        )
    }
    val tf = listOf("正确", "错误", "对", "错")
    if (selection in tf) return tf.any { nodeText.contains(it) }
    return nodeText.contains(selection, ignoreCase = true)
}

/** Extract the text block for a specific question number from the source text. */
internal fun extractQuestionBlock(sourceText: String, qNum: Int): String {
    val lines = sourceText.lines()
    val startMarker = "$qNum、"
    var startIdx = lines.indexOfFirst { it.trimStart().startsWith(startMarker) }
    if (startIdx < 0) {
        // Try "N." format
        val altMarker = "$qNum."
        startIdx = lines.indexOfFirst { it.trimStart().startsWith(altMarker) }
    }
    if (startIdx < 0) return ""

    val nextQRegex = Regex("""^\s*\d+\s*[、.]""")
    val endIdx = lines.subList(startIdx + 1, lines.size)
        .indexOfFirst { nextQRegex.containsMatchIn(it.trim()) }
    val blockEnd = if (endIdx < 0) lines.size else startIdx + 1 + endIdx
    return lines.subList(startIdx, blockEnd).joinToString("\n")
}
