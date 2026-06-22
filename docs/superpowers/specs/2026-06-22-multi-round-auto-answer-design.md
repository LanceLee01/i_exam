# 多轮次自动答题 — 设计文档

> 日期: 2026-06-22 | 状态: 已确认

## 背景

当前考试助手一次只能处理一页题目。i国网考试每页仅显示 1 道题，100 题的考试需要手动重复"读屏→解答→填入→翻页"100 次。需要实现自动翻页、全量扫描、统一解答、逐页填入的功能。

## 考试页面结构（实测发现）

```
┌──────────────────────────┐
│ 在线考试          剩余时间  │
│ 6月22日-D一线人员/47-...  │
├──────────────────────────┤
│                          │
│  1、单选题                │
│  题目正文...              │
│                          │
│  A.选项1  B.选项2         │
│  C.选项3  D.选项4         │
│                          │
├──────────────────────────┤
│  上一页   1/100   下一页   │
└──────────────────────────┘
```

- 每页 1 道题，共 N 页
- 底部固定：`上一页` / `当前页/总页数` / `下一页`（可点击按钮）
- 翻页方式：点击"下一页"或"上一页"节点（非滚动）
- 最后一页可能显示"提交答卷"
- 存在干扰文本：`gradeEvaluationPlan`、Unicode 私有区字符（``、``）

## 整体流程

```
用户点击「多轮答题」
      │
      ▼
┌─ 阶段1: 全量扫描 ────────────────────────────────┐
│                                                  │
│  [读取当前页] → 记录题目文本                        │
│       │                                          │
│       ▼                                          │
│  检查底部:                                        │
│  ├─ 有「下一页」且 当前页 < 总页数?                  │
│  │   → 点击「下一页」→ delay(600ms) → 循环          │
│  ├─ 当前页 == 总页数 (N/N)? → 停止翻页              │
│  └─ 发现「提交答卷」? → 停止翻页                    │
│                                                  │
│  上限: 100 次翻页（安全阀）                          │
│  → 输出: 全部题目的完整文本                         │
└──────────────────────────────────────────────────┘
      │
      ▼
┌─ 阶段2: 统一解答 ──────────────────────────────────┐
│  SolvePipeline.solve(全部题目文本)                  │
│  → L1 题库匹配 + L4 LLM 统一答题                     │
│  → 输出: 所有题目的答案                              │
└──────────────────────────────────────────────────┘
      │
      ▼
┌─ 阶段3: 逐页填入 ──────────────────────────────────┐
│  回退到第1页（连续点击「上一页」至 1/N）              │
│  → 填入当前页答案 → 点击「下一页」                    │
│  → 重复直到最后一页                                  │
│  → ⚠️ 不点击「提交答卷」                             │
└──────────────────────────────────────────────────┘
```

## 模块设计

### 1. MultiRoundRunner（多轮引擎）

**位置**: `app/src/main/java/com/examhelper/app/pipeline/MultiRoundRunner.kt`

**职责**: 编排多轮流程，管理状态转换

```kotlin
class MultiRoundRunner(
    private val context: Context,
    private val pipeline: SolvePipeline
) {
    sealed class MultiRoundState {
        data object Idle
        data class Scanning(val currentPage: Int, val totalPages: Int)
        data class Solving(val progress: Float, val etaSec: Int)
        data class Filling(val currentPage: Int, val totalPages: Int)
        data class Done(val totalAnswered: Int)
        data class Error(val message: String)
    }

    val multiRoundState: StateFlow<MultiRoundState>

    suspend fun start()
    fun cancel()
}
```

**核心方法**:
- `scanAllPages()`: 循环翻页读屏 → 拼接全部题目
- `solveAll()`: 调用 `pipeline.solve(combinedText)`
- `fillAllPages()`: 回退到首页 → 逐页填入
- `clickNextPage()`: 搜索"下一页"节点并点击
- `clickPrevPage()`: 搜索"上一页"节点并点击
- `parseProgress()`: 从页面文本中提取 `N/M` 进度
- `navigateToPage(target)`: 点击"下一页"/"上一页"直到到达目标页

### 2. 新增 SidebarState

**位置**: `ExtractedTextBus.kt` — 新增 `SidebarState.MultiRound`

```kotlin
sealed class SidebarState {
    // ... existing states ...
    data class MultiRound(
        val phase: MultiPhase,
        val currentPage: Int = 0,
        val totalPages: Int = 0,
        val progress: Float = 0f,
        val message: String = ""
    ) : SidebarState()

    enum class MultiPhase { SCANNING, SOLVING, FILLING, DONE, ERROR }
}
```

### 3. 侧边栏 UI

**位置**: `SidebarPanel.kt` — 新增长显式的「多轮答题」按钮

**位置**: `SidebarStateRenderer.kt` — 新增 `MultiRound` 状态渲染：

```
┌────────────────────────┐
│  🔄 多轮答题            │
│                        │
│  ● 扫描中...            │
│  📖 第 25/100 页        │
│                        │
│  ━━━━━━━━━━━━━━ 25%   │
│                        │
│  [ ⏹ 停止 ]            │
└────────────────────────┘
```

阶段显示：
- SCANNING: 进度条 + 页数
- SOLVING: 复用现有 Loading 效果
- FILLING: 进度条 + 已填页数
- DONE: "已完成 N 道题"
- ERROR: 错误信息 + 重试按钮

### 4. ExamAccessibilityService 修改

新增方法（在 `performAutoClick` 基础上）：

- `clickPageButton(text: String)`: 搜索文字为"下一页"/"上一页"的可点击节点并点击
- `extractCurrentPageText()`: 仅读取当前页题目（不滚动）
- `parsePageProgress(text: String)`: 从底部文字中提取 `当前页/总页数`

## 状态流

```
SidebarState.MultiRound(SCANNING, page=1, total=100)
  → SCANNING, page=2 → ... → SCANNING, page=100
  → SOLVING(progress=0.5, etaSec=15)
  → FILLING(page=1, total=100)
  → FILLING, page=2 → ... → FILLING, page=100
  → DONE(totalAnswered=100)
```

## 停止机制

- 用户点击「停止」按钮 → `MultiRoundRunner.cancel()` → 当前阶段安全退出
- 翻页上限：`scanAllPages()` 最多翻 100 页
- 每次翻页验证进度文字是否递增（防止"下一页"按钮失效时死循环）
- 空页面保护：连续 3 次读到空文本 → 报错退出

## 干扰文本过滤

在扫描结果拼接时过滤以下内容：
- `gradeEvaluationPlan` 
- `在线考试`
- `剩余时间` 行
- `上一页` / `下一页` / 进度文字（`N/M`）
- Unicode 私有区字符（``-``）
- ``、`` 等特殊字符
- 考试标题行（含日期和路径的）
