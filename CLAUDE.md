# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Build & Test

```bash
# Prerequisites: Java 17+, Android SDK 36+
export JAVA_HOME="/path/to/jdk"

# Build debug APK
./gradlew :app:assembleDebug

# Build release APK (note: no signing config — must sign externally)
./gradlew :app:assembleRelease

# Run all unit tests (JUnit 5 + MockK + Turbine)
./gradlew :app:test

# Run a single test class
./gradlew :app:test --tests "com.examhelper.app.pipeline.SolvePipelineTest"

# Run a single test method
./gradlew :app:test --tests "com.examhelper.app.knowledge.KBEntryTest.methodName"

# Clean build
./gradlew clean

# Run Android lint checks
./gradlew :app:lint

# Install on device
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

Tests use JUnit 5 Jupiter (`org.junit.jupiter`), MockK 1.13.12 (not Mockito), and Turbine 1.1 for Flow testing. `build.gradle.kts` sets `useJUnitPlatform()` and `isReturnDefaultValues = true` for unit tests. Gradle wrapper is **9.5.1**.

## Architecture

This is an Android Accessibility Service-based exam assistant ("考试助手" / ExamHelper) that reads questions from the "i国网" exam app, matches answers via a 4-level pipeline, and auto-clicks options through Accessibility gestures.

**Tech stack:** Kotlin 2.0.21, Jetpack Compose + Material 3 (BOM 2024.09), AGP 8.7.3, KSP 2.0.21-1.0.25 (Room annotation processing), Room 2.6.1 (with FTS4), OkHttp 4.12 (SSE streaming), Apache POI 5.2.5 (Excel + PPT parsing), PDFBox Android 2.0.27.0 (PDF parsing), DataStore Preferences, Gson 2.11.

**App entry:** `MainActivity.kt` — single Activity with Compose navigation (Welcome → Settings → KnowledgeBase). Dispatches foreground `SidebarService` and relies on `ExamAccessibilityService` for screen reading and auto-click.

**Dependency Injection:** No DI framework (no Hilt, Dagger, Koin). Dependencies are wired manually via `ExamApplication.instance` static singleton and direct constructor instantiation (`LLMClient()`, `KBEngine(context)`, etc.).

### 4-Level Solve Pipeline

`SolvePipeline.solve(text)` runs a cascading pipeline with early-exit at each level:

| Level | What | Threshold | Cost |
|-------|------|-----------|------|
| L1 | Excel KB trigram Jaccard match | ≥ 0.50 → direct return | ~1–3 ms |
| L2 | Wiki KB (FTS4 + trigram scan) | ≥ 0.50 → direct return | ~10–25 ms |
| L3 | Tavily web search + LLM KB extraction | found → enrich context | ~1–7 s (network) |
| L4 | LLM SSE streaming (OpenAI protocol) | fallback for all remaining | ~5–30 s (network) |

L1 answers override L4 on question-number collision. L2 (`tryWikiMatchAll`) is currently part of the hint system but has been refactored — check `SidebarPanel` call site for actual invocation flow, as `SolvePipeline.solve()` jumps from L1 directly to L3/L4 in the current code path.

### Communication Bus

`ExtractedTextBus` (singleton `object`) is the central event bus using Kotlin StateFlow/SharedFlow:

- `sidebarState: StateFlow<SidebarState>` — drives Compose UI in `SidebarPanel`. States: `Idle`, `Loading`, `Preview`, `Streaming`, `Answering`, `Done`, `Error`.
- `events: SharedFlow<Event>` — consumed by `ExamAccessibilityService` for `RequestExtract` and `ClickAnswer` actions.
- `@Volatile` fields for ETA metrics: `lastTokensPerSec`, `lastPromptTokens`, `lastTtftMs`.

### Dual Knowledge Base

1. **Excel KB** (`KnowledgeBaseManager`): Singleton `object`, holds in-memory `List<KBEntry>`, serialized to `kb_data.json` in filesDir. Trigram Jaccard similarity matching. Full JSON re-serialize on every mutation — unbounded growth risk. Backward-compat JSON loading for older formats missing fields like `importRecords`.

2. **Wiki KB** (`KBEngine` + Room DB): `WikiPage` entity with FTS4 virtual table (`WikiPageFts`) for full-text search. Entities: `WikiPage`, `SourceFile`, `Wikilink`. Documents (txt/md) are parsed by LLM into structured Wiki pages with YAML frontmatter. Supports `[[wikilink]]` cross-references via `Wikilink` entity. Import dedup via SHA-256 content hash.

### Services

- **`SidebarService`**: Foreground service managing the floating overlay window. `TYPE_APPLICATION_OVERLAY`, `FLAG_NOT_FOCUSABLE` + `FLAG_NOT_TOUCH_MODAL`. Hosts a Compose view tree with a custom `WindowLifecycleOwner`.
- **`ExamAccessibilityService`**: Reads screen content via `AccessibilityNodeInfo` tree traversal, filters watermarks (`WatermarkFilter`), scrolls to capture all questions, and performs auto-click by matching parsed answer pairs to option nodes. Uses `CoroutineScope(SupervisorJob() + Dispatchers.Main)` — note: **`scope.cancel()` is missing from `onDestroy()`**.

### UI Structure

```
ui/
├── sidebar/
│   ├── EdgeHandle.kt          # 4dp visible + 20dp touch area on right edge
│   ├── SidebarPanel.kt        # Main panel (65% width, 80% height), state-driven
│   ├── SidebarStateRenderer.kt # Renders each SidebarState variant
│   ├── SidebarComponents.kt   # Shared composables
│   └── SidebarActions.kt      # Action button composables
├── screen/
│   ├── WelcomeScreen.kt       # 4-step onboarding
│   ├── SettingsScreen.kt      # API + model config, KB entry point
│   └── KnowledgeBaseScreen.kt # KB management, import, switch
└── theme/
    ├── Theme.kt
    └── Type.kt
