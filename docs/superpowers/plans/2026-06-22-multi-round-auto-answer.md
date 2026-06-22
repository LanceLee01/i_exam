# 多轮次自动答题 — 实现计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 实现自动翻页、全量扫描全部考试题目、统一 LLM 解答、逐页自动填入的多轮自动答题功能。

**Architecture:** 新增 `MultiRoundRunner` 编排三阶段流程（扫描→解答→填入），通过 `ExtractedTextBus` 的新状态 `SidebarState.MultiRound` 驱动侧边栏进度面板 UI，复用现有的 `ExamAccessibilityService` 做页面操作（读屏/点击选项/点击翻页按钮）。

**Tech Stack:** Kotlin, Compose, StateFlow, AccessibilityService (现有架构内)

## Global Constraints

- 翻页上限: 100 页
- 每次翻页后等待 600ms
- 题干保护: 连续 3 次读到空文本 → 报错退出
- 不点击「提交答卷」按钮
- 干扰文本过滤: `gradeEvaluationPlan`, Unicode 私有区字符, 考试标题行, 进度文字

---

### Task 1: 新增 SidebarState.MultiRound

**Files:**
- Modify: `app/src/main/java/com/examhelper/app/util/ExtractedTextBus.kt`

**Interfaces:**
- Consumes: (none)
- Produces: `SidebarState.MultiRound` sealed class + `MultiPhase` enum

在 `SidebarState` 中新增多轮答题状态，用于驱动侧边栏进度面板。

- [ ] **Step 1: 在 ExtractedTextBus.kt 的 SidebarState 中新增 MultiRound 状态**

```kotlin
// 在 SidebarState sealed class 内部，所有现有状态之后新增:

    enum class MultiPhase { SCANNING, SOLVING, FILLING, DONE, ERROR }

    data class MultiRound(
        val phase: MultiPhase,
        val currentPage: Int = 0,
        val totalPages: Int = 0,
        val progress: Float = 0f,
        val answeredCount: Int = 0,
        val message: String = "",
        val errorMessage: String = ""
    ) : SidebarState()
```

- [ ] **Step 2: Build check**

```bash
./gradlew :app:compileDebugKotlin
```

Expected: BUILD SUCCESSFUL

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/com/examhelper/app/util/ExtractedTextBus.kt
git commit -m "feat: add SidebarState.MultiRound for multi-round auto-answer progress"
```

---

### Task 2: 新增 ScanPageFilter（干扰文本过滤器）

**Files:**
- Create: `app/src/main/java/com/examhelper/app/pipeline/ScanPageFilter.kt`

**Interfaces:**
- Consumes: (none)
- Produces: `object ScanPageFilter` — `fun filter(text: String): String`, `fun extractProgress(text: String): Pair<Int, Int>?`

在扫描阶段，从读屏的原始文本中过滤干扰内容，并提取页面进度。

- [ ] **Step 1: Create ScanPageFilter**

```kotlin
package com.examhelper.app.pipeline

object ScanPageFilter {

    private val FILTER_PATTERNS = listOf(
        Regex("""在线考试"""),
        Regex("""剩余时间[：:]\s*\d{2}:\d{2}:\d{2}"""),
        Regex("""gradeEvaluationPlan"""),
        Regex("""^上一页$"""),
        Regex("""^下一页$"""),
        Regex("""[-]"""),       // Unicode Private Use Area
        Regex("""^[ -~]$""")         // single ASCII char
    )

    private val PROGRESS_REGEX = Regex("""(\d+)\s*/\s*(\d+)""")

    /** Filter noise text, keep only question content */
    fun filter(text: String): String {
        var result = text
        for (pattern in FILTER_PATTERNS) {
            result = pattern.replace(result, "")
        }
        // Filter exam title lines (with date and path)
        result = result.replace(Regex("""\d+月\d+日-.+/\d+-.+/"""), "")
        // Normalize blank lines
        result = result.replace(Regex("""\n{3,}"""), "\n\n")
        return result.trim()
    }

    /** Extract N/M progress from text, returns (current, total) or null on failure */
    fun extractProgress(text: String): Pair<Int, Int>? {
        val match = PROGRESS_REGEX.find(text) ?: return null
        val current = match.groupValues[1].toIntOrNull() ?: return null
        val total = match.groupValues[2].toIntOrNull() ?: return null
        return current to total
    }
}
```

- [ ] **Step 2: Build check**

```bash
./gradlew :app:compileDebugKotlin
```

Expected: BUILD SUCCESSFUL

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/com/examhelper/app/pipeline/ScanPageFilter.kt
git commit -m "feat: add ScanPageFilter for multi-round scan text cleaning"
```

