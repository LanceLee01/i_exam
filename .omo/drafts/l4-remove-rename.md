# Draft: parseL4Answer 移除 + 应用改名

## Requirements (Confirmed)
- **parseL4Answer**: 彻底去掉正则解析功能。改用什么方式？直接用 LLM 原始输出？还是用 `combined` 格式代替？
- **应用名**: 改为"i考助手"

## Technical Context
- `parseL4Answer` 在 `SolvePipeline.kt:469-485`
- 调用位置：`callLLMAndCombine` 第 439 行 `val l4Parsed = parseL4Answer(l4Answer, unmatchedQ)`
- 如果不解析，则 `l4Parsed` 为空 → `combined = formatCombinedAnswer(空, l1Answers)` → 只有 L1 答案
- 那 L4 的答案怎么合并进去？

## 需要确认
- [ ] parseL4Answer 去掉后，L4 答案如何格式化？
- [ ] 是否保留 `normalizeAnswer`？
- [ ] 应用名在哪些文件里改？

## Scope Boundaries
- INCLUDE: parseL4Answer 移除 + 应用改名
- EXCLUDE: (待定)
