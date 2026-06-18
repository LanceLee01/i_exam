# Excel 题库增强管理 — 实施计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 为 Excel 题库增加搜索、编辑、批量删除和导入文件查看功能，以独立详情页承载。

**Architecture:** 在 `KnowledgeBase` 数据类中新增 `ImportRecord` 追踪导入文件历史，新增 `updateEntry`/`deleteEntries` 方法；在 `KnowledgeBaseManager` 中暴露操作方法；新建 `KbDetailScreen` Composable 作为详情页；修改 `KnowledgeBaseScreen` 通过状态变量实现页面切换。

**Tech Stack:** Kotlin, Jetpack Compose (Material3), Gson, JUnit 5

## Global Constraints

- minSdk 26, compileSdk 35
- JSON 持久化向后兼容（旧数据无 importRecords 不崩溃）
- 保持现有搜索算法不变，只封装接口给 UI
- 项目无 Navigation Compose，页面切换用 `remember { mutableStateOf }` 状态变量

---

## 文件结构

| 文件 | 职责 | 操作 |
|------|------|------|
| `knowledge/KnowledgeBaseManager.kt` | 数据模型 + 业务逻辑 + 持久化 | 修改 |
| `ui/screen/KnowledgeBaseScreen.kt` | 知识库列表页 + 页面导航 | 修改 |
| `ui/screen/KbDetailScreen.kt` | 题库详情页（搜索/编辑/批量删除/文件列表） | **新建** |
| `knowledge/KnowledgeBaseManagerTest.kt` | ImportRecord 序列化、updateEntry/deleteEntries 测试 | **新建** |

---

### Task 1: 新增 ImportRecord 并扩展 KnowledgeBase 数据模型

**Files:**
- Modify: `app/src/main/java/com/examhelper/app/knowledge/KnowledgeBaseManager.kt`

**Interfaces:**
- Produces: `ImportRecord` data class, `KnowledgeBase.importRecords`, `KnowledgeBase.updateEntry(index, entry)`, `KnowledgeBase.deleteEntries(indices)`, `KnowledgeBase.getImportFiles()`, modified `importExcelWithDedup(path, mapping, displayFileName)`

- [ ] **Step 1: 在 KBEntry 下方添加 ImportRecord 数据类**

在 `KBEntry` companion object 闭合大括号之后（第 126 行附近），`private fun extractQuestionBlocks` 之前插入：

```kotlin
/** 记录每次导入的文件信息 */
data class ImportRecord(
    val fileName: String,
    val hash: String,
    val importedAt: Long = System.currentTimeMillis(),
    val entryCount: Int = 0
)
```

- [ ] **Step 2: 在 KnowledgeBase 类中添加 importRecords 字段**

在 `KnowledgeBase` 类体内，`val importedHashes = mutableSetOf<String>()` 之后添加：

```kotlin
val importRecords = mutableListOf<ImportRecord>()
```

- [ ] **Step 3: 修改 importExcelWithDedup 签名和实现，记录 ImportRecord**

将方法签名从：
```kotlin
fun importExcelWithDedup(path: String, mapping: ColumnMapping? = null): Int {
```
改为：
```kotlin
fun importExcelWithDedup(path: String, mapping: ColumnMapping? = null, displayFileName: String? = null): Int {
```

在方法末尾 `buildFeatureCache()` 之前，`importedHashes.add(hash)` 之后添加 ImportRecord 记录：

```kotlin
importedHashes.add(hash)
// Record import file info
val fileName = displayFileName ?: File(path).name
importRecords.add(
    ImportRecord(
        fileName = fileName,
        hash = hash,
        importedAt = System.currentTimeMillis(),
        entryCount = imported  // imported is the count from this batch
    )
)
Log.d("KnowledgeBase", "[$name] Recorded import: $fileName ($imported entries)")
```

- [ ] **Step 4: 在 KnowledgeBase 类中添加 updateEntry 方法**

在 `buildFeatureCache()` 之后，`importExcel` 之前插入：