---

### Task 3: 新增 PageNavigator（页面操作隔离层）

**Files:**
- Create: `app/src/main/java/com/examhelper/app/service/PageNavigator.kt`

**Interfaces:**
- Consumes: `ScanPageFilter` (Task 2), `ExtractedTextBus` (existing)
- Produces: `class PageNavigator` — `suspend fun readCurrentPage(): String`, `suspend fun clickNextPage(): Boolean`, `suspend fun clickPrevPage(): Boolean`, `suspend fun navigateToFirstPage()`, `suspend fun clickAnswer(answer: String, sourceText: String)`

将页面操作（读屏、翻页、答题点击）封装为独立接口，与 `ExamAccessibilityService` 解耦。

- [ ] **Step 1: Create PageNavigator**

```kotlin
package com.examhelper.app.service

import android.util.Log
import android.view.accessibility.AccessibilityNodeInfo
import com.examhelper.app.pipeline.ScanPageFilter
import com.examhelper.app.util.ExtractedTextBus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.delay

class PageNavigator {
    companion object {
        private const val TAG = "PageNavigator"
        private const val MAX_BACK_PAGES = 100
    }

    /** Read current page text: send RequestExtract, wait for Preview state */
    suspend fun readCurrentPage(): String = withContext(Dispatchers.Default) {
        var result = ""
        ExtractedTextBus.sendEvent(ExtractedTextBus.Event.RequestExtract)

        val deadline = System.currentTimeMillis() + 10_000
        while (result.isEmpty() && System.currentTimeMillis() < deadline) {
            delay(100)
            val state = ExtractedTextBus.sidebarState.value
            if (state is ExtractedTextBus.SidebarState.Preview) {
                result = state.text
            }
        }
        if (result.isEmpty()) Log.w(TAG, "readCurrentPage timed out")
        result
    }

    /** Click 'next page' button, returns success */
    suspend fun clickNextPage(): Boolean = withContext(Dispatchers.Main) {
        clickPageButton("下一页")
    }

    /** Click 'previous page' button, returns success */
    suspend fun clickPrevPage(): Boolean = withContext(Dispatchers.Main) {
        clickPageButton("上一页")
    }

    /** Navigate back to first page */
    suspend fun navigateToFirstPage() {
        for (i in 1..MAX_BACK_PAGES) {
            val text = readCurrentPage()
            val progress = ScanPageFilter.extractProgress(text)
            if (progress != null && progress.first <= 1) break
            val clicked = clickPrevPage()
            if (!clicked) break
            delay(400)
        }
    }

    /** Click answer options on the current page */
    suspend fun clickAnswer(answer: String, sourceText: String) {
        ExtractedTextBus.sendEvent(ExtractedTextBus.Event.ClickAnswer(answer, sourceText))
        // Wait for auto-click to finish (1500ms per question + buffer)
        val answerCount = answer.lines().size.coerceAtLeast(1)
        delay(1500L * answerCount + 2000L)
    }

    // Internal: find and click a button by text
    private fun clickPageButton(targetText: String): Boolean {
        val root = getRootNode() ?: return false
        val matches = mutableListOf<AccessibilityNodeInfo>()
        collectButtonNodes(root, matches, targetText)
        root.recycle()

        if (matches.isEmpty()) {
            Log.d(TAG, "clickPageButton: '$targetText' not found")
            return false
        }

        val clicked = matches.firstOrNull { it.isClickable } ?: matches.first()
        val parent = clicked.parent
        val toClick = if (parent?.isClickable == true) parent else clicked
        val result = toClick.performAction(AccessibilityNodeInfo.ACTION_CLICK)
        Log.d(TAG, "clickPageButton: '$targetText' clicked=$result")

        matches.forEach { it.recycle() }
        if (toClick != clicked) clicked.recycle()
        return result
    }

    private fun getRootNode(): AccessibilityNodeInfo? {
        return try {
            val clz = Class.forName("android.view.accessibility.AccessibilityInteractionClient")
            val method = clz.getDeclaredMethod("getInstance")
            method.isAccessible = true
            val client = method.invoke(null)
            val getRootMethod = client.javaClass.getDeclaredMethod(
                "findAccessibilityNodeInfoByAccessibilityId",
                Int::class.java, Long::class.java, Int::class.java,
                Int::class.javaPrimitiveType, android.os.Bundle::class.java
            )
            getRootMethod.isAccessible = true
            @Suppress("DEPRECATION")
            val node = getRootMethod.invoke(client, 0, Long.MAX_VALUE, 0, 0, null)
                as? AccessibilityNodeInfo
            node
        } catch (e: Exception) {
            Log.w(TAG, "Failed to get root node via reflection: ${e.message}")
            null
        }
    }

    private fun collectButtonNodes(
        node: AccessibilityNodeInfo,
        results: MutableList<AccessibilityNodeInfo>,
        target: String
    ) {
        val text = node.text?.toString()?.trim()
            ?: node.contentDescription?.toString()?.trim() ?: ""
        if (text == target && node.isVisibleToUser) {
            results.add(node)
            return
        }
        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            collectButtonNodes(child, results, target)
            child.recycle()
        }
    }
}
```

