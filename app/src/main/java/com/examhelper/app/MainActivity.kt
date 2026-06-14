package com.examhelper.app

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.core.view.WindowCompat
import androidx.lifecycle.lifecycleScope
import com.examhelper.app.knowledge.KnowledgeBaseManager
import com.examhelper.app.service.SidebarService
import com.examhelper.app.ui.screen.KnowledgeBaseScreen
import com.examhelper.app.ui.screen.SettingsScreen
import com.examhelper.app.ui.screen.WelcomeScreen
import com.examhelper.app.ui.theme.ExamHelperTheme
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        WindowCompat.setDecorFitsSystemWindows(window, false)

        val appConfig = ExamApplication.instance.appConfig

        lifecycleScope.launch {
            val setupComplete = appConfig.setupComplete.first()

            setContent {
                ExamHelperTheme {
                    var showKB by remember { mutableStateOf(false) }
                    when {
                        !setupComplete -> WelcomeScreen(onGetStarted = { handleSetupComplete() })
                        showKB -> KnowledgeBaseScreen(onBack = { showKB = false })
                        else -> SettingsScreen(
                            onBack = { finish() },
                            onOpenKB = { showKB = true }
                        )
                    }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // 每次回到 App 时检查并启动侧边栏服务
        lifecycleScope.launch {
            val setupComplete = ExamApplication.instance.appConfig.setupComplete.first()
            if (setupComplete && canDrawOverlays()) {
                startSidebarService()
            }
        }
    }

    private fun handleSetupComplete() {
        lifecycleScope.launch {
            // 1. 检查悬浮窗权限
            if (!canDrawOverlays()) {
                requestOverlayPermission()
                return@launch
            }

            // 2. 提示开启无障碍服务
            if (!isAccessibilityServiceEnabled()) {
                openAccessibilitySettings()
                return@launch
            }

            // 3. 标记设置完成，显示设置页面
            ExamApplication.instance.appConfig.setSetupComplete(true)

            // 4. 启动侧边栏服务
            if (canDrawOverlays()) {
                startSidebarService()
            }

            // 刷新 UI
            recreate()
        }
    }

    private fun canDrawOverlays(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            Settings.canDrawOverlays(this)
        } else {
            true
        }
    }

    private fun requestOverlayPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val intent = Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:$packageName")
            )
            startActivity(intent)
        }
    }

    private fun isAccessibilityServiceEnabled(): Boolean {
        val service = "$packageName/com.examhelper.app.service.ExamAccessibilityService"
        val enabledServices = Settings.Secure.getString(
            contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        ) ?: ""
        return enabledServices.contains(service)
    }

    private fun openAccessibilitySettings() {
        val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
        startActivity(intent)
    }

    private fun startSidebarService() {
        val intent = Intent(this, SidebarService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent)
        } else {
            startService(intent)
        }
    }
}
