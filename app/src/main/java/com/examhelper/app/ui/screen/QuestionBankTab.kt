package com.examhelper.app.ui.screen

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.examhelper.app.ExamApplication
import com.examhelper.app.knowledge.KBEntry
import com.examhelper.app.knowledge.KnowledgeBaseManager
import com.examhelper.app.ui.components.EmptyState
import com.examhelper.app.ui.components.SearchBar
import com.examhelper.app.ui.theme.LocalAppColors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QuestionBankTab(
    initialSearchQuery: String = "",
    isDarkMode: Boolean,
    onNavigateToKB: () -> Unit,
) {
    val colors = LocalAppColors.current
    val scope = rememberCoroutineScope()
    var searchQuery by remember { mutableStateOf(initialSearchQuery) }
    var selectedType by remember { mutableStateOf<String?>(null) }
    var isImporting by remember { mutableStateOf(false) }
    var importProgress by remember { mutableFloatStateOf(0f) }
    var importMessage by remember { mutableStateOf("") }
    var refreshKey by remember { mutableStateOf(0L) }
    var showKbSwitcher by remember { mutableStateOf(false) }
    var editingEntry by remember { mutableStateOf<Pair<Int, KBEntry>?>(null) }
    var showDeleteConfirm by remember { mutableStateOf<Int?>(null) }
    var detailKbIndex by remember { mutableStateOf(-1) }

    val kb = remember(refreshKey) { KnowledgeBaseManager.activeKB }
    val allEntries = remember(kb, refreshKey) { kb?.entries?.toList() ?: emptyList() }

    // Filter
    val filtered = remember(allEntries, searchQuery, selectedType) {
        var r = allEntries
        if (searchQuery.length >= 2) {
            val q = searchQuery.lowercase()
            r = r.filter {
                it.question.lowercase().contains(q) || it.answer.lowercase().contains(q) ||
                it.options.lowercase().contains(q)
            }
        }
        if (selectedType != null) r = r.filter { it.questionType == selectedType }
        r
    }

    // Excel/ZIP import launcher
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        if (uris.isNullOrEmpty()) return@rememberLauncherForActivityResult
        val kbIdx = KnowledgeBaseManager.allKBs.indexOf(KnowledgeBaseManager.activeKB)
        scope.launch(Dispatchers.IO) {
            withContext(Dispatchers.Main) { isImporting = true; importProgress = 0f; importMessage = "正在准备导入..." }
            var totalImported = 0; var totalSkipped = 0; var totalFailed = 0
            for (uri in uris) {
                try {
                    val ctx = ExamApplication.instance.applicationContext
                    val fileName = getFileNameFromUri(ctx, uri)
                    if (fileName.lowercase().endsWith(".zip")) {
                        val result = KnowledgeBaseManager.importZipFromUri(ctx, uri) { cur, tot, name ->
                            scope.launch(Dispatchers.Main) {
                                importProgress = cur.toFloat() / tot; importMessage = "导入 $cur/$tot: $name"
                            }
                        }
                        totalImported += result.importedEntries; totalSkipped += result.skippedFiles; totalFailed += result.failedFiles
                    } else {
                        val inputStream = ctx.contentResolver.openInputStream(uri) ?: continue
                        val ext = fileName.substringAfterLast('.', "xlsx")
                        val tmp = java.io.File(ctx.cacheDir, "kb_import_${System.nanoTime()}.$ext")
                        tmp.outputStream().use { inputStream.copyTo(it) }
                        withContext(Dispatchers.Main) { importProgress = 0.5f; importMessage = "导入: $fileName" }
                        val count = KnowledgeBaseManager.allKBs.getOrNull(kbIdx)?.importExcelWithDedup(tmp.absolutePath, displayFileName = fileName) ?: -1
                        tmp.delete()
                        when { count == -2 -> totalSkipped++; count >= 0 -> { totalImported += count; KnowledgeBaseManager.save() }; else -> totalFailed++ }
                    }
                } catch (_: Exception) { totalFailed++ }
            }
            withContext(Dispatchers.Main) {
                isImporting = false; refreshKey++
                val msg = buildString {
                    if (totalImported > 0) append("导入成功: $totalImported 条")
                    if (totalSkipped > 0) append(" 跳过: $totalSkipped")
                    if (totalFailed > 0) append(" 失败: $totalFailed")
                    if (totalImported == 0 && totalSkipped == 0 && totalFailed == 0) append("未找到可导入的文件")
                }
                Toast.makeText(ExamApplication.instance, msg, Toast.LENGTH_SHORT).show()
            }
        }
    }

    // Detail screen inline
    if (detailKbIndex >= 0) {
        KbDetailScreen(kbIndex = detailKbIndex, onBack = { detailKbIndex = -1; refreshKey++ })
        return
    }

    Column(
        modifier = Modifier.fillMaxSize().background(colors.surface).padding(horizontal = 16.dp),
    ) {
        // ── Overview + Manage (merged) ──
        Card(
            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = colors.surfaceCard),
        ) {
            Column(modifier = Modifier.padding(18.dp)) {
                // Top row: count + action buttons
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("${allEntries.size}", fontSize = 36.sp, fontWeight = FontWeight.Bold, color = colors.primary)
                        Text(KnowledgeBaseManager.activeKBName, fontSize = 13.sp, color = colors.onSurfaceSecondary)
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Box(modifier = Modifier.height(34.dp).clip(RoundedCornerShape(10.dp)).background(colors.primary).clickable { detailKbIndex = KnowledgeBaseManager.allKBs.indexOf(kb).coerceAtLeast(0) }.padding(horizontal = 12.dp), contentAlignment = Alignment.Center) {
                            Row(verticalAlignment = Alignment.CenterVertically) { Text("📖", fontSize = 14.sp); Spacer(modifier = Modifier.width(4.dp)); Text("详情", fontSize = 12.sp, color = androidx.compose.ui.graphics.Color.White, fontWeight = FontWeight.Medium) }
                        }
                        Box(modifier = Modifier.height(34.dp).clip(RoundedCornerShape(10.dp)).background(colors.surfaceCard).border(1.dp, colors.outline, RoundedCornerShape(10.dp)).clickable { showKbSwitcher = true }.padding(horizontal = 12.dp), contentAlignment = Alignment.Center) {
                            Row(verticalAlignment = Alignment.CenterVertically) { Text("🔄", fontSize = 14.sp); Spacer(modifier = Modifier.width(4.dp)); Text("切换", fontSize = 12.sp, color = colors.onSurfaceSecondary, fontWeight = FontWeight.Medium) }
                        }
                    }
                }
                Spacer(modifier = Modifier.height(4.dp))
                Box(modifier = Modifier.background(colors.primary.copy(alpha = 0.1f), RoundedCornerShape(4.dp)).padding(horizontal = 6.dp, vertical = 2.dp)) {
                    Text("● 已激活", fontSize = 10.sp, color = colors.primary, fontWeight = FontWeight.SemiBold)
                }
            }
        }

        // ── Import bar ──
        if (isImporting) {
            LinearProgressIndicator(progress = { importProgress }, modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp), color = colors.success)
            Text(importMessage, fontSize = 11.sp, color = colors.onSurfaceSecondary, modifier = Modifier.padding(bottom = 4.dp))
        }

        Spacer(modifier = Modifier.height(6.dp))

        Row(
            modifier = Modifier.fillMaxWidth().height(42.dp)
                .clip(RoundedCornerShape(12.dp)).background(colors.surfaceCard)
                .clickable { importLauncher.launch(arrayOf("*/*")) }
                .padding(horizontal = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("📤", fontSize = 18.sp)
            Spacer(modifier = Modifier.width(8.dp))
            Text("导入题目", fontSize = 13.sp, fontWeight = FontWeight.Medium, color = colors.onSurface, modifier = Modifier.weight(1f))
            Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                for (f in listOf(".xlsx", ".xls", ".et", ".zip")) {
                    Box(modifier = Modifier.background(colors.outline, RoundedCornerShape(3.dp)).padding(horizontal = 5.dp, vertical = 2.dp)) {
                        Text(f, fontSize = 9.sp, color = colors.onSurfaceSecondary)
                    }
                }
            }
            Text("→", fontSize = 14.sp, color = colors.onSurfaceSecondary)
        }

        Spacer(modifier = Modifier.height(12.dp))

        // ── Search ──
        SearchBar(query = searchQuery, onQueryChange = { searchQuery = it }, placeholder = "搜索题目...", colors = colors, height = 44, radius = 12)

        Spacer(modifier = Modifier.height(8.dp))

        // ── Filter Chips ──
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            for ((key, label) in listOf(null to "全部", "单选题" to "单选题", "多选题" to "多选题", "判断题" to "判断题")) {
                FilterChip(
                    selected = selectedType == key,
                    onClick = { selectedType = if (selectedType == key) null else key },
                    label = { Text(label, fontSize = 11.sp) },
                    colors = FilterChipDefaults.filterChipColors(selectedContainerColor = colors.primary.copy(alpha = 0.12f), selectedLabelColor = colors.primary),
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // ── Question List ──
        if (filtered.isEmpty()) {
            EmptyState(if (allEntries.isEmpty()) "暂无题目，点击上方导入" else "无匹配结果", colors.onSurfaceSecondary)
        } else {
            LazyColumn(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                itemsIndexed(filtered, key = { _, e -> e.hashCode() }) { _, entry ->
                    val idx = allEntries.indexOf(entry)
                    QuestionItem(
                        index = idx, entry = entry, colors = colors,
                        onEdit = { editingEntry = idx to entry },
                        onDelete = { showDeleteConfirm = idx },
                    )
                }
                item { Spacer(modifier = Modifier.height(8.dp)) }
            }
        }
    }

    // ── Dialogs ──
    if (showKbSwitcher) {
        KbSwitcherDialog(colors = colors, onDismiss = { showKbSwitcher = false }, onSelect = { KnowledgeBaseManager.selectKB(it); refreshKey++; showKbSwitcher = false })
    }

    if (editingEntry != null) {
        EditEntryDialog(entry = editingEntry!!, colors = colors, onDismiss = { editingEntry = null }, onSave = { idx, e ->
            kb?.updateEntry(idx, e); KnowledgeBaseManager.save(); refreshKey++; editingEntry = null
        })
    }

    if (showDeleteConfirm != null) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = null },
            title = { Text("确认删除", color = colors.onSurface) },
            text = { Text("删除后将无法恢复", color = colors.onSurfaceSecondary) },
            confirmButton = { TextButton(onClick = { kb?.deleteEntries(setOf(showDeleteConfirm!!)); KnowledgeBaseManager.save(); refreshKey++; showDeleteConfirm = null }) { Text("删除", color = colors.error) } },
            dismissButton = { TextButton(onClick = { showDeleteConfirm = null }) { Text("取消") } },
            containerColor = colors.surfaceCard,
        )
    }
}