- [ ] **Step 2: Build check**

```bash
./gradlew :app:compileDebugKotlin
```

Expected: BUILD SUCCESSFUL

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/com/examhelper/app/service/PageNavigator.kt
git commit -m "feat: add PageNavigator for page-level operations"
```

---

### Task 4: 新增 MultiRoundRunner（多轮引擎）

**Files:**
- Create: `app/src/main/java/com/examhelper/app/pipeline/MultiRoundRunner.kt`

**Interfaces:**
- Consumes: `ScanPageFilter` (Task 2), `PageNavigator` (Task 3), `SidebarState.MultiRound` (Task 1), `SolvePipeline` (existing), `ExtractedTextBus` (existing)
- Produces: `class MultiRoundRunner` — `fun start(scope: CoroutineScope)`, `fun cancel()`, `val state: StateFlow<MultiRoundState>`

多轮引擎编排三阶段流程。这是本功能的核心模块。

- [ ] **Step 1: Create MultiRoundRunner**

```kotlin
package com.examhelper.app.pipeline

import android.util.Log
import com.examhelper.app.service.PageNavigator
import com.examhelper.app.util.ExtractedTextBus
import com.examhelper.app.util.ExtractedTextBus.SidebarState
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

class MultiRoundRunner(
    private val pipeline: SolvePipeline,
    private val pageNavigator: PageNavigator
) {
    companion object {
        private const val TAG = "MultiRoundRunner"
        private const val MAX_PAGES = 100
        private const val PAGE_WAIT_MS = 600L
        private const val MAX_CONSECUTIVE_EMPTY = 3
        private const val SOLVE_TIMEOUT_MS = 300_000L
    }

    private val _state = MutableStateFlow<MultiRoundState>(MultiRoundState.Idle)
    val state: StateFlow<MultiRoundState> = _state.asStateFlow()

    private var job: Job? = null
    private var cancelled = false

    sealed class MultiRoundState {
        data object Idle : MultiRoundState()
        data class Scanning(val currentPage: Int, val totalPages: Int) : MultiRoundState()
        data class Solving(val progress: Float, val etaSec: Int) : MultiRoundState()
        data class Filling(val currentPage: Int, val totalPages: Int, val answeredCount: Int) : MultiRoundState()
        data class Done(val totalAnswered: Int) : MultiRoundState()
        data class Error(val message: String) : MultiRoundState()
    }

    fun start(scope: CoroutineScope) {
        cancelled = false
        job = scope.launch(Dispatchers.Default) {
            try {
                // Phase 1: Scan all pages
                val allPages = scanAllPages()
                if (cancelled) return@launch
                if (allPages.isEmpty()) {
                    updateSidebarError("未读取到任何题目")
                    return@launch
                }
                Log.d(TAG, "Scanned ${allPages.size} pages")

                // Phase 2: Solve combined text
                val combined = combinePages(allPages)
                val answer = solveAll(combined)
                if (cancelled) return@launch
                if (answer.isEmpty()) {
                    updateSidebarError("解答失败")
                    return@launch
                }

                // Phase 3: Fill answers page by page
                fillAllPages(answer, allPages.size)
            } catch (e: CancellationException) {
                Log.d(TAG, "Cancelled")
            } catch (e: Exception) {
                Log.e(TAG, "MultiRound failed", e)
                updateSidebarError(e.message ?: "未知错误")
            }
        }
    }

    fun cancel() {
        cancelled = true
        job?.cancel()
    }

    // ── Phase 1: Scan ──

    private suspend fun scanAllPages(): Map<Int, String> {
        val pages = mutableMapOf<Int, String>()
        var consecutiveEmpty = 0
        var lastPage = -1

        for (round in 1..MAX_PAGES) {
            if (cancelled) return emptyMap()

            val text = pageNavigator.readCurrentPage()
            val filtered = ScanPageFilter.filter(text)
            val progress = ScanPageFilter.extractProgress(text)
            val (current, total) = progress ?: (round to -1)

            if (filtered.isBlank()) {
                consecutiveEmpty++
                if (consecutiveEmpty >= MAX_CONSECUTIVE_EMPTY) {
                    Log.w(TAG, "Stopping scan: $MAX_CONSECUTIVE_EMPTY consecutive empty pages")
                    break
                }
            } else {
                consecutiveEmpty = 0
                pages[current.if (it > 0) it else round] = filtered
            }

            _state.value = MultiRoundState.Scanning(current, total)
            updateSidebarMulti(SidebarState.MultiPhase.SCANNING, currentPage = current, totalPages = total)

            // Stop conditions
            if (current == lastPage) { Log.w(TAG, "Page stuck, stopping"); break }
            lastPage = current
            if (current > 0 && current >= total) break

            // Next page
            val clicked = pageNavigator.clickNextPage()
            if (!clicked) { Log.d(TAG, "No next page button"); break }
            delay(PAGE_WAIT_MS)
        }
        return pages
    }

    private fun combinePages(pages: Map<Int, String>): String {
        return pages.entries.sortedBy { it.key }.joinToString("\n\n") { (_, text) -> text }
    }

    // ── Phase 2: Solve ──

    private suspend fun solveAll(combinedText: String): String {
        _state.value = MultiRoundState.Solving(0f, 0)
        updateSidebarMulti(SidebarState.MultiPhase.SOLVING, progress = 0f)

        pipeline.solve(combinedText)

        var answer = ""
        var done = false
        val collection = ExtractedTextBus.sidebarState.collect { s ->
            if (s is SidebarState.Done && !done) {
                answer = s.answer; done = true
            }
        }
        val deadline = System.currentTimeMillis() + SOLVE_TIMEOUT_MS
        while (!done && !cancelled && System.currentTimeMillis() < deadline) {
            delay(200)
        }
        collection.cancel()
        return if (cancelled) "" else answer
    }

    // ── Phase 3: Fill ──

    private suspend fun fillAllPages(answer: String, totalPages: Int) {
        pageNavigator.navigateToFirstPage()

        val pairs = parseAnswerPairs(answer)
        updateSidebarMulti(SidebarState.MultiPhase.FILLING, currentPage = 1, totalPages = totalPages)

        for (qNum in 1..totalPages) {
            if (cancelled) return

            _state.value = MultiRoundState.Filling(qNum, totalPages, qNum - 1)
            updateSidebarMulti(SidebarState.MultiPhase.FILLING,
                currentPage = qNum, totalPages = totalPages, answeredCount = qNum - 1)

            val current = pairs.firstOrNull { it.first == qNum }
            if (current != null) {
                val line = "[${qNum}] ${current.second.joinToString(" ")}"
                pageNavigator.clickAnswer(line, "")
                delay(200)
            }

            if (qNum >= totalPages) break
            val clicked = pageNavigator.clickNextPage()
            if (!clicked) break
            delay(PAGE_WAIT_MS)
        }

        _state.value = MultiRoundState.Done(totalPages)
        updateSidebarMulti(SidebarState.MultiPhase.DONE,
            totalPages = totalPages, answeredCount = totalPages)
    }

    // ── Helpers ──

    private fun updateSidebarMulti(
        phase: SidebarState.MultiPhase,
        currentPage: Int = 0,
        totalPages: Int = 0,
        progress: Float = 0f,
        answeredCount: Int = 0,
        message: String = ""
    ) {
        ExtractedTextBus.updateSidebarState(
            SidebarState.MultiRound(phase, currentPage, totalPages, progress, answeredCount, message)
        )
    }

    private fun updateSidebarError(msg: String) {
        _state.value = MultiRoundState.Error(msg)
        ExtractedTextBus.updateSidebarState(
            SidebarState.MultiRound(SidebarState.MultiPhase.ERROR, errorMessage = msg)
        )
    }

    private fun parseAnswerPairs(answer: String): List<Pair<Int, List<String>>> {
        val result = mutableListOf<Pair<Int, List<String>>>()
        val pattern = Regex("""[\[【]?(\d+)[\]】]?\s*[.、:：)）]?\s*(.+)""")
        for (line in answer.lines()) {
            val t = line.trim()
            if (t.isEmpty()) continue
            val m = pattern.find(t) ?: continue
            val q = m.groupValues[1].toIntOrNull() ?: continue
            val a = m.groupValues[2].trim()
            val sels = when {
                a.contains("不确定") -> listOf("__UNCERTAIN__")
                a.contains("正确") -> listOf("正确")
                a.contains("错误") -> listOf("错误")
                else -> a.uppercase().filter { it in 'A'..'F' }.map { it.toString() }
            }
            if (sels.isNotEmpty()) result.add(q to sels)
        }
        return result
    }
}
```

- [ ] **Step 2: Build check**

```bash
./gradlew :app:compileDebugKotlin
```

Expected: BUILD SUCCESSFUL

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/com/examhelper/app/pipeline/MultiRoundRunner.kt
git commit -m "feat: add MultiRoundRunner core engine"
```

