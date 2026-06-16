# Fix: 先滚动再捕获，解决最后一题文字缺失

## TL;DR
> **Quick Summary**: 第13题题干文字缺失的原因是考试 App 对该题的可访问性节点未填充 text。解决方案：在文字捕获前先向下滚动一次，让 App 填充所有节点的 text，再执行正常的文字捕获。
>
> **Deliverables**:
> - 修改 `ExamAccessibilityService.kt`：增加滚动逻辑
> - 恢复非调试版（清理调试日志，只保留滚动逻辑）
> - 构建 APK 并安装到设备
>
> **Estimated Effort**: Short
> **Parallel Execution**: NO

---

## Context

### Bug Root Cause
- 日志确认：考试 App (`com.dlxx.mam.Internal`) 对最后一题（第13题）的 `AccessibilityNodeInfo.text` 为空
- 其他12题正常，因为它们在可见区域内，App 填充了 text
- 第13题在屏幕底部，App 可能使用虚拟列表/懒加载，最后一个可见但 text 未被填充

### Solution Strategy
在 `extractAndSendText()` 中，找到 scrollable 的父节点 → 执行 `ACTION_SCROLL_FORWARD` → 等待 500ms → 再执行正常 `traverseNode` 捕获。

### Target File
- `app/src/main/java/com/examhelper/app/service/ExamAccessibilityService.kt`

---

## Work Objectives

### Concrete Deliverables
- 修改后的 `ExamAccessibilityService.kt`（含滚动 + 等待 + 捕获）
- 保留调试日志（方便后续排查），但可以缩减
- 构建并通过测试的 APK

### Must Have
- 滚动操作必须是**安全的**：检查 scrollable 节点存在再执行
- 等待时间充足（≥500ms）让 App 填充节点
- 如果滚动失败或没有 scrollable 节点，走原有的直接捕获逻辑
- 只滚动一次，不要反复滚动

### Must NOT Have
- 不要修改其他文件
- 不要影响非 scrollable 页面的正常捕获

---

## Execution Strategy

```
Wave 1:
├── Task 1: Implement scroll-before-capture logic
├── Task 2: Build APK, install, test
```

---

## TODOs

