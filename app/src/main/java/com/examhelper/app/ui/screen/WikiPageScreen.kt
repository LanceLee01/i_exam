package com.examhelper.app.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.examhelper.app.ExamApplication
import com.examhelper.app.knowledge.KBEngine
import com.examhelper.app.knowledge.db.WikiPage
import com.examhelper.app.knowledge.db.Wikilink
import com.examhelper.app.ui.theme.LocalAppColors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WikiPageScreen(pageUid: String, onBack: () -> Unit) {
    val colors = LocalAppColors.current
    val scope = rememberCoroutineScope()
    val kbEngine = remember { KBEngine(ExamApplication.instance) }
    var page by remember { mutableStateOf<WikiPage?>(null) }
    var wikilinks by remember { mutableStateOf<List<Wikilink>>(emptyList()) }
    var linkedPageMap by remember { mutableStateOf<Map<Long, WikiPage>>(emptyMap()) }

    fun loadPage(uid: String) {
        scope.launch(Dispatchers.IO) {
            val p = kbEngine.getPage(uid)
            if (p != null) {
                val wl = kbEngine.getWikilinks(p.id)
                val linked = mutableMapOf<Long, WikiPage>()
                wl.forEach { kbEngine.getPageById(it.targetId)?.let { lp -> linked[it.id] = lp } }
                withContext(Dispatchers.Main) { page = p; wikilinks = wl; linkedPageMap = linked }
            }
        }
    }

    LaunchedEffect(pageUid) { loadPage(pageUid) }

    if (page == null) {
        Box(modifier = Modifier.fillMaxSize().background(colors.surface), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(color = colors.primary)
        }
        return
    }

    val p = page!!
    val typeColor = when (p.pageType) { "规程" -> colors.primary; "概念" -> colors.success; "流程" -> colors.warning; else -> colors.onSurfaceSecondary }

    Column(modifier = Modifier.fillMaxSize().background(colors.surface)) {
        Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "返回", tint = colors.onSurface) }
            Text(p.title, fontSize = 17.sp, fontWeight = FontWeight.Bold, color = colors.onSurface, modifier = Modifier.weight(1f))
        }

        Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
            if (p.pageType.isNotBlank()) Box(modifier = Modifier.background(typeColor.copy(alpha = 0.08f), RoundedCornerShape(6.dp)).padding(horizontal = 8.dp, vertical = 3.dp)) { Text(p.pageType, fontSize = 11.sp, fontWeight = FontWeight.Medium, color = typeColor) }
            p.tags.split(",").take(4).forEach { t -> if (t.trim().isNotEmpty()) Box(modifier = Modifier.background(colors.outline, RoundedCornerShape(6.dp)).padding(horizontal = 8.dp, vertical = 3.dp)) { Text(t.trim(), fontSize = 11.sp, color = colors.onSurfaceSecondary) } }
            if (p.sources.isNotBlank()) Text("📄 ${p.sources}", fontSize = 11.sp, color = colors.onSurfaceSecondary, modifier = Modifier.padding(start = 4.dp))
        }
        HorizontalDivider(color = colors.outline)

        Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)) {
            val sections = parseMarkdownSections(p.content)
            for (section in sections) {
                when (section.type) {
                    "h2" -> Text(section.text, fontSize = 18.sp, fontWeight = FontWeight.Bold, color = colors.onSurface, modifier = Modifier.padding(top = 20.dp, bottom = 10.dp))
                    "h3" -> Text(section.text, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = colors.onSurface, modifier = Modifier.padding(top = 16.dp, bottom = 8.dp))
                    "tip" -> Box(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp).clip(RoundedCornerShape(8.dp)).background(colors.primary.copy(alpha = 0.05f)).padding(12.dp)) { Text(section.text, fontSize = 13.sp, color = colors.onSurface, lineHeight = 22.sp) }
                    "image" -> Box(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp).clip(RoundedCornerShape(12.dp)).background(colors.surfaceCard), contentAlignment = Alignment.Center) { Text("📷 ${section.text}", fontSize = 12.sp, color = colors.onSurfaceSecondary, modifier = Modifier.padding(24.dp)) }
                    "list" -> Column(modifier = Modifier.padding(start = 8.dp, top = 4.dp, bottom = 8.dp)) {
                        section.text.split("\n").filter { it.isNotBlank() }.forEach { li ->
                            Row(modifier = Modifier.padding(vertical = 3.dp)) { Text("•", color = colors.primary, fontSize = 14.sp, modifier = Modifier.width(16.dp)); Text(li.removePrefix("- ").removePrefix("• "), fontSize = 14.sp, color = colors.onSurface, lineHeight = 22.sp) }
                        }
                    }
                    "text" -> if (section.text.isNotBlank()) Text(section.text, fontSize = 14.sp, color = colors.onSurface, lineHeight = 24.sp, modifier = Modifier.padding(vertical = 4.dp))
                }
            }

            if (wikilinks.isNotEmpty()) {
                Spacer(modifier = Modifier.height(24.dp))
                HorizontalDivider(color = colors.outline)
                Spacer(modifier = Modifier.height(16.dp))
                Text("📎 相关概念", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = colors.onSurface)
                Spacer(modifier = Modifier.height(10.dp))
                for (wl in wikilinks) {
                    val linked = linkedPageMap.values.find { it.id == wl.targetId }
                    if (linked != null) Row(
                        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(colors.surfaceCard).clickable { loadPage(linked.uid) }.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text("📄", fontSize = 18.sp)
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(wl.label.ifBlank { linked.title }, fontSize = 13.sp, fontWeight = FontWeight.Medium, color = colors.onSurface, modifier = Modifier.weight(1f))
                        Text("→", fontSize = 12.sp, color = colors.onSurfaceSecondary)
                    }
                    if (linked != null) Spacer(modifier = Modifier.height(6.dp))
                }
            }
            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}

private data class MdSection(val type: String, val text: String)

private fun parseMarkdownSections(content: String): List<MdSection> {
    val sections = mutableListOf<MdSection>()
    val lines = content.lines()
    var i = 0
    val buf = StringBuilder()
    var bufType = "text"
    fun flush() { if (buf.isNotBlank()) { sections.add(MdSection(bufType, buf.toString().trim())); buf.clear() }; bufType = "text" }
    while (i < lines.size) {
        val line = lines[i]
        when {
            line.trim().startsWith("## ") -> { flush(); sections.add(MdSection("h2", line.removePrefix("## ").trim())); i++ }
            line.trim().startsWith("### ") -> { flush(); sections.add(MdSection("h3", line.removePrefix("### ").trim())); i++ }
            line.trim().startsWith("> ") || line.trim().startsWith(">") -> { flush(); bufType = "tip"; buf.appendLine(line.removePrefix(">").trim()); i++ }
            line.trim().startsWith("!") && line.contains("](") -> { flush(); val alt = line.substringAfter("[").substringBefore("]"); sections.add(MdSection("image", alt)); i++ }
            line.trim().startsWith("- ") || line.trim().startsWith("• ") -> { if (bufType != "list") { flush(); bufType = "list" }; buf.appendLine(line.trim()); i++ }
            line.isBlank() -> { flush(); i++ }
            else -> { if (bufType != "text") flush(); buf.appendLine(line.trim()); i++ }
        }
    }
    flush()
    if (sections.isEmpty()) sections.add(MdSection("text", content.take(500)))
    return sections
}
