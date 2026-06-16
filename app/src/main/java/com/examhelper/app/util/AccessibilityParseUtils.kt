package com.examhelper.app.util

/** Extract (questionNumber, selections) pairs from an answer string. */
internal fun parseAnswerPairs(answer: String): List<Pair<Int, List<String>>> {
    return ExamConstants.ANSWER_PARSE_REGEX.findAll(answer).mapNotNull { match ->
        val qNum = match.groupValues[1].toIntOrNull() ?: return@mapNotNull null
        val raw = match.groupValues[2].uppercase().filter { it in ExamConstants.OPTION_LETTERS || "正确错误对".indexOf(it) >= 0 }
        if (raw.isEmpty()) return@mapNotNull null
        val selections = if (raw.all { it in 'A'..'F' }) {
            raw.map { it.toString() }
        } else {
            listOf(raw)
        }
        qNum to selections
    }.toList()
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
