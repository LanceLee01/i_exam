# Fix: 从 contentDescription 读取被遗漏的题目文字

## TL;DR
> **Quick Summary**: `traverseNode()` 只检查了 `AccessibilityNodeInfo.text`，没检查 `contentDescription`。有些 App 把文字放在后者里。改成两者都取。
>
> **Deliverables**:
> - 修改 `ExamAccessibilityService.kt` 中的 `traverseNode`
> - 构建 APK → 安装 → 用户测试
>
> **Estimated Effort**: Quick

---

## Context
- 日志确认：第13题在无障碍树中**没有** `text` 字段
- `contentDescription` 是 Android 无障碍的另一个文字字段，许多自定义 View 用这个而非 `text`
- `performAutoClick()` 和 `searchMatches()` 已经用了 `contentDescription` 作为 fallback，唯独 `traverseNode` 没有！

### Evidence: contentDescription 已在别处使用
```kotlin
// performAutoClick -> findAllClickable (line 248):
val text = node.text?.toString()?.trim()
    ?: node.contentDescription?.toString()?.trim() ?: ""

// searchMatches (line 300):
val text = node.text?.toString()?.trim()
    ?: node.contentDescription?.toString()?.trim() ?: ""
```

## Changes

### In `traverseNode()` (line 198)
Change:
```kotlin
val text = node.text?.toString()?.trim()
```
To:
```kotlin
val text = node.text?.toString()?.trim()
    ?: node.contentDescription?.toString()?.trim()
```

This matches the same pattern already used in `findAllClickable` and `searchMatches`.

### Must NOT do
- Don't change any other methods
- Don't modify any other files

## Verification
- Build APK
- 用户测试：打开 13 题页面 → 点"读取屏幕" → 第13题有文字