// ── Question Item ─────────────────────────────────────────────────

@Composable
private fun QuestionItem(
    index: Int, entry: KBEntry, colors: com.examhelper.app.ui.theme.AppColors,
    onEdit: () -> Unit, onDelete: () -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp), colors = CardDefaults.cardColors(containerColor = colors.surfaceCard)) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(modifier = Modifier.size(28.dp).clip(RoundedCornerShape(8.dp)).background(colors.primary.copy(alpha = 0.1f)), contentAlignment = Alignment.Center) {
                    Text("${index + 1}", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = colors.primary)
                }
                Spacer(modifier = Modifier.width(10.dp))
                Text(entry.question, fontSize = 13.sp, color = colors.onSurface, maxLines = 3, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                if (!entry.questionType.isNullOrBlank()) {
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(entry.questionType!!, fontSize = 10.sp, color = colors.onSurfaceSecondary, modifier = Modifier.background(colors.outline, RoundedCornerShape(4.dp)).padding(horizontal = 6.dp, vertical = 2.dp))
                }
            }

            // Options
            if (entry.options.isNotBlank()) {
                Spacer(modifier = Modifier.height(8.dp))
                val opts = entry.options.split(Regex("""\s{2,}|\n|(?=[A-F]\s*[.、:：)）])""")).filter { it.isNotBlank() }
                if (opts.size >= 2) {
                    val correctLetters = entry.answer.uppercase().filter { it in 'A'..'F' }.toSet()
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                        opts.take(4).forEach { opt ->
                            val letter = opt.trim().firstOrNull()?.uppercaseChar()
                            val isCorrect = letter != null && letter in correctLetters
                            Text(
                                opt.trim().take(30),
                                fontSize = 11.sp,
                                color = if (isCorrect) colors.success else colors.onSurfaceSecondary,
                                fontWeight = if (isCorrect) FontWeight.Bold else FontWeight.Normal,
                            )
                        }
                    }
                } else {
                    Text(entry.options.take(120), fontSize = 11.sp, color = colors.onSurfaceSecondary)
                }
            }

            // Answer + actions
            Spacer(modifier = Modifier.height(6.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("答案: ${entry.answer}", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = colors.success)
                Spacer(modifier = Modifier.weight(1f))
                IconButton(onClick = onEdit, modifier = Modifier.size(30.dp)) {
                    Icon(Icons.Filled.Edit, null, tint = colors.onSurfaceSecondary, modifier = Modifier.size(16.dp))
                }
                IconButton(onClick = onDelete, modifier = Modifier.size(30.dp)) {
                    Icon(Icons.Filled.Delete, null, tint = colors.error, modifier = Modifier.size(16.dp))
                }
            }
        }
    }
}

