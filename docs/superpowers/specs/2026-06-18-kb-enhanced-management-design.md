# Excel 题库增强管理 — 设计文档

## 目标

为 i_exam 项目的 Excel 题库（KnowledgeBase）增加管理功能：
1. 题库内搜索
2. 编辑单条题目
3. 批量删除
4. 查看导入文件列表

以独立详情页（`KbDetailScreen`）承载上述功能。

## 涉及文件

| 文件 | 改动 |
|------|------|
| `knowledge/KnowledgeBaseManager.kt` | 新增 ImportRecord 数据类；KnowledgeBase 增加 importRecords/搜索/编辑/删除方法；KBData/KBStorageData 扩展；Manager 暴露操作方法 |
| `ui/screen/KnowledgeBaseScreen.kt` | "查看"按钮改为导航到详情页；删除旧的 viewDialog |
| `ui/screen/KbDetailScreen.kt` | **新增** — 独立详情页，包含搜索、列表、编辑、批量删除、文件列表 |
| `app/build.gradle.kts` 或相关导航 | 如有导航注册需更新（当前项目无导航库，用 Composable 参数回调） |

## 数据模型

### 新增 ImportRecord

```kotlin
data class ImportRecord(
    val fileName: String,
    val hash: String,
    val importedAt: Long = System.currentTimeMillis(),
    val entryCount: Int = 0
)
```

### KnowledgeBase 变更

```kotlin
class KnowledgeBase(...) {
    // 新增
    val importRecords: MutableList<ImportRecord>
    
    // importExcelWithDedup() 在导入成功时追加 ImportRecord
    
    // 新增方法
    fun searchEntries(query: String): List<KBEntry>    // 封装 search()，返回纯 entry 列表
    fun updateEntry(index: Int, entry: KBEntry)         // 编辑单条
    fun deleteEntries(indices: Set<Int>)                // 批量删除（倒序删）
    fun getImportFiles(): List<ImportRecord>            // 返回导入文件列表
}
```

### JSON 持久化兼容

- `KBData` 新增 `importRecords: List<ImportRecord>`
- `load()` 时若旧数据无 importRecords，自动从 importedHashes 生成占位记录（文件名为"未知文件(首次导入)"）
- Gson 序列化 ImportRecord 无需额外适配器

## UI 设计

### KbDetailScreen 结构

```
TopAppBar: ← 返回    "题库名称"    (无右侧操作)

搜索栏: OutlinedTextField + 搜索图标，输入即时过滤

工具栏 Row:
  [全选/取消] [删除选中(N)] [查看文件(X)]

题目列表 LazyColumn:
  每项 Row:
    Checkbox (多选模式时显示)
    题目序号 + 题目文本预览 (maxLines=2)
    题型标签 Chip (单选题/多选题/判断题)
    IconButton: 编辑 (✏️)
    IconButton: 删除 (🗑️)
  空状态: "无匹配结果"

编辑对话框 AlertDialog:
  OutlinedTextField: 题目 (多行)
  OutlinedTextField: 答案
  OutlinedTextField: 选项
  DropdownMenu 或简单 TextField: 题型

文件列表对话框 AlertDialog:
  每项显示: 文件名 | 导入时间 | X条 | SHA256前8位
```

### 状态管理

```kotlin
// KbDetailScreen 内部状态
var searchQuery: String
var selectedIndices: Set<Int>       // 多选集合
var isMultiSelectMode: Boolean      // 是否处于多选模式
var editingEntry: Pair<Int, KBEntry>? // 正在编辑的条目
var showFileListDialog: Boolean
var showDeleteConfirmDialog: Boolean
var kb: KnowledgeBase               // 从 KnowledgeBaseManager 获取
var displayEntries: List<IndexedValue<KBEntry>>  // 过滤后的条目（带原始索引）
```

### 搜索逻辑

- 输入 ≥ 2 字符时触发 `kb.search(query)`，否则显示全部
- 搜索结果保持排序（按相似度），全部显示时按原始顺序
- 空搜索框 = 显示全部

### 编辑逻辑

- 编辑完成后 `kb.updateEntry(originalIndex, newEntry)` → 重建 featureCache → `KnowledgeBaseManager.save()`
- 题型可选项: 单选题 / 多选题 / 判断题 / 未知

### 批量删除逻辑

- 选中 ≥ 1 条时显示"删除选中(N)"按钮
- 点击弹出确认对话框，确认后从高索引到低索引删除 → 重建缓存 → save
- 删除后清空选中状态

### 导航方式

当前项目无 Navigation Compose，采用回调参数：
```kotlin
@Composable
fun KnowledgeBaseScreen(onBack: () -> Unit) {
    var detailKbIndex by remember { mutableStateOf(-1) }
    
    if (detailKbIndex >= 0) {
        KbDetailScreen(kbIndex = detailKbIndex, onBack = { detailKbIndex = -1 })
    } else {
        // 现有列表 UI，查看按钮设 detailKbIndex = index
    }
}
```

## 兼容性

- 旧 JSON 数据无 `importRecords` 字段：Gson 解析时缺失字段不会崩溃（Kotlin data class 需要默认值）
- `KBData` 中 `importRecords` 设默认值 `emptyList()`，load 时检测并迁移
- `KBEntry` 的 `questionType` 已有 null 兼容处理（load 时修复）

## 验证

- `./gradlew assembleDebug` 编译通过
- 安装测试：创建题库 → 导入 Excel → 搜索 → 编辑 → 批量删除 → 查看文件列表
- 旧数据兼容：安装新版本后旧题库正常加载、文件列表显示占位信息
