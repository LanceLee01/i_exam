package com.examhelper.app.ui.screen

import android.app.Activity
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Api
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material.icons.filled.Thermostat
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.examhelper.app.ExamApplication
import com.examhelper.app.data.AppConfig
import com.examhelper.app.knowledge.KnowledgeBaseManager
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    onOpenKB: () -> Unit = {}
) {
    val scope = rememberCoroutineScope()
    val appConfig = ExamApplication.instance.appConfig

    var endpoint by remember { mutableStateOf(AppConfig.DEFAULT_ENDPOINT) }
    var apiKey by remember { mutableStateOf("") }
    var tavilyApiKey by remember { mutableStateOf("") }
    var modelName by remember { mutableStateOf(AppConfig.DEFAULT_MODEL) }
    var temperature by remember { mutableFloatStateOf(AppConfig.DEFAULT_TEMPERATURE) }
    var maxTokens by remember { mutableIntStateOf(AppConfig.DEFAULT_MAX_TOKENS) }

    var systemPrompt by remember { mutableStateOf("") }

    var isSaving by remember { mutableStateOf(false) }
    var saveMessage by remember { mutableStateOf("") }

    LaunchedEffect(Unit) {
        val snapshot = appConfig.getSnapshot()
        endpoint = snapshot.apiEndpoint
        apiKey = snapshot.apiKey
        tavilyApiKey = snapshot.tavilyApiKey
        modelName = snapshot.modelName
        temperature = snapshot.temperature
        maxTokens = snapshot.maxTokens
        systemPrompt = snapshot.systemPrompt
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text("API 配置", fontWeight = FontWeight.Bold)
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "返回",
                            tint = Color.White
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Transparent,
                    titleContentColor = Color.White
                )
            )
        },
        containerColor = Color(0xFF121220)
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(rememberScrollState())
                .padding(16.dp)
        ) {
            // API 端点
            SettingsCard(
                icon = Icons.Filled.Link,
                title = "API 端点",
                value = endpoint,
                placeholder = "https://api.deepseek.com",
                onValueChange = { endpoint = it }
            )

            // API Key
            SettingsCard(
                icon = Icons.Filled.Key,
                title = "API Key",
                value = apiKey,
                placeholder = "sk-...",
                onValueChange = { apiKey = it },
                isPassword = true
            )

            // Tavily API Key（联网搜索）
            SettingsCard(
                icon = Icons.Filled.Search,
                title = "Tavily API Key（联网搜索）",
                value = tavilyApiKey,
                placeholder = "tvly-...（可选，免费 1000 次/月）",
                onValueChange = { tavilyApiKey = it },
                isPassword = true
            )

            // 模型名称
            SettingsCard(
                icon = Icons.Filled.SmartToy,
                title = "模型名称",
                value = modelName,
                placeholder = "deepseek-chat",
                onValueChange = { modelName = it }
            )

            // Temperature
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 6.dp),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(
                    containerColor = Color.White.copy(alpha = 0.06f)
                )
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Filled.Thermostat,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            "Temperature: ${"%.1f".format(temperature)}",
                            color = Color.White,
                            fontWeight = FontWeight.Medium
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                    Slider(
                        value = temperature,
                        onValueChange = { temperature = it },
                        valueRange = 0f..2f,
                        colors = SliderDefaults.colors(
                            thumbColor = MaterialTheme.colorScheme.primary,
                            activeTrackColor = MaterialTheme.colorScheme.primary
                        )
                    )
                }
            }

            // Max Tokens
            SettingsCard(
                icon = Icons.Filled.Description,
                title = "Max Tokens",
                value = maxTokens.toString(),
                placeholder = "2048",
                onValueChange = { maxTokens = it.toIntOrNull() ?: maxTokens },
                isNumeric = true
            )

            Spacer(Modifier.height(8.dp))

            // ── 系统提示词 ──
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 6.dp),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(
                    containerColor = Color.White.copy(alpha = 0.06f)
                )
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Filled.Api,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            "系统提示词",
                            color = Color.White.copy(alpha = 0.7f),
                            style = MaterialTheme.typography.labelLarge,
                            modifier = Modifier.weight(1f)
                        )
                        Text(
                            text = "恢复默认",
                            color = MaterialTheme.colorScheme.primary,
                            style = MaterialTheme.typography.labelSmall,
                            modifier = Modifier
                                .padding(start = 8.dp)
                                .clickable { systemPrompt = AppConfig.DEFAULT_SYSTEM_PROMPT }
                        )
                    }
                    Spacer(Modifier.height(4.dp))
                    OutlinedTextField(
                        value = systemPrompt,
                        onValueChange = { systemPrompt = it },
                        placeholder = { Text("输入系统提示词...", color = Color.White.copy(alpha = 0.3f)) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(160.dp),
                        textStyle = MaterialTheme.typography.bodySmall.copy(
                            color = Color.White,
                            lineHeight = 18.sp
                        ),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            focusedBorderColor = Color.White.copy(alpha = 0.3f),
                            unfocusedBorderColor = Color.White.copy(alpha = 0.1f),
                            cursorColor = MaterialTheme.colorScheme.primary
                        )
                    )
                }
            }

            Spacer(Modifier.height(24.dp))

            // ── 知识库 ──
            Card(
                onClick = onOpenKB,
                modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.06f))
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.AutoMirrored.Filled.List,
                        contentDescription = null,
                        tint = Color(0xFF22C55E),
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text("知识库管理", color = Color.White, fontWeight = FontWeight.Bold)
                        val name = KnowledgeBaseManager.activeKBName
                        Text(
                            if (name != "无") "激活: $name (${KnowledgeBaseManager.activeKB?.count ?: 0}条)" else "未激活",
                            color = Color.White.copy(0.5f), style = MaterialTheme.typography.bodySmall
                        )
                    }
                    Icon(
                        Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = null,
                        tint = Color.White.copy(0.3f),
                        modifier = Modifier.size(16.dp)
                    )
                }
            }

            // 保存按钮
            Button(
                onClick = {
                    scope.launch {
                        isSaving = true
                        try {
                            appConfig.setApiEndpoint(endpoint)
                            appConfig.setApiKey(apiKey)
                            appConfig.setTavilyApiKey(tavilyApiKey)
                            appConfig.setModelName(modelName)
                            appConfig.setTemperature(temperature)
                            appConfig.setMaxTokens(maxTokens)
                            appConfig.setSystemPrompt(systemPrompt)
                            appConfig.setSetupComplete(true)
                            saveMessage = "配置已保存"
                        } catch (e: Exception) {
                            saveMessage = "保存失败: ${e.message}"
                        }
                        isSaving = false
                    }
                },
                modifier = Modifier.fillMaxWidth().height(48.dp),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary
                )
            ) {
                Text(
                    text = if (isSaving) "保存中..." else "保存配置",
                    fontWeight = FontWeight.Bold
                )
            }

            if (saveMessage.isNotEmpty()) {
                Spacer(Modifier.height(8.dp))
                Text(
                    text = saveMessage,
                    color = if (saveMessage.contains("失败")) Color(0xFFEF4444) else Color(0xFF22C55E),
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }
    }
}

@Composable
private fun SettingsCard(
    icon: ImageVector,
    title: String,
    value: String,
    placeholder: String,
    onValueChange: (String) -> Unit,
    isPassword: Boolean = false,
    isNumeric: Boolean = false
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = Color.White.copy(alpha = 0.06f)
        )
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    title,
                    color = Color.White.copy(alpha = 0.7f),
                    style = MaterialTheme.typography.labelLarge
                )
            }
            Spacer(Modifier.height(4.dp))
            OutlinedTextField(
                value = value,
                onValueChange = onValueChange,
                placeholder = { Text(placeholder, color = Color.White.copy(alpha = 0.3f)) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                visualTransformation = if (isPassword) PasswordVisualTransformation() else androidx.compose.ui.text.input.VisualTransformation.None,
                keyboardOptions = KeyboardOptions(
                    keyboardType = if (isNumeric) KeyboardType.Number else KeyboardType.Text
                ),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White,
                    focusedBorderColor = Color.White.copy(alpha = 0.3f),
                    unfocusedBorderColor = Color.White.copy(alpha = 0.1f),
                    cursorColor = MaterialTheme.colorScheme.primary
                )
            )
        }
    }
}
