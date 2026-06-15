# Fix: 答案格式规范化 + 真值识别

## 改动

### 1. `SolvePipeline.kt` — 增强 `parseL4Answer` 解析器

支持多种格式：
- `[1] A`、`【1】A`
- `1. A`、`1) A`、`1、A`、`1 A`
- `第1题：A`、`答案1：A`

并规范化真值答案：
- `对`、`yes`、`✓` → `正确`
- `错`、`no`、`×`、`✗` → `错误`

### 2. `SolvePipeline.kt` — 合并前规范化答案

新增 `normalizeAnswer(text: String): String` 函数：
- 检测 `正确/对/yes/✓` → 输出 `正确`
- 检测 `错误/错/no/×/✗` → 输出 `错误`
- 提取所有 A-F 字母，空格分隔
- 其他情况原样返回

### 3. `parseL4Answer` 重写

```kotlin
private fun parseL4Answer(l4Answer: String, expectedNumbers: List<Int>): Map<Int, String> {
    val result = mutableMapOf<Int, String>()
    // 多种题目-答案分隔符
    val pattern = Regex("""(?:[\\[【第]?(\d+)[\\]】题]?[\s.、:：)）]*)([^\\[【第\n]+?)(?=\s*[\\[【第]?\d+[\\]】题]?[\s.、:：)）]|$)""", RegexOption.MULTILINE)
    pattern.findAll(l4Answer).forEach { match ->
        val qNum = match.groupValues[1].toIntOrNull() ?: return@forEach
        val raw = match.groupValues[2].trim()
        val normalized = normalizeAnswer(raw)
        if (qNum in expectedNumbers && normalized.isNotBlank()) {
            result[qNum] = normalized
        }
    }
    return result
}

private fun normalizeAnswer(text: String): String {
    val t = text.trim()
    // 真值
    if (Regex("""^(正确|对|yes|✓|√|是|true)$""", RegexOption.IGNORE_CASE).matches(t)) return "正确"
    if (Regex("""^(错误|错|no|×|✗|否|false)$""", RegexOption.IGNORE_CASE).matches(t)) return "错误"
    // 单选/多选：提取 A-F 字母
    val letters = Regex("""[A-Fa-f]""").findAll(t).map { it.value.uppercase() }.toSet()
    if (letters.isNotEmpty()) return letters.sorted().joinToString(" ")
    // 其他原样返回（截断 20 字符避免过长）
    return t.take(20)
}
```
