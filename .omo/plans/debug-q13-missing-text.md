# Debug: 第13题读取屏幕后缺少题目文字

## TL;DR
> **Quick Summary**: 用户在知识库导入完成后，测试读取屏幕功能时，前 12 题文字正常，第 13 题（判断题，最后一题）没有题目文字。此计划通过添加详细日志定位根因。
>
> **Deliverables**:
> - 添加了详细日志的 ExamAccessibilityService.kt (调试版)
> - 调试版 APK
> - 日志分析 → 根因结论
>
> **Estimated Effort**: Short
> **Parallel Execution**: NO - sequential

---

## Context

### Bug Report
- 考试共 13 题，第 13 题是判断题
- 点"读取屏幕"后，预览中前 12 题文字正常
- 第 13 题的题干文字没有显示（但题号 13 出现了）
- 发生在文字捕获阶段（ExamAccessibilityService），与 Excel 导入无关

### Hypothesis Pool

| # | 假设 | 代码位置 | 概率 |
|---|------|---------|------|
| H1 | 第13题题干节点不在无障碍树中（App 渲染方式不同） | `traverseNode()` | 高 |
| H2 | 题干文字所在行长度 >500 被 `cleanAndFormat` 丢弃 | `cleanAndFormat()` L315 | 中 |
| H3 | 题干文字和前面某行完全重复，被 `seen` 去重 | `cleanAndFormat()` L316 | 低 |
| H4 | 题干文字所在 AccessibilityNodeInfo.text 为空 | `traverseNode()` L152 | 中 |

### Target File
- `app/src/main/java/com/examhelper/app/service/ExamAccessibilityService.kt`

---

## Work Objectives

### Core Objective
通过日志确认第13题题干文字在捕获流程的哪一步丢失。

### Concrete Deliverables
- 添加了详细日志的 `ExamAccessibilityService.kt`
- 调试版 APK 并安装到设备
- 日志分析结论

### Definition of Done
- [ ] 用户复现问题后将日志提供给我
- [ ] 我根据日志确定根因（H1-H4 之一或新发现）

### Must Have
- 日志必须打印每一行原始捕获文字（前120字预览）
- 日志必须打印 `cleanAndFormat()` 后的最终完整文本
- 日志必须用 Log.d(TAG, ...)，用户通过 `adb logcat -s ExamAccessibility` 查看

### Must NOT Have
- 不改动其他业务逻辑
- 不改变 cleanAndFormat 的过滤行为（调试版只加日志，不做修复）

---

## Verification Strategy

> 无单元测试。日志即验证。通过 adb logcat 查看日志。

---

## Execution Strategy

```
Wave 1 (Single task - everything):
├── Task 1: Add debug logging + build APK + deploy
```

---

## TODOs

- [ ] 1. Add debug logging to ExamAccessibilityService.kt, build APK, deploy

  **What to do**:
  - 在 `traverseNode()` 收集完 `lines` 后，打印每行的索引、长度和前120个字符：
    ```kotlin
    lines.forEachIndexed { idx, line ->
        val preview = line.take(120).replace("\n", "\\n")
        Log.d(TAG, "  line[$idx] len=${line.length} text=\"$preview\"")
    }
    ```
  - 在 `cleanAndFormat(lines)` 返回 `result` 后，打印最终结果全文：
    ```kotlin
    Log.d(TAG, "Result text:\n$result")
    ```
  - `Extracted ${lines.size} lines` 已存在，保留
  - `Result text length: ${result.length}` 已存在，保留
  - 修改完成后：`./gradlew assembleDebug` 编译 APK
  - `adb install -r app/build/outputs/apk/debug/app-debug.apk` 安装到设备

  **Must NOT do**:
  - 不要修改 `cleanAndFormat` 的过滤逻辑（500字限制、去重等）
  - 不要改动任何业务逻辑
  - 不要改其他文件

  **Recommended Agent Profile**:
  - **Category**: `quick`
    - Reason: 单文件、两处日志添加，5分钟可完成
  - **Skills**: none needed

  **Parallelization**:
  - **Can Run In Parallel**: NO
  - **Blocks**: Task 2（用户复现 + 给我日志）
  - **Blocked By**: None

  **References**:
  - `ExamAccessibilityService.kt:308-323` - `cleanAndFormat()` 和日志插入点
  - `ExamAccessibilityService.kt:106-112` - 日志插入点（lines 收集后）
  - `.omo/plans/apk-build-deploy-test.md` - 上次 APK 构建部署流程参考

  **Acceptance Criteria**:
  - [ ] 日志已添加，编译通过
  - [ ] APK 安装到设备成功

  **QA Scenarios**:

  ```
  Scenario: 日志添加后编译通过
    Tool: Bash
    Preconditions: 代码修改完成
    Steps:
      1. ./gradlew assembleDebug
    Expected Result: BUILD SUCCESSFUL，APK 生成
    Evidence: .omo/evidence/debug-q13/build-output.txt

  Scenario: APK 安装到设备
    Tool: Bash
    Preconditions: 上一步 build 成功
    Steps:
      1. adb install -r app/build/outputs/apk/debug/app-debug.apk
    Expected Result: Success 提示
    Evidence: .omo/evidence/debug-q13/install-output.txt
  ```

  **Commit**: NO (调试版，不提交)

---

## Success Criteria

### Verification Commands
```bash
# 启动 app，触发读取屏幕后，查看日志
adb logcat -s ExamAccessibility
```

### 日志预期
- `Extracted N lines` - 显示捕获了多少行
- `line[0] len=xx text="..."` - 每一行的原始内容（前120字）
- `Result text length: N` - 最终结果长度
- `Result text:` 后跟全文