// ── KB Switcher Dialog ─────────────────────────────────────────────

@Composable
private fun KbSwitcherDialog(colors: com.examhelper.app.ui.theme.AppColors, onDismiss: () -> Unit, onSelect: (Int) -> Unit) {
    var newName by remember { mutableStateOf("") }
    var showNew by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("切换题库", color = colors.onSurface, fontWeight = FontWeight.Bold) },
        text = {
            Column {
                KnowledgeBaseManager.allKBs.forEachIndexed { i, kb ->
                    val isActive = kb == KnowledgeBaseManager.activeKB
                    Row(
                        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(if (isActive) colors.primary.copy(alpha = 0.08f) else colors.surfaceCard).clickable { onSelect(i) }.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(kb.name, fontSize = 13.sp, fontWeight = if (isActive) FontWeight.Bold else FontWeight.Medium, color = colors.onSurface)
                            Text("${kb.count} 条", fontSize = 11.sp, color = colors.onSurfaceSecondary)
                        }
                        if (isActive) Text("✓", color = colors.primary, fontWeight = FontWeight.Bold)
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                }
                TextButton(onClick = { showNew = true }) { Text("+ 新建题库", color = colors.primary) }
                if (showNew) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        OutlinedTextField(value = newName, onValueChange = { newName = it }, placeholder = { Text("题库名称") }, singleLine = true, modifier = Modifier.weight(1f), colors = OutlinedTextFieldDefaults.colors(focusedTextColor = colors.onSurface, unfocusedTextColor = colors.onSurface))
                        Spacer(modifier = Modifier.width(8.dp))
                        TextButton(onClick = {
                            if (newName.isNotBlank()) { KnowledgeBaseManager.addKB(newName.trim()); onSelect(KnowledgeBaseManager.allKBs.size - 1) }
                        }) { Text("创建") }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("关闭") } },
        containerColor = colors.surfaceCard,
    )
}

