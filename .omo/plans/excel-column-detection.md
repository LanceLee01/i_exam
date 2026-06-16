# Excel 导入列自动检测

## TL;DR

> **Quick Summary**: 替换 Excel 导入时硬编码的列索引（F=题目, G=来源, H=答案），改为两层自动检测：先表头关键词匹配，失败则回落 LLM 分析，双失败时报错提示。
>
> **Deliverables**:
> - `ColumnMapping` data class（3 个字段，sourceCol 可空）
> - `ColumnDetector` 类（header 检测 + LLM 检测）
> - 修改 `KnowledgeBase.importExcel()` / `importExcelWithDedup()` 接受 `ColumnMapping`
> - 单元测试覆盖表头检测、LLM 回退、边界情况（12+ 测试用例）
> - 3 个测试用 Excel 文件（中文表头 / 英文表头 / 无表头）
>
> **Estimated Effort**: Short（~300-500 行代码）
> **Parallel Execution**: YES - 2 waves
> **Critical Path**: Task 1 → Task 2 → Task 4 → Task 5

---

## Context

### Original Request
"如果题目和答案不在表格固定列，你要思考合理办法来识别"

### Interview Summary
**Key Discussions**:
- **策略选择**：用户从 5 个方案中选择了「先表头检测，失败则回落 LLM」
- **LLM 失败兜底**：报错提示用户手动处理（不弹 UI 选列）
- **LLM 样本量**：发送前 5 行数据给 LLM
- **表头关键词**：中文（题目/问题/试题/考题）+ 英文（question/answer/key）
- **测试**：包含单元测试，使用 JUnit 5 + MockK

**Research Findings**:
- `LLMClient.chatSync()` 已存在（suspendCancellableCoroutine 包装，可用作同步调用）
- `KnowledgeBase.importExcel()` / `importExcelWithDedup()` 是修改目标
- 项目已有 JUnit 5 + MockK + Turbine 测试基建
- `source` 列在当前代码中已经是可选的（`?: ""` 兜底）

### Metis Review
**Identified Gaps** (addressed):
- **Partial detection**：若只检测出 2/3 列，sourceCol 设为 null 继续导入
- **Header row 定位**：先试 row 0，如为空则扫描 row 1-2
- **LLM 响应验证**：返回的列索引必须在 Sheet 实际列数范围内，否则拒绝
- **API Key 空检查**：LLM fallback 前先检查 AppConfig 中 API Key 是否已配置
- **向后兼容**：不传 ColumnMapping 时保持旧硬编码行为

---

## Work Objectives

### Core Objective
Excel 导入时列位置不再硬编码，改为两层自动检测（表头关键词 → LLM），双失败时报错。

### Concrete Deliverables
- `app/src/main/java/com/examhelper/app/knowledge/ColumnMapping.kt` — data class
- `app/src/main/java/com/examhelper/app/knowledge/ColumnDetector.kt` — 检测逻辑
- 修改 `KnowledgeBaseManager.kt` 中的 `importExcel()` / `importExcelWithDedup()`
- `app/src/test/java/com/examhelper/app/knowledge/ColumnDetectorTest.kt` — 单元测试
- `app/src/test/resources/excel/` — 3 个测试 Excel 文件

### Definition of Done
- [ ] `./gradlew :app:test` → PASS（含 ColumnDetectorTest 所有用例）
- [ ] 带中文表头的 Excel 文件导入成功（列自动识别）
- [ ] 带英文表头的 Excel 文件导入成功
- [ ] 无表头 Excel 文件且配置了 LLM → LLM 检测后导入成功
- [ ] 无表头且无 API Key → 显示错误信息
- [ ] 旧代码路径（不传 ColumnMapping） → 保持硬编码 F/H/G 行为无变化

### Must Have
- `sourceColIndex` 可为 null，兼容只有题目+答案两列的 Excel
- 向后兼容：不传 `ColumnMapping` 时使用旧硬编码
- LLM fallback 前检查 API Key 是否为空
- LLM 响应必须做边界验证（列索引不超 Sheet 实际范围）
- 表头检测扫描 row 0-2（处理标题行/空行）