---

### Task 5: 侧边栏多轮答题 UI

**Files:**
- Modify: `app/src/main/java/com/examhelper/app/ui/sidebar/SidebarPanel.kt`
- Modify: `app/src/main/java/com/examhelper/app/ui/sidebar/SidebarStateRenderer.kt`
- Modify: `app/src/main/java/com/examhelper/app/ui/sidebar/SidebarActions.kt`

**Interfaces:**
- Consumes: `MultiRoundRunner` (Task 4), `SidebarState.MultiRound` (Task 1), `PageNavigator` (Task 3)
- Produces: Composable — MultiRoundButton + progress panel

在侧边栏新增多轮答题的入口和进度展示。

- [ ] **Step 1: Add MultiRoundButton composable to SidebarActions.kt**

At the end of `SidebarActions.kt`, add:

```kotlin
@Composable
fun MultiRoundButton(
    isRunning: Boolean,
    onClick: () -> Unit,
    onStop: () -> Unit,
) {
    val colors = LocalExamHelperColors.current
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(if (isPressed) 0.96f else 1f)

    Button(
        onClick = { if (isRunning) onStop() else onClick() },
        modifier = Modifier.fillMaxWidth().height(48.dp).scale(scale),
        shape = RoundedCornerShape(12.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = if (isRunning) colors.Error.copy(alpha = 0.3f)
                            else Color(0xFF9C27B0),
            contentColor = if (isRunning) colors.Error else Color.White
        ),
        interactionSource = interactionSource
    ) {
        if (isRunning) {
            CircularProgressIndicator(
                modifier = Modifier.size(18.dp),
                color = colors.Error,
                strokeWidth = 2.dp
            )
            Spacer(Modifier.width(8.dp))
            Text("停止多轮", fontSize = 15.sp, fontWeight = FontWeight.Bold)
        } else {
            Icon(Icons.Filled.Refresh, null, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(8.dp))
            Text("多轮自动答题", fontSize = 15.sp, fontWeight = FontWeight.Bold)
        }
    }
}
```

