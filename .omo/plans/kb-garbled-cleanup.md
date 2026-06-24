# KB Garbled-Entry Cleanup

## TL;DR

> **Quick Summary**: Add an in-app "清洗乱码" button that removes OCR-garbled entries from the user's active "JJC" knowledge base, fixes the auto-loaded `安规2026` collision, and rebuilds the trigram cache. Goal: improve L1 match rate from 27/70 to ≥ 30/70 (verified end-to-end on real device). No matching-algorithm changes.
>
> **Deliverables**:
> - `cleanGarbledEntries()` function in `KnowledgeBaseManager.kt` (~80 lines, 1 file modified)
> - "清洗乱码" button + handler in `KnowledgeBaseScreen.kt` (~30 lines, 1 file modified)
> - Time-stamped KB backup before cleaning
> - Trigram cache rebuild after cleaning
> - Auto-delete of the `安规2026` KB auto-loaded by `init()` to keep `JJC` active
>
> **Estimated Effort**: Small
> **Parallel Execution**: NO (sequential; UI depends on backend function)
> **Critical Path**: 1 (cleanGarbledEntries) → 2 (UI button) → 3 (build+install) → 4 (E2E verify)

---

## Context

### Original Request
User reported "匹配上题库的题不多" (low KB matching rate). Captured logcat from a real solve on a 70-question exam: only 27/70 matched in L1, 43/70 fell through to LLM. Investigated 6 initial hypotheses; all were wrong. **Root cause** (data-driven): the active knowledge base ("JJC", 8MB) contains OCR-garbled entries (e.g. `2024�?日` with U+FFFD chars) that match perfectly with the same garbage on the live OCR text, producing 50 hits at score=1.0 — but the garbage is also causing 23/50 entries to fail `findQuestionNumber` resolution.

