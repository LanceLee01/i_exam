package com.examhelper.app.ui.sidebar

import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccessibilityNew
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.delay
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.examhelper.app.ExamApplication
import com.examhelper.app.knowledge.KBEngine
import com.examhelper.app.knowledge.KnowledgeBaseManager
import com.examhelper.app.network.LLMClient
import com.examhelper.app.ui.theme.TextCorrect
import com.examhelper.app.ui.theme.TextError
import com.examhelper.app.ui.theme.TextSecondary
import com.examhelper.app.util.ExtractedTextBus
import com.examhelper.app.util.ExtractedTextBus.AnswerSource
import com.examhelper.app.util.ExtractedTextBus.SidebarState
import kotlinx.coroutines.launch

@Composable
fun SidebarPanel(onHide: () -> Unit) {
    val state by ExtractedTextBus.sidebarState.collectAsState()
    val scope = rememberCoroutineScope()
    val scrollState = rememberScrollState()
    val kbEngine = remember { KBEngine(ExamApplication.instance) }

    val isAccessibilityConnected by ExtractedTextBus.accessibilityConnected.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(
                brush = Brush.verticalGradient(
                    colors = listOf(
                        Color(0xB31A1A30),
                        Color(0xB3121220)
                    )
                )
            )
    ) {
        // ── 顶部标题栏 ──
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Filled.Psychology,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(24.dp)
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = "考试助手",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
            Spacer(Modifier.weight(1f))
            IconButton(onClick = onHide, modifier = Modifier.size(32.dp)) {
                Icon(
                    imageVector = Icons.Filled.Close,
                    contentDescription = "关闭",
                    tint = Color.White.copy(alpha = 0.6f),
                    modifier = Modifier.size(20.dp)
                )
            }
        }

        HorizontalDivider(color = Color.White.copy(alpha = 0.08f))

        // ── 主内容区 ──
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(scrollState)
                .padding(16.dp)
        ) {
            // 无障碍状态提示
            if (!isAccessibilityConnected) {
                StatusBanner(
                    icon = Icons.Filled.AccessibilityNew,
                    text = "无障碍服务未连接",
                    isError = true
                )
                Spacer(Modifier.height(8.dp))
            }

            // 读取屏幕按钮
            val isPending = state is SidebarState.Loading
            Button(
                onClick = {
                    if (!isAccessibilityConnected) {
                        ExtractedTextBus.updateSidebarState(
                            SidebarState.Error("请先开启无障碍服务")
                        )
                        return@Button
                    }
                    ExtractedTextBus.updateSidebarState(
                        SidebarState.Loading("正在读取屏幕...")
                    )
                    ExtractedTextBus.sendEvent(ExtractedTextBus.Event.RequestExtract)
                },
                enabled = !isPending,
                modifier = Modifier.fillMaxWidth().height(52.dp),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary
                )
            ) {
                if (isPending) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        color = Color.White,
                        strokeWidth = 2.dp
                    )
                    Spacer(Modifier.width(8.dp))
                    Text("读取中...", fontSize = 15.sp)
                } else {
                    Icon(
                        Icons.Filled.Visibility,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text("读取屏幕", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                }
            }

            // 根据状态显示内容
            when (val s = state) {
                is SidebarState.Idle -> {
                    Spacer(Modifier.height(32.dp))
                    StatusHint("空闲检测中...")
                }

                is SidebarState.Loading -> {
                    var elapsedSec by remember { mutableIntStateOf(0) }
                    LaunchedEffect(s.startTimeMs) {
                        while (true) {
                            elapsedSec = if (s.startTimeMs > 0)
                                ((System.currentTimeMillis() - s.startTimeMs) / 1000).toInt() else 0
                            delay(1000)
                        }
                    }
                    val speed = if (ExtractedTextBus.lastTokensPerSec > 0) ExtractedTextBus.lastTokensPerSec else 35f
                    val ttftEstSec = if (ExtractedTextBus.lastTtftMs > 0) ExtractedTextBus.lastTtftMs / 1000f else 0f
                    val remainingEst = (s.maxTokens / speed).toInt()
                    val totalEst = (maxOf(elapsedSec.toFloat(), ttftEstSec) + remainingEst).toInt()
                    Log.d("DebugETA", "elapsedSec=$elapsedSec lastTtftMs=${ExtractedTextBus.lastTtftMs} maxTokens=${s.maxTokens} lastTokensPerSec=${ExtractedTextBus.lastTokensPerSec} totalEst=$totalEst")
                    val promptInfo = if (ExtractedTextBus.lastPromptTokens > 0)
                        " [prompt:${ExtractedTextBus.lastPromptTokens}tok]" else ""
                    Spacer(Modifier.height(24.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            color = MaterialTheme.colorScheme.primary,
                            strokeWidth = 2.dp
                        )
                        Spacer(Modifier.width(8.dp))
                        Text("${s.message}（${elapsedSec}s 预估总时间${totalEst}s）$promptInfo", color = TextSecondary, fontSize = 13.sp)
                    }
                }

                is SidebarState.Preview -> {
                    Spacer(Modifier.height(12.dp))

                    Button(
                        onClick = {
                            scope.launch {
                                val requestStartMs = System.currentTimeMillis()
                                val config = ExamApplication.instance.appConfig.getSnapshot()
                                val maxTokens = config.maxTokens
                                val client = LLMClient()
                                val userMessage = "以下是考试界面提取的文字，请根据内容答题：\n\n${s.text}"

                                // L1: Excel 题库精准匹配
                                val excelHits = KnowledgeBaseManager.activeKB?.search(s.text, topN = 5) ?: emptyList()
                                val excelDirectHit = excelHits.firstOrNull()?.takeIf { (_, score) -> score >= 0.70f }

                                if (excelDirectHit != null) {
                                    val (entry, _) = excelDirectHit
                                    Log.d("SidebarPanel", "Excel direct hit: ${entry.answer}")
                                    ExtractedTextBus.updateSidebarState(
                                        SidebarState.Done(s.text, entry.answer, AnswerSource.EXCEL_MATCH)
                                    )
                                    return@launch
                                }

                                // L2: Wiki 知识库检索
                                val wikiResult = kbEngine.searchByQuestion(s.text)
                                val combinedPages = (wikiResult.ftsPages + wikiResult.trigramPages).distinctBy { it.id }
                                val wikiTopScore = combinedPages.maxOfOrNull { page ->
                                    val pTri = com.examhelper.app.knowledge.KBEntry.computeTrigrams(page.title + page.summary.take(200))
                                    val qTri = com.examhelper.app.knowledge.KBEntry.computeTrigrams(s.text)
                                    com.examhelper.app.knowledge.KBEntry.jaccard(qTri, pTri)
                                } ?: 0f

                                if (wikiTopScore >= 0.50f && combinedPages.isNotEmpty()) {
                                    val answer = kbEngine.getAnswerFromKB(s.text, combinedPages) ?: ""
                                    Log.d("SidebarPanel", "Wiki KB direct hit, score=$wikiTopScore pages=${combinedPages.size}")
                                    ExtractedTextBus.updateSidebarState(
                                        SidebarState.Done(s.text, answer, AnswerSource.KB_MATCH)
                                    )
                                    return@launch
                                }

                                // Build context for LLM (L3: KB推断 / L4: 纯AI)
                                var llmSource = AnswerSource.LLM_DIRECT
                                var effectiveMessage = userMessage

                                val excelHints = excelHits.filter { (_, score) -> score >= 0.40f }
                                val wikiHints = if (wikiTopScore >= 0.20f) combinedPages else emptyList()

                                if (excelHints.isNotEmpty() || wikiHints.isNotEmpty()) {
                                    val parts = mutableListOf<String>()

                                    if (excelHints.isNotEmpty()) {
                                        val excelCtx = excelHints.joinToString("\n") { (e, _) ->
                                            "题目: ${e.question}\n答案: ${e.answer}"
                                        }
                                        parts.add("以下是题库中匹配的题目和答案，请优先参考：\n\n$excelCtx")
                                        llmSource = AnswerSource.KB_INFER
                                    }

                                    if (wikiHints.isNotEmpty()) {
                                        val wikiCtx = wikiHints.joinToString("\n\n") { page ->
                                            "【${page.title}】\n${page.summary}\n${page.content.take(300)}"
                                        }
                                        parts.add("以下是知识库中的相关知识点，请参考后作答：\n\n$wikiCtx")
                                        llmSource = AnswerSource.KB_INFER
                                    }

                                    effectiveMessage = parts.joinToString("\n\n") + "\n\n$userMessage"
                                }

                                ExtractedTextBus.updateSidebarState(
                                    SidebarState.Loading("正在调用 LLM 解答...", requestStartMs, maxTokens)
                                )
                                try {
                                    val textLen = effectiveMessage.length
                                    val estTokens = (textLen / 1.5).toInt().coerceAtLeast(50)
                                    ExtractedTextBus.lastPromptTokens = estTokens
                                    val accumulated = StringBuilder()
                                    val estimatedTotalTokens = maxTokens.coerceAtLeast(1)
                                    var firstChunk = true
                                    var streamStartMs = 0L
                                    client.chatStream(
                                        endpoint = config.apiEndpoint,
                                        apiKey = config.apiKey,
                                        model = config.modelName,
                                        temperature = config.temperature,
                                        maxTokens = maxTokens,
                                        systemPrompt = config.systemPrompt,
                                        userMessage = effectiveMessage
                                    ).collect { chunk ->
                                        if (firstChunk) {
                                            firstChunk = false
                                            streamStartMs = System.currentTimeMillis()
                                            ExtractedTextBus.lastTtftMs = streamStartMs - requestStartMs
                                        }
                                        accumulated.append(chunk)
                                        val roughTokenEstimate = accumulated.length * 2
                                        val progress = (roughTokenEstimate.toFloat() / estimatedTotalTokens)
                                            .coerceIn(0f, 0.95f)
                                        val elapsed = ((System.currentTimeMillis() - streamStartMs) / 1000).toInt() + 1
                                        val speed = roughTokenEstimate.toFloat() / elapsed
                                        if (speed > 0) ExtractedTextBus.lastTokensPerSec = speed
                                        ExtractedTextBus.updateSidebarState(
                                            SidebarState.Streaming(s.text, accumulated.toString(), progress, requestStartMs, maxTokens)
                                        )
                                    }
                                    val finalAnswer = if (accumulated.isEmpty()) client.reasoningBuffer.toString() else accumulated.toString()
                                    ExtractedTextBus.updateSidebarState(
                                        SidebarState.Done(s.text, finalAnswer, llmSource)
                                    )
                                } catch (e: Exception) {
                                    ExtractedTextBus.updateSidebarState(
                                        SidebarState.Error("请求异常: ${e.message}")
                                    )
                                }
                            }
                        },
                        modifier = Modifier.fillMaxWidth().height(48.dp),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFF22C55E)
                        )
                    ) {
                        Icon(
                            Icons.Filled.Psychology,
                            contentDescription = null,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(Modifier.width(8.dp))
                        Text("解答", fontSize = 15.sp, fontWeight = FontWeight.Bold)
                    }

                    Spacer(Modifier.height(12.dp))

                    SectionHeader("识别结果")
                    Text(
                        text = s.text,
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.White.copy(alpha = 0.85f),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color.White.copy(alpha = 0.05f))
                            .padding(12.dp),
                        lineHeight = 22.sp
                    )
                }

                is SidebarState.Done -> {
                    Log.d("SidebarPanel", "Done state rendered, answer length=${s.answer.length}")
                    Spacer(Modifier.height(12.dp))
                    SectionHeader("答案")
                    Text(
                        text = "来源: ${s.source.label}",
                        color = Color(0xFF22C55E).copy(alpha = 0.7f),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.padding(bottom = 4.dp)
                    )
                    val lines = s.answer.lines()
                    lines.forEach { line ->
                        val isAnswerLine = line.contains("✓") ||
                            Regex("""^\s*[\[【]?\d+[\]】]?\s*[A-Da-d]\b""").containsMatchIn(line)
                        Text(
                            text = line,
                            style = MaterialTheme.typography.bodyMedium,
                            color = Color.White,
                            fontWeight = if (isAnswerLine) FontWeight.Bold else FontWeight.Normal,
                            modifier = Modifier.padding(vertical = 2.dp),
                            lineHeight = 22.sp
                        )
                    }
                    Spacer(Modifier.height(12.dp))
                    Button(
                        onClick = {
                            ExtractedTextBus.updateSidebarState(
                                SidebarState.Preview(s.text)
                            )
                        },
                        shape = RoundedCornerShape(8.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)
                        )
                    ) {
                        Icon(
                            Icons.Filled.Psychology,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(Modifier.width(4.dp))
                        Text("重新解答", fontSize = 13.sp)
                    }
                    Spacer(Modifier.height(8.dp))
                    Button(
                        onClick = {
                            ExtractedTextBus.sendEvent(ExtractedTextBus.Event.ClickAnswer(s.answer, s.text))
                        },
                        modifier = Modifier.fillMaxWidth().height(48.dp),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFF22C55E)
                        )
                    ) {
                        Icon(
                            Icons.Filled.Check,
                            contentDescription = null,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(Modifier.width(8.dp))
                        Text("自动填入", fontSize = 15.sp, fontWeight = FontWeight.Bold)
                    }
                }

                is SidebarState.Streaming -> {
                    Log.d("SidebarPanel", "Streaming state, partialAnswer length=${s.partialAnswer.length}")
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = s.partialAnswer,
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.White,
                        modifier = Modifier.fillMaxWidth(),
                        lineHeight = 22.sp
                    )
                }

                is SidebarState.Answering -> {
                    Spacer(Modifier.height(24.dp))
                    StatusHint(s.text, isError = false)
                }

                is SidebarState.Error -> {
                    Log.d("SidebarPanel", "Error state: ${s.message}")
                    Spacer(Modifier.height(24.dp))
                    StatusHint(s.message, isError = true)
                }
            }
        }

        // ── 底部状态栏 ──
        HorizontalDivider(color = Color.White.copy(alpha = 0.08f))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = when (state) {
                    is SidebarState.Idle -> "● 空闲检测中"
                    is SidebarState.Loading -> "● ${(state as SidebarState.Loading).message}"
                    is SidebarState.Preview -> "● 已识别内容"
                    is SidebarState.Done -> "● 作答完成"
                    is SidebarState.Streaming -> "● 作答中..."
                    is SidebarState.Answering -> "● 解答中..."
                    is SidebarState.Error -> "● 异常"
                },
                style = MaterialTheme.typography.labelSmall,
                color = TextSecondary,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun SectionHeader(title: String) {
    Text(
        text = "── $title ──",
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(bottom = 8.dp),
        textAlign = TextAlign.Center
    )
}

@Composable
private fun StatusHint(message: String, isError: Boolean = false) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            imageVector = if (isError) Icons.Filled.Close else Icons.Filled.Inventory2,
            contentDescription = null,
            tint = if (isError) TextError else TextSecondary,
            modifier = Modifier.size(32.dp)
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium,
            color = if (isError) TextError else TextSecondary,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun StatusBanner(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    text: String,
    isError: Boolean
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(if (isError) TextError.copy(alpha = 0.15f) else Color.White.copy(alpha = 0.08f))
            .padding(10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            icon,
            contentDescription = null,
            tint = if (isError) TextError else TextSecondary,
            modifier = Modifier.size(18.dp)
        )
        Spacer(Modifier.width(6.dp))
        Text(
            text = text,
            color = if (isError) TextError else TextSecondary,
            style = MaterialTheme.typography.bodySmall
        )
    }
}


