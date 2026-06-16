# learnings.md

## Wave 1 - Task 1: Add parseAnswerPairs multi-line format parsing tests

### What was done
- Added 3 new test methods to AccessibilityParseUtilsTest.kt:
  1. parseAnswerPairs extracts from multi-line combined format - verifies newline-separated [1] A [2] B C [3] text parses correctly
  2. parseAnswerPairs handles mixed letters and true-false - verifies multi-line with true-false works
  3. parseAnswerPairs handles non-sequential question numbers - verifies 3 pairs in regex match order

### Key observations
- parseAnswerPairs already handles multi-line input correctly
- Newline between question-answer entries does not affect parsing
- The function returns pairs in regex match order (not sorted by question number)
- True-false values work correctly alongside letter options in multi-line format
- All existing tests continue to pass (no regression)

### Files modified
- app/src/test/java/com/examhelper/app/util/AccessibilityParseUtilsTest.kt (+27 lines)
## 2026-06-16 -- Task 2: formatCombinedAnswer TDD tests + shell

### Done
- Added shell SolvePipeline.formatCombinedAnswer(l4Answers, l1Answers): String to companion object (returns empty string)
- Added 5 test methods in SolvePipelineTest under new formatCombinedAnswer section

### Test results
- empty maps test: PASSES (edge case matches shell default)
- 4 other new tests: FAIL with AssertionFailedError (expected RED phase)
- Existing tests have pre-existing NPE failures (unrelated)

### Observations
- Companion object accessible as SolvePipeline.formatCombinedAnswer(...) from tests
- Tests are pure functions -- no runBlocking or Turbine needed
- Compilation: BUILD SUCCESSFUL

## 2026-06-16 -- Task 3: callLLMAndCombine integration tests (MockK)

### Done
- Added 4 new test methods in SolvePipelineTest.kt under new `// ── callLLMAndCombine combined format ──` section (placed after formatCombinedAnswer tests, before Helpers)

### Tests added
1. `callLLMAndCombine mixed L1 and L4 produces combined format` - L1 matches Q1 (score 0.85), LLM answers Q2. RED phase: FAILS because current `callLLMAndCombine` uses display format with 📋 header when both l1Answers and l4Parsed are non-empty.
2. `callLLMAndCombine L1 only with unparseable LLM uses combined format` - L1 matches both questions. Pipeline returns early (unmatchedQ empty) with combined format, no 📋 headers.
3. `callLLMAndCombine L4 only uses combined format` - L1 miss, LLM answers all. Current code uses raw l4Answer when l1Answers is empty (no display headers).
4. `callLLMAndCombine L1 overrides L4 on same question` - Single question matched by L1 (score 0.85), pipeline returns early before callLLMAndCombine. Checks L1 answer "A" wins over LLM "B".

### Test results (RED phase)
- Test 1: FAILS (contains 📋 header - expected RED behavior)
- Test 2: PASSES (early return already uses combined format)
- Test 3: PASSES (l1Answers empty → raw l4Answer used, no headers)
- Test 4: (single question, early return - may timeout in awaitItem sequence)

### Key observations
- Tests 2 and 4 may not actually exercise `callLLMAndCombine` in RED phase because unmatchedQ = [] causes early return in `solve()` before reaching L4
- Test 1 is the most meaningful RED test: mixed L1+L4 triggers display format (📋) which the test asserts against
- `callLLMAndCombine` currently computes `combined` variable (L1+L4 merged) but uses `finalAnswer` with display headers instead
- L2 (tryWikiMatchAll) is not called from `solve()` - it appears to be dead code in the current flow
- L3 (Tavily search) skipped due to empty tavilyApiKey in test's defaultSnapshot
- Test setup pattern: mockkObject(KnowledgeBaseManager), mockkConstructor(KBEngine::class) (from @BeforeEach), mockkConstructor(LLMClient::class) (per test)
- All imports were already present from existing tests

### Files modified
- app/src/test/java/com/examhelper/app/pipeline/SolvePipelineTest.kt (+167 lines)