```kotlin
/** 更新指定索引的条目并重建缓存 */
fun updateEntry(index: Int, entry: KBEntry) {
    if (index in entries.indices) {
        entries[index] = entry
        buildFeatureCache()
    }
}
```

- [ ] **Step 5: 在 KnowledgeBase 类中添加 deleteEntries 方法**

在 `updateEntry` 之后插入：

```kotlin
/** 批量删除条目（传入索引集合，自动从高到低删除以避免索引偏移） */
fun deleteEntries(indices: Set<Int>) {
    val sorted = indices.sortedDescending()
    for (idx in sorted) {
        if (idx in entries.indices) {
            entries.removeAt(idx)
        }
    }
    if (sorted.isNotEmpty()) {
        buildFeatureCache()
    }
}
```

- [ ] **Step 6: 在 KnowledgeBase 类中添加 getImportFiles 方法**

在 `deleteEntries` 之后插入：

```kotlin
/** 返回导入文件列表（按导入时间倒序） */
fun getImportFiles(): List<ImportRecord> {
    return importRecords.sortedByDescending { it.importedAt }
}
```

- [ ] **Step 7: 编译验证**

```bash
cd d:/cc/i_exam && ./gradlew assembleDebug
```

Expected: BUILD SUCCESSFUL

---

### Task 2: 更新 JSON 持久化（KBData / KBStorageData）

**Files:**
- Modify: `app/src/main/java/com/examhelper/app/knowledge/KnowledgeBaseManager.kt`

**Interfaces:**
- Consumes: `ImportRecord` (from Task 1), `KnowledgeBase.importRecords` (from Task 1)
- Produces: Updated `KBData` with `importRecords`, updated `save()` and `load()`

- [ ] **Step 1: 更新 KBData 数据类，添加 importRecords 字段**

找到 `private data class KBData`（约第 611 行），改为：

```kotlin
private data class KBData(
    val id: String,
    val name: String,
    val entries: List<KBEntry>,
    val importedHashes: List<String>,
    val importRecords: List<ImportRecord> = emptyList()  // 新增，默认值兼容旧数据
)
```

- [ ] **Step 2: 更新 save() 方法，序列化 importRecords**

找到 `save()` 方法（约第 519 行），将 `KBData` 构造改为：

```kotlin
val data = KBStorageData(
    kbs = kbs.map { kb ->
        KBData(
            id = kb.id,
            name = kb.name,
            entries = kb.entries,
            importedHashes = kb.importedHashes.toList(),
            importRecords = kb.importRecords.toList()  // 新增
        )
    },
    activeKbIndex = activeIndex
)
```

- [ ] **Step 3: 更新 load() 方法，反序列化并兼容旧数据**

找到 `load()` 方法中的 KB 重建逻辑（约第 544 行），改为：

```kotlin
for (kd in data.kbs) {
    val kb = KnowledgeBase(id = kd.id, name = kd.name)
    kb.entries.addAll(kd.entries)
    kb.importedHashes.addAll(kd.importedHashes)
    // 兼容旧数据：如果 importRecords 为空但 importedHashes 有值，生成占位记录
    if (kd.importRecords.isEmpty() && kd.importedHashes.isNotEmpty()) {
        kb.importRecords.addAll(kd.importedHashes.map { hash ->
            ImportRecord(
                fileName = "未知文件(首次导入)",
                hash = hash,
                importedAt = 0L,
                entryCount = 0
            )
        })
    } else {
        kb.importRecords.addAll(kd.importRecords)
    }
    kbs.add(kb)
}
```

注意：删除原来重复的 `kb.importedHashes.addAll(kd.importedHashes)` 行（load 中有两行一样的）。

- [ ] **Step 4: 更新 KnowledgeBaseManager.init() 中资产导入调用，传入文件名**

找到 `init()` 方法中的资产导入循环（约第 460 行），将：
```kotlin
val count = kb.importExcelWithDedup(tmpFile.absolutePath)
```
改为：
```kotlin
val count = kb.importExcelWithDedup(tmpFile.absolutePath, displayFileName = assetName)
```

- [ ] **Step 5: 更新 importZipFromUri 中的 importExcelWithDedup 调用**