Add required imports at the top:
```kotlin
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
```

- [ ] **Step 2: Add MultiRound rendering to SidebarStateRenderer.kt**

Insert before the `is SidebarState.Error ->` branch (after line 314):

```kotlin
is SidebarState.MultiRound -> Column {
    Spacer(Modifier.height(12.dp))

    val phaseLabel = when (s.phase) {
        SidebarState.MultiPhase.SCANNING -> "📖 扫描中"
        SidebarState.MultiPhase.SOLVING -> "🤖 解答中"
        SidebarState.MultiPhase.FILLING -> "✅ 填入中"
        SidebarState.MultiPhase.DONE -> "🎉 已完成"
        SidebarState.MultiPhase.ERROR -> "❌ 错误"
    }

    SectionHeader(phaseLabel)

    if (s.phase == SidebarState.MultiPhase.SCANNING) {
        val pageInfo = if (s.totalPages > 0) "第 ${s.currentPage} / ${s.totalPages} 页"
                       else "翻页中..."
        Text(pageInfo, color = colors.OnSurface, fontSize = 14.sp,
            fontWeight = FontWeight.Medium)
    }
    if (s.phase == SidebarState.MultiPhase.FILLING) {
        Text("已填入 ${s.answeredCount} / ${s.totalPages} 题",
            color = colors.OnSurface, fontSize = 14.sp, fontWeight = FontWeight.Medium)
    }
    if (s.phase == SidebarState.MultiPhase.DONE) {
        Text("全部 ${s.totalPages} 道题已完成",
            color = colors.Success, fontSize = 14.sp, fontWeight = FontWeight.Bold)
    }
    if (s.phase == SidebarState.MultiPhase.ERROR) {
        StatusHint(s.errorMessage.ifBlank { s.message }, isError = true)
    }

    if (s.phase != SidebarState.MultiPhase.DONE && s.phase != SidebarState.MultiPhase.ERROR) {
        val barProgress = when (s.phase) {
            SidebarState.MultiPhase.SCANNING ->
                if (s.totalPages > 0) s.currentPage.toFloat() / s.totalPages else s.progress
            SidebarState.MultiPhase.FILLING ->
                if (s.totalPages > 0) s.answeredCount.toFloat() / s.totalPages else 0.5f
            else -> s.progress
        }
        Spacer(Modifier.height(8.dp))
        LinearProgressIndicator(
            progress = { barProgress },
            modifier = Modifier.fillMaxWidth().height(3.dp).clip(RoundedCornerShape(2.dp)),
            color = colors.Primary,
            trackColor = colors.Outline
        )
    }
}
```

