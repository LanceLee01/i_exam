package com.examhelper.app.ui.screen

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material3.AlertDialog
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
    val kbs = KnowledgeBaseManager.allKBs

    val pickerLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch(Dispatchers.IO) {
            try {
                val ctx = ExamApplication.instance.applicationContext
                val inputStream = ctx.contentResolver.openInputStream(uri)
                val tmpFile = java.io.File(ctx.cacheDir, "kb_import.xlsx")
                tmpFile.outputStream().use { inputStream?.copyTo(it) }
                val count = KnowledgeBaseManager.activeKB?.importExcel(tmpFile.absolutePath) ?: -1
                tmpFile.delete()
                KnowledgeBaseManager.save()
                withContext(Dispatchers.Main) {
                    if (count >= 0) Toast.makeText(ctx, "导入成功: $count 条", Toast.LENGTH_SHORT).show()
                    else Toast.makeText(ctx, "导入失败", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
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

            if (kbs.isEmpty()) {
                Text("暂无知识库，点击下方按钮创建", color = Color.White.copy(0.5f))
                Spacer(Modifier.height(12.dp))
            } else {
                kbs.forEachIndexed { index, kb ->
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
                                Text(kb.name, color = Color.White, fontWeight = FontWeight.Bold)
                                Text("${kb.count} 条题目", color = Color.White.copy(0.5f), style = MaterialTheme.typography.bodySmall)
                                if (isActive) Text("激活", color = Color(0xFF22C55E), style = MaterialTheme.typography.labelSmall)
                            }
                            IconButton(onClick = {
                                KnowledgeBaseManager.selectKB(index)
                                pickerLauncher.launch(arrayOf(
                                    "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                                    "application/vnd.ms-excel"
                                ))
                            }) {
                                Icon(Icons.Filled.UploadFile, contentDescription = "导入", tint = Color(0xFF22C55E), modifier = Modifier.size(20.dp))
                            }
                            IconButton(onClick = {
                                KnowledgeBaseManager.deleteKB(index)
                            }) {
                                Icon(Icons.Filled.Delete, contentDescription = "删除", tint = Color(0xFFEF4444), modifier = Modifier.size(20.dp))
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(12.dp))
            Button(
                onClick = { showNewDialog = true },
                modifier = Modifier.fillMaxWidth().height(48.dp),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF22C55E))
            ) {
                Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp))
                Text("新建知识库", fontWeight = FontWeight.Bold)
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