- [ ] 1. Add scroll logic to ExamAccessibilityService.kt

  **What to do**:

  在 `extractAndSendText()` 方法中，在 `traverseNode()` 调用之前，添加滚动逻辑。

  具体修改位置：在 `val lines = mutableListOf<String>()` 和 `traverseNode(rootNode, keywords, lines)` 之间。

  ```kotlin
  // 尝试向下滚动一屏，触发 App 填充未可见节点的文本
  try {
      val scrollable = findScrollableParent(rootNode)
      if (scrollable != null) {
          Log.d(TAG, "Found scrollable node, performing ACTION_SCROLL_FORWARD")
          scrollable.performAction(AccessibilityNodeInfo.ACTION_SCROLL_FORWARD)
          scrollable.recycle()
          // 等待 App 重新渲染，填充节点文本
          Thread.sleep(500)
          Log.d(TAG, "Scroll done, recapturing root node")
          // 重新获取 root（滚动后 newNode 可能变了）
          // 注意：rootInActiveWindow 要在主线程获取，这里用新变量
      } else {
          Log.d(TAG, "No scrollable parent found, skipping scroll")
      }
  } catch (e: Exception) {
      Log.w(TAG, "Scroll attempt failed", e)
  }
  ```

  需要新增一个辅助方法：
  ```kotlin
  private fun findScrollableParent(node: AccessibilityNodeInfo?): AccessibilityNodeInfo? {
      if (node == null) return null
      if (node.isScrollable) return node
      for (i in 0 until node.childCount) {
          val child = node.getChild(i) ?: continue
          val found = findScrollableParent(child)
          if (found != null) {
              child.recycle()
              return found
          }
          child.recycle()
      }
      return null
  }
  ```

  **重要注意**：
  - `performAction(ACTION_SCROLL_FORWARD)` 是异步的，所以需要用 `Thread.sleep(500)` 等待
  - 滚动后 `rootInActiveWindow` 可能还是同一个节点（因为 View 结构没变），所以 `rootNode` 仍然有效
  - 但为了安全，滚动后应该重新获取 `rootInActiveWindow`：
    ```kotlin
    // 重新获取 root（滚动可能导致节点刷新）
    rootNode?.let { if (it != rootInActiveWindow) it.recycle() }
    rootNode = rootInActiveWindow
    if (rootNode == null) { ... }
    ```
  - 实际上这里的代码是在 `Dispatchers.Default` 协程中，不能直接访问 `rootInActiveWindow`（需要在主线程），所以需要 `withContext(Dispatchers.Main)` 或 `launch(Dispatchers.Main)`
  
  **更好的实现方式**：因为当前代码在 `scope.launch(Dispatchers.Default)` 中，重新获取 root 需要切换到 Main 线程。最简单的方式：在协程中先切到 Main 执行滚动+等待，再切回 Default 执行捕获。

  建议的修改方案：将 `extractAndSendText` 中的 `scope.launch(Dispatchers.Default)` 里的逻辑拆成：
  ```
  // 1. 在主线程获取 rootNode 并执行滚动
  // 2. 在 Default 线程执行遍历捕获
  ```

  或者更简单：在 `scope.launch(Dispatchers.Default) { ... }` 内部，用 `runBlocking(Dispatchers.Main) { ... }` 包裹滚动逻辑。

  **推荐的最简实现**：

  在 `val lines = mutableListOf<String>()` 上方添加：
  ```kotlin
  // 【修复】先滚动再捕获，解决最后一题无障碍节点 text 为空的问题
  try {
      val scrollable = findScrollableParent(rootNode)
      if (scrollable != null) {
          Log.d(TAG, "Found scrollable node, performing ACTION_SCROLL_FORWARD")
          scrollable.performAccessibilityAction(AccessibilityNodeInfo.ACTION_SCROLL_FORWARD, null)
          scrollable.recycle()
          delay(500)
          Log.d(TAG, "Scroll done, re-reading rootInActiveWindow")
          rootNode?.let { if (it != rootInActiveWindow) it.recycle() }
          rootNode = rootInActiveWindow
          if (rootNode == null) {
              Log.d(TAG, "rootNode became null after scroll")
              launch(Dispatchers.Main) { ... }
              return@launch
          }
      } else {
          Log.d(TAG, "No scrollable parent found")
      }
  } catch (e: Exception) {
      Log.w(TAG, "Scroll failed", e)
  }
  ```

  但注意：`rootInActiveWindow` 和 `performAccessibilityAction` 都需要在主线程调用！而当前代码在 `Dispatchers.Default` 中。

  所以正确做法是把这些主线程操作包在 `withContext(Dispatchers.Main)` 或 `runBlocking(Dispatchers.Main)` 中。

  实际上，更简单的做法：在进入 `Dispatchers.Default` 之前就做完滚动。把滚动逻辑放在 `scope.launch(Dispatchers.Default) { ... }` **之前**，在 `Dispatchers.Main` 上执行。

  但是 `delay()` 需要协程作用域...

  最直接安全的做法：把 `scope.launch(Dispatchers.Default) { ... }` 改成先在 Main 线程获取 root + 执行滚动 + 等待，再切换到 Default 遍历。

  **我的最终推荐实现**：

  保持外层结构基本不变，在 `scope.launch(Dispatchers.Default)` **内部**，用 `runBlocking(Dispatchers.Main)` 包裹滚动相关的代码，因为 `performAccessibilityAction` 和 `rootInActiveWindow` 需要 Main 线程。但 `delay` 在 `runBlocking` 里会阻塞 Default 线程池，不过 500ms 可以接受。

  更简洁的方式——直接在 `extractAndSendText()` 开头（在 `scope.launch(Dispatchers.Default)` 之前）加一个 scroll 步骤：

  ```kotlin
  private fun extractAndSendText() {
      // 步骤0：如果有滚动容器，先向下滚动再捕获（修复最后一题文字缺失）
      scope.launch(Dispatchers.Main) {
          val initialRoot = rootInActiveWindow ?: return@launch
          val scrollable = findScrollableParent(initialRoot)
          if (scrollable != null) {
              scrollable.performAction(AccessibilityNodeInfo.ACTION_SCROLL_FORWARD)
              scrollable.recycle()
          }
          initialRoot.recycle()
          delay(500)
          // 滚动完成后，继续执行原来的 extractAndSendText 流程
          scope.launch(Dispatchers.Default) {
              // ... 原来的逻辑 ...
          }
      }
  }
  ```

  但这个会改变原有流程结构。

  **最安全、改动最小的方法**：在现有 `scope.launch(Dispatchers.Default) { ... }` 内部，在 `// 优先用 rootInActiveWindow` 之前，加一段：

  ```kotlin
  try {
      // 在 Main 线程执行滚动
      val scrolled = withContext(Dispatchers.Main) {
          val r = rootInActiveWindow ?: false
          if (r == null) return@withContext false
          val s = findScrollableParent(r)
          if (s != null) {
              s.performAction(AccessibilityNodeInfo.ACTION_SCROLL_FORWARD)
              s.recycle()
              true
          } else false
      }
      if (scrolled) {
          delay(500)
          Log.d(TAG, "Scroll completed, re-fetching root")
          // 重新获取 rootNode
          withContext(Dispatchers.Main) {
              rootNode?.let { if (it != rootInActiveWindow && it.isVisibleToUser) it.recycle() }
              rootNode = rootInActiveWindow
          }
      }
  } catch (e: Exception) {
      Log.w(TAG, "Scroll step failed", e)
  }
  ```

  `withContext(Dispatchers.Main)` 需要 `kotlinx.coroutines.withContext` import。

  这个改动最小，而且不影响其他流程。但 `findScrollableParent` 需要在调用上下文中可访问。

  我推荐用这个方案。

  **Must NOT do**:
  - 不要滚动多次
  - 不要影响非 scrollable 页面的正常流程
  - 不要修改其他文件

  **References**:
  - `ExamAccessibilityService.kt:64-143` - `extractAndSendText()` 方法
  - `AccessibilityNodeInfo.ACTION_SCROLL_FORWARD` - Android API
  - `withContext(Dispatchers.Main)` - Kotlin coroutines

  **Acceptance Criteria**:
  - [ ] 编译通过
  - [ ] 滚动逻辑仅在 scrollable 容器存在时执行
  - [ ] 任何异常都被 catch 住，不影响原有捕获流程

  **QA Scenarios**:

  ```
  Scenario: 滚动后第13题有题干文字
    Tool: Bash (adb logcat)
    Preconditions: 调试版 APK 已安装
    Steps:
      1. 打开有13题的考试页面
      2. 点击"读取屏幕"
      3. adb logcat -s ExamAccessibility
    Expected Result:
      - line[13xx] 包含第13题的题干文字（不再是空）
      - 日志中包含 "Found scrollable node" 或 "No scrollable parent found"
    Evidence: .omo/evidence/fix-q13/scroll-capture-log.txt

  Scenario: 滚动不破坏前12题的文字
    Tool: Bash (adb logcat)
    Preconditions: 同上
    Steps:
      1. 同上
    Expected Result: 前12题题干文字正常
    Evidence: 同上
  ```

  **Commit**: YES
  - Message: `fix: scroll before capture to populate last question's text in accessibility tree`
  - Files: `app/src/main/java/com/examhelper/app/service/ExamAccessibilityService.kt`

- [ ] 2. Build APK + install + test

  **What to do**:
  - `./gradlew assembleDebug`
  - `adb install -r app/build/outputs/apk/debug/app-debug.apk`
  - 让用户测试

  **Must NOT do**:
  - 不需要提交调试日志部分

---

## Success Criteria

### Verification
```bash
# 安装后，用户打开考试页、点"读取屏幕"
adb logcat -s ExamAccessibility

# 日志应包含（其中一种）：
# "Found scrollable node, performing ACTION_SCROLL_FORWARD" - 滚动成功
# "No scrollable parent found" - 页面不可滚动，直接捕获
# "Scroll completed, re-fetching root" - 重新获取节点成功

# 关键：第13题的题干文本出现在日志中
```

### Final Checklist
- [ ] 第13题题干文字可以正常显示
- [ ] 前12题不受影响
- [ ] 非 scrollable 页面不受影响