- [ ] **Step 3: Wire MultiRoundRunner into SidebarPanel.kt**

Add new state variables after `val pipeline = remember { SolvePipeline(ExamApplication.instance) }`:

```kotlin
val pageNavigator = remember { PageNavigator() }
val multiRoundRunner = remember { MultiRoundRunner(pipeline, pageNavigator) }
var multiRoundRunning by remember { mutableStateOf(false) }
```

Add import at top:
```kotlin
import com.examhelper.app.pipeline.MultiRoundRunner
import com.examhelper.app.service.PageNavigator
```

Add the MultiRound button UI after `AutoFillButton` block (after line 148):

```kotlin
// 多轮自动答题按钮
Spacer(Modifier.height(8.dp))
MultiRoundButton(
    isRunning = multiRoundRunning,
    onClick = {
        multiRoundRunning = true
        multiRoundRunner.start(scope)
    },
    onStop = {
        multiRoundRunner.cancel()
        multiRoundRunning = false
    }
)
```

Add LaunchedEffect to detect completion:
```kotlin
LaunchedEffect(state) {
    if (state is SidebarState.MultiRound) {
        val s = state as SidebarState.MultiRound
        if (s.phase == SidebarState.MultiPhase.DONE ||
            s.phase == SidebarState.MultiPhase.ERROR) {
            multiRoundRunning = false
        }
    }
}
```

- [ ] **Step 4: Build check**

```bash
./gradlew :app:compileDebugKotlin
```

Expected: BUILD SUCCESSFUL

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/examhelper/app/ui/sidebar/
git commit -m "feat: add multi-round auto-answer UI to sidebar"
```

---

### Task 6: 集成测试 — 完整流程验证

**Files:**
- (No changes, test only)

**Interfaces:**
- Consumes: All Task 1-5 outputs
- Produces: Verification results

- [ ] **Step 1: Build and install**

```bash
./gradlew :app:assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

- [ ] **Step 2: Clear logcat and start test**

```bash
adb logcat -c
```

Manual steps:
1. Open i国网, enter exam page
2. Expand sidebar
3. Click「多轮自动答题」
4. Observe sidebar progress

- [ ] **Step 3: Verify Phase 1 (Scan) completes**

```bash
adb logcat -d -s "MultiRoundRunner" "PageNavigator" "ScanPageFilter" | head -60
```

Expected: `MultiRoundRunner: Scanned N pages`

- [ ] **Step 4: Verify Phase 2 (Solve) completes**

Expected: Sidebar shows solving progress, then transitions to filling

- [ ] **Step 5: Verify Phase 3 (Fill) completes**

```bash
adb logcat -d -s "MultiRoundRunner" | grep -E "Filling|Done|stopping"
```

Expected: `MultiRoundRunner: Done` and sidebar shows "全部 N 道题已完成"

- [ ] **Step 6: Test stop functionality**

Manual steps:
1. Click「多轮自动答题」again
2. Click「停止多轮」during scan
3. Confirm progress stops and button resets

- [ ] **Step 7: Commit any fixes**

```bash
git add -A
git commit -m "fix: minor fixes from integration testing"
```
