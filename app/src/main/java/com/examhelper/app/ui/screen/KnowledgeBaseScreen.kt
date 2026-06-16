package com.examhelper.app.ui.screen

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.examhelper.app.ExamApplication
import com.examhelper.app.knowledge.KBEngine
import com.examhelper.app.knowledge.KnowledgeBase
import com.examhelper.app.knowledge.KnowledgeBaseManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun KnowledgeBaseScreen(onBack: () -> Unit) {
    val scope = rememberCoroutineScope()
    var showNewDialog by remember { mutableStateOf(false) }
    var newKBName by remember { mutableStateOf("") }
    var isImportingDoc by remember { mutableStateOf(false) }
    var refreshKey by remember { mutableStateOf(0L) }
    val displayData = remember(refreshKey) {
        KnowledgeBaseManager.allKBs.map { kb -> Triple(kb, kb.name, kb.count) }
    }
    val kbEngine = remember { KBEngine(ExamApplication.instance) }

    val excelLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenMultipleDocuments()
    ) { uris ->
        if (uris.isNullOrEmpty()) return@rememberLauncherForActivityResult
        scope.launch(Dispatchers.IO) {
            var totalImported = 0
            var totalSkipped = 0
            var totalFailed = 0
            for (uri in uris) {
                try {
                    val ctx = ExamApplication.instance.applicationContext
                    val inputStream = ctx.contentResolver.openInputStream(uri)
                    val fileIndex = uris.indexOf(uri)
                    val tmpFile = java.io.File(ctx.cacheDir, "kb_import_${System.nanoTime()}_${fileIndex}.xlsx")
                    tmpFile.outputStream().use { inputStream?.copyTo(it) }
                    val count = KnowledgeBaseManager.activeKB?.importExcelWithDedup(tmpFile.absolutePath) ?: -1
                    tmpFile.delete()
                    when {
                        count == -2 -> totalSkipped++
                        count == -3 -> withContext(Dispatchers.Main) {
                            Toast.makeText(ExamApplication.instance, "请先在设置中配置 API Key", Toast.LENGTH_LONG).show()
                        }
                        count == -4 -> withContext(Dispatchers.Main) {
                            Toast.makeText(ExamApplication.instance, "列检测失败，请手动调整 Excel 文件格式", Toast.LENGTH_LONG).show()
                        }
                        count >= 0 -> { totalImported += count; KnowledgeBaseManager.save() }
                        else -> totalFailed++
                    }
                } catch (e: Exception) {
                    totalFailed++
                }
            }
            withContext(Dispatchers.Main) {
                refreshKey++  // Force UI recomposition
                val msg = buildString {
                    if (totalImported > 0) append("导入成功: $totalImported 条")
                    if (totalSkipped > 0) append("，跳过: $totalSkipped 个文件")
                    if (totalFailed > 0) append("，失败: $totalFailed 个文件")
                }
                Toast.makeText(ExamApplication.instance, msg, Toast.LENGTH_LONG).show()
            }
        }
    }

    val docLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        isImportingDoc = true
        scope.launch(Dispatchers.IO) {
            try {
                val result = kbEngine.importFile(uri)
                withContext(Dispatchers.Main) {
                    isImportingDoc = false
                    when {
                        result.skipped -> Toast.makeText(ExamApplication.instance, "内容未变化，已跳过", Toast.LENGTH_SHORT).show()
                        result.success -> Toast.makeText(ExamApplication.instance, "导入成功: 生成 ${result.pagesGenerated} 个知识页", Toast.LENGTH_SHORT).show()
                        else -> Toast.makeText(ExamApplication.instance, result.error ?: "导入失败", Toast.LENGTH_SHORT).show()
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    isImportingDoc = false
                    Toast.makeText(ExamApplication.instance, "出错: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("知识库管理", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回", tint = Color.White)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent, titleContentColor = Color.White)
            )
        },
        containerColor = Color(0xFF121220)
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp).verticalScroll(rememberScrollState())
        ) {
            Text("已激活: ${KnowledgeBaseManager.activeKBName}", color = Color(0xFF22C55E), fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(12.dp))

            if (displayData.isEmpty()) {
                Text("暂无知识库，点击下方按钮创建", color = Color.White.copy(0.5f))
                Spacer(Modifier.height(12.dp))
            } else {
                displayData.forEachIndexed { index, (kb, name, count) ->
                    val isActive = kb == KnowledgeBaseManager.activeKB
                    Card(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
                            .border(1.dp, if (isActive) Color(0xFF22C55E) else Color.Transparent, RoundedCornerShape(12.dp)),
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.06f)),
                        onClick = { KnowledgeBaseManager.selectKB(index) }
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(name, color = Color.White, fontWeight = FontWeight.Bold)
                                Text("$count 条题目", color = Color.White.copy(0.5f), style = MaterialTheme.typography.bodySmall)
                                if (isActive) Text("激活", color = Color(0xFF22C55E), style = MaterialTheme.typography.labelSmall)
                            }
                            if (isImportingDoc) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(20.dp),
                                    color = Color(0xFF22C55E),
                                    strokeWidth = 2.dp
                                )
                                Spacer(Modifier.width(8.dp))
                            }
                            IconButton(onClick = {
                                KnowledgeBaseManager.selectKB(index)
                                excelLauncher.launch(arrayOf(
                                    "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                                    "application/vnd.ms-excel"
                                ))
                            }) {
                                Icon(Icons.Filled.UploadFile, contentDescription = "导入Excel", tint = Color(0xFF22C55E), modifier = Modifier.size(20.dp))
                            }
                            IconButton(onClick = {
                                KnowledgeBaseManager.selectKB(index)
                                docLauncher.launch(arrayOf("text/plain", "text/markdown", "text/x-markdown"))
                            }) {
                                Icon(Icons.Filled.Description, contentDescription = "导入文档", tint = Color(0xFF3B82F6), modifier = Modifier.size(20.dp))
                            }
                            IconButton(onClick = {
                                KnowledgeBaseManager.deleteKB(index)
                                refreshKey++
                            }) {
                                Icon(Icons.Filled.Delete, contentDescription = "删除", tint = Color(0xFFEF4444), modifier = Modifier.size(20.dp))
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(12.dp))
            Row(modifier = Modifier.fillMaxWidth()) {
                Button(
                    onClick = { showNewDialog = true },
                    modifier = Modifier.weight(1f).height(48.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF22C55E))
                ) {
                    Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("新建", fontWeight = FontWeight.Bold)
                }
                Spacer(Modifier.width(8.dp))
                Button(
                    onClick = {
                        KnowledgeBaseManager.selectKB(KnowledgeBaseManager.allKBs.indexOf(KnowledgeBaseManager.activeKB))
                        docLauncher.launch(arrayOf("text/plain", "text/markdown", "text/x-markdown"))
                    },
                    enabled = KnowledgeBaseManager.activeKB != null && !isImportingDoc,
                    modifier = Modifier.weight(1f).height(48.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF3B82F6))
                ) {
                    if (isImportingDoc) {
                        CircularProgressIndicator(modifier = Modifier.size(18.dp), color = Color.White, strokeWidth = 2.dp)
                    } else {
                        Icon(Icons.Filled.Description, contentDescription = null, modifier = Modifier.size(20.dp))
                    }
                    Spacer(Modifier.width(4.dp))
                    Text("导入文档", fontWeight = FontWeight.Bold)
                }
            }
        }

        if (showNewDialog) {
            AlertDialog(
                onDismissRequest = { showNewDialog = false },
                title = { Text("新建知识库", color = Color.White) },
                text = {
                    OutlinedTextField(
                        value = newKBName,
                        onValueChange = { newKBName = it },
                        placeholder = { Text("输入名称") },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(focusedTextColor = Color.White, unfocusedTextColor = Color.White)
                    )
                },
                confirmButton = {
                    TextButton(onClick = {
                        if (newKBName.isNotBlank()) {
                            KnowledgeBaseManager.addKB(newKBName.trim())
                            showNewDialog = false
                            newKBName = ""
                        }
                    }) { Text("创建") }
                },
                dismissButton = {
                    TextButton(onClick = { showNewDialog = false }) { Text("取消") }
                },
                containerColor = Color(0xFF1E1E2E)
            )
        }
    }
}
