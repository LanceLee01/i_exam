# Fix: 简化 L4 解析器 regex

## 问题
`parseL4Answer` 中使用复杂的多字符类嵌套 regex，JVM PatternSyntaxException 报错「Missing closing bracket in character class near index 89」。

## 修复
把复杂 regex 替换为简单且明确的两次扫描方案。

**改前**：
```kotlin
val pattern = Regex("""(?:[\\[【第]?(\d+)[\\]】题]?[\s.、:：)）]*)([^\\[【第\n]+?)(?=\s*[\\[【第]?\d+[\\]】题]?[\s.、:：)）]|$)""", RegexOption.MULTILINE)
```

**改后**：
```kotlin
// 简单分两行扫描
for (line in l4Answer.lines()) {
    val match = Regex("""[\\[【第]?(\d+)[\\]】题]?[\\s\\.\\u3001:\\uff1a)\\uff09]*[:：\\s]+(.+)""").find(line)
    if (match != null) {
        val qNum = match.groupValues[1].toIntOrNull() ?: continue
        val ans = normalizeAnswer(match.groupValues[2].trim())
        if (qNum in expectedNumbers && ans.isNotBlank()) {
            result[qNum] = ans
        }
    }
}
```
