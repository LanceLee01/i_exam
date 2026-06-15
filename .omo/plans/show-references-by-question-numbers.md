# Fix: 参考资料按 LLM 答题的题号显示

## 改动

### 1. `SidebarStateRenderer.kt` Done 渲染区

在显示参考资料时，加上题号标签。

```kotlin
if (s.references.isNotEmpty()) {
    // 计算 LLM 答的题号列表
    val llmQuestions = s.questionSources
        .filterValues { it.contains("AI") || it.contains("LLM") }
        .keys
        .sorted()
    
    Spacer(Modifier.height(8.dp))
    SectionHeader("🔍 参考资料${if (llmQuestions.isNotEmpty()) "（题 ${llmQuestions.joinToString(", ")}）" else ""}")
    Column {
        s.references.take(5).forEachIndexed { index, ref ->
            Row(modifier = Modifier.padding(vertical = 2.dp)) {
                Text(
                    text = "[${index + 1}] ",
                    color = MaterialTheme.colorScheme.primary,
                    fontSize = 12.sp
                )
                Text(
                    text = ref.title,
                    color = Color.White.copy(alpha = 0.8f),
                    fontSize = 12.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
            }
            Text(
                text = ref.url,
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.6f),
                fontSize = 10.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
    Spacer(Modifier.height(8.dp))
}
```

显示效果：
```
🔍 参考资料（题 2, 4, 5, 8, 9, 10）
- [1] Title1
  url1
- [2] Title2  
  url2
```