找到 `importZipFromUri` 方法中的 `kb.importExcelWithDedup(file.absolutePath)`（约第 583 行），改为：

```kotlin
val count = kb.importExcelWithDedup(file.absolutePath, displayFileName = file.name)
```

- [ ] **Step 6: 编译验证**

```bash
cd d:/cc/i_exam && ./gradlew assembleDebug
```

Expected: BUILD SUCCESSFUL

---

### Task 3: 更新 KnowledgeBaseScreen 导入调用 + 页面导航

**Files:**
- Modify: `app/src/main/java/com/examhelper/app/ui/screen/KnowledgeBaseScreen.kt`

**Interfaces:**
- Consumes: `importExcelWithDedup` 新签名 (from Task 1)
- Produces: 页面导航到 `KbDetailScreen`，传入原始文件名给导入方法，移除旧 viewDialog

- [ ] **Step 1: 在单文件导入处传入原始文件名**

找到单文件 Excel 导入代码（约第 132 行）：
```kotlin
val count = KnowledgeBaseManager.activeKB?.importExcelWithDedup(tmpFile.absolutePath) ?: -1
```
改为：
```kotlin
val count = KnowledgeBaseManager.activeKB?.importExcelWithDedup(
    tmpFile.absolutePath,
    displayFileName = fileName
) ?: -1
```

- [ ] **Step 2: 添加 detailKbIndex 状态变量**

在 `KnowledgeBaseScreen` Composable 函数体开头（约第 70 行 `val scope = ...` 附近），添加：

```kotlin
var detailKbIndex by remember { mutableStateOf(-1) }
```

- [ ] **Step 3: 将 Composable 主体改为页面切换结构**

在 `Scaffold` 调用之前，包裹一个 if/else 页面切换。现有的整个 Scaffold 内容用 `if (detailKbIndex >= 0)` 包裹。改写后的函数结构为：

```kotlin
@Composable
fun KnowledgeBaseScreen(onBack: () -> Unit) {
    val scope = rememberCoroutineScope()
    var detailKbIndex by remember { mutableStateOf(-1) }
    // ... 其他状态变量保持不变 ...
    // ... launcher 定义保持不变 ...
    
    if (detailKbIndex >= 0) {
        // 详情页
        KbDetailScreen(
            kbIndex = detailKbIndex,
            onBack = { detailKbIndex = -1; refreshKey++ }
        )
        return
    }
    
    // 原有的 Scaffold 列表 UI ...
    Scaffold(
        // ... 保持不变 ...
    )
}
```

- [ ] **Step 4: 修改"查看"按钮的 onClick，设为跳转到详情页**

找到 `IconButton(onClick = { ... viewKBName = kb.name ... showViewDialog = true })`（约第 287-292 行），改为：

```kotlin
IconButton(onClick = {
    detailKbIndex = index
}) {
    Icon(Icons.Filled.Visibility, contentDescription = "查看题目", tint = Color(0xFFA78BFA), modifier = Modifier.size(20.dp))
}
```

- [ ] **Step 5: 删除旧的 viewDialog 相关代码**

删除以下状态变量声明：
```kotlin
var showViewDialog by remember { mutableStateOf(false) }
var viewKBEntries by remember { mutableStateOf<List<Pair<String, String>>>(emptyList()) }
var viewKBName by remember { mutableStateOf("") }
```

删除旧的 `showViewDialog` AlertDialog 代码块（约第 369-398 行的整个 `if (showViewDialog) { AlertDialog(...) }` 块）。

- [ ] **Step 6: 编译验证**

```bash
cd d:/cc/i_exam && ./gradlew assembleDebug
```

Expected: 编译失败（KbDetailScreen 尚未创建）— 符合预期，Task 4 创建它。

---

### Task 4: 创建 KbDetailScreen

**Files:**
- Create: `app/src/main/java/com/examhelper/app/ui/screen/KbDetailScreen.kt`

**Interfaces:**
- Consumes: `KnowledgeBaseManager.allKBs`, `KnowledgeBase` methods from Task 1
- Produces: `KbDetailScreen` Composable

