package com.examhelper.app.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "exam_helper_config")

class AppConfig(private val context: Context) {

    companion object {
        private val KEY_API_ENDPOINT = stringPreferencesKey("api_endpoint")
        private val KEY_API_KEY = stringPreferencesKey("api_key")
        private val KEY_TAVILY_API_KEY = stringPreferencesKey("tavily_api_key")
        private val KEY_MODEL_NAME = stringPreferencesKey("model_name")
        private val KEY_TEMPERATURE = floatPreferencesKey("temperature")
        private val KEY_MAX_TOKENS = intPreferencesKey("max_tokens")
        private val KEY_SYSTEM_PROMPT = stringPreferencesKey("system_prompt")
        private val KEY_WATERMARK_KEYWORDS = stringSetPreferencesKey("watermark_keywords")
        private val KEY_SETUP_COMPLETE = booleanPreferencesKey("setup_complete")
        private val KEY_SIDEBAR_RUNNING = booleanPreferencesKey("sidebar_running")

        const val DEFAULT_ENDPOINT = "https://opencode.ai/zen/go/v1"
        const val DEFAULT_API_KEY = "sk-nzwiwWiLqYyT5dxG9DXSUSAvm9uOlyYZgn4gQC5LqWNl8r5clhfqCWFZxGMOsxm7"
        const val DEFAULT_TAVILY_API_KEY = "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"
        const val DEFAULT_MODEL = "deepseek-v4-flash"
        const val DEFAULT_TEMPERATURE = 0.3f
        const val DEFAULT_MAX_TOKENS = 10240

        val DEFAULT_WATERMARK_KEYWORDS = setOf(
            "非涉密平台",
            "严禁处理",
            "国家秘密",
            "严禁",
            "非涉密",
            "本平台为非涉密平台",
            "传输国家秘密"
        )

        val DEFAULT_SYSTEM_PROMPT = """
你是考试答题助手。请认真阅读以下从考试界面提取的文字，其中可能包含水印等无关信息，请忽略它们。
直接给出答案，不要思考过程。识别出所有题目后，只输出题号和答案选项，不要解释理由。每行一题，格式严格如下：

[题号] 答案选项

重要规则：
- 单选题：输出单个字母，如 [1] A
- 判断题：输出 正确 或 错误，如 [38] 正确
- 多选题（最重要）：必须输出全部正确选项！如果正确答案是B和C，必须输出 [44] B C（空格分隔）。
  多选题最少选2个，绝不要只输出1个字母。如果题目中明确标有"多选题"字样，必须输出2个或以上选项。
- 必须回答题目列表中的每一道题，不可跳过。如果某题不确定，请写"不确定"
- 禁止输出思考过程、解释或任何非答案的内容
        """.trimIndent()
    }

    val apiEndpoint: Flow<String> = context.dataStore.data.map { prefs ->
        prefs[KEY_API_ENDPOINT] ?: DEFAULT_ENDPOINT
    }

    val apiKey: Flow<String> = context.dataStore.data.map { prefs ->
        prefs[KEY_API_KEY] ?: DEFAULT_API_KEY
    }

    val tavilyApiKey: Flow<String> = context.dataStore.data.map { prefs ->
        prefs[KEY_TAVILY_API_KEY] ?: DEFAULT_TAVILY_API_KEY
    }

    val modelName: Flow<String> = context.dataStore.data.map { prefs ->
        prefs[KEY_MODEL_NAME] ?: DEFAULT_MODEL
    }

    val temperature: Flow<Float> = context.dataStore.data.map { prefs ->
        prefs[KEY_TEMPERATURE] ?: DEFAULT_TEMPERATURE
    }

    val maxTokens: Flow<Int> = context.dataStore.data.map { prefs ->
        prefs[KEY_MAX_TOKENS] ?: DEFAULT_MAX_TOKENS
    }

    val systemPrompt: Flow<String> = context.dataStore.data.map { prefs ->
        prefs[KEY_SYSTEM_PROMPT] ?: DEFAULT_SYSTEM_PROMPT
    }

    val watermarkKeywords: Flow<Set<String>> = context.dataStore.data.map { prefs ->
        prefs[KEY_WATERMARK_KEYWORDS] ?: DEFAULT_WATERMARK_KEYWORDS
    }

    val setupComplete: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[KEY_SETUP_COMPLETE] ?: false
    }

    val sidebarRunning: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[KEY_SIDEBAR_RUNNING] ?: false
    }

    suspend fun setApiEndpoint(endpoint: String) {
        context.dataStore.edit { it[KEY_API_ENDPOINT] = endpoint }
    }

    suspend fun setApiKey(key: String) {
        context.dataStore.edit { it[KEY_API_KEY] = key }
    }

    suspend fun setTavilyApiKey(key: String) {
        context.dataStore.edit { it[KEY_TAVILY_API_KEY] = key }
    }

    suspend fun setModelName(model: String) {
        context.dataStore.edit { it[KEY_MODEL_NAME] = model }
    }

    suspend fun setTemperature(temp: Float) {
        context.dataStore.edit { it[KEY_TEMPERATURE] = temp }
    }

    suspend fun setMaxTokens(tokens: Int) {
        context.dataStore.edit { it[KEY_MAX_TOKENS] = tokens }
    }

    suspend fun setSystemPrompt(prompt: String) {
        context.dataStore.edit { it[KEY_SYSTEM_PROMPT] = prompt }
    }

    suspend fun setWatermarkKeywords(keywords: Set<String>) {
        context.dataStore.edit { it[KEY_WATERMARK_KEYWORDS] = keywords }
    }

    suspend fun setSetupComplete(complete: Boolean) {
        context.dataStore.edit { it[KEY_SETUP_COMPLETE] = complete }
    }

    suspend fun setSidebarRunning(running: Boolean) {
        context.dataStore.edit { it[KEY_SIDEBAR_RUNNING] = running }
    }

    suspend fun getSnapshot(): ConfigSnapshot {
        val prefs = context.dataStore.data.first()
        return ConfigSnapshot(
            apiEndpoint = prefs[KEY_API_ENDPOINT] ?: DEFAULT_ENDPOINT,
            apiKey = prefs[KEY_API_KEY] ?: DEFAULT_API_KEY,
            tavilyApiKey = prefs[KEY_TAVILY_API_KEY] ?: DEFAULT_TAVILY_API_KEY,
            modelName = prefs[KEY_MODEL_NAME] ?: DEFAULT_MODEL,
            temperature = prefs[KEY_TEMPERATURE] ?: DEFAULT_TEMPERATURE,
            maxTokens = prefs[KEY_MAX_TOKENS] ?: DEFAULT_MAX_TOKENS,
            systemPrompt = prefs[KEY_SYSTEM_PROMPT] ?: DEFAULT_SYSTEM_PROMPT,
            watermarkKeywords = prefs[KEY_WATERMARK_KEYWORDS] ?: DEFAULT_WATERMARK_KEYWORDS
        )
    }
}

data class ConfigSnapshot(
    val apiEndpoint: String,
    val apiKey: String,
    val tavilyApiKey: String = "",
    val modelName: String,
    val temperature: Float,
    val maxTokens: Int,
    val systemPrompt: String,
    val watermarkKeywords: Set<String>
)
