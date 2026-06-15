# fix-tavily-llm-to-kb Learnings

## Changes Made

### SolvePipeline.kt — 3 new methods + L3/L4 flow change

**New methods added:**
1. `parseKBEntries(text: String): List<KBEntry>` — Parses LLM output of format `题目：... 答案：...` into KBEntry objects
2. `processSearchToKB(searchText, references, config): List<KBEntry>` — Calls LLM via `chatSync()` to extract KB entries from search results
3. `trySearchKBWithLLM(enhancement, text, config): AnswerSource?` — Orchestrates search→KB processing and Jaccard matching

**Modified solve() L3/L4 flow:**
- Old: L3 search → inject context → L4 LLM with context
- New: L3 search → try KB match via LLM (with Jaccard threshold 0.70) → if match found, return early → L4 plain LLM with baseMessage only

**Companion object:**
- Added `const val SEARCH_KB_MATCH_THRESHOLD = 0.70f`

**Preserved:**
- `enhanceWithSearch()` method kept (not removed, not called from solve())
- L1 (`tryExcelMatch`) and L2 (`tryWikiMatch`) unchanged
- `enhancement.references` still passed to `callLLM` for sidebar display
- No other files modified

### Verification
- `./gradlew assembleDebug` → BUILD SUCCESSFUL
- `./gradlew test` → BUILD SUCCESSFUL
