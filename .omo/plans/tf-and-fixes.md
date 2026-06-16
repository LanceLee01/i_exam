# 判断题映射 + 去预估时间 + LLM触发诊断

## TL;DR
> 3个修复：判断题答案映射（A→正确）、去掉预估时间显示、LLM流程日志

## Task 1: 判断题答案映射
- 位置: `SolvePipeline.kt` `tryExcelMatchAll()` 和 KB helper
- KB source 列有 `"A-正确|B-错误"` 映射，但 answer 存的是 `"A"`
- 需要解析 source 列把字母转成文字
- 影响自动填入 toggle 匹配

## Task 2: 去掉预估总时间
- 位置: `SidebarStateRenderer.kt:78`
- 删掉 `${totalEst}s` 部分

## Task 3: LLM触发诊断日志
- 位置: `SolvePipeline.kt` `solve()`
- 加 Log.d 输出 allQ/l1Keys/unmatchedQ
- 帮助判断为什么 LLM 没触发

## Scope
- IN: 3个修复
- OUT: KB重新导入、UI大改
