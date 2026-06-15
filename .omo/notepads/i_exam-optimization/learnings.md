
## Task 1: Test Dependencies

- Added JUnit 5 (5.11.0), MockK (1.13.12), Turbine (1.1.0) as `testImplementation` deps
- JUnit Platform configured via `tasks.withType<Test> { useJUnitPlatform() }` at top level
- AGP's `testOptions.unitTests.all { useJUnitPlatform() }` does NOT work (unresolved reference)
- Created `app/src/test/java/com/examhelper/app/DummyTest.kt` placeholder
- `./gradlew assembleDebug` → BUILD SUCCESSFUL
- `./gradlew test` → BUILD SUCCESSFUL (test task passes)
- Java 17 via Homebrew: `/opt/homebrew/Cellar/openjdk@17/17.0.19/libexec/openjdk.jdk/Contents/Home`
- Android SDK at `/Users/like/Library/Android/sdk`

## SearchManagerTest (Task 3)

- `extractSearchQueries()` is a pure function with two branches: (1) bracket-pattern lines get cleaned of paren content, (2) fallback concatenates first 3 stem lines (raw).
- The fallback (pattern 2) always runs if there are stem lines (length > 8), adding a raw concatenated query even when bracket-extracted queries already exist.
- `searchQuestions()` is a suspend function — use `runBlocking` in tests (no need for `kotlinx-coroutines-test`).
- MockK `coEvery` works for mocking suspend methods; no special setup needed.
- JUnit 5 `org.junit.jupiter.api.Assertions.*` works; `kotlin.test.*` requires the kotlin-test dependency which wasn't configured.
- The Android `testDebugUnitTest` task supports `--tests` filter for class/method selection.

## Task 7: Sidebar Panel File Split

- SidebarPanel.kt (541 lines) split into 4 files:
  - `SidebarPanel.kt` (194 lines) — main panel structure, title bar, bottom bar, public API
  - `SidebarComponents.kt` (94 lines) — SectionHeader, StatusHint, StatusBanner (no state deps)
  - `SidebarActions.kt` (159 lines) — ReadScreenButton, AutoFillButton, SolveButton, ReworkButton, SaveToKBButton
  - `SidebarStateRenderer.kt` (222 lines) — full `when (state)` block, ETA logic, parseOptionMap/appendOptionText
- `SidebarPanel(onHide: () -> Unit)` public API remains unchanged
- `SidebarService.kt` line 194 still calls `SidebarPanel(onHide = { hidePanel() })` unchanged
- Type `MutableIntState` delegate not available in this Compose version — use `mutableStateOf(0)` + `setValue` import instead
- `import androidx.compose.runtime.setValue` explicitly needed for `var ... by remember { mutableStateOf(...) }` delegate
- `parseOptionMap`/`appendOptionText` moved to SidebarStateRenderer.kt (will be extracted by Task 6)
- `./gradlew assembleDebug` → BUILD SUCCESSFUL

## Task 10 — Extract 3 logic functions

### What
Extracted `parseAnswerPairs`, `countOptionsPerQuestion`, `matchesSelection` from `ExamAccessibilityService.kt` into `util/AccessibilityParseUtils.kt`.

### Key decisions
- Used `internal` visibility (same-package access for tests)
- `parseAnswerPairs` relies on `ExamConstants.ANSWER_PARSE_REGEX` (uppercase-only `[A-F\s、,，;]`) — tests must use uppercase letters
- `matchesSelection` for 正确/错误 checks `nodeText in tf` (set membership), not strict equality — this is existing behavior
- Pre-existing `MutableIntState` delegation bug in `SidebarStateRenderer.kt` had to be fixed (`mutableIntStateOf` instead of `mutableStateOf` + `: Int`)

### Tests
- 28 tests covering all 3 functions
- All 64 tests pass (28 new + 36 existing)

## Task 11: Performance Report

- Created `PERFORMANCE_REPORT.md` with profiling analysis for 2 scenarios:
  - **Solve Pipeline Latency**: L1 (Excel jaccard ~2 ms), L2 (Wiki FTS4+trigram ~15 ms), L3 (Tavily ~1–7 s), L4 (LLM SSE ~5–30 s)
  - **POI Large File Import**: WorkbookFactory ~200–800 ms for 10k rows, peak heap ~10–25 MB
- Identified SHA256 `readBytes()` creates double-buffer during import — `bytes` + POI internals simultaneous
- L2 trigram search does full `getAll()` scan — O(n) per request, no cache or inverted index
- Threshold cascade (0.70/0.50/0.40/0.20) means ~35% of requests short-circuit at L1/L2, avoiding network
- Top 3 bottlenecks: L3+L4 network serialization, L2 full-table trigram scan, POI dual-buffer + full JSON save
- Recommended first-phase optimization: O1 (trigram inverted index) + O3 (LRU result cache) + O4 (DigestInputStream)
- Left Memory Leak Check placeholder for Task 12
- Report written in Chinese, saved to `i_exam/PERFORMANCE_REPORT.md`

## Task 9: ExamConstants & Frontmatter Fix

- Created `ExamConstants.kt` with `OPTION_LETTERS = 'A'..'F'` as a `var` for runtime extensibility
- `parseOptionMap` in `OptionTextUtils.kt` now uses `ExamConstants.OPTION_LETTERS` for regex
- `isOptionNode` in `ExamAccessibilityService.kt` now uses `ExamConstants.OPTION_RANGE_REGEX`
- `parseAnswerPairs` in `AccessibilityParseUtils.kt` now uses `ExamConstants.ANSWER_PARSE_REGEX`
- `extractFrontmatter` in `KBEngine.kt` fixed: uses `indexOf("---")` to find first + second delimiter precisely
- Pre-existing build fix: `SidebarStateRenderer.kt` line 54 — replaced non-existent `mutableIntStateOf` with `mutableStateOf<Int>`

## Task 12: Memory Leak Check

- Filled `PERFORMANCE_REPORT.md` section 6 with comprehensive memory leak analysis across 4 categories
- **LEAK** found: `ExamAccessibilityService.scope` not cancelled in `onDestroy()` — coroutines hold Service reference after destruction
- **LEAK** found: `findAllClickable()` and `searchMatches()` missing `child.recycle()` — AccessibilityNodeInfo native objects not released
- **RISK** found: `KnowledgeBaseManager.kbs` — no upper bound on entry accumulation, process-lifetime growth
- **RISK** found: `SidebarService` OnTouchListener SAM lambda — captures Service, only released if `removeView()` succeeds
- All 6 source files analyzed: ExamAccessibilityService, LLMClient, KnowledgeBaseManager, ExtractedTextBus, SidebarService, SidebarStateRenderer, SidebarPanel
- 6 sub-sections: 6.1 Global Singletons, 6.2 Coroutine Scopes, 6.3 Anonymous Callbacks, 6.4 Compose Lifecycle, 6.5 Supplementary Findings, 6.6 Summary Table
- 13 findings total: 2 LEAK, 2 RISK, 8 SAFE, 1 NOTE