### Must NOT Have (Guardrails)
- 不要新增手动列映射 UI（无对话框、无下拉选择）
- 不要缓存检测结果（每次导入重新检测）
- 不要修改 `LLMClient` 接口
- 不要支持多 sheet（只处理第一个 sheet）
- 不要修改 `KnowledgeBaseScreen` 的文件选择器

---

## Verification Strategy (MANDATORY)

> **ZERO HUMAN INTERVENTION** - ALL verification is agent-executed. No exceptions.

### Test Decision
- **Infrastructure exists**: YES (JUnit 5 + MockK + Turbine)
- **Automated tests**: YES (tests-after — 先写测试，后实现)
- **Framework**: JUnit 5 + MockK
- **Tests-after**: 先定义 `ColumnMapping` 和 `ColumnDetector` 接口，编写测试验证所有场景，再实现实际逻辑

### QA Policy
Every task MUST include agent-executed QA scenarios.
Evidence saved to `.omo/evidence/task-{N}-{scenario-slug}.{ext}`.

- **Unit tests**: `./gradlew :app:test --tests "*ColumnDetector*"` → verify pass/fail
- **Integration (Android)**: Build APK with `./gradlew :app:assembleDebug` and test on device/emulator
- **Regression**: `./gradlew :app:test --tests "*KnowledgeBaseManager*"` → existing tests still pass

---

## Execution Strategy

### Parallel Execution Waves

```
Wave 1 (Foundation):
├── Task 1: ColumnMapping data class + ColumnDetectionException [quick]
├── Task 2: ColumnDetector — header detection logic [deep]
├── Task 3: ColumnDetector — LLM fallback detection [deep]
└── Task 4: Test Excel files + ColumnDetectorTest skeleton [quick]

Wave 2 (Integration):
├── Task 5: Modify importExcel / importExcelWithDedup to accept ColumnMapping [deep]
├── Task 6: Error handling integration (API key check, LLM error → user error) [quick]
└── Task 7: Regression tests + end-to-end verification [unspecified-high]

Wave FINAL (Parallel review):
├── Task F1: Plan compliance audit (oracle)
├── Task F2: Code quality review (unspecified-high)
├── Task F3: Real manual QA (unspecified-high)
└── Task F4: Scope fidelity check (deep)

Critical Path: Task 1 → Task 2 → Task 5 → Task 7 → F1-F4
Parallel Speedup: ~40% faster than sequential
Max Concurrent: 3 (Wave 1)
```

### Dependency Matrix
- **1**: - → 2, 5, 4
- **2**: 1 → 5
- **3**: 1 → 5
- **4**: - → 5
- **5**: 1, 2, 3, 4 → 6, 7
- **6**: 5 → 7
- **7**: 5, 6 → F1-F4

### Agent Dispatch Summary
- **Wave 1**: 4 tasks — Task 1 → `quick`, Task 2 → `deep`, Task 3 → `deep`, Task 4 → `quick`
- **Wave 2**: 3 tasks — Task 5 → `deep`, Task 6 → `quick`, Task 7 → `unspecified-high`
- **FINAL**: 4 tasks — F1 → `oracle`, F2 → `unspecified-high`, F3 → `unspecified-high`, F4 → `deep`

---

## TODOs

