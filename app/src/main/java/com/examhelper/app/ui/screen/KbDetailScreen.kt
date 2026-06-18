package com.examhelper.app.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.examhelper.app.knowledge.KBEntry
import com.examhelper.app.knowledge.KnowledgeBaseManager
import com.examhelper.app.knowledge.ImportRecord
import com.examhelper.app.ui.theme.LocalAppColors
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun KbDetailScreen(kbIndex: Int, onBack: () -> Unit) {
    val colors = LocalAppColors.current
    val kb = KnowledgeBaseManager.allKBs.getOrNull(kbIndex) ?: run { onBack(); return }

    var searchQuery by remember { mutableStateOf("") }
    var selectedIndices by remember { mutableStateOf(setOf<Int>()) }
    var editingEntry by remember { mutableStateOf<Pair<Int, KBEntry>?>(null) }
    var showFileList by remember { mutableStateOf(false) }
    var showDeleteConfirm by remember { mutableStateOf(false) }
    var refreshTrigger by remember { mutableStateOf(0L) }

    val displayEntries = remember(searchQuery, refreshTrigger) {
        val all = kb.entries.mapIndexed { idx, entry -> IndexedValue(idx, entry) }
        if (searchQuery.length >= 2) all.filter { (_, e) -> e.question.contains(searchQuery, true) } else all
    }

    Column(modifier = Modifier.fillMaxSize().background(colors.surface)) {
        // Header
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "返回", tint = colors.onSurface) }
            Text(kb.name, fontSize = 17.sp, fontWeight = FontWeight.Bold, color = colors.onSurface, modifier = Modifier.weight(1f))
        }

        Column(modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
            // Search
            OutlinedTextField(
                value = searchQuery, onValueChange = { searchQuery = it; selectedIndices = emptySet() },
                placeholder = { Text("搜索题目...", color = colors.onSurfaceMuted) },
                leadingIcon = { Icon(Icons.Filled.Search, null, tint = colors.onSurfaceSecondary) },
                trailingIcon = { if (searchQuery.isNotEmpty()) IconButton(onClick = { searchQuery = "" }) { Icon(Icons.Filled.Close, null, tint = colors.onSurfaceSecondary) } },
                singleLine = true, modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                shape = RoundedCornerShape(12.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = colors.onSurface, unfocusedTextColor = colors.onSurface,
                    focusedBorderColor = colors.primary, unfocusedBorderColor = colors.outlineInput,
                    focusedContainerColor = colors.surfaceCard, unfocusedContainerColor = colors.surfaceCard,
                ),
            )

            // Toolbar
            Row(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text("${displayEntries.size} 条", fontSize = 12.sp, color = colors.onSurfaceSecondary)
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    TextButton(onClick = { selectedIndices = if (selectedIndices.size == displayEntries.size) emptySet() else displayEntries.map { it.index }.toSet() }) {
                        Text(if (selectedIndices.size == displayEntries.size && displayEntries.isNotEmpty()) "取消全选" else "全选", fontSize = 11.sp, color = colors.info)
                    }
                    if (selectedIndices.isNotEmpty()) TextButton(onClick = { showDeleteConfirm = true }) {
                        Text("删除(${selectedIndices.size})", fontSize = 11.sp, color = colors.error)
                    }
                    TextButton(onClick = { showFileList = true }) {
                        Text("文件(${kb.importRecords.size})", fontSize = 11.sp, color = colors.primary)
                    }
                }
            }
            HorizontalDivider(color = colors.outline, thickness = 1.dp)

            // List
            if (displayEntries.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(if (searchQuery.length >= 2) "无匹配结果" else "题库为空", color = colors.onSurfaceSecondary, fontSize = 14.sp)
                }
            } else {
                LazyColumn(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(6.dp), contentPadding = PaddingValues(vertical = 6.dp)) {
                    itemsIndexed(displayEntries, key = { _, iv -> iv.index }) { displayIdx, (origIdx, entry) ->
                        Card(
                            modifier = Modifier.fillMaxWidth().border(1.dp, if (origIdx in selectedIndices) colors.primary.copy(alpha = 0.3f) else colors.outline, RoundedCornerShape(12.dp)),
                            shape = RoundedCornerShape(12.dp),
                            colors = CardDefaults.cardColors(containerColor = if (origIdx in selectedIndices) colors.primary.copy(alpha = 0.05f) else colors.surfaceCard),
                        ) {
                            Row(modifier = Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                                Checkbox(checked = origIdx in selectedIndices, onCheckedChange = { selectedIndices = if (origIdx in selectedIndices) selectedIndices - origIdx else selectedIndices + origIdx }, colors = CheckboxDefaults.colors(checkedColor = colors.primary, uncheckedColor = colors.onSurfaceSecondary), modifier = Modifier.size(20.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text("${displayIdx + 1}.", color = colors.onSurfaceSecondary, fontSize = 12.sp, modifier = Modifier.width(28.dp))
                                        Text(entry.question, color = colors.onSurface, fontSize = 13.sp, maxLines = 4, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                                    }
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Spacer(modifier = Modifier.width(28.dp))
                                        Text("答: ${entry.answer}", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = colors.success)
                                        if (!entry.questionType.isNullOrBlank()) {
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Box(modifier = Modifier.background(colors.primary.copy(alpha = 0.1f), RoundedCornerShape(4.dp)).padding(horizontal = 6.dp, vertical = 2.dp)) {
                                                Text(entry.questionType!!, fontSize = 10.sp, color = colors.primary)
                                            }
                                        }
                                    }
                                }
                                IconButton(onClick = { editingEntry = origIdx to entry }, modifier = Modifier.size(30.dp)) { Icon(Icons.Filled.Edit, null, tint = colors.info, modifier = Modifier.size(16.dp)) }
                                IconButton(onClick = {
                                    kb.deleteEntries(setOf(origIdx)); KnowledgeBaseManager.save()
                                    selectedIndices = selectedIndices - origIdx; refreshTrigger++
                                }, modifier = Modifier.size(30.dp)) { Icon(Icons.Filled.Delete, null, tint = colors.error, modifier = Modifier.size(16.dp)) }
                            }
                        }
                    }
                }
            }
        }
    }

    // Edit dialog
    if (editingEntry != null) {
        val (origIdx, orig) = editingEntry!!
        var q by remember { mutableStateOf(orig.question) }
        var a by remember { mutableStateOf(orig.answer) }
        var opts by remember { mutableStateOf(orig.options) }
        var qt by remember { mutableStateOf(orig.questionType ?: "") }
        AlertDialog(
            onDismissRequest = { editingEntry = null },
            title = { Text("编辑题目", color = colors.onSurface, fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(value = q, onValueChange = { q = it }, label = { Text("题目") }, minLines = 2, maxLines = 4, modifier = Modifier.fillMaxWidth(), colors = OutlinedTextFieldDefaults.colors(focusedTextColor = colors.onSurface, unfocusedTextColor = colors.onSurface, focusedBorderColor = colors.primary, unfocusedBorderColor = colors.outlineInput))
                    OutlinedTextField(value = a, onValueChange = { a = it }, label = { Text("答案") }, singleLine = true, modifier = Modifier.fillMaxWidth(), colors = OutlinedTextFieldDefaults.colors(focusedTextColor = colors.onSurface, unfocusedTextColor = colors.onSurface, focusedBorderColor = colors.primary, unfocusedBorderColor = colors.outlineInput))
                    OutlinedTextField(value = opts, onValueChange = { opts = it }, label = { Text("选项") }, minLines = 1, maxLines = 3, modifier = Modifier.fillMaxWidth(), colors = OutlinedTextFieldDefaults.colors(focusedTextColor = colors.onSurface, unfocusedTextColor = colors.onSurface, focusedBorderColor = colors.primary, unfocusedBorderColor = colors.outlineInput))
                    Row {
                        for (t in listOf("", "单选题", "多选题", "判断题")) {
                            FilterChip(selected = qt == t, onClick = { qt = t }, label = { Text(t.ifBlank { "未知" }, fontSize = 11.sp) }, modifier = Modifier.padding(end = 4.dp), colors = FilterChipDefaults.filterChipColors(selectedContainerColor = colors.primary.copy(alpha = 0.12f), selectedLabelColor = colors.primary))
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { kb.updateEntry(origIdx, orig.copy(question = q.trim(), answer = a.trim(), options = opts.trim(), questionType = qt.ifBlank { null })); KnowledgeBaseManager.save(); editingEntry = null; refreshTrigger++ }) { Text("保存", color = colors.primary) } },
            dismissButton = { TextButton(onClick = { editingEntry = null }) { Text("取消") } },
            containerColor = colors.surfaceCard,
        )
    }

    // Batch delete confirm
    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("确认删除", color = colors.onSurface) },
            text = { Text("删除 ${selectedIndices.size} 条？不可撤销", color = colors.onSurfaceSecondary) },
            confirmButton = { TextButton(onClick = { kb.deleteEntries(selectedIndices); KnowledgeBaseManager.save(); selectedIndices = emptySet(); showDeleteConfirm = false; refreshTrigger++ }) { Text("删除", color = colors.error) } },
            dismissButton = { TextButton(onClick = { showDeleteConfirm = false }) { Text("取消") } },
            containerColor = colors.surfaceCard,
        )
    }

    // File list dialog
    if (showFileList) {
        val df = remember { SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()) }
        AlertDialog(
            onDismissRequest = { showFileList = false },
            title = { Text("导入文件", color = colors.onSurface, fontWeight = FontWeight.Bold) },
            text = {
                if (kb.importRecords.isEmpty()) Text("暂无记录", color = colors.onSurfaceSecondary)
                else LazyColumn(modifier = Modifier.height(350.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    itemsIndexed(kb.importRecords) { _, r ->
                        Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(10.dp), colors = CardDefaults.cardColors(containerColor = colors.surfaceCard)) {
                            Column(modifier = Modifier.padding(10.dp)) {
                                Text(r.fileName, fontSize = 13.sp, fontWeight = FontWeight.Medium, color = colors.onSurface)
                                Spacer(modifier = Modifier.height(4.dp))
                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Text(if (r.importedAt > 0) df.format(Date(r.importedAt)) else "历史", fontSize = 11.sp, color = colors.onSurfaceSecondary)
                                    Text("${r.entryCount} 条", fontSize = 11.sp, color = colors.success)
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { showFileList = false }) { Text("关闭", color = colors.primary) } },
            containerColor = colors.surfaceCard,
        )
    }
}
