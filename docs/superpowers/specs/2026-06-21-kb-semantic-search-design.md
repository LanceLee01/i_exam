# 知识库模块重构设计

## Context

知识库（Wiki KB）当前存在多个严重问题：中文搜索完全不可用（FTS4 单字分词 + 全量内存 trigram 扫描）、导入无进度提示且同步阻塞、AI 问答无流式、wikilink 渲染有 bug。本次重构以**语义搜索**为核心，同时修复数据层和 UI 的积压问题。

## Architecture Overview

```
用户
  ├─ 导入 PDF/PPT/Excel → 文本提取 → LLM 解析 → Embedding API → WikiPage + Embedding → Room
  ├─ 搜索输入 → Embedding API → 本地余弦相似度 → Top-10 页面
  ├─ AI 问答   → 搜索 → LLM 流式回答
  └─ 浏览页面 → WikiPageScreen（wikilink 可点击）
```

### 核心变更

| 组件 | 变更类型 | 说明 |
|------|----------|------|
| `EmbeddingClient` | **新建** | 封装 SiliconFlow `/v1/embeddings`，BGE-large-zh-v1.5，1024维 |
| `WikiPageEmbedding` | **新建** | Room 实体，存 embedding BLOB + pageId 外键 |
| `WikiPageEmbeddingDao` | **新建** | insert/deleteByPageId/getAll |
| `KBEngine.searchByQuestion()` | **重写** | 去掉 trigram + FTS，改用 embedding 语义搜索 |
| `KBEngine.importFile()` | **修改** | 导入后自动生成 embedding；改为可取消、暴露进度 |
| `KBEngine.answerQuestion()` | **修改** | 改流式 SSE |
| `AppDatabase` | **修改** | migration v1→v2，加 embedding 表 |
| `KnowledgeBaseTab` | **修改** | 搜索防抖 300ms、导入进度指示 |
| `WikiPageScreen` | **修改** | 正文 `[[链接]]` 渲染为可点击，NPE 防护 |
| `LLMClient` | **扩展** | 加 streaming 回答支持（已有 chatStream，复用） |

## Component Specs

### 1. EmbeddingClient `[新建]`

```
class EmbeddingClient(apiKey: String, baseUrl: String = "https://api.siliconflow.cn/v1")
  fun embed(text: String): FloatArray          // 单文本 → 1024 维
  fun embedBatch(texts: List<String>): List<FloatArray>  // 批量
  fun cosineSimilarity(a: FloatArray, b: FloatArray): Float  // 余弦相似度
```

- model: `BAAI/bge-large-zh-v1.5`
- 复用现有 OkHttpClient 4.12，不新建实例
- 向量序列化：FloatArray → ByteArray（Little Endian）→ Room BLOB

### 2. WikiPageEmbedding `[新建]`

```kotlin
@Entity(tableName = "wiki_page_embeddings", foreignKeys = [...], indices = [...])
data class WikiPageEmbedding(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "page_id") val pageId: Long,
    val embedding: ByteArray  // 1024 * 4 bytes = 4096 bytes
)
```

- pageId 外键关联 WikiPage.id，CASCADE 删除
- embedding 用于搜索时加载全量，在 Kotlin 层算余弦

### 3. KBEngine.searchByQuestion() `[重写]`

```kotlin
suspend fun searchByQuestion(questionText: String): SearchResult {
    // 1. 调 EmbeddingClient.embed(questionText)
    // 2. 加载所有 WikiPageEmbedding（只加载有 embedding 的）
    // 3. 逐条算余弦相似度
    // 4. Top-10 返回
    // 降级：Embedding API 不可用时 → FTS4 LIKE 查询
}
```

- 搜索范围：title + summary 拼接后做 embedding（与导入时一致）
- 降级策略：网络异常 → `buildFtsQuery` → `LIKE '%keyword%'` 查询

### 4. KBEngine.importFile() `[修改]`

- 解析成功后，收集所有新页面的 title+summary
- 调 `EmbeddingClient.embedBatch()` 批量生成向量
- 写入 `WikiPageEmbedding` 表
- 添加 `onProgress: (ImportProgress) -> Unit` 回调
- 支持 `Job.cancel()` 取消

### 5. KBEngine.answerQuestion() `[修改]`

- 搜索 → 取 Top-5 → 构建上下文 → `chatStream()` 流式输出
- 每页内容截取 400 字（而非 800）
- 系统提示词要求引用页面标题

### 6. AppDatabase `[修改]`

- version: 1 → 2
- migration 1→2: 建 `wiki_page_embeddings` 表
- EXISTING 数据保留

### 7. UI 变更

| 文件 | 变更 |
|------|------|
| `KnowledgeBaseTab.kt` | 搜索 `snapshotFlow` + `debounce(300)` 防抖 |
| `KnowledgeBaseTab.kt` | 导入弹窗显示进度（已导入 N 页） |
| `WikiPageScreen.kt` | `parseMarkdownSections()` 中正则 `[[...]]` → 渲染为可点击 Span |
| `WikiPageScreen.kt` | `linkedPageMap.values.find` → 用 `getOrElse` 防 NPE |

### 8. 降级策略

| 场景 | 行为 |
|------|------|
| Embedding API 不可用 | 回退到 `LIKE '%keyword%'` SQLite 文本搜索 |
| 旧页面无 embedding | `getAllEmbeddings()` 只返回有向量的行 |
| API key 未配置 | 提示用户配置，搜索不可用 |
| 导入时 embedding 失败 | 页面已写入，标记 `needsEmbedding = true`，后台重试 |

## Data Flow

```
导入: PDF/PPT/Excel → 文本提取 → LLM 结构化 → WikiPage(insert) → Embedding API → WikiPageEmbedding(insert) → Room

搜索: "怎么报故障" → Embedding API → FloatArray(1024) → getAllEmbeddings() → 逐条 cosSim → TopK → SearchResult

问答: 问题 → 搜索 → Top-5 页面 → chatStream(上下文 + 问题) → Flow<String> → UI 实时渲染
```

## Error Handling

- Embedding API: try/catch → Log.w + 降级搜索
- Embedding 批处理: 单条失败不影响其他
- LLM 解析失败: 已有 fallback 单页逻辑，保留
- 数据库写入: Room 自动事务

## File Changes Summary

| 操作 | 文件 |
|------|------|
| 新建 | `network/EmbeddingClient.kt` |
| 新建 | `knowledge/db/WikiPageEmbedding.kt` |
| 新建 | `knowledge/db/WikiPageEmbeddingDao.kt` |
| 修改 | `knowledge/db/AppDatabase.kt` |
| 修改 | `knowledge/KBEngine.kt` |
| 修改 | `ui/screen/KnowledgeBaseTab.kt` |
| 修改 | `ui/screen/WikiPageScreen.kt` |

## Verification

1. `./gradlew :app:assembleDebug` 构建通过
2. `./gradlew :app:test` 全部测试通过
3. 手动测试：导入 PDF → 搜索关键词 → 验证语义匹配
4. 手动测试：AI 问答 → 验证流式输出
5. 手动测试：断网搜索 → 验证降级方案