- [x] 1. ColumnMapping data class + ColumnDetectionException

  **What to do**:
  - 新建 `com.examhelper.app.knowledge.ColumnMapping` data class
  - 三个字段：`questionCol: Int`, `answerCol: Int`, `sourceCol: Int?`（可空）
  - 新建 `ColumnDetectionException` 异常类（继承 `Exception`），接受 `message: String` 和 `reason: DetectionFailReason` 枚举
  - `DetectionFailReason` 枚举：`NO_HEADER_MATCH`, `LLM_FAILED`, `LLM_NOT_CONFIGURED`, `LLM_INVALID_RESPONSE`, `SHEET_EMPTY`

  **Must NOT do**:
  - 不要添加任何序列化/持久化逻辑
  - 不要添加 UI 相关类型

  **Recommended Agent Profile**:
  - **Category**: `quick`
    - Reason: 简单的 data class + exception，无复杂逻辑
  - **Skills**: none (纯 Kotlin data class)
  - **Skills Evaluated but Omitted**: all

  **Parallelization**:
  - **Can Run In Parallel**: YES
  - **Parallel Group**: Wave 1 (with Tasks 2, 3, 4)
  - **Blocks**: Tasks 2, 3, 5
  - **Blocked By**: None (can start immediately)

  **References**:
  - `app/src/main/java/com/examhelper/app/knowledge/KnowledgeBaseManager.kt:15-49` — `KBEntry` data class pattern（当前项目的 data class 风格）
  - `app/src/main/java/com/examhelper/app/util/ExtractedTextBus.kt:18-23` — sealed class + enum pattern（枚举定义风格）

  **Acceptance Criteria**:
  - [ ] `ColumnMapping(questionCol=0, answerCol=1, sourceCol=2)` 编译通过
  - [ ] `ColumnMapping(questionCol=0, answerCol=1, sourceCol=null)` 编译通过（nullable 确认）
  - [ ] `ColumnDetectionException("msg", DetectionFailReason.NO_HEADER_MATCH)` throws with correct message
  - [ ] `./gradlew :app:build` 编译通过

  **QA Scenarios**:
  ```
  Scenario: ColumnMapping 正常构建
    Tool: Bash
    Preconditions: 项目已创建 ColumnMapping.kt 文件
    Steps:
      1. 执行 echo 'import com.examhelper.app.knowledge.ColumnMapping; fun main() { val m = ColumnMapping(0, 1, null); println(m) }' > /tmp/test_mapping.kt 2>/dev/null; 实际使用 gradle 编译测试
      2. 运行 ./gradlew :app:build
    Expected Result: BUILD SUCCESSFUL
    Evidence: .omo/evidence/task-1-build.txt

  Scenario: ColumnDetectionException 抛出
    Tool: Bash
    Preconditions: ColumnDetectionException 已定义
    Steps:
      1. 运行 ./gradlew :app:test --tests "*ColumnDetector*" 中的异常测试
    Expected Result: 异常能正确 capture message 和 reason
    Evidence: .omo/evidence/task-1-exception.txt
  ```

  **Commit**: YES
  - Message: `feat(kb): add ColumnMapping data class and ColumnDetectionException`
  - Files: `app/src/main/java/com/examhelper/app/knowledge/ColumnMapping.kt`
  - Pre-commit: `./gradlew :app:build`

- [x] 2. ColumnDetector — header detection logic

  **What to do**:
  - 新建 `com.examhelper.app.knowledge.ColumnDetector` object（或 class，取决于是否需要注入）
  - 实现 `detectByHeader(sheet: Sheet): ColumnMapping?` 方法
  - 逻辑：
    1. 先读 sheet 的 row 0，获取所有单元格文本
    2. 对每个单元格匹配关键词表：
       - **题目关键词**: `题目`, `问题`, `试题`, `考题`, `question`（大小写不敏感）
       - **答案关键词**: `答案`, `回答`, `answer`, `key`（大小写不敏感）
       - **来源关键词**: `来源`, `source`, `出处`, `选项`（大小写不敏感）
    3. 如果 row 0 所有单元格都是空或全部不匹配，扫描 row 1-2
    4. 必须同时匹配出 questionCol 和 answerCol 才算成功（sourceCol 可选）
    5. 如果匹配到重复类型（两个列都匹配"题目"），取第一个匹配
    6. 返回 `ColumnMapping` 或 null
  - 实现 `detectColumns(filePath: String): ColumnMapping` 公共入口：
    1. 打开 workbook → 取 sheet 0
    2. 调用 `detectByHeader` — 成功则返回
    3. 失败则调用 `detectByLLM` — 由 Task 3 实现
    4. 都失败 → throw `ColumnDetectionException`

  **Must NOT do**:
  - 不要处理多 sheet
  - 不要缓存检测结果
  - 不要修改 KnowledgeBaseManager 或其他类

  **Recommended Agent Profile**:
  - **Category**: `deep`
    - Reason: 需要仔细处理表头匹配逻辑的边界情况（空白行、空单元格、大小写、中英文混合）
  - **Skills**: none
  - **Skills Evaluated but Omitted**: all

  **Parallelization**:
  - **Can Run In Parallel**: YES
  - **Parallel Group**: Wave 1 (with Tasks 1, 3, 4)
  - **Blocks**: Task 5
  - **Blocked By**: Task 1 (ColumnMapping)

  **References**:
  - `app/src/main/java/com/examhelper/app/knowledge/KnowledgeBaseManager.kt:73-130` — `importExcel` / `importExcelWithDedup` 中 POI `Sheet` 和 `Row` 的使用模式
  - `app/src/main/java/com/examhelper/app/knowledge/KnowledgeBaseManager.kt:132-158` — 现有 `search()` 方法作为该类方法风格的参考
  - `app/build.gradle.kts:83-84` — Apache POI 依赖确认（`poi:5.2.5`, `poi-ooxml:5.2.5`）
  - `app/src/main/java/com/examhelper/app/filter/WatermarkFilter.kt:9-13` — 关键词文本匹配模式（作为简单关键词匹配的参考）

  **Acceptance Criteria**:
  - [ ] 中文表头（"题目", "答案"）→ 返回正确 mapping
  - [ ] 英文表头（"Question", "Answer"）→ 返回正确 mapping
  - [ ] 中英混合无匹配行（row 0 无关键词，row 1 有）→ 扫描 row 1-2 后匹配
  - [ ] 完全无匹配关键词 → 返回 null
  - [ ] 只有 2 列有匹配（题目+答案，无来源）→ `sourceCol = null`
  - [ ] 空 sheet（0 行）→ 返回 null
  - [ ] `./gradlew :app:test --tests "*ColumnDetector*"` → 含 header 测试全部 PASS

  **Evidence to Capture**:
  - [ ] 测试结果输出：`.omo/evidence/task-2-header-tests.txt`

  **Commit**: YES (groups with Task 3)
  - Message: `feat(kb): add ColumnDetector with header keyword detection + LLM fallback`
  - Files: `app/src/main/java/com/examhelper/app/knowledge/ColumnDetector.kt`
  - Pre-commit: `./gradlew :app:test --tests "*ColumnDetector*"`