```

Navigation is state-machine based in `MainActivity` (not Jetpack Navigation): `!setupComplete → WelcomeScreen`, `showKB → KnowledgeBaseScreen`, otherwise `SettingsScreen`. v3.0.0 added a **Bottom Navigation** system with `BottomNavItem` enum: `HOME`, `QUESTION_BANK`, `FAB` (center button), `KNOWLEDGE_BASE`, `SETTINGS`.

## Critical Platform Constraints

- **`AccessibilityNodeInfo.getChild()` must call `.recycle()` on every returned child.** Two functions currently leak: `findAllClickable()` and `searchMatches()` in `ExamAccessibilityService.kt` — child nodes are retrieved but never recycled. The `traverseNode()` function correctly recycles.
- Overlay windows must be removed in `Service.onDestroy()` — `SidebarService.onDestroy()` calls `removeAllViews()` + `removeView()` inside try-catch, but TouchListener references may persist if removal fails.
- `TYPE_APPLICATION_OVERLAY` requires `Settings.canDrawOverlays()` permission.
- **Accessibility Service XML config** (`res/xml/accessibility_service_config.xml`): listens to `typeViewScrolled` and `typeViewFocused` in addition to window events. `canPerformGestures = true` enables auto-click. `flagIncludeNotImportantViews` traverses all nodes including layout containers. `notificationTimeout = 300ms` sets aggressive polling.
- **Network Security** (`res/xml/network_security_config.xml`): permits cleartext HTTP to **all hosts**. Required for connecting to local/dev LLM servers. Trusts system certificates only.
- **`SidebarService`** uses `foregroundServiceType="specialUse"` (Android 14+ requirement for non-standard foreground services). Also requests `POST_NOTIFICATIONS` permission (Android 13+).

## Key Conventions

- **No Jetpack Navigation** — screens are toggled via boolean state in `MainActivity.setContent {}`.
- **No version catalog** — all dependency/plugin versions are raw strings in `build.gradle.kts` files. No `libs.versions.toml`.
- **`FAIL_ON_PROJECT_REPOS`** in `settings.gradle.kts` — cannot add `repositories {}` blocks in module-level `build.gradle.kts`.
- **No product flavors** — single `debug`/`release` variant only. **No `androidTest`** source set (no instrumentation tests).
- **Release builds are unsigned** — no `signingConfigs` block in `build.gradle.kts`. Release APKs must be signed externally.
- **LLM client** is instantiated per-call (`LLMClient()`), not a singleton — each `solve()` or `importFile()` creates a fresh instance.
- **Config** is read via `AppConfig.getSnapshot()` which returns a `ConfigSnapshot` data class from DataStore flow — always use `first()` to get the current value in coroutines.
- **Answer format**: `[题号] 答案选项` per line, e.g. `[1] A`, `[2] B C`. Multi-select is space-separated letters. True/false normalized to "正确"/"错误".
- **Question type detection**: looks for "单选题"/"多选题"/"判断题" labels in the extracted text block.
- The `ExamApplication` singleton pattern (`companion object { lateinit var instance }`) is the primary way services access Application-scoped dependencies (config, database).

## Known Issues (from PERFORMANCE_REPORT.md)

1. 🔴 `ExamAccessibilityService.onDestroy()` — `scope.cancel()` is missing, leaking coroutines on service restart.
2. 🔴 `findAllClickable()` and `searchMatches()` — `AccessibilityNodeInfo.recycle()` not called on children.
3. ⚠️ `KnowledgeBaseManager` — unbounded in-memory entry list, full JSON re-serialization on every save.
4. ⚠️ `SidebarService` — `OnTouchListener` SAM captures Service reference; may leak on abnormal destroy path.
5. ℹ️ `importExcelWithDedup()` — `readBytes()` and POI parsing hold double memory (file bytes + POI internals).
6. ℹ️ L2 trigram scan is O(n) over all WikiPages — scales poorly beyond ~1000 pages.
7. ℹ️ Default API keys hardcoded in `AppConfig.kt` — LLM endpoint, API key, and Tavily key have default values in source, making them visible in version control.

## Reference Documents

- `PERFORMANCE_REPORT.md` — Detailed performance analysis (Chinese): pipeline latency, memory footprint, startup time, battery/network impact, 10 optimization recommendations.
- `unified-jingling-clover.md` — UI beautification design doc with color design token system (`ExamHelperColors`).
- `docs/superpowers/specs/` — Design specs for v3.0.0 redesign, KB management, and UI refinement.
- `docs/superpowers/plans/` — Detailed implementation plans for features/fixes.
- `design_preview/` — HTML mockups for KB screen, question bank, and wiki page views.
- `.omo/plans/` — Historical AI-assisted implementation plans with context on past decisions and evolution of features.