// ── Edit Dialog ────────────────────────────────────────────────────

@Composable
private fun EditEntryDialog(
    entry: Pair<Int, KBEntry>, colors: com.examhelper.app.ui.theme.AppColors,
    onDismiss: () -> Unit, onSave: (Int, KBEntry) -> Unit,
) {
    var question by remember { mutableStateOf(entry.second.question) }
    var answer by remember { mutableStateOf(entry.second.answer) }
    var options by remember { mutableStateOf(entry.second.options) }
    var qType by remember { mutableStateOf(entry.second.questionType ?: "") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("编辑题目", color = colors.onSurface, fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(value = question, onValueChange = { question = it }, label = { Text("题目") }, minLines = 2, maxLines = 4, modifier = Modifier.fillMaxWidth(), colors = OutlinedTextFieldDefaults.colors(focusedTextColor = colors.onSurface, unfocusedTextColor = colors.onSurface))
                OutlinedTextField(value = answer, onValueChange = { answer = it }, label = { Text("答案") }, singleLine = true, modifier = Modifier.fillMaxWidth(), colors = OutlinedTextFieldDefaults.colors(focusedTextColor = colors.onSurface, unfocusedTextColor = colors.onSurface))
                OutlinedTextField(value = options, onValueChange = { options = it }, label = { Text("选项") }, minLines = 1, maxLines = 3, modifier = Modifier.fillMaxWidth(), colors = OutlinedTextFieldDefaults.colors(focusedTextColor = colors.onSurface, unfocusedTextColor = colors.onSurface))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("题型: ", fontSize = 13.sp, color = colors.onSurfaceSecondary)
                    for (t in listOf("", "单选题", "多选题", "判断题")) {
                        FilterChip(selected = qType == t, onClick = { qType = t }, label = { Text(if (t.isEmpty()) "未知" else t, fontSize = 11.sp) }, modifier = Modifier.padding(end = 4.dp), colors = FilterChipDefaults.filterChipColors(selectedContainerColor = colors.primary.copy(alpha = 0.12f), selectedLabelColor = colors.primary))
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = { onSave(entry.first, entry.second.copy(question = question, answer = answer, options = options, questionType = qType.ifBlank { null })) }) { Text("保存", color = colors.primary) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
        containerColor = colors.surfaceCard,
    )
}

// ── Utils ──────────────────────────────────────────────────────────

private fun getFileNameFromUri(context: android.content.Context, uri: android.net.Uri): String {
    val cursor = context.contentResolver.query(uri, null, null, null, null)
    return cursor?.use {
        if (it.moveToFirst()) {
            val idx = it.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
            if (idx >= 0) it.getString(idx) else uri.lastPathSegment ?: "unknown"
        } else uri.lastPathSegment ?: "unknown"
    } ?: (uri.lastPathSegment ?: "unknown")
}