- [x] 3. ColumnDetector — LLM fallback detection

  **What to do**:
  - 在 `ColumnDetector` 中实现 `detectByLLM(sheet: Sheet): ColumnMapping?` 方法
  - 逻辑：
    1. 从 `ExamApplication.instance.appConfig.getSnapshot()` 获取配置
    2. 检查 `apiKey` 是否为空 → 为空则返回 null（上层抛 `LLM_NOT_CONFIGURED`）
    3. 构建 LLM prompt：
       - 取 row 0（表头行）的文本作为 header 行
       - 取接下来 5 个数据行（row 1-5）的每列文本
       - 格式化为结构化文本
    4. 使用 `LLMClient().chatSync()` 发送给 LLM
    5. 解析响应 JSON：`{questionCol: <index>, answerCol: <index>, sourceCol: <index | null>}`
    6. **验证**：返回的索引必须在 0..lastColIndex 范围内，否则视为无效
    7. 返回 `ColumnMapping` 或 null
  - Prompt 模板（发送给 LLM）：
    ```
    You are analyzing a spreadsheet with exam questions.
    Identify which column contains the question text, which contains the answer, and which contains the source/options.

    Column headers:
    Col 0: "序号"
    Col 1: "题目"
    Col 2: "选项"
    Col 3: "答案"

    First 5 data rows (as CSV):
    Row 1: "1" | "电气安全距离是？" | "A:0.5m B:1m" | "B"
    Row 2: "2" | "触电急救步骤" | "A:报警 B:CPR" | "B"
    ...

    Respond ONLY with a JSON object (no markdown, no explanation):
    {"questionCol": 1, "answerCol": 3, "sourceCol": 2}
    ```

  **Must NOT do**:
  - 不要修改 `LLMClient` 接口
  - 不要发送超过 5 行数据（控制 token 消耗）
  - 不要假设列名和内容语言

  **Recommended Agent Profile**:
  - **Category**: `deep`
    - Reason: LLM prompt 设计、响应解析、错误处理和边界验证需要细致处理
  - **Skills**: none
  - **Skills Evaluated but Omitted**: all

  **Parallelization**:
  - **Can Run In Parallel**: YES
  - **Parallel Group**: Wave 1 (with Tasks 1, 2, 4)
  - **Blocks**: Task 5
  - **Blocked By**: Task 1 (ColumnMapping)

  **References**:
  - `app/src/main/java/com/examhelper/app/network/LLMClient.kt:97-143` — `chatSync()` 方法签名和用法
  - `app/src/main/java/com/examhelper/app/data/AppConfig.kt:140-153` — `getSnapshot()` 获取配置
  - `app/src/main/java/com/examhelper/app/network/LLMClient.kt:52-59` — `buildUrl()` URL 构建逻辑（了解 endpoint 格式）
  - `app/src/main/java/com/examhelper/app/knowledge/KBEngine.kt:232-262` — 已有 LLM prompt 构建模式作为参考

  **Acceptance Criteria**:
  - [ ] LLM 返回有效 JSON → 正确解析为 ColumnMapping
  - [ ] LLM 返回 JSON 但索引超界 → 视为无效，返回 null
  - [ ] LLM 返回非 JSON 文本 → catch 解析异常，返回 null
  - [ ] API Key 为空 → 直接返回 null（不调 LLM）
  - [ ] Network error（模拟） → catch 异常，返回 null
  - [ ] `./gradlew :app:test --tests "*ColumnDetector*"` → 含 LLM 测试全部 PASS

  **Evidence to Capture**:
  - [ ] 测试结果输出：`.omo/evidence/task-3-llm-tests.txt`

  **Commit**: YES (groups with Task 2)
  - Message: `feat(kb): add ColumnDetector with header keyword detection + LLM fallback`
  - Files: `app/src/main/java/com/examhelper/app/knowledge/ColumnDetector.kt`
  - Pre-commit: `./gradlew :app:test --tests "*ColumnDetector*"`

