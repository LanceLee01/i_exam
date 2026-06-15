# Fix: 批量导入 Excel

## 改动
`KnowledgeBaseScreen.kt` 中的 `excelLauncher` 从单文件选择改为多文件选择，逐个导入。

## 改动点

1. `ActivityResultContracts.OpenDocument()` → `OpenMultipleDocuments()`
2. 回调从 `uri ->` 改为 `uris ->`，遍历 `uris` 逐个导入
3. 临时文件用 UUID 命名避免冲突

## TODOs

- [ ] 1. **修改导入代码**

  **改前**:
  ```kotlin
  val excelLauncher = rememberLauncherForActivityResult(
      ActivityResultContracts.OpenDocument()
  ) { uri ->
      if (uri == null) return@rememberLauncherForActivityResult
      ...
      val tmpFile = java.io.File(ctx.cacheDir, "kb_import.xlsx")
      ...
  }
  ```

  **改后**:
  ```kotlin
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
                  val tmpFile = java.io.File(ctx.cacheDir, "kb_import_${System.currentTimeMillis()}_${uris.indexOf(uri)}.xlsx")
                  tmpFile.outputStream().use { inputStream?.copyTo(it) }
                  val count = KnowledgeBaseManager.activeKB?.importExcelWithDedup(tmpFile.absolutePath) ?: -1
                  tmpFile.delete()
                  when {
                      count == -2 -> totalSkipped++
                      count >= 0 -> { totalImported += count; KnowledgeBaseManager.save() }
                      else -> totalFailed++
                  }
              } catch (e: Exception) {
                  totalFailed++
              }
          }
          withContext(Dispatchers.Main) {
              val msg = buildString {
                  if (totalImported > 0) append("导入成功: $totalImported 条")
                  if (totalSkipped > 0) append("，跳过: $totalSkipped 个文件")
                  if (totalFailed > 0) append("，失败: $totalFailed 个文件")
              }
              Toast.makeText(ExamApplication.instance, msg, Toast.LENGTH_LONG).show()
          }
      }
  }
  ```