- [ ] **Step 1: 创建文件骨架和 Composable 签名**

```kotlin
package com.examhelper.app.ui.screen

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.examhelper.app.ExamApplication
import com.examhelper.app.knowledge.KBEntry
import com.examhelper.app.knowledge.KnowledgeBase
import com.examhelper.app.knowledge.KnowledgeBaseManager
import com.examhelper.app.knowledge.ImportRecord
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun KbDetailScreen(kbIndex: Int, onBack: () -> Unit) {
    val kb = KnowledgeBaseManager.allKBs.getOrNull(kbIndex) ?: run {
        // KB 不存在，直接返回
        onBack()
        return
    }
    
    var searchQuery by remember { mutableStateOf("") }
    var selectedIndices by remember { mutableStateOf(setOf<Int>()) }
    var editingEntry by remember { mutableStateOf<Pair<Int, KBEntry>?>(null) }
    var showFileListDialog by remember { mutableStateOf(false) }
    var showDeleteConfirm by remember { mutableStateOf(false) }
    var refreshTrigger by remember { mutableStateOf(0L) }
    
    // 根据搜索过滤条目，使用 remember 带 key 来响应 refreshTrigger
    val displayEntries = remember(searchQuery, refreshTrigger) {
        val all = kb.entries.mapIndexed { idx, entry -> IndexedValue(idx, entry) }
        if (searchQuery.length >= 2) {
            val results = kb.search(searchQuery)
            // 将搜索结果映射回原始索引
            results.mapNotNull { (entry, _) ->
                val origIdx = kb.entries.indexOf(entry)
                if (origIdx >= 0) IndexedValue(origIdx, entry) else null
            }
        } else {
            all
        }
    }
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(kb.name, fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回", tint = Color.White)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Transparent,
                    titleContentColor = Color.White
                )
            )
        },
        containerColor = Color(0xFF121220)
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp)
        ) {
            // ── 搜索栏 ──
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { 
                    searchQuery = it
                    selectedIndices = emptySet() // 搜索时清空选中
                },
                placeholder = { Text("搜索题目...", color = Color.White.copy(0.4f)) },
                leadingIcon = {
                    Icon(Icons.Filled.Search, contentDescription = null, tint = Color.White.copy(0.5f))
                },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { searchQuery = "" }) {
                            Icon(Icons.Filled.Close, contentDescription = "清除", tint = Color.White.copy(0.5f))
                        }
                    }
                },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                shape = RoundedCornerShape(12.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White,
                    focusedBorderColor = Color(0xFF22C55E),
                    unfocusedBorderColor = Color.White.copy(0.15f),
                    focusedContainerColor = Color.White.copy(0.04f),
                    unfocusedContainerColor = Color.White.copy(0.04f)
                )
            )
            
            // ── 工具栏 ──
            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "${displayEntries.size} 条题目",
                    color = Color.White.copy(0.5f),
                    style = MaterialTheme.typography.bodySmall
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    // 全选/取消
                    TextButton(onClick = {
                        selectedIndices = if (selectedIndices.size == displayEntries.size) {
                            emptySet()
                        } else {
                            displayEntries.map { it.index }.toSet()
                        }
                    }) {
                        Text(
                            if (selectedIndices.size == displayEntries.size && displayEntries.isNotEmpty()) "取消全选"
                            else "全选",
                            color = Color(0xFF60A5FA),
                            style = MaterialTheme.typography.labelSmall
                        )
                    }
                    // 删除选中
                    if (selectedIndices.isNotEmpty()) {
                        TextButton(onClick = { showDeleteConfirm = true }) {
                            Text(
                                "删除选中(${selectedIndices.size})",
                                color = Color(0xFFEF4444),
                                style = MaterialTheme.typography.labelSmall
                            )
                        }
                    }
                    // 查看文件
                    TextButton(onClick = { showFileListDialog = true }) {
                        Text(
                            "查看文件(${kb.importRecords.size})",
                            color = Color(0xFFA78BFA),
                            style = MaterialTheme.typography.labelSmall
                        )
                    }
                }
            }
            
            HorizontalDivider(color = Color.White.copy(0.08f), thickness = 1.dp)
            
            // ── 题目列表 ──
            if (displayEntries.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        if (searchQuery.length >= 2) "无匹配结果" else "题库为空",
                        color = Color.White.copy(0.4f)
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                    contentPadding = PaddingValues(vertical = 4.dp)
                ) {
                    itemsIndexed(displayEntries, key = { _, iv -> iv.index }) { displayIdx, (origIdx, entry) ->
                        KbEntryRow(
                            index = origIdx,
                            entry = entry,
                            displayNumber = displayIdx + 1,
                            isSelected = origIdx in selectedIndices,
                            onToggleSelect = {
                                selectedIndices = if (origIdx in selectedIndices) {
                                    selectedIndices - origIdx
                                } else {
                                    selectedIndices + origIdx
                                }
                            },
                            onEdit = { editingEntry = origIdx to entry },
                            onDelete = {
                                kb.deleteEntries(setOf(origIdx))
                                KnowledgeBaseManager.save()
                                selectedIndices = selectedIndices - origIdx
                                refreshTrigger++
                            }
                        )
                    }
                }
            }
        }
    }
    
    // ── 编辑对话框 ──
    if (editingEntry != null) {
        EditEntryDialog(
            entry = editingEntry!!,
            onDismiss = { editingEntry = null },
            onSave = { origIdx, newEntry ->
                kb.updateEntry(origIdx, newEntry)
                KnowledgeBaseManager.save()
                editingEntry = null
                refreshTrigger++
            }
        )
    }
    
    // ── 批量删除确认对话框 ──
    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("确认删除", color = Color.White) },
            text = { Text("确定要删除选中的 ${selectedIndices.size} 条题目吗？此操作不可撤销。", color = Color.White.copy(0.7f)) },
            confirmButton = {
                TextButton(onClick = {
                    kb.deleteEntries(selectedIndices)
                    KnowledgeBaseManager.save()
                    selectedIndices = emptySet()
                    showDeleteConfirm = false
                    refreshTrigger++
                }) {
                    Text("删除", color = Color(0xFFEF4444))
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) {
                    Text("取消", color = Color.White.copy(0.5f))
                }
            },
            containerColor = Color(0xFF1E1E2E)
        )
    }
    
    // ── 文件列表对话框 ──
    if (showFileListDialog) {
        ImportFilesDialog(
            records = kb.getImportFiles(),
            onDismiss = { showFileListDialog = false }
        )
    }
}

// ── 单行题目组件 ──

@Composable
private fun KbEntryRow(
    index: Int,
    entry: KBEntry,
    displayNumber: Int,
    isSelected: Boolean,
    onToggleSelect: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) Color(0xFF22C55E).copy(0.08f)
            else Color.White.copy(0.04f)
        )
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 勾选框
            Checkbox(
                checked = isSelected,
                onCheckedChange = { onToggleSelect() },
                colors = CheckboxDefaults.colors(
                    checkedColor = Color(0xFF22C55E),
                    uncheckedColor = Color.White.copy(0.3f)
                ),
                modifier = Modifier.size(24.dp)
            )
            Spacer(Modifier.width(4.dp))
            
            // 题目内容
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "$displayNumber.",
                        color = Color.White.copy(0.4f),
                        style = MaterialTheme.typography.labelSmall,
                        modifier = Modifier.width(28.dp)
                    )
                    Text(
                        text = entry.question,
                        color = Color.White,
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                }
                Spacer(Modifier.height(2.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Spacer(Modifier.width(28.dp))
                    Text(
                        text = "答: ${entry.answer}",
                        color = Color(0xFF22C55E),
                        style = MaterialTheme.typography.labelSmall
                    )
                    if (!entry.questionType.isNullOrBlank()) {
                        Spacer(Modifier.width(8.dp))
                        QTypeChip(entry.questionType!!)
                    }
                }
            }
            
            // 操作按钮
            IconButton(onClick = onEdit, modifier = Modifier.size(32.dp)) {
                Icon(Icons.Filled.Edit, contentDescription = "编辑", tint = Color(0xFF60A5FA), modifier = Modifier.size(18.dp))
            }
            IconButton(onClick = onDelete, modifier = Modifier.size(32.dp)) {
                Icon(Icons.Filled.Delete, contentDescription = "删除", tint = Color(0xFFEF4444), modifier = Modifier.size(18.dp))
            }
        }
    }
}

// ── 题型标签 ──

@Composable
private fun QTypeChip(type: String) {
    val bgColor = when (type) {
        "单选题" -> Color(0xFF3B82F6).copy(0.2f)
        "多选题" -> Color(0xFFF59E0B).copy(0.2f)
        "判断题" -> Color(0xFF22C55E).copy(0.2f)
        else -> Color.White.copy(0.1f)
    }
    val textColor = when (type) {
        "单选题" -> Color(0xFF93C5FD)
        "多选题" -> Color(0xFFFCD34D)
        "判断题" -> Color(0xFF86EFAC)
        else -> Color.White.copy(0.6f)
    }
    Box(
        modifier = Modifier
            .background(bgColor, RoundedCornerShape(4.dp))
            .padding(horizontal = 6.dp, vertical = 2.dp)
    ) {
        Text(text = type, color = textColor, style = MaterialTheme.typography.labelSmall)
    }
}

// ── 编辑对话框 ──

@Composable
private fun EditEntryDialog(
    entry: Pair<Int, KBEntry>,
    onDismiss: () -> Unit,
    onSave: (Int, KBEntry) -> Unit
) {
    val (origIdx, origEntry) = entry
    var question by remember { mutableStateOf(origEntry.question) }
    var answer by remember { mutableStateOf(origEntry.answer) }
    var options by remember { mutableStateOf(origEntry.options) }
    var qType by remember { mutableStateOf(origEntry.questionType ?: "") }
    var qTypeExpanded by remember { mutableStateOf(false) }
    val qTypeOptions = listOf("", "单选题", "多选题", "判断题")
    
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("编辑题目", color = Color.White, fontWeight = FontWeight.Bold) },
        text = {
            Column(
                modifier = Modifier.height(400.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = question,
                    onValueChange = { question = it },
                    label = { Text("题目") },
                    minLines = 2,
                    maxLines = 4,
                    modifier = Modifier.fillMaxWidth(),
                    colors = dialogTextFieldColors()
                )
                OutlinedTextField(
                    value = answer,
                    onValueChange = { answer = it },
                    label = { Text("答案") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    colors = dialogTextFieldColors()
                )
                OutlinedTextField(
                    value = options,
                    onValueChange = { options = it },
                    label = { Text("选项（可选）") },
                    minLines = 1,
                    maxLines = 3,
                    modifier = Modifier.fillMaxWidth(),
                    colors = dialogTextFieldColors()
                )
                // 题型下拉
                ExposedDropdownMenuBox(
                    expanded = qTypeExpanded,
                    onExpandedChange = { qTypeExpanded = it }
                ) {
                    OutlinedTextField(
                        value = qType.ifBlank { "未知" },
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("题型") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = qTypeExpanded) },
                        modifier = Modifier.fillMaxWidth().menuAnchor(),
                        colors = dialogTextFieldColors()
                    )
                    ExposedDropdownMenu(
                        expanded = qTypeExpanded,
                        onDismissRequest = { qTypeExpanded = false }
                    ) {
                        qTypeOptions.forEach { option ->
                            DropdownMenuItem(
                                text = { Text(option.ifBlank { "未知" }) },
                                onClick = {
                                    qType = option
                                    qTypeExpanded = false
                                }
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val newEntry = origEntry.copy(
                    question = question.trim(),
                    answer = answer.trim(),
                    options = options.trim(),
                    questionType = qType.trim().ifBlank { null }
                )
                onSave(origIdx, newEntry)
            }) {
                Text("保存", color = Color(0xFF22C55E))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("取消", color = Color.White.copy(0.5f))
            }
        },
        containerColor = Color(0xFF1E1E2E)
    )
}

@Composable
private fun dialogTextFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedTextColor = Color.White,
    unfocusedTextColor = Color.White,
    focusedBorderColor = Color(0xFF22C55E),
    unfocusedBorderColor = Color.White.copy(0.15f),
    focusedLabelColor = Color(0xFF22C55E),
    unfocusedLabelColor = Color.White.copy(0.5f),
    focusedContainerColor = Color.White.copy(0.04f),
    unfocusedContainerColor = Color.White.copy(0.04f)
)

// ── 文件列表对话框 ──

@Composable
private fun ImportFilesDialog(
    records: List<ImportRecord>,
    onDismiss: () -> Unit
) {
    val dateFormat = remember { SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()) }
    
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("导入文件列表", color = Color.White, fontWeight = FontWeight.Bold) },
        text = {
            if (records.isEmpty()) {
                Text("暂无导入记录", color = Color.White.copy(0.4f))
            } else {
                LazyColumn(
                    modifier = Modifier.height(400.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    itemsIndexed(records) { idx, record ->
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(8.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = Color.White.copy(0.04f)
                            )
                        ) {
                            Column(modifier = Modifier.fillMaxWidth().padding(10.dp)) {
                                Text(
                                    text = record.fileName,
                                    color = Color.White,
                                    fontWeight = FontWeight.Medium,
                                    style = MaterialTheme.typography.bodySmall
                                )
                                Spacer(Modifier.height(4.dp))
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        text = if (record.importedAt > 0) dateFormat.format(Date(record.importedAt)) else "首次导入",
                                        color = Color.White.copy(0.4f),
                                        style = MaterialTheme.typography.labelSmall
                                    )
                                    Text(
                                        text = "${record.entryCount} 条",
                                        color = Color(0xFF22C55E),
                                        style = MaterialTheme.typography.labelSmall
                                    )
                                }
                                Text(
                                    text = "SHA256: ${record.hash.take(16)}...",
                                    color = Color.White.copy(0.25f),
                                    style = MaterialTheme.typography.labelSmall
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("关闭", color = Color(0xFF22C55E))
            }
        },
        containerColor = Color(0xFF1E1E2E)
    )
}
```

