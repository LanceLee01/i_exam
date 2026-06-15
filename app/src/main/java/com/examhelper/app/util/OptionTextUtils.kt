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
