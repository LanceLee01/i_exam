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
            // 简单子串匹配：题目或答案包含搜索词即匹配
            all.filter { (_, entry) ->
                entry.question.contains(searchQuery, ignoreCase = true) ||
                entry.answer.contains(searchQuery, ignoreCase = true)
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

@OptIn(ExperimentalMaterial3Api::class)
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
                                        text = if (record.importedAt > 0) dateFormat.format(Date(record.importedAt)) else "历史记录",
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
