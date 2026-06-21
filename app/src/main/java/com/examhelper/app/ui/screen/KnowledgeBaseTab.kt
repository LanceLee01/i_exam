package com.examhelper.app.ui.screen

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.examhelper.app.ExamApplication
import com.examhelper.app.knowledge.KBEngine
import com.examhelper.app.knowledge.db.WikiPage
import com.examhelper.app.ui.components.EmptyState
import com.examhelper.app.ui.components.SearchBar
import com.examhelper.app.ui.theme.LocalAppColors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun KnowledgeBaseTab(isDarkMode: Boolean) {
    val colors = LocalAppColors.current
    val scope = rememberCoroutineScope()
    val kbEngine = remember { KBEngine.getInstance(ExamApplication.instance) }
    var searchQuery by remember { mutableStateOf("") }
    var selectedCategory by remember { mutableStateOf<String?>(null) }
    var isImporting by remember { mutableStateOf(false) }
    var importProgress by remember { mutableStateOf("") }
    var refreshKey by remember { mutableStateOf(0L) }
    var selectedPageUid by remember { mutableStateOf<String?>(null) }
    var showClearConfirm by remember { mutableStateOf(false) }
    var llmAnswer by remember { mutableStateOf<com.examhelper.app.knowledge.LlmAnswer?>(null) }
    var isAsking by remember { mutableStateOf(false) }

    var allPages by remember { mutableStateOf<List<WikiPage>>(emptyList()) }
    var searchResults by remember { mutableStateOf<List<WikiPage>?>(null) }
    var pageCount by remember { mutableIntStateOf(0) }
    var sourceCount by remember { mutableIntStateOf(0) }

    LaunchedEffect(refreshKey) {
        withContext(Dispatchers.IO) {
            allPages = kbEngine.getAllPages()
            pageCount = allPages.size
            sourceCount = kbEngine.getSourceFileCount()
        }
    }

    LaunchedEffect(searchQuery) {
        if (searchQuery.length >= 2) {
            delay(300)  // debounce user typing
            withContext(Dispatchers.IO) { searchResults = kbEngine.searchByQuestion(searchQuery).pages }
        } else {
            searchResults = null
        }
    }

    val displayPages = remember(allPages, searchResults, selectedCategory, searchQuery) {
        val base = if (searchQuery.length >= 2 && searchResults != null) searchResults!! else allPages
        if (selectedCategory != null) base.filter { it.pageType == selectedCategory } else base
    }
    val categories = remember(allPages) {
        listOf(null to "全部") + allPages.map { it.pageType }.distinct().filter { it.isNotBlank() }.map { it to it }
    }

    val docLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        if (uris.isNullOrEmpty()) return@rememberLauncherForActivityResult
        scope.launch(Dispatchers.IO) {
            withContext(Dispatchers.Main) { isImporting = true; importProgress = "" }
            var imported = 0
            var totalGenerated = 0
            for ((idx, uri) in uris.withIndex()) {
                try {
                    val r = kbEngine.importFile(uri)
                    if (r.success) { imported++; totalGenerated += r.pagesGenerated }
                    withContext(Dispatchers.Main) { importProgress = "已处理 ${idx + 1}/${uris.size} 个文件，已生成 $totalGenerated 页" }
                } catch (_: Exception) {}
            }
            withContext(Dispatchers.Main) { isImporting = false; importProgress = ""; refreshKey++; if (imported > 0) Toast.makeText(ExamApplication.instance, "导入完成: $imported 个文件, $totalGenerated 页面", Toast.LENGTH_SHORT).show() }
        }
    }

    if (selectedPageUid != null) {
        WikiPageScreen(pageUid = selectedPageUid!!, onBack = { selectedPageUid = null })
        return
    }

    Column(modifier = Modifier.fillMaxSize().background(colors.surface).padding(horizontal = 16.dp)) {
        // Overview stats
        Row(
            modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp).clip(RoundedCornerShape(16.dp)).background(colors.surfaceCard).padding(18.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
        ) {
            StatItem("$pageCount", "Wiki 页面", colors.primary)
            StatItem("$sourceCount", "导入文档", colors.success)
            StatItem("${categories.size - 1}", "分类", colors.onSurfaceSecondary)
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Format badges
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            for (f in listOf("📄PPT", "📕PDF", "📊Excel", "📝TXT", "📋MD")) {
                Box(modifier = Modifier.clip(RoundedCornerShape(8.dp)).background(colors.primary.copy(alpha = 0.06f)).padding(horizontal = 8.dp, vertical = 4.dp)) {
                    Text(f, fontSize = 10.sp, color = colors.primary, fontWeight = FontWeight.Medium)
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Search + AI Q&A + import
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Box(modifier = Modifier.weight(1f)) {
                SearchBar(query = searchQuery, onQueryChange = { searchQuery = it; llmAnswer = null }, placeholder = "输入问题或关键词...", colors = colors, height = 44, radius = 12)
            }
            // AI 问答 button
            Box(
                modifier = Modifier.height(44.dp).clip(RoundedCornerShape(12.dp)).background(colors.primary).clickable(enabled = searchQuery.length >= 2 && !isAsking) {
                    isAsking = true; llmAnswer = null
                    scope.launch(Dispatchers.IO) {
                        val answer = kbEngine.answerQuestion(searchQuery)
                        withContext(Dispatchers.Main) { llmAnswer = answer; isAsking = false }
                    }
                }.padding(horizontal = 14.dp), contentAlignment = Alignment.Center,
            ) {
                if (isAsking) CircularProgressIndicator(modifier = Modifier.size(18.dp), color = androidx.compose.ui.graphics.Color.White, strokeWidth = 2.dp)
                else Text("AI 问答", color = androidx.compose.ui.graphics.Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
            }
            // Import button
            Box(
                modifier = Modifier.size(44.dp).clip(RoundedCornerShape(12.dp)).background(colors.surfaceCard).border(1.dp, colors.outline, RoundedCornerShape(12.dp)).clickable { docLauncher.launch(arrayOf("*/*")) },
                contentAlignment = Alignment.Center,
            ) { Text("+", fontSize = 20.sp, color = colors.primary, fontWeight = FontWeight.Bold) }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Import progress indicator
        if (isImporting && importProgress.isNotEmpty()) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(bottom = 4.dp),
            ) {
                CircularProgressIndicator(modifier = Modifier.size(14.dp), color = colors.primary, strokeWidth = 2.dp)
                Spacer(modifier = Modifier.width(8.dp))
                Text(importProgress, fontSize = 12.sp, color = colors.onSurfaceSecondary)
            }
        }

        // LLM Answer card
        if (llmAnswer != null) {
            Card(
                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = colors.primary.copy(alpha = 0.04f)),
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("🤖", fontSize = 16.sp); Spacer(modifier = Modifier.width(6.dp))
                        Text("AI 回答", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = colors.primary)
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(llmAnswer!!.answer, fontSize = 13.sp, color = colors.onSurface, lineHeight = 22.sp)
                    if (llmAnswer!!.references.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(10.dp))
                        HorizontalDivider(color = colors.outline)
                        Spacer(modifier = Modifier.height(8.dp))
                        Text("📎 参考页面", fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = colors.onSurfaceSecondary)
                        Spacer(modifier = Modifier.height(4.dp))
                        llmAnswer!!.references.forEach { (uid, title) ->
                            Text("· $title", fontSize = 12.sp, color = colors.primary, modifier = Modifier.clickable { selectedPageUid = uid }.padding(vertical = 2.dp))
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.height(4.dp))
        }

        // Category chips
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            categories.take(7).forEach { (key, label) ->
                FilterChip(selected = selectedCategory == key, onClick = { selectedCategory = if (selectedCategory == key) null else key },
                    label = { Text(label, fontSize = 11.sp) },
                    colors = FilterChipDefaults.filterChipColors(selectedContainerColor = colors.primary.copy(alpha = 0.1f), selectedLabelColor = colors.primary))
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        if (displayPages.isEmpty()) {
            EmptyState(if (allPages.isEmpty()) "暂无知识页面，点击 + 导入文档" else "无匹配结果", colors.onSurfaceSecondary)
        } else {
            LazyColumn(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(displayPages, key = { it.uid }) { page -> WikiPageCard(page, colors, onClick = { selectedPageUid = page.uid }) }
                item { Spacer(modifier = Modifier.height(8.dp)) }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))
        Button(onClick = { showClearConfirm = true }, modifier = Modifier.fillMaxWidth().height(40.dp), shape = RoundedCornerShape(12.dp), colors = ButtonDefaults.buttonColors(containerColor = colors.error.copy(alpha = 0.1f))) {
            Text("清空知识库", fontSize = 13.sp, color = colors.error)
        }
        Spacer(modifier = Modifier.height(16.dp))
    }

    if (showClearConfirm) AlertDialog(
        onDismissRequest = { showClearConfirm = false }, title = { Text("清空知识库", color = colors.onSurface) },
        text = { Text("删除全部 Wiki 页面，不可撤销。", color = colors.onSurfaceSecondary) },
        confirmButton = { TextButton(onClick = { scope.launch(Dispatchers.IO) { kbEngine.clearAll(); withContext(Dispatchers.Main) { refreshKey++; showClearConfirm = false } } }) { Text("确认", color = colors.error) } },
        dismissButton = { TextButton(onClick = { showClearConfirm = false }) { Text("取消") } }, containerColor = colors.surfaceCard,
    )
}

@Composable
private fun StatItem(count: String, label: String, accent: androidx.compose.ui.graphics.Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(count, fontSize = 26.sp, fontWeight = FontWeight.Bold, color = accent)
        Text(label, fontSize = 11.sp, color = LocalAppColors.current.onSurfaceSecondary, modifier = Modifier.padding(top = 4.dp))
    }
}

@Composable
private fun WikiPageCard(page: WikiPage, colors: com.examhelper.app.ui.theme.AppColors, onClick: () -> Unit) {
    val typeColor = when (page.pageType) { "规程" -> colors.primary; "概念" -> colors.success; "流程" -> colors.warning; else -> colors.onSurfaceSecondary }
    Card(modifier = Modifier.fillMaxWidth().clickable(onClick = onClick), shape = RoundedCornerShape(12.dp), colors = CardDefaults.cardColors(containerColor = colors.surfaceCard)) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("📄", fontSize = 18.sp); Spacer(modifier = Modifier.width(8.dp))
                Text(page.title, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = colors.onSurface, modifier = Modifier.weight(1f))
                if (page.pageType.isNotBlank()) Box(modifier = Modifier.background(typeColor.copy(alpha = 0.08f), RoundedCornerShape(4.dp)).padding(horizontal = 6.dp, vertical = 2.dp)) {
                    Text(page.pageType, fontSize = 10.sp, color = typeColor)
                }
            }
            if (page.tags.isNotBlank()) { Spacer(modifier = Modifier.height(6.dp)); Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                page.tags.split(",").take(4).forEach { t -> if (t.trim().isNotEmpty()) Box(modifier = Modifier.background(colors.outline, RoundedCornerShape(3.dp)).padding(horizontal = 6.dp, vertical = 1.dp)) { Text(t.trim(), fontSize = 10.sp, color = colors.onSurfaceSecondary) } }
            }}
            if (page.summary.isNotBlank()) { Spacer(modifier = Modifier.height(6.dp)); Text(page.summary.take(120), fontSize = 12.sp, color = colors.onSurfaceSecondary, maxLines = 2, overflow = TextOverflow.Ellipsis, lineHeight = 18.sp) }
            if (page.sources.isNotBlank()) { Spacer(modifier = Modifier.height(8.dp)); HorizontalDivider(color = colors.outline); Spacer(modifier = Modifier.height(6.dp)); Text("📎 ${page.sources}", fontSize = 10.sp, color = colors.onSurfaceSecondary) }
        }
    }
}
