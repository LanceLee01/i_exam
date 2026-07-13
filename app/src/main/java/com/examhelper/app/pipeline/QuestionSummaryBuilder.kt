package com.examhelper.app.pipeline

import com.examhelper.app.util.parseOptionMapInline

/**
 * Build a sidebar-friendly summary for one question:
 *  📝 题库原题
 *  📋 KB 选项
 *  ✅ 解析后答案 (KB字母→屏幕字母, 显示选项文字)
 *
 * Used by both SolvePipeline (for SidebarState.Done summary) and
 * MultiRoundRunner (for SidebarState.MultiRound.FILLING summary).
 */
internal fun buildQuestionSummaryForUI(
    filteredText: String,
    answer: String,
    kbQuestionTexts: Map<Int, String>,
    kbAnswerOptions: Map<Int, String>,
    kbOriginalAnswers: Map<Int, String>
): String {
    val qPattern = Regex("""(\d+)[、.]""")
    val firstQMatch = qPattern.find(filteredText)
    val firstQNum = firstQMatch?.groupValues?.get(1)?.toIntOrNull()

    if (firstQNum != null && firstQNum in kbQuestionTexts) {
        val kbQuestion = kbQuestionTexts[firstQNum] ?: ""
        val kbOptions = kbAnswerOptions[firstQNum] ?: ""
        val kbOrigAnswer = kbOriginalAnswers[firstQNum] ?: ""
        val ansDisplay = buildKbAnswerDisplayForUI(firstQNum, kbOrigAnswer, kbOptions, answer)

        return buildString {
            append("📝 $kbQuestion")
            if (kbOptions.isNotBlank()) append("\n📋 $kbOptions")
            if (ansDisplay.isNotBlank()) append("\n✅ $ansDisplay")
        }
    }

    // Fallback: extract stem + options from filtered exam text (no KB data available)
    val lines = filteredText.lines().map { it.trim() }.filter { it.isNotBlank() }
    val stopWords = setOf("上一页", "下一页", "开始考试", "提交答案")
    val optionPattern = Regex("""^[A-F]\s*[.、:：)）]""")
    val stemLines = lines
        .dropWhile {
            it.startsWith("单选题") || it.startsWith("多选题") || it.startsWith("判断题") ||
                Regex("""^\d+[、.]""").matches(it) ||
                Regex("""^\d+-\S+""").matches(it)
        }
        .takeWhile { !optionPattern.containsMatchIn(it) && it !in stopWords && it != "正确" && it != "错误" }
    val stem = stemLines.joinToString(" ").take(100)
        .ifBlank { lines.firstOrNull()?.take(60) ?: filteredText.take(40) }
    val optionLines = lines
        .filter { Regex("""^[A-F]\s*[.、:：)）]""").containsMatchIn(it) || it == "正确" || it == "错误" }
    val ansLine = answer.lines().firstOrNull { Regex("""[\[【]?\d+[\]】]?""").containsMatchIn(it) }?.trim()?.take(30) ?: ""
    return buildString {
        append("📝 $stem")
        if (optionLines.isNotEmpty()) append("\n📋 ${optionLines.joinToString("  ").take(80)}")
        if (ansLine.isNotBlank()) append("\n✅ $ansLine")
    }
}

/** Build the answer display line using KB answer TEXT extracted from options.
 *  When KB and screen labels differ due to option shuffling, both are shown.
 *  Example output: "安全培训" or "安全培训 (题库:C → 屏幕:B)" */
internal fun buildKbAnswerDisplayForUI(
    qNum: Int,
    kbOrigAnswer: String,
    kbOptions: String,
    answer: String
): String {
    // 判断题: show the answer text directly
    if (kbOrigAnswer in listOf("正确", "错误", "对", "错")) return kbOrigAnswer

    // Extract KB answer text from options
    val kbAnswerLetters = kbOrigAnswer.uppercase().filter { it in 'A'..'F' }.map { it.toString() }
    if (kbAnswerLetters.isEmpty()) {
        return answer.lines().firstOrNull {
            it.contains("[$qNum]") || it.contains("$qNum]") || it.contains("[$qNum")
        }?.trim()?.take(60) ?: ""
    }

    val kbOptionMap = parseOptionMapInline(kbOptions)
    val kbAnswerTexts = kbAnswerLetters.mapNotNull { kbOptionMap[it] }

    val screenAnsLine = answer.lines().firstOrNull {
        it.contains("[$qNum]") || it.contains("$qNum]") || it.contains("[$qNum")
    }?.trim()?.take(60) ?: ""
    val screenLetter = Regex("""^[\[【]?\d+[\]】]?\s*[.、:：)）]?\s*(.+)""").find(screenAnsLine)
        ?.groupValues?.get(1)?.trim() ?: ""
    val screenLetters = screenLetter.uppercase().filter { it in 'A'..'F' }.map { it.toString() }

    if (kbAnswerTexts.isNotEmpty()) {
        val answerText = kbAnswerTexts.joinToString(" ")
        return if (screenLetters.isNotEmpty() && screenLetters.joinToString("") != kbAnswerLetters.joinToString("")) {
            "$answerText (题库:${kbAnswerLetters.joinToString("")} → 屏幕:${screenLetters.joinToString("")})"
        } else {
            answerText
        }
    }
    return screenAnsLine.ifBlank { kbOrigAnswer }
}