- [x] 4. Test Excel files + ColumnDetectorTest skeleton

  **What to do**:
  - 创建 3 个测试用 Excel 文件：
    1. `src/test/resources/excel/headers_cn.xlsx` — 中文表头（"题目"列 F, "答案"列 H, "来源"列 G）
    2. `src/test/resources/excel/headers_en.xlsx` — 英文表头（"Question"列 A, "Answer"列 C, 无 source）
    3. `src/test/resources/excel/no_headers.xlsx` — 无表头，直接数据行
  - 每个文件包含 3-5 行有效数据
  - 编写 `ColumnDetectorTest.kt` 测试类（MockK mock POI 对象）：
    - `should detect Chinese headers` → ColumnMapping
    - `should detect English headers` → ColumnMapping
    - `should return null when no headers match` → null
    - `should scan down to row 1-2 if row0 empty` → 找到 mapping
    - `should accept null sourceCol` → sourceCol=null
    - `should handle empty sheet` → null
    - `should call LLM when header detection returns null` — verify 调用了 LLM
    - `should parse LLM JSON response` → ColumnMapping
    - `should reject out-of-bounds LLM response` → null
    - `should skip LLM when API key is empty` → 不调 LLM
    - `should handle LLM network error` → catch → null

  **Must NOT do**:
  - 不要添加 Android 仪器化测试（只在 JVM 单元测试层）

  **Recommended Agent Profile**:
  - **Category**: `quick`
    - Reason: 创建测试文件和测试代码，遵循已存在的项目测试模式
  - **Skills**: none
  - **Skills Evaluated but Omitted**: all

  **Parallelization**:
  - **Can Run In Parallel**: YES
  - **Parallel Group**: Wave 1 (with Tasks 1, 2, 3)
  - **Blocks**: Task 5
  - **Blocked By**: None

  **References**:
  - `app/src/test/java/com/examhelper/app/pipeline/SolvePipelineTest.kt` — 项目已有的测试风格（MockK + JUnit 5）
  - `app/src/test/java/com/examhelper/app/pipeline/SearchManagerTest.kt` — MockK 测试范例

  **Acceptance Criteria**:
  - [ ] `./gradlew :app:test --tests "*ColumnDetector*"` → 11 个测试全部 PASS
  - [ ] `./gradlew :app:test` → 全部 PASS（不破坏现有测试）

  **Evidence to Capture**:
  - [ ] 测试结果：`.omo/evidence/task-4-test-results.txt`

  **Commit**: YES (groups with Task 2/3)
  - Message: `feat(kb): add ColumnDetector with header keyword detection + LLM fallback`
  - Pre-commit: `./gradlew :app:test --tests "*ColumnDetector*"`

