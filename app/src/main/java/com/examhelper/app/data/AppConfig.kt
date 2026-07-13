package com.examhelper.app.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "exam_helper_config")

class AppConfig(private val context: Context) {

    companion object {
        private val KEY_WATERMARK_KEYWORDS = stringSetPreferencesKey("watermark_keywords")
        private val KEY_SETUP_COMPLETE = booleanPreferencesKey("setup_complete")
        private val KEY_SIDEBAR_RUNNING = booleanPreferencesKey("sidebar_running")
        private val KEY_IS_DARK_MODE = booleanPreferencesKey("is_dark_mode")

        val DEFAULT_WATERMARK_KEYWORDS = setOf(
            "非涉密平台",
            "严禁处理",
            "国家秘密",
            "严禁",
            "非涉密",
            "本平台为非涉密平台",
            "传输国家秘密"
        )
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

    fun isDarkMode(): Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[KEY_IS_DARK_MODE] ?: false
    }

    suspend fun setIsDarkMode(dark: Boolean) {
        context.dataStore.edit { it[KEY_IS_DARK_MODE] = dark }
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
            watermarkKeywords = prefs[KEY_WATERMARK_KEYWORDS] ?: DEFAULT_WATERMARK_KEYWORDS
        )
    }
}

data class ConfigSnapshot(
    val watermarkKeywords: Set<String>
)
