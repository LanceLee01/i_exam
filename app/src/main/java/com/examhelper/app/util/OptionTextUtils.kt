package com.examhelper.app.util

/** 从题目文本中提取选项字母到选项文字的映射 */
internal fun parseOptionMap(text: String): Map<String, String> {
    val map = mutableMapOf<String, String>()
    val letters = ExamConstants.OPTION_LETTERS
    val regex = Regex("""^([${letters.first}-${letters.last}])[.、．\s]\s*(.+)""", RegexOption.MULTILINE)
    regex.findAll(text).forEach { match ->
        val text2 = match.groupValues[2].trim().replace(Regex("\\s+"), "")
        if (text2.length in 2..30) {
            map[match.groupValues[1]] = text2
        }
    }
    return map
}

/** 在答案行中，将选项字母替换为"字母.选项文字" */
internal fun appendOptionText(line: String, optionMap: Map<String, String>): String {
    if (optionMap.isEmpty()) return line
    var result = line
    for ((letter, text) in optionMap) {
        val hint = text.take(20)
        // "答案：A" → "答案：A.施工方案"
        result = result.replace(Regex("""答案[：:]\s*$letter(?![.\w])""")) {
            "答案：" + letter + "." + hint
        }
        // 行首或行尾的单独字母 "A" → "A.施工方案"
        result = result.replace(Regex("""(^|\s)$letter(\s*$)""")) {
            it.groupValues[1] + letter + "." + hint + it.groupValues[2]
        }
    }
    return result
}

// ── Option text resolution for shuffled screen options ─────────────────

/**
 * Parse option labels from inline/single-line text like "A. text1 B. text2 C. text3".
 * Unlike [parseOptionMap] which uses MULTILINE mode expecting one option per line,
 * this handles KB option fields that are a single line with multiple options.
 */
internal fun parseOptionMapInline(text: String): Map<String, String> {
    val map = mutableMapOf<String, String>()
    val letters = ExamConstants.OPTION_LETTERS
    // Normalize: replace option separators (|) with spaces for easier parsing
    val normalized = text.replace(Regex("[|]+"), " ")
    // Match each option label+text, stopping at next option label or end of string
    val regex = Regex(
        """([${letters.first}-${letters.last}])[.、．:：)）\-]\s*(\S{1,60}?)(?=\s*[${letters.first}-${letters.last}][.、．:：)）\-]|$)"""
    )
    regex.findAll(normalized).forEach { match ->
        val optionText = match.groupValues[2].trim()
        if (optionText.isNotBlank()) {
            map[match.groupValues[1]] = optionText
        }
    }
    return map
}

/**
 * Given KB options text and on-screen option nodes, resolve which on-screen letter(s)
 * correspond to the KB answer letters via text matching.
 *
 * @param kbOptionsText  KB entry's raw options string, e.g. "A. 施工方案 B. 质量管理 C. 安全培训 D. 进度控制"
 * @param answerLetters  Answer letters from KB, e.g. ["A"] or ["A", "B"]
 * @param onScreenOptions List of pairs (onScreenLetter, onScreenText), e.g. [("A", "进度控制"), ("B", "施工方案"), ...]
 * @return List of on-screen letters to click, e.g. ["B"] instead of ["A"]
 */
internal fun resolveOnScreenLetters(
    kbOptionsText: String,
    answerLetters: List<String>,
    onScreenOptions: List<Pair<String, String>>
): List<String> {
    // 1. Parse KB options to get letter→text map
    val kbOptionMap = parseOptionMapInline(kbOptionsText)
    if (kbOptionMap.isEmpty()) return answerLetters // fallback: use original letters

    val resolved = mutableListOf<String>()
    for (ansLetter in answerLetters) {
        val kbText = kbOptionMap[ansLetter]
        if (kbText == null) {
            resolved.add(ansLetter) // fallback: keep original letter
            continue
        }
        // Find best matching on-screen option by text similarity
        val bestMatch = onScreenOptions.maxByOrNull { (_, screenText) ->
            computeTextSimilarity(kbText, screenText)
        }
        if (bestMatch != null) {
            val similarity = computeTextSimilarity(kbText, bestMatch.second)
            if (similarity >= 0.4f) {
                resolved.add(bestMatch.first)
                continue
            }
        }
        // No good match found — keep original letter as fallback
        resolved.add(ansLetter)
    }
    return resolved
}

/**
 * Simple text similarity: character-set Jaccard after whitespace normalization.
 * Returns 0.0–1.0.
 */
internal fun computeTextSimilarity(a: String, b: String): Float {
    val normalizedA = a.trim().replace(Regex("\\s+"), "")
    val normalizedB = b.trim().replace(Regex("\\s+"), "")
    if (normalizedA == normalizedB) return 1.0f
    if (normalizedA.isEmpty() || normalizedB.isEmpty()) return 0.0f
    // Exact substring match (one contains the other) → high similarity
    if (normalizedA in normalizedB || normalizedB in normalizedA) return 0.85f
    // Character overlap Jaccard
    val setA = normalizedA.toSet()
    val setB = normalizedB.toSet()
    val intersection = (setA intersect setB).size
    val union = (setA union setB).size
    return if (union > 0) intersection.toFloat() / union.toFloat() else 0.0f
}
