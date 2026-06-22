package com.examhelper.app.ui.screen

import android.util.Log
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
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
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

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
    var showRemoteDialog by remember { mutableStateOf(false) }
    var remoteFiles by remember { mutableStateOf<List<com.examhelper.app.network.RemoteFile>>(emptyList()) }
    var remoteLoading by remember { mutableStateOf(false) }
    var remoteDownloading by remember { mutableStateOf<String?>(null) }
    var remoteError by remember { mutableStateOf<String?>(null) }

    val kb = remember(refreshKey) { KnowledgeBaseManager.activeKB }
    val allEntries = remember(kb, refreshKey) { kb?.entries?.toList() ?: emptyList() }

    // Crash log helper — must be a val lambda, not a named fun (K2 compiler restriction in @Composable)
    val logCrash: (String, Throwable?) -> Unit = remember { { msg, ex ->
        try {
            val tag = "QuestionBankCrash"
            Log.e(tag, msg, ex)
            val logDir = File(ExamApplication.instance.filesDir, "crash_logs")
            logDir.mkdirs()
            val file = File(logDir, "crash_${SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())}.txt")
            PrintWriter(file).use { pw ->
                pw.println("=== 题库搜索崩溃 ===")
                pw.println("时间: ${SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US).format(Date())}")
                pw.println("搜索关键字: $searchQuery")
                pw.println("筛选类型: $selectedType")
                pw.println("活跃KB: ${KnowledgeBaseManager.activeKBName}")
                pw.println("总条目数: ${allEntries.size}")
                pw.println()
                pw.println("=== 异常信息 ===")
                pw.println(msg)
                if (ex != null) {
                    val sw = StringWriter()
                    ex.printStackTrace(PrintWriter(sw))
                    pw.println()
                    pw.println("=== 堆栈跟踪 ===")
                    pw.print(sw.toString())
                }
                pw.println()
                pw.println("=== 搜索结果概要 ===")
                try {
                    val matched = allEntries.count {
                        try {
                            it.question.lowercase().contains(searchQuery.lowercase()) ||
                            it.answer.lowercase().contains(searchQuery.lowercase()) ||
                            it.options.lowercase().contains(searchQuery.lowercase())
                        } catch (_: Exception) { false }
                    }
                    pw.println("匹配条目数: $matched")
                    pw.println()
                    pw.println("=== 匹配条目详情 ===")
                    allEntries.forEachIndexed { i, e ->
                        try {
                            if (e.question.lowercase().contains(searchQuery.lowercase()) ||
                                e.answer.lowercase().contains(searchQuery.lowercase()) ||
                                e.options.lowercase().contains(searchQuery.lowercase())) {
                                pw.println("[${i}] 题目(${e.question.length}字): ${e.question.take(100)}")
                                pw.println("    答案: ${e.answer}")
                                pw.println("    选项(${e.options.length}字): ${e.options.take(200)}")
                                pw.println("    题型: ${e.questionType ?: "null"}")
                                pw.println()
                            }
                        } catch (err: Exception) {
                            pw.println("[${i}] ERROR dumping: ${err.message}")
                        }
                    }
                } catch (e: Exception) {
                    pw.println("搜索概要提取失败: ${e.message}")
                }
            }
            Log.i(tag, "Crash log saved to: ${file.absolutePath}")
        } catch (e: Exception) {
            Log.e("QuestionBankCrash", "Failed to write crash log", e)
        }
    } }

    // Filter
    val filtered = remember(allEntries, searchQuery, selectedType) {
        try {
            var r = allEntries
            if (searchQuery.length >= 2) {
                val q = searchQuery.lowercase()
                // 日志: 开始过滤
                Log.d("QuestionBankFilter", "Start filter: query='$q', entries=${allEntries.size}")
                var filterCrashed = false
                var crashIndex = -1
                r = r.filterIndexed { i, entry ->
                    if (filterCrashed) return@filterIndexed false
                    try {
                        entry.question.lowercase().contains(q) || entry.answer.lowercase().contains(q) ||
                        entry.options.lowercase().contains(q)
                    } catch (ex: Exception) {
                        filterCrashed = true
                        crashIndex = i
                        logCrash("filter 崩溃: 第 $i 条条目过滤异常, question='${entry.question.take(80)}', answer='${entry.answer.take(80)}', options='${entry.options.take(80)}'", ex)
                        false
                    }
                }
            }
            if (selectedType != null) {
                try {
                    r = r.filter { it.questionType == selectedType }
                } catch (ex: Exception) {
                    logCrash("题型过滤异常", ex)
                }
            }
            Log.d("QuestionBankFilter", "Filter done: result=${r.size}")
            r
        } catch (e: Exception) {
            logCrash("filter 整体异常", e)
            emptyList()
        }
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

        // ── Import bars ──
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            // Local import
            Row(
                modifier = Modifier.weight(1f).height(42.dp)
                    .clip(RoundedCornerShape(12.dp)).background(colors.surfaceCard)
                    .clickable { importLauncher.launch(arrayOf("*/*")) }
                    .padding(horizontal = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("📤", fontSize = 16.sp)
                Spacer(modifier = Modifier.width(6.dp))
                Text("本地导入", fontSize = 12.sp, fontWeight = FontWeight.Medium, color = colors.onSurface, modifier = Modifier.weight(1f))
                Text("→", fontSize = 14.sp, color = colors.onSurfaceSecondary)
            }
            // Remote import
            Row(
                modifier = Modifier.weight(1f).height(42.dp)
                    .clip(RoundedCornerShape(12.dp)).background(colors.info.copy(alpha = 0.08f))
                    .clickable {
                        showRemoteDialog = true; remoteFiles = emptyList(); remoteError = null; remoteLoading = true
                        scope.launch(Dispatchers.IO) {
                            com.examhelper.app.network.RemoteTikuClient.listFiles().fold(
                                onSuccess = { withContext(Dispatchers.Main) { remoteFiles = it; remoteLoading = false } },
                                onFailure = { withContext(Dispatchers.Main) { remoteError = it.message; remoteLoading = false } }
                            )
                        }
                    }
                    .padding(horizontal = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("☁️", fontSize = 16.sp)
                Spacer(modifier = Modifier.width(6.dp))
                Text("云端导入", fontSize = 12.sp, fontWeight = FontWeight.Medium, color = colors.info)
            }
        }

        Spacer(modifier = Modifier.height(4.dp))
        if (isImporting) {
            LinearProgressIndicator(progress = { importProgress }, modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp), color = colors.success)
            Text(importMessage, fontSize = 11.sp, color = colors.onSurfaceSecondary, modifier = Modifier.padding(bottom = 4.dp))
        }

        Spacer(modifier = Modifier.height(10.dp))

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
                itemsIndexed(filtered, key = { idx, e -> "${idx}_${e.hashCode()}" }) { index, entry ->
                    val idx = allEntries.indexOf(entry)
                    QuestionItem(
                        index = if (idx >= 0) idx else index, entry = entry, colors = colors,
                        onEdit = { editingEntry = if (idx >= 0) idx to entry else index to entry },
                        onDelete = { showDeleteConfirm = if (idx >= 0) idx else index },
                    )
                }
                item { Spacer(modifier = Modifier.height(8.dp)) }
            }
        }
    }

    if (showRemoteDialog) {
        RemoteImportDialog(
            colors = colors,
            files = remoteFiles,
            isLoading = remoteLoading,
            downloadingFile = remoteDownloading,
            error = remoteError,
            onDismiss = { showRemoteDialog = false },
            onRefresh = {
                remoteLoading = true; remoteError = null
                scope.launch(Dispatchers.IO) {
                    com.examhelper.app.network.RemoteTikuClient.listFiles().fold(
                        onSuccess = { withContext(Dispatchers.Main) { remoteFiles = it; remoteLoading = false } },
                        onFailure = { withContext(Dispatchers.Main) { remoteError = it.message; remoteLoading = false } }
                    )
                }
            },
            onDownload = { file ->
                remoteDownloading = file.name
                scope.launch(Dispatchers.IO) {
                    val result = com.examhelper.app.network.RemoteTikuClient.download(file.name, ExamApplication.instance.cacheDir)
                    withContext(Dispatchers.Main) {
                        remoteDownloading = null
                        if (result.success && result.localPath != null) {
                            val count = kb?.importExcelWithDedup(result.localPath, displayFileName = file.name) ?: -1
                            if (count >= 0) { KnowledgeBaseManager.save(); refreshKey++; Toast.makeText(ExamApplication.instance, "导入成功: $count 条", Toast.LENGTH_SHORT).show() }
                            java.io.File(result.localPath).delete()
                        } else {
                            remoteError = result.error
                        }
                    }
                }
            },
        )
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
                val opts = try {
                    entry.options.split(Regex("""\s{2,}|\n|(?=[A-F]\s*[.、:：)）])""")).filter { it.isNotBlank() }
                } catch (e: Exception) {
                    Log.e("QuestionItem", "options split crash: entry=${entry.question.take(40)}, options=${entry.options.take(200)}", e)
                    listOf(entry.options.take(120))
                }
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

// ── Remote Import Dialog ──────────────────────────────────────────

@Composable
private fun RemoteImportDialog(
    colors: com.examhelper.app.ui.theme.AppColors,
    files: List<com.examhelper.app.network.RemoteFile>,
    isLoading: Boolean,
    downloadingFile: String?,
    error: String?,
    onDismiss: () -> Unit,
    onRefresh: () -> Unit,
    onDownload: (com.examhelper.app.network.RemoteFile) -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("☁️ 云端题库", color = colors.onSurface, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.weight(1f))
                if (!isLoading) {
                    TextButton(onClick = onRefresh) { Text("刷新", color = colors.primary, fontSize = 12.sp) }
                }
            }
        },
        text = {
            Column(modifier = Modifier.height(400.dp).verticalScroll(rememberScrollState())) {
                Text("服务器: 106.14.10.27", fontSize = 11.sp, color = colors.onSurfaceSecondary, modifier = Modifier.padding(bottom = 8.dp))
                if (isLoading) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(modifier = Modifier.size(18.dp), color = colors.primary, strokeWidth = 2.dp)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("加载中...", fontSize = 13.sp, color = colors.onSurfaceSecondary)
                    }
                } else if (error != null) {
                    Text("连接失败: $error", color = colors.error, fontSize = 13.sp)
                } else if (files.isEmpty()) {
                    Text("服务器暂无题库文件", color = colors.onSurfaceSecondary, fontSize = 13.sp)
                    Text("将 Excel 文件上传到服务器 /www/wwwroot/default/tiku/ 目录", fontSize = 11.sp, color = colors.onSurfaceMuted, modifier = Modifier.padding(top = 8.dp))
                } else {
                    files.forEach { file ->
                        Row(
                            modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).background(colors.surfaceCard).padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(if (file.name.endsWith(".zip")) "📦" else "📊", fontSize = 18.sp)
                            Spacer(modifier = Modifier.width(8.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(file.name, fontSize = 12.sp, color = colors.onSurface, fontWeight = FontWeight.Medium)
                                Text(formatSize(file.size), fontSize = 10.sp, color = colors.onSurfaceSecondary)
                            }
                            if (downloadingFile == file.name) {
                                CircularProgressIndicator(modifier = Modifier.size(18.dp), color = colors.primary, strokeWidth = 2.dp)
                            } else {
                                Box(modifier = Modifier.clip(RoundedCornerShape(6.dp)).background(colors.primary).clickable { onDownload(file) }.padding(horizontal = 10.dp, vertical = 4.dp)) {
                                    Text("导入", fontSize = 11.sp, color = androidx.compose.ui.graphics.Color.White, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("关闭", color = colors.primary) } },
        containerColor = colors.surfaceCard,
    )
}

private fun formatSize(bytes: Long): String = when {
    bytes < 1024 -> "$bytes B"
    bytes < 1024 * 1024 -> "${bytes / 1024} KB"
    else -> "${"%.1f".format(bytes.toDouble() / 1024 / 1024)} MB"
}
