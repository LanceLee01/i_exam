package com.examhelper.app.util

import android.util.Log

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
 * Supports pipe-delimited segments ("A. text1 | B. text2") and space-separated options.
 */
internal fun parseOptionMapInline(text: String): Map<String, String> {
    val map = mutableMapOf<String, String>()
    val letters = ExamConstants.OPTION_LETTERS
    // Non-greedy match + lookahead to avoid capturing subsequent options
    val regex = Regex(
        """([${letters.first}-${letters.last}])[.、．:：)）\-]\s*(.*?)(?=\s*[${letters.first}-${letters.last}][.、．:：)）\-]|$)"""
    )
    // Split by | first so pipe-delimited blocks don't pollute each other
    val segments = text.split(Regex("[|]+")).filter { it.isNotBlank() }
    for (segment in segments) {
        regex.findAll(segment.trim()).forEach { match ->
            val optionText = match.groupValues[2].trim()
            if (optionText.length in 1..60) {
                map[match.groupValues[1]] = optionText
            }
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
    if (kbOptionMap.isEmpty()) {
        android.util.Log.d("ResolveDebug", "kbOptionMap empty, falling back to original letters=$answerLetters")
        return answerLetters
    }

    android.util.Log.d("ResolveDebug", "kbOptionMap=$kbOptionMap")
    android.util.Log.d("ResolveDebug", "onScreenOptions=$onScreenOptions")
    android.util.Log.d("ResolveDebug", "answerLetters=$answerLetters")

    val resolved = mutableListOf<String>()
    val usedScreenLetters = mutableSetOf<String>()
    for (ansLetter in answerLetters) {
        val kbText = kbOptionMap[ansLetter]
        if (kbText == null) {
            android.util.Log.w("ResolveDebug", "ansLetter=$ansLetter NOT FOUND in kbOptionMap, using fallback")
            if (ansLetter !in usedScreenLetters) {
                resolved.add(ansLetter)
                usedScreenLetters.add(ansLetter)
            } else {
                val unused = onScreenOptions.firstOrNull { (letter, _) -> letter !in usedScreenLetters }
                if (unused != null) {
                    usedScreenLetters.add(unused.first)
                    resolved.add(unused.first)
                } else {
                    resolved.add(ansLetter)
                }
            }
            continue
        }
        // Find best matching on-screen option by text similarity, excluding already-matched ones
        val bestMatch = onScreenOptions
            .filter { (letter, _) -> letter !in usedScreenLetters }
            .maxByOrNull { (_, screenText) ->
                computeTextSimilarity(kbText, screenText)
            }
        if (bestMatch != null) {
            val similarity = computeTextSimilarity(kbText, bestMatch.second)
            val kbTextPreview = kbText.take(40)
            val screenTextPreview = bestMatch.second.take(40)
            android.util.Log.d("ResolveDebug", "ansLetter=$ansLetter bestMatch=${bestMatch.first} similarity=${"%.2f".format(similarity)} kb='$kbTextPreview' screen='$screenTextPreview'")
            if (similarity >= 0.4f) {
                usedScreenLetters.add(bestMatch.first)
                resolved.add(bestMatch.first)
                continue
            } else {
                android.util.Log.w("ResolveDebug", "ansLetter=$ansLetter bestMatch=${bestMatch.first} similarity=$similarity < 0.4, falling back")
            }
        }
        // No good match found — keep original letter as fallback
        android.util.Log.d("ResolveDebug", "ansLetter=$ansLetter no good match, keeping original")
        resolved.add(ansLetter)
    }
    android.util.Log.d("ResolveDebug", "final resolved=$resolved")
    return resolved
}

/**
 * Text similarity combining character-set Jaccard and bigram Jaccard.
 * Bigrams preserve character ordering info to distinguish
 * "先降级再扣分" from "先扣分再降级" (same char set, different order).
 * Returns 0.0–1.0.
 */
internal fun computeTextSimilarity(a: String, b: String): Float {
    val normalizedA = a.trim().replace(Regex("\\s+"), "")
    val normalizedB = b.trim().replace(Regex("\\s+"), "")
    if (normalizedA == normalizedB) return 1.0f
    if (normalizedA.isEmpty() || normalizedB.isEmpty()) return 0.0f
    // Exact substring match (one contains the other) → high similarity
    if (normalizedA in normalizedB || normalizedB in normalizedA) return 0.85f

    // 1) Character-set Jaccard (order-insensitive)
    val setA = normalizedA.toSet()
    val setB = normalizedB.toSet()
    val charIntersect = (setA intersect setB).size
    val charUnion = (setA union setB).size
    val charScore = if (charUnion > 0) charIntersect.toFloat() / charUnion.toFloat() else 0f

    // 2) Bigram Jaccard (order-sensitive)
    val bigramsA = normalizedA.windowed(2).toSet()
    val bigramsB = normalizedB.windowed(2).toSet()
    val bigramIntersect = (bigramsA intersect bigramsB).size
    val bigramUnion = (bigramsA union bigramsB).size
    val bigramScore = if (bigramUnion > 0) bigramIntersect.toFloat() / bigramUnion.toFloat() else 0f

    // Blend: weight bigrams more (they reveal order differences)
    // For short text (<6 chars), weight bigrams even more
    val avgLen = (normalizedA.length + normalizedB.length) / 2
    val bigramWeight = if (avgLen <= 6) 0.70f else 0.55f
    val charWeight = 1.0f - bigramWeight

    return charScore * charWeight + bigramScore * bigramWeight
}