- [ ] **Step 2: 编译验证**

```bash
cd d:/cc/i_exam && ./gradlew assembleDebug
```

Expected: BUILD SUCCESSFUL

---

### Task 5: 编写测试

**Files:**
- Create: `app/src/test/java/com/examhelper/app/knowledge/KnowledgeBaseManagerTest.kt`

- [ ] **Step 1: 创建测试文件**

```kotlin
package com.examhelper.app.knowledge

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class KnowledgeBaseManagerTest {

    // ── ImportRecord ──────────────────────────────────────────────

    @Test
    fun `ImportRecord default values`() {
        val record = ImportRecord(fileName = "test.xls", hash = "abc123")
        assertEquals("test.xls", record.fileName)
        assertEquals("abc123", record.hash)
        assertEquals(0, record.entryCount)
        assertTrue(record.importedAt > 0)
    }

    @Test
    fun `ImportRecord with entry count`() {
        val record = ImportRecord(fileName = "test.xls", hash = "def456", entryCount = 42)
        assertEquals(42, record.entryCount)
    }

    // ── KnowledgeBase updateEntry ─────────────────────────────────

    @Test
    fun `updateEntry replaces entry at correct index`() {
        val kb = KnowledgeBase(name = "test")
        kb.entries.add(KBEntry(question = "Q1", answer = "A1"))
        kb.entries.add(KBEntry(question = "Q2", answer = "A2"))
        kb.entries.add(KBEntry(question = "Q3", answer = "A3"))

        kb.updateEntry(1, KBEntry(question = "Q2-edited", answer = "A2-edited"))

        assertEquals(3, kb.entries.size)
        assertEquals("Q1", kb.entries[0].question)
        assertEquals("Q2-edited", kb.entries[1].question)
        assertEquals("Q3", kb.entries[2].question)
    }

    @Test
    fun `updateEntry out of bounds does nothing`() {
        val kb = KnowledgeBase(name = "test")
        kb.entries.add(KBEntry(question = "Q1", answer = "A1"))

        kb.updateEntry(5, KBEntry(question = "Q5", answer = "A5"))

        assertEquals(1, kb.entries.size)
        assertEquals("Q1", kb.entries[0].question)
    }

    @Test
    fun `updateEntry rebuilds feature cache`() {
        val kb = KnowledgeBase(name = "test")
        kb.entries.add(KBEntry(question = "安全生产管理办法是什么", answer = "A1"))
        kb.buildFeatureCache()

        kb.updateEntry(0, KBEntry(question = "安全管理规定有哪些", answer = "A2"))

        // 搜索新题目应该能匹配
        val results = kb.search("安全管理规定")
        assertTrue(results.isNotEmpty())
    }

    // ── KnowledgeBase deleteEntries ───────────────────────────────

    @Test
    fun `deleteEntries removes specified indices`() {
        val kb = KnowledgeBase(name = "test")
        kb.entries.add(KBEntry(question = "Q1", answer = "A1"))
        kb.entries.add(KBEntry(question = "Q2", answer = "A2"))
        kb.entries.add(KBEntry(question = "Q3", answer = "A3"))
        kb.entries.add(KBEntry(question = "Q4", answer = "A4"))

        kb.deleteEntries(setOf(1, 3))

        assertEquals(2, kb.entries.size)
        assertEquals("Q1", kb.entries[0].question)
        assertEquals("Q3", kb.entries[1].question)
    }

    @Test
    fun `deleteEntries with out of bounds indices handled gracefully`() {
        val kb = KnowledgeBase(name = "test")
        kb.entries.add(KBEntry(question = "Q1", answer = "A1"))

        kb.deleteEntries(setOf(0, 99, -1))

        assertEquals(0, kb.entries.size)
    }

    @Test
    fun `deleteEntries empty set does nothing`() {
        val kb = KnowledgeBase(name = "test")
        kb.entries.add(KBEntry(question = "Q1", answer = "A1"))
        kb.entries.add(KBEntry(question = "Q2", answer = "A2"))

        kb.deleteEntries(emptySet())

        assertEquals(2, kb.entries.size)
    }

    @Test
    fun `deleteEntries rebuilds feature cache when entries removed`() {
        val kb = KnowledgeBase(name = "test")
        kb.entries.add(KBEntry(question = "Q1", answer = "A1"))
        kb.entries.add(KBEntry(question = "Q2", answer = "A2"))
        kb.buildFeatureCache()

        kb.deleteEntries(setOf(0))

        assertEquals(1, kb.entries.size)
        // 验证缓存已重建（不抛异常即可）
        val results = kb.search("Q2")
        assertTrue(results.isNotEmpty())
    }

    // ── KnowledgeBase getImportFiles ──────────────────────────────

    @Test
    fun `getImportFiles returns records sorted by time descending`() {
        val kb = KnowledgeBase(name = "test")
        kb.importRecords.add(
            ImportRecord(fileName = "old.xls", hash = "111", importedAt = 1000L, entryCount = 5)
        )
        kb.importRecords.add(
            ImportRecord(fileName = "new.xls", hash = "222", importedAt = 2000L, entryCount = 10)
        )

        val files = kb.getImportFiles()

        assertEquals(2, files.size)
        assertEquals("new.xls", files[0].fileName) // 最新的在前
        assertEquals("old.xls", files[1].fileName)
    }
}
```

- [ ] **Step 2: 运行测试**

```bash
cd d:/cc/i_exam && ./gradlew test
```

Expected: All tests PASS

---

### Task 6: 最终构建验证

- [ ] **Step 1: 完整编译**

```bash
cd d:/cc/i_exam && ./gradlew assembleDebug
```

Expected: BUILD SUCCESSFUL

- [ ] **Step 2: 运行全部测试**

```bash
cd d:/cc/i_exam && ./gradlew test
```

Expected: All tests PASS

- [ ] **Step 3: Commit**

```bash
cd d:/cc/i_exam && git add -A && git commit -m "feat: Excel KB enhanced management - search, edit, batch delete, import file list"
```