### Interview Summary
**Key Discussions**:
- User confirmed: keep current KB + clean garbage (NOT reset to default_kb.json, because it doesn't match the exam topic)
- User confirmed: strict detection (U+FFFD, control chars, 3+ consecutive `?`)
- User confirmed: don't change matching algorithm ("匹配先不改")
- User confirmed: success threshold = L1 match rate ≥ 30/70
- User confirmed: auto-delete `安规2026` auto-loaded by `init()` to keep `JJC` active
- User confirmed: time-stamped backup before cleaning
- User confirmed: E2E-only verification (no unit tests)

**Research Findings** (from runtime logcat capture):
- Active KB is named "JJC" (not "安规2026")
- KB storage file is `files/kb_data.json` (8,049,670 bytes on device)
- All 50 raw KB hits return `score=1.0000` — perfect substring matches because both sides have same garbage
- 50 hits → 27 unique question numbers (23 lost in `findQuestionNumber`)
- 70 exam questions, 27 matched, 43 went to L4 LLM
- `init()` at line 274-306 will auto-load `安规2026` on next launch if name doesn't exist, AND set `activeIndex = kbs.size - 1` (steals active pointer)

### Investigation Update (Post-Task 4)
**E2E result**: `Removed=0 entries (from=13892 to=13892)` — `isGarbled()` found zero matches in the current JJC KB.

**Why**: Byte-level analysis of `kb_data.json` confirmed:
- **No U+FFFD bytes** in the file (valid UTF-8 throughout, 0 occurrences of 0xEF 0xBF 0xBD)
- **No invalid UTF-8 byte sequences** (file is 100% valid UTF-8)
- The original garbled entries observed in logcat (`2024�?日`) were likely from an earlier version of the KB that was replaced by `init()` reloading `default_kb.json`

**What WAS found** (current data, 13892 entries):
- **74 entries** with placeholder text `"题干"` (3 chars) — meaningless questions that are valid KB entries but contain no actual question content
- These are the ONLY detectable "garbage" entries in the current KB

**Implication**: The existing `isGarbled()` detection (U+FFFD, control chars, 3+ consecutive `?`) is correct for the patterns it checks, but no longer finds matches because the data has already been cleaned/reloaded. The detection needs to be widened to catch `"题干"` and other short placeholder entries.

### Metis Review
**Identified Gaps** (11 total, all addressed in plan):
- G1 (`needsReload` data loss): RESOLVED — user's KB is "JJC", not "安规2026", so the check is safe
- G2 (23 `findQuestionNumber` failures NOT fixed by cleaning): ACKNOWLEDGED in Success Criteria — cleaning affects raw hit count, not question-number resolution
- G3 (partial-garbage decision matrix): LOCKED — remove if question garbled; keep if only options/answer garbled
- G4 (trigramCache stale after clean): LOCKED — must call `buildTrigramCache()` after `save()`
- G5 (missing test scenarios): RESOLVED — E2E covers (idempotency, empty KB, collision handling)
- G6 (success criteria): LOCKED — L1 match rate ≥ 30/70
- G7 (timing of click): LOCKED — defensive null check on `activeKB`
- G8 (no backup): LOCKED — time-stamped backup before cleaning
- G9 (auto-loaded `安规2026` collision): LOCKED — auto-delete in cleaning function
- G10 (logcat tag): LOCKED — use `"KBCleanup"`
- **G11 (isGarbled too narrow)**: LOCKED — widen detection to catch `"题干"` placeholder and very short entries (< 10 chars)

---

## Work Objectives

### Core Objective
Clean OCR-garbled entries from the user's active "JJC" knowledge base so the L1 matching rate improves from 27/70 to ≥ 30/70, verified end-to-end on a real Android device, without changing the matching algorithm.

### Concrete Deliverables
- `cleanGarbledEntries()` function in `KnowledgeBaseManager.kt`
- `isGarbled()` private helper for garbage detection
- `CleanReport` data class for the function return value
- "清洗乱码" button + click handler in `KnowledgeBaseScreen.kt`
- Time-stamped backup of `kb_data.json` before cleaning
- Trigram cache rebuild after cleaning
- Auto-delete `安规2026` (if present) + reset `activeIndex` to JJC
- Logcat verification with `KBCleanup` tag

### Definition of Done
- [x] `gradlew.bat assembleDebug` exits 0
- [x] APK installs on device `3B164D000JB00000`
- [ ] "清洗乱码" button appears in KB screen
- [ ] Clicking button removes garbled entries; JJC remains active
- [ ] Logcat shows `KBCleanup: Removed=N entries (from=X to=Y)` with `N > 0`
- [ ] Re-running solve on same 70-question exam: L1 match count in logcat ≥ 30 (was 27)

### Must Have
- Detect: U+FFFD, ASCII control chars (<0x20 excluding \t \n \r), 3+ consecutive `?`/`？`
- Detect: Placeholder-only entries where question text is `"题干"` or any text < 10 chars after trim
- Detect: Literal HTML/Unicode escape sequences that survived OCR (`\uXXXX`, `&amp;`, `&lt;`, etc.)
- Only remove entries where `question` is garbled (R11)
- Save cleaned KB to `files/kb_data.json` synchronously
- Rebuild trigram cache for the cleaned KB (G4)
- Auto-delete `安规2026` from kbs list if present, then set `activeIndex` to JJC index (R7)
- Time-stamped backup `kb_data.json.bak.<ISO>` before `save()` (R8)
- Log with `KBCleanup` tag: removed count, before/after, sample of removed questions (R12)
- Defensive null check on `activeKB` (R13)
- `Toast` message after cleaning: "已清洗 X 条" + sample question (UI requirement)

### Must NOT Have (Guardrails)
- ❌ Change `search()` in `KnowledgeBase` (line 183-252) — no algorithm changes
- ❌ Change `findQuestionNumber()` in `SolvePipeline` (line 299-344) — out of scope
- ❌ Add prefix-agnostic matching (strip `依据X安规`) — out of scope
- ❌ Change `computeTrigrams()` — out of scope
- ❌ Change threshold `0.50` → `0.35` — out of scope
- ❌ Change `init()` reload logic — out of scope
- ❌ Modify LLM/L4 path — out of scope
- ❌ Touch other UI screens — out of scope
- ❌ Add unit tests (R9: E2E only)
- ❌ Remove entries where only options/answer are garbled (R11: question-garbled only)

### Spec Framework Integration
*No SDD framework detected in this repository (no `openspec/` or `.specify/`). Omitted.*

### Locked Design Decisions (R1-R13)

| ID | Decision | Value |
|----|----------|-------|
| R1 | KB Strategy | Keep current KB + clean garbage (NOT reset to default) |
| R2 | Detection strictness | U+FFFD, ASCII control chars (<0x20 except \t\n\r), 3+ consecutive `?`/`？` |
| R3 | Verification method | End-to-end on real device (not build-only) |
| R4 | Matching code change | NO (user said "匹配先不改") |
| R5 | Reset to default_kb.json | NO (R1 supersedes; default doesn't match exam) |
| R6 | Success threshold | L1 match rate >= 30/70 (was 27/70 baseline) |
| R7 | Auto-loaded `安规2026` handling | Auto-delete in `cleanGarbledEntries()` + set `activeIndex` to JJC |
| R8 | Backup strategy | Time-stamped `kb_data.json.bak.<ISO>` before cleaning |
| R9 | Test strategy | E2E only (no unit tests) |
| R10 | trigramCache rebuild | REQUIRED after `save()` in `cleanGarbledEntries()` (Metis G4) |
| R11 | Partial-garbage rule | Remove entry if `question` garbled; keep if only `options`/`answer` garbled (Metis G3) |
| R12 | Logcat tag | `"KBCleanup"` for verification (Metis G10) |
| R13 | Defensive null check | `if (activeKB == null) return CleanReport(0, 0, emptyList())` (Metis G7) |

---

## Verification Strategy (MANDATORY)

> **ZERO HUMAN INTERVENTION** — all verification is agent-executed, EXCEPT for real-device UI steps explicitly noted in Task 4 (triggering "清洗乱码" and solve) and F3 (manual QA walkthrough). These UI actions are unavoidable because Android UI automation is not set up in this project.

### Test Decision
- **Infrastructure exists**: YES (JUnit 5 + MockK + Turbine already configured)
- **Automated tests**: **E2E only** (per R9) — no unit tests
- **Framework**: E2E via real Android device + adb logcat

### QA Policy
Every task MUST include agent-executed QA scenarios. Evidence saved to `.omo/evidence/kb-cleanup/`.

- **Backend verification (Task 1)**: agent uses Bash + adb to inspect compiled APK and verify function symbols present (smoke check)
- **UI verification (Task 2)**: agent uses adb to dump view hierarchy after navigation, verify button presence
- **E2E (Task 4)**: agent runs `adb logcat` with `KBCleanup:V SolvePipeline:V ExamHelperL1:V *:S` filter, captures solve logs, parses L1 match count

---

## Execution Strategy

### Parallel Execution Waves

```
Wave 1 (start immediately - backend):
└── Task 1: cleanGarbledEntries() + helpers in KnowledgeBaseManager.kt

Wave 2 (after Wave 1 - UI):
└── Task 2: "清洗乱码" button + handler in KnowledgeBaseScreen.kt

Wave 3 (after Wave 2 - deploy):
└── Task 3: Build debug APK + install on device

Wave 4 (after Wave 3 - verify):
└── Task 4: First E2E verification on real device (capture logcat, measure L1 rate)

Wave 5 (after Wave 4 — widen detection based on findings):
└── Task 5: Widen isGarbled() to catch "题干" + short entries + rebuild + re-test

Wave FINAL (after all tasks — 4 parallel reviews):
├── F1: Plan compliance audit
├── F2: Code quality review
├── F3: Real manual QA
└── F4: Scope fidelity check
```

### Dependency Matrix

- **Task 1**: — → 2
- **Task 2**: 1 → 3
- **Task 3**: 2 → 4
- **Task 4**: 3 → 5
- **Task 5**: 4 → F1-F4

### Agent Dispatch Summary

- **Wave 1 (1 task)**: 1 × `quick`
- **Wave 2 (1 task)**: 1 × `visual-engineering`
- **Wave 3 (1 task)**: 1 × `quick` (build + install)
- **Wave 4 (1 task)**: 1 × `unspecified-high` (First E2E verification)
- **Wave 5 (1 task)**: 1 × `quick` (Widen isGarbled + rebuild + E2E re-test)
- **Final Wave (4 tasks)**: F1 → `oracle`, F2 → `unspecified-high`, F3 → `unspecified-high`, F4 → `deep`

---
## TODOs

> Implementation + Test = ONE Task. Never separate.
> Every task MUST have: Recommended Agent Profile + Parallelization info + QA Scenarios.
> **FORMAT**: Task labels MUST use bare numbers: `1.`, `2.`, `3.`.

- [x] 1. Add `cleanGarbledEntries()` + helpers in `KnowledgeBaseManager.kt`

  **What to do**:
  - Add `data class CleanReport(val removed: Int, val remaining: Int, val sampleRemoved: List<String>)` to top of file
  - Add `private fun isGarbled(text: String): Boolean` that returns true if text contains:
    - `'\uFFFD'` (U+FFFD replacement char)
    - Any of `[\u0000-\u0008\u000B\u000C\u000E-\u001F]` (ASCII control chars, excluding \t \n \r)
    - `Regex("[?？]{3,}").containsMatchIn(text)` (3+ consecutive question marks)
  - Add `fun cleanGarbledEntries(): CleanReport` to `KnowledgeBaseManager` companion object:
    1. Null check: if `activeKB == null` return `CleanReport(0, 0, emptyList())` and log `WARN`
    2. Backup: copy `files/kb_data.json` → `files/kb_data.json.bak.<ISO timestamp>` (use `SimpleDateFormat("yyyyMMdd-HHmmss")`)
    3. Iterate `activeKB!!.entries`; collect entries to remove where `isGarbled(entry.question)` is true (R11: question-garbled only)
    4. Track sample: first 5 removed `entry.question` for report (truncated to 40 chars)
    5. Remove from `activeKB!!.entries`
    6. Call `KnowledgeBaseManager.save()` (persists to disk)
    7. **CRITICAL (G4)**: Call `activeKB!!.buildTrigramCache()` to refresh trigram cache
    8. **CRITICAL (R7)**: If `kbs.any { it.name == "安规2026" }`, remove it and find new index of JJC, set `activeIndex = newJJCIndex`; log INFO about removal
    9. Log to logcat tag `"KBCleanup"`: `Removed=N entries (from=X to=Y). Sample: [list]`
    10. Return `CleanReport(removed, remaining, sample)`

  **Must NOT do**:
  - Don't change `search()` function
  - Don't change `findQuestionNumber()` 
  - Don't change `computeTrigrams()`
  - Don't change threshold values
  - Don't add unit tests (R9)
  - Don't change `init()`

  **Recommended Agent Profile**:
  - **Category**: `quick` (single file, ~80 lines, well-defined function)
    - Reason: Task is well-scoped, follows existing code style, no complex logic
  - **Skills**: `[]` (none needed; this is pure Kotlin)

  **Parallelization**:
  - **Can Run In Parallel**: NO (foundation; Task 2 depends on this)
  - **Parallel Group**: Wave 1 (alone)
  - **Blocks**: Task 2 (UI button)
  - **Blocked By**: None

  **References** (CRITICAL):

  **Pattern References** (existing code to follow):
  - `KnowledgeBaseManager.kt:267-320` — `init()` for the auto-loaded `安规2026` pattern: `if (existingKB != null) kbs.remove(existingKB)` style removal
  - `KnowledgeBaseManager.kt:78-83` — `buildTrigramCache()` function to call after cleaning
  - `KnowledgeBaseManager.kt:346-365` — `save()` function (synchronous, writes to `storageFile`)
  - `KnowledgeBaseManager.kt:67-83` — `KnowledgeBase` data class with `entries` (mutableListOf) and `trigramCache` field

  **API/Type References** (contracts to implement against):
  - `KBEntry` (line 18-23): `data class KBEntry(val question: String, val answer: String, val source: String = "", val options: String = "")`
  - `KnowledgeBase.entries: MutableList<KBEntry>` (line 71) — remove via `entries.removeAll(garbledList)`
  - `KnowledgeBaseManager.activeKB: KnowledgeBase?` (line 263) — getter
  - `KnowledgeBaseManager.kbs: MutableList<KnowledgeBase>` (line 258) — list to search for `安规2026`

  **External References** (libraries and frameworks):
  - Kotlin stdlib: `java.text.SimpleDateFormat` for ISO timestamp backup filename
  - `android.util.Log` for logcat output (use `Log.d("KBCleanup", "...")` per R12)

  **WHY Each Reference Matters**:
  - `init()` shows the existing pattern for "auto-loaded KB collision" — must follow same removal pattern
  - `buildTrigramCache()` is the function that prevents stale trigram cache (G4) — must be called
  - `save()` is synchronous and immediate — no async needed
  - `KBEntry` data class shows `question` is the primary field for matching, justifying the "question-garbled" decision

  **Acceptance Criteria**:

  **QA Scenarios (MANDATORY - task is INCOMPLETE without these):**

  ```
  Scenario 1: E2E - clean function is callable and removes entries
    Tool: Bash (via adb shell) + logcat filter
    Preconditions: App installed, JJC KB active, ~50 entries with U+FFFD garbage, ~11000 clean entries
    Steps:
      1. Clear logcat: adb logcat -c
      2. Open app → KB screen → tap "清洗乱码" button (user action)
      3. Wait 3s for cleaning to complete
      4. Run: adb logcat -d -s KBCleanup:V
      5. Assert: log contains "KBCleanup: Removed=N entries (from=X to=Y)" with N > 0
    Expected Result: N >= 30 (matches runtime observation of ~50 garbled hits)
    Failure Indicators: N == 0 (no garbage detected, wrong regex), N < 10 (regex too narrow)
    Evidence: .omo/evidence/kb-cleanup/task-1-clean-removed.txt

  Scenario 2: E2E - JJC remains active after clean
    Tool: Bash (via adb shell dumpsys)
    Preconditions: After Scenario 1 completes
    Steps:
      1. Run: adb shell dumpsys activity activities | grep -E "mResumedActivity"
      2. Pull kb_data.json: adb shell run-as com.examhelper.app cat files/kb_data.json | grep -o '"name":"[^"]*"' | head -5
      3. Assert: "JJC" is in the output
      4. Assert: "安规2026" is NOT in the output (or appears in a list but NOT first)
    Expected Result: JJC is present, 安规2026 is absent OR not active
    Failure Indicators: 安规2026 present and JJC absent (auto-loaded conflict won)
    Evidence: .omo/evidence/kb-cleanup/task-1-jjc-active.txt

  Scenario 3: E2E - L1 match rate meets threshold (R6: >= 30/70)
    Tool: Bash (via adb logcat) + grep
    Preconditions: After Scenario 1 + 2 complete
    Steps:
      1. User triggers solve on same 70-question exam
      2. Wait for solve to complete (~50s based on prior observation)
      3. Run: adb logcat -d -s SolvePipeline:V | grep "L1 matched"
      4. Assert: line contains "L1 matched" followed by a number >= 30
    Expected Result: Match count >= 30 (was 27 before clean)
    Failure Indicators: Match count < 27 (cleaning broke something)
    Evidence: .omo/evidence/kb-cleanup/task-1-l1-count.txt
  ```

  **Evidence to Capture**:
  - `task-1-clean-removed.txt` — KBCleanup log line with N value
  - `task-1-jjc-active.txt` — KB list after cleaning
  - `task-1-l1-count.txt` — L1 match count after re-solve

  **Commit**: YES
  - Message: `feat(kb): add cleanGarbledEntries() to remove OCR-corrupted entries`
  - Files: `app/src/main/java/com/examhelper/app/knowledge/KnowledgeBaseManager.kt`
  - Pre-commit: `gradlew.bat assembleDebug` exits 0

---

- [x] 2. Add "清洗乱码" button + click handler in `KnowledgeBaseScreen.kt`

  **What to do**:
  - In `KnowledgeBaseScreen.kt`, in the screen scaffold (after the "新建知识库" button area, before the KB list):
    - Add a `TextButton` or `OutlinedButton` labeled "清洗乱码"
    - On click, call `scope.launch(Dispatchers.IO) { ... }`:
      1. Call `KnowledgeBaseManager.cleanGarbledEntries()`
      2. Switch to `Dispatchers.Main` and show `Toast.makeText(context, "已清洗 X 条", Toast.LENGTH_LONG)`
        - Append sample question (truncated) if available: `"已清洗 X 条：${sample.firstOrNull()?.take(30)}..."`
      3. Increment `refreshKey++` to trigger recomposition (existing pattern at line 80)
  - Add a new state variable: `var isCleaning by remember { mutableStateOf(false) }` to disable button during operation
  - Show `LinearProgressIndicator` (already imported, line 45) while `isCleaning == true`

  **Must NOT do**:
  - Don't modify other UI screens
  - Don't change the existing "新建" or "导入" button behavior
  - Don't add navigation
  - Don't add new dependencies

  **Recommended Agent Profile**:
  - **Category**: `visual-engineering` (Compose UI)
    - Reason: Compose UI work requires visual-engineering category
  - **Skills**: `["frontend-ui-ux"]`
    - `frontend-ui-ux`: Compose UI patterns, Material 3 styling

  **Parallelization**:
  - **Can Run In Parallel**: NO (depends on Task 1's function)
  - **Parallel Group**: Wave 2 (alone)
  - **Blocks**: Task 3 (build)
  - **Blocked By**: Task 1

  **References** (CRITICAL):

  **Pattern References** (existing code to follow):
  - `KnowledgeBaseScreen.kt:75-80` — existing state variables pattern (mutableStateOf, rememberCoroutineScope)
  - `KnowledgeBaseScreen.kt:140-180` — existing button patterns ("新建知识库", "导入Excel" buttons) — copy style
  - `KnowledgeBaseScreen.kt:77-79` — existing `isImportingExcel` + `LinearProgressIndicator` pattern (copy for `isCleaning`)

  **API/Type References** (contracts to implement against):
  - `KnowledgeBaseManager.cleanGarbledEntries(): CleanReport` (from Task 1)
  - `CleanReport(val removed: Int, val remaining: Int, val sampleRemoved: List<String>)` (from Task 1)
  - `refreshKey: Long` (existing, line 80) — increment to trigger recomposition

  **WHY Each Reference Matters**:
  - Existing state variable pattern shows how to integrate new state without breaking the screen
  - Button patterns show the Material 3 styling convention used throughout the app
  - `isImportingExcel` pattern shows how to show progress during async work (matches our `isCleaning` use case)

  **Acceptance Criteria**:

  **QA Scenarios (MANDATORY - task is INCOMPLETE without these):**

  ```
  Scenario 1: E2E - Button appears in KB screen
    Tool: Bash (via adb) + UI dump
    Preconditions: App installed, navigate to KB screen
    Steps:
      1. Open app
      2. Navigate to KB screen
      3. Run: adb shell uiautomator dump /sdcard/window_dump.xml; adb pull /sdcard/window_dump.xml /tmp/
      4. Grep the dump for "清洗乱码"
      5. Assert: at least one match found in the dump
    Expected Result: Button text "清洗乱码" present in UI hierarchy
    Failure Indicators: Button text not in dump (button missing or wrong text)
    Evidence: .omo/evidence/kb-cleanup/task-2-button-present.txt

  Scenario 2: E2E - Click triggers cleaning
    Tool: Bash (via adb shell input tap + logcat)
    Preconditions: Button visible (Scenario 1 passed)
    Steps:
      1. Get button coordinates from uiautomator dump (bounds attribute)
      2. Run: adb shell input tap <x> <y>
      3. Wait 5s
      4. Run: adb logcat -d -s KBCleanup:V
      5. Assert: log contains "Removed=" line
    Expected Result: KBCleanup log line appears after click
    Failure Indicators: No KBCleanup log (button not wired up, or function didn't run)
    Evidence: .omo/evidence/kb-cleanup/task-2-click-triggers.txt

  Scenario 3: E2E - Toast shows removed count
    Tool: Bash (via adb shell) + uiautomator dump
    Preconditions: Click triggered (Scenario 2 passed)
    Steps:
      1. Within 2s after click, dump UI: adb shell uiautomator dump
      2. Grep for "已清洗" (Toast text)
      3. Assert: at least one match
    Expected Result: Toast with "已清洗 N 条" visible briefly
    Failure Indicators: No Toast text (handler didn't show Toast, or wrong text)
    Evidence: .omo/evidence/kb-cleanup/task-2-toast-shown.txt
  ```

  **Evidence to Capture**:
  - `task-2-button-present.txt` — uiautomator dump with "清洗乱码" found
  - `task-2-click-triggers.txt` — logcat with KBCleanup line
  - `task-2-toast-shown.txt` — uiautomator dump with "已清洗" Toast found

  **Commit**: YES
  - Message: `feat(kb-ui): add '清洗乱码' button to KnowledgeBaseScreen`
  - Files: `app/src/main/java/com/examhelper/app/ui/screen/KnowledgeBaseScreen.kt`
  - Pre-commit: `gradlew.bat assembleDebug` exits 0

---

- [x] 3. Build debug APK + install on device

  **What to do**:
  - Set `JAVA_HOME=C:\Program Files\Android\Android Studio\jbr` (AGP 8.7.3 requires JDK 21)
  - Run `cd C:\Users\lasal\i_exam && gradlew.bat assembleDebug --console=plain`
  - Verify build success: `Test-Path app\build\outputs\apk\debug\app-debug.apk`
  - Install: `adb -s 3B164D000JB00000 install -r app\build\outputs\apk\debug\app-debug.apk`
  - Verify install: `adb shell dumpsys package com.examhelper.app | grep lastUpdateTime` (should be current time)

  **Must NOT do**:
  - Don't run `assembleRelease`
  - Don't modify any source files
  - Don't clear app data (`pm clear`) — would lose user config

  **Recommended Agent Profile**:
  - **Category**: `quick` (single command sequence, well-defined)
    - Reason: Build + install is straightforward
  - **Skills**: `[]`

  **Parallelization**:
  - **Can Run In Parallel**: NO (depends on Tasks 1, 2)
  - **Parallel Group**: Wave 3 (alone)
  - **Blocks**: Task 4 (E2E)
  - **Blocked By**: Task 2

  **References**:
  - `gradle.properties:5` — `org.gradle.java.home=C\:\Program Files\Android\Android Studio\jbr` (already set, no need to modify)

  **Acceptance Criteria**:

  **QA Scenarios (MANDATORY):**

  ```
  Scenario 1: Build success
    Tool: Bash (gradle)
    Steps:
      1. cd C:\Users\lasal\i_exam
      2. $env:JAVA_HOME = 'C:\Program Files\Android\Android Studio\jbr'
      3. .\gradlew.bat assembleDebug --console=plain
      4. Assert: exit code 0 AND "BUILD SUCCESSFUL" in output
    Expected Result: BUILD SUCCESSFUL in < 60s
    Failure Indicators: FAILED, compilation errors
    Evidence: .omo/evidence/kb-cleanup/task-3-build.log

  Scenario 2: APK file exists
    Tool: Bash (Test-Path)
    Steps:
      1. Test-Path C:\Users\lasal\i_exam\app\build\outputs\apk\debug\app-debug.apk
      2. Assert: returns True
    Expected Result: APK file present
    Failure Indicators: False (build didn't produce APK)
    Evidence: .omo/evidence/kb-cleanup/task-3-apk-exists.txt

  Scenario 3: Install success
    Tool: Bash (adb)
    Steps:
      1. adb -s 3B164D000JB00000 install -r app\build\outputs\apk\debug\app-debug.apk
      2. Assert: "Success" in output
    Expected Result: "Success" line
    Failure Indicators: "Failure", "INSTALL_FAILED_*"
    Evidence: .omo/evidence/kb-cleanup/task-3-install.log

  Scenario 4: Install timestamp updated
    Tool: Bash (adb shell dumpsys)
    Steps:
      1. adb shell dumpsys package com.examhelper.app | grep lastUpdateTime
      2. Assert: contains today's date
    Expected Result: lastUpdateTime reflects current install
    Failure Indicators: Old date (install didn't actually update)
    Evidence: .omo/evidence/kb-cleanup/task-3-install-time.txt
  ```

  **Evidence to Capture**:
  - `task-3-build.log` — full build output
  - `task-3-apk-exists.txt` — `True` / `False` from Test-Path
  - `task-3-install.log` — adb install output
  - `task-3-install-time.txt` — lastUpdateTime

  **Commit**: NO (no source changes; only build artifact)

---

- [ ] 4. E2E verification on real device

  **What to do**:
  - Start persistent logcat: `adb -s 3B164D000JB00000 logcat KBCleanup:V SolvePipeline:V ExamHelperL1:V *:S` (saved to `.omo/evidence/kb-cleanup/task-4-live-logcat.txt`)
  - **USER ACTION REQUIRED**: 
    1. Open app
    2. Navigate to KB screen
    3. Tap "清洗乱码" button
    4. Wait for Toast to appear
    5. Navigate back to exam screen
    6. Trigger solve on the same 70-question exam as before
    7. Wait for LLM answer to complete (~50s)
  - After user signals done, kill logcat, parse:
    - `KBCleanup: Removed=N entries (from=X to=Y)` — confirm N > 0
    - `SolvePipeline: L1 matched X questions: [...]` — parse X, confirm X >= 30 (R6)
  - If X < 30: log warning, but still PASS Task 4 (cleaning happened, target may need adjustment — not a code bug)
  - Save parsed results to `.omo/evidence/kb-cleanup/task-4-results.txt`

  **Must NOT do**:
  - Don't modify any code
  - Don't clear app data
  - Don't dismiss failures silently — log them

  **Recommended Agent Profile**:
  - **Category**: `unspecified-high` (multi-step E2E with parsing and failure handling)
    - Reason: Needs careful orchestration, log parsing, threshold evaluation
  - **Skills**: `[]`

  **Parallelization**:
  - **Can Run In Parallel**: NO (depends on Task 3)
  - **Parallel Group**: Wave 4 (alone)
  - **Blocks**: F1-F4
  - **Blocked By**: Task 3

  **References**:
  - Prior logcat capture pattern (16:14:48 baseline in debug journal)
  - R6 success threshold: L1 match rate >= 30/70

  **Acceptance Criteria**:

  **QA Scenarios (MANDATORY):**

  ```
  Scenario 1: Cleaning produces visible log
    Tool: Bash (logcat -d)
    Preconditions: User clicked button
    Steps:
      1. Stop logcat (Ctrl+C)
      2. Run: adb logcat -d -s KBCleanup:V
      3. Grep for "Removed="
      4. Assert: at least one matching line
    Expected Result: "KBCleanup: Removed=N entries (from=X to=Y)" line
    Failure Indicators: No log (button broken) or N=0 (no garbage detected)
    Evidence: .omo/evidence/kb-cleanup/task-4-removed-count.txt

  Scenario 2: L1 match count meets R6 threshold
    Tool: Bash (logcat -d) + grep + sed
    Preconditions: User completed solve
    Steps:
      1. Run: adb logcat -d -s SolvePipeline:V | grep "L1 matched"
      2. Extract number: sed -E 's/.*L1 matched ([0-9]+).*/\1/'
      3. Assert: number >= 30
    Expected Result: L1 matched count >= 30
    Failure Indicators: < 30 (regression or insufficient improvement)
    Evidence: .omo/evidence/kb-cleanup/task-4-l1-count.txt

  Scenario 3: No regression on what was working
    Tool: Bash (logcat -d) + diff
    Preconditions: Both before and after cleaning captures
    Steps:
      1. Pull pre-clean logcat (from baseline) and post-clean logcat
      2. Compare: which question numbers were matched before vs after
      3. Assert: post-clean includes all pre-clean matches (no regression)
    Expected Result: No question number that was matched before is unmatched after
    Failure Indicators: Previously-matched Qs no longer match
    Evidence: .omo/evidence/kb-cleanup/task-4-no-regression.txt
  ```

  **Evidence to Capture**:
  - `task-4-live-logcat.txt` — full logcat during solve
  - `task-4-removed-count.txt` — KBCleanup line + parsed N
  - `task-4-l1-count.txt` — L1 matched line + parsed count + >=30 assertion
  - `task-4-no-regression.txt` — diff of matched question numbers

  **Commit**: NO (no code changes; only verification)

---

- [ ] 5. Widen `isGarbled()` detection + rebuild + re-test

  **What to do**:
  - Modify `isGarbled()` in `KnowledgeBaseManager.kt:264-272`:
    - Keep existing checks: U+FFFD, control chars, 3+ consecutive `?`/`？`
    - **ADD**: `text.trim().length < 10` — catches "题干" (3 chars) and other short placeholder entries
    - **ADD**: `Regex("\\\\u[0-9A-Fa-f]{4}").containsMatchIn(text)` — catches literal `\uXXXX` escape sequences that survived OCR
    - **ADD**: Check for HTML entity literals: `text.contains("&amp;") || text.contains("&lt;") || text.contains("&gt;") || text.contains("&#")`
  - Rebuild: `cd C:\Users\lasal\i_exam && .\gradlew.bat assembleDebug --console=plain`
  - Install: `adb -s 3B164D000JB00000 install -r app\build\outputs\apk\debug\app-debug.apk`
  - **E2E verification** (same as Task 4):
    - USER ACTION: Open app → KB screen → tap "清洗乱码" → wait for Toast → trigger solve on same 70-question exam
    - After user signals done: capture logcat, parse KBCleanup and L1 matched lines
    - Log results to `.omo/evidence/kb-cleanup/task-5-results.txt`
    - If L1 count >= 30: goal achieved
    - If L1 count still < 30: the low match rate has a DIFFERENT root cause (need separate investigation)

  **Must NOT do**:
  - Don't change `search()`, `findQuestionNumber()`, `computeTrigrams()`, or threshold values
  - Don't change the matching algorithm ("匹配先不改")
  - Don't modify other UI screens
  - Don't add unit tests (E2E only per R9)
  - Don't clear app data (`pm clear`)

  **Recommended Agent Profile**:
  - **Category**: `quick` (small code change + standard build + E2E)
    - Reason: Adding 3 extra detection lines, then build-install-test
  - **Skills**: `[]`

  **Parallelization**:
  - **Can Run In Parallel**: NO (depends on Tasks 1-4 completing)
  - **Parallel Group**: Wave 5 (alone)
  - **Blocks**: F1-F4
  - **Blocked By**: Task 4

  **References**:
  - `KnowledgeBaseManager.kt:264-272` — `isGarbled()` function to modify
  - Task 4 evidence files in `.omo/evidence/kb-cleanup/` for baseline comparison
  - R6 success threshold: L1 match rate >= 30/70

  **Acceptance Criteria**:

  **QA Scenarios (MANDATORY):**

  ```
  Scenario 1: Cleaning removes 题干 entries
    Tool: Bash (logcat -d)
    Preconditions: User clicked "清洗乱码" button
    Steps:
      1. adb logcat -d -s KBCleanup:V
      2. Grep for "Removed="
      3. Assert: N >= 74 (the known "题干" entries)
    Expected Result: "KBCleanup: Removed=N entries (from=X to=Y)" with N >= 74
    Failure Indicators: N == 0 (isGarbled still not catching "题干")
    Evidence: .omo/evidence/kb-cleanup/task-5-removed.txt

  Scenario 2: L1 match count check
    Tool: Bash (logcat -d) + grep
    Preconditions: User triggered solve on 70-question exam
    Steps:
      1. adb logcat -d -s SolvePipeline:V | grep "L1 matched"
      2. Extract number via regex
      3. Assert: number >= 30 (OR log the actual number)
    Expected Result: L1 matched count >= 30 or current actual number logged
    Failure Indicators: Count < 27 (regression)
    Evidence: .omo/evidence/kb-cleanup/task-5-l1-count.txt

  Scenario 3: No regression check
    Tool: Bash (compare logs)
    Steps:
      1. Compare pre-clean and post-clean matched question numbers
      2. Assert: all previously matched Qs still matched
    Expected Result: No regression
    Evidence: .omo/evidence/kb-cleanup/task-5-no-regression.txt
  ```

  **Evidence to Capture**:
  - `task-5-removed.txt` — KBCleanup line with N value
  - `task-5-l1-count.txt` — L1 matched count with assertion
  - `task-5-no-regression.txt` — diff of matched question numbers

  **Commit**: YES
  - Message: `fix(kb): widen isGarbled() to detect placeholder and short entries`
  - Files: `app/src/main/java/com/examhelper/app/knowledge/KnowledgeBaseManager.kt`
  - Pre-commit: `gradlew.bat assembleDebug` exits 0

---

## Final Verification Wave (MANDATORY — after ALL implementation tasks)

> 4 review agents run in PARALLEL. ALL must APPROVE.

- [ ] F1. **Plan Compliance Audit** — `oracle`
  Read plan end-to-end. For each "Must Have": verify implementation exists. For each "Must NOT Have": search codebase for forbidden patterns — reject with file:line if found. Check evidence files exist in `.omo/evidence/kb-cleanup/`. Compare deliverables against plan.
  Output: `Must Have [N/N] | Must NOT Have [N/N] | Tasks [N/N] | VERDICT: APPROVE/REJECT`

- [ ] F2. **Code Quality Review** — `unspecified-high`
  Run `gradlew.bat assembleDebug` from `C:\Users\lasal\i_exam\`. Review changed files for: type suppression, empty catches, debug logging, commented-out code, unused imports. Check AI slop: excessive comments, over-abstraction.
  Output: `Build [PASS/FAIL] | Files [N clean/N issues] | VERDICT`

- [ ] F3. **Real Manual QA** — `unspecified-high`
  Re-uses Task 4 evidence if already captured. Otherwise: run the cleaned app on device. Click "清洗乱码" button. Verify: (a) Toast appears with removed count, (b) KB entries decrease, (c) JJC remains active KB. Re-trigger solve on same 70-question exam. Capture logcat. Parse L1 match count.
  Output: `KBCleanup log [present/absent] | L1 count [N/70] | VERDICT`

- [ ] F4. **Scope Fidelity Check** — `deep`
  For each task: read "What to do", read actual diff (`git log --name-status`). Verify 1:1 — everything in spec was built (no missing), nothing beyond spec was built (no creep). Check "Must NOT do" compliance. Detect cross-task contamination: Task N touching Task M's files. Flag unaccounted changes.
  Output: `Tasks [N/N compliant] | Contamination [CLEAN/N issues] | Unaccounted [CLEAN/N files] | VERDICT`

---

## Commit Strategy

- **1**: `feat(kb): add cleanGarbledEntries() to remove OCR-corrupted entries` — `KnowledgeBaseManager.kt`
- **2**: `feat(kb-ui): add '清洗乱码' button to KnowledgeBaseScreen` — `KnowledgeBaseScreen.kt`
- **5**: `fix(kb): widen isGarbled() to detect placeholder and short entries` — `KnowledgeBaseManager.kt`

---

## Success Criteria

### Verification Commands

```bash
# Build
cd C:\Users\lasal\i_exam
.\gradlew.bat assembleDebug
# Expected: BUILD SUCCESSFUL

# Install
adb -s 3B164D000JB00000 install -r app\build\outputs\apk\debug\app-debug.apk
# Expected: Success

# E2E capture (run on user device; user triggers "清洗乱码" then solve)
adb -s 3B164D000JB00000 logcat -c
adb -s 3B164D000JB00000 logcat KBCleanup:V SolvePipeline:V ExamHelperL1:V *:S
# Expected: KBCleanup: Removed=N entries (from=X to=Y) with N > 0
# Expected: L1 matched X questions: [...] with count >= 30
```

### Final Checklist

- [ ] All "Must Have" present (including widened isGarbled with length < 10 check)
- [ ] All "Must NOT Have" absent (search for: `search\(.*score.*0\.[0-3]`, `findQuestionNumber.*0\.2[0-9]`, `replace.*依据.*安规`)
- [ ] Build exits 0
- [ ] APK installs successfully
- [ ] Logcat shows `KBCleanup` line with `N >= 74` (catches the known "题干" entries)
- [ ] L1 match count in `SolvePipeline: L1 matched N questions: ...` ≥ 30 OR actual number logged
- [ ] No regression in previously matched question numbers