- [x] 5. Modify importExcel / importExcelWithDedup to accept ColumnMapping

  **What to do**:
  - 修改 `KnowledgeBase.importExcel(path: String)` → 添加 `mapping: ColumnMapping? = null` 参数
    - mapping 为 null：先调用 `ColumnDetector.detectColumns(path)` 自动检测
      - 成功 → 使用检测结果
      - 失败（ColumnDetectionException）→ catch 后按错误类型返回负值
    - mapping 不为 null：直接使用传入的 mapping
  - 修改 `importExcelWithDedup` 同理
  - 错误码映射：LLM_NOT_CONFIGURED → -3, 其他检测失败 → -4
  - 使用列映射解析每一行：
    ```kotlin
    val question = row.getCell(mapping.questionCol)?.toString()?.trim()
    val answer = row.getCell(mapping.answerCol)?.toString()?.trim()
    val source = mapping.sourceCol?.let { row.getCell(it)?.toString()?.trim() } ?: ""
    ```

  **Must NOT do**:
  - 不要改变返回类型（保持 `Int`）
  - 不要修改 `KnowledgeBaseScreen` 文件选择流程
  - 不要修改序列化逻辑

  **Recommended Agent Profile**:
  - **Category**: `deep`
    - Reason: 需要平衡向后兼容性和新功能，处理好异常流程和错误码

  **Parallelization**:
  - **Can Run In Parallel**: NO
  - **Parallel Group**: Wave 2 (with Tasks 6, 7)
  - **Blocks**: Tasks 6, 7
  - **Blocked By**: Tasks 1, 2, 3, 4

  **References**:
  - `app/src/main/java/com/examhelper/app/knowledge/KnowledgeBaseManager.kt:73-130` — 当前 importExcel 完整实现

  **Acceptance Criteria**:
  - [ ] `importExcel("file.xlsx")` 自动检测列并导入成功
  - [ ] `importExcel("file.xlsx", ColumnMapping(5, 7, 6))` 使用传入 mapping
  - [ ] 旧调用方（不传 mapping）仍然正常工作
  - [ ] 无表头 + 无 API Key → 返回 -3
  - [ ] `./gradlew :app:test --tests "*KnowledgeBaseManager*"` → 现有测试全部 PASS

  **Evidence to Capture**:
  - [ ] 编译通过：`.omo/evidence/task-5-build.txt`
  - [ ] 测试通过：`.omo/evidence/task-5-tests.txt`

  **Commit**: YES
  - Message: `feat(kb): integrate ColumnDetector into importExcel with backward compat`
  - Pre-commit: `./gradlew :app:test`

- [x] 6. Error handling integration (API key check, LLM error → user error)

  **What to do**:
  - `importExcelWithDedup` 返回 -3 → `KnowledgeBaseScreen` 显示 "请先在设置中配置 API Key"
  - 返回 -4 → 显示 "列检测失败，请手动调整 Excel 文件格式"
  - 修改 `KnowledgeBaseScreen.kt` 中的导入结果 Toast 处理

  **Must NOT do**:
  - 不要新增 UI 组件或对话框

  **Recommended Agent Profile**:
  - **Category**: `quick`
    - Reason: 少量 UI 层错误处理修改
  - **Skills**: none

  **Parallelization**:
  - **Can Run In Parallel**: NO
  - **Parallel Group**: Wave 2 (with Task 7)
  - **Blocks**: Task 7
  - **Blocked By**: Task 5

  **References**:
  - `app/src/main/java/com/examhelper/app/ui/screen/KnowledgeBaseScreen.kt` — 现有导入结果处理

  **Acceptance Criteria**:
  - [ ] 无 API Key 导入 → Toast "请先在设置中配置 API Key"
  - [ ] LLM 检测失败 → Toast "列检测失败，请手动调整 Excel 文件格式"
  - [ ] `./gradlew :app:build` → SUCCESSFUL

  **Evidence to Capture**:
  - [ ] 构建通过：`.omo/evidence/task-6-build.txt`

  **Commit**: YES (groups with Task 5)
  - Message: `feat(kb): integrate ColumnDetector into importExcel with backward compat`
  - Pre-commit: `./gradlew :app:build`

- [x] 7. Regression tests + end-to-end verification

  **What to do**:
  - 运行 `./gradlew :app:test` 确保全部测试通过
  - 验证旧的硬编码路径（不传 ColumnMapping）行为不变

  **Must NOT do**:
  - 不要修改现有测试逻辑

  **Recommended Agent Profile**:
  - **Category**: `unspecified-high`
    - Reason: 系统性回归验证

  **Parallelization**:
  - **Can Run In Parallel**: NO
  - **Parallel Group**: Wave 2 (with Task 6)
  - **Blocks**: F1-F4
  - **Blocked By**: Tasks 5, 6

  **References**:
  - `app/src/test/java/com/examhelper/app/pipeline/SolvePipelineTest.kt` — 回归参考
  - `app/src/test/java/com/examhelper/app/knowledge/KBEntryTest.kt` — 回归参考

  **Acceptance Criteria**:
  - [ ] `./gradlew :app:test` → 全部 PASS
  - [ ] `./gradlew :app:build` → BUILD SUCCESSFUL

  **Evidence to Capture**:
  - [ ] 构建+测试输出：`.omo/evidence/task-7-full-test.txt`

  **Commit**: NO (merge with Task 5/6 commit)

---

## Final Verification Wave (MANDATORY)

> 4 review agents run in PARALLEL. ALL must APPROVE.

- [x] F1. **Plan Compliance Audit** — `oracle`
  Read the plan end-to-end. Check: Must Have all present? Must NOT Have absent? Test Excel files exist? Evidence files exist? Verify deliverables match requirements.
  Output: `Must Have [N/N] | Must NOT Have [N/N] | Tasks [N/N] | VERDICT: APPROVE/REJECT`

- [x] F2. **Code Quality Review** — `unspecified-high`
  Run `./gradlew :app:build` + `./gradlew :app:test`. Review changes for: empty catches, console.log in prod, commented-out code, unused imports, over-abstraction.
  Output: `Build [PASS/FAIL] | Tests [N pass/N fail] | Files [N clean/N issues] | VERDICT`

- [x] F3. **Real Manual QA** — `unspecified-high`
  From clean state: execute ALL QA scenarios from ALL tasks. Test cross-task integration. Save to `.omo/evidence/final-qa/`.
  Output: `Scenarios [N/N pass] | Integration [N/N] | VERDICT`

- [x] F4. **Scope Fidelity Check** — `deep`
  Verify 1:1 — everything in spec was built, nothing beyond spec was built. Check "Must NOT do" compliance. Detect cross-task contamination.
  Output: `Tasks [N/N compliant] | Contamination [CLEAN/N issues] | VERDICT`

---

## Commit Strategy

- **Commit 1**: `feat(kb): add ColumnMapping data class and ColumnDetectionException`
  - Files: `app/src/main/java/com/examhelper/app/knowledge/ColumnMapping.kt`
- **Commit 2**: `feat(kb): add ColumnDetector with header keyword detection + LLM fallback`
  - Files: `ColumnDetector.kt`, `ColumnDetectorTest.kt`, test Excel files
- **Commit 3**: `feat(kb): integrate ColumnDetector into importExcel with backward compat`
  - Files: `KnowledgeBaseManager.kt`, `KnowledgeBaseScreen.kt`

---

## Success Criteria

### Verification Commands
```bash
./gradlew :app:test --tests "*ColumnDetector*"  # Expected: ALL PASS
./gradlew :app:test --tests "*KnowledgeBaseManager*"  # Expected: ALL PASS
./gradlew :app:test  # Expected: ALL PASS
./gradlew :app:build  # Expected: BUILD SUCCESSFUL
```

### Final Checklist
- [ ] ColumnMapping data class 创建（questionCol, answerCol, sourceCol?）
- [ ] ColumnDetectionException + DetectionFailReason 枚举
- [ ] ColumnDetector.detectByHeader() — 关键词匹配 row 0-2
- [ ] ColumnDetector.detectByLLM() — LLM + JSON 解析 + 边界验证
- [ ] ColumnDetector.detectColumns() — 公共入口：header → LLM → throw
- [ ] importExcel / importExcelWithDedup 接受 ColumnMapping 参数
- [ ] 11 个 ColumnDetectorTest 测试全部 PASS
- [ ] 旧路径（不传 mapping）向后兼容
- [ ] 无 API Key 时显示配置提示
- [ ] 所有检测失败时显示用户可理解的错误消息
