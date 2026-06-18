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
import androidx.core.view.WindowCompat
import androidx.lifecycle.lifecycleScope
import com.examhelper.app.service.SidebarService
import com.examhelper.app.ui.screen.MainScreen
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
            val isDarkMode = appConfig.isDarkMode().first()

            setContent {
                ExamHelperTheme(appDarkTheme = isDarkMode) {
                    when {
                        !setupComplete -> WelcomeScreen(onGetStarted = { handleSetupComplete() })
                        else -> MainScreen()
                    }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        lifecycleScope.launch {
            val setupComplete = ExamApplication.instance.appConfig.setupComplete.first()
            if (setupComplete && canDrawOverlays()) {
                startSidebarService()
            }
        }
    }

    private fun handleSetupComplete() {
        lifecycleScope.launch {
            if (!canDrawOverlays()) {
                requestOverlayPermission()
                return@launch
            }

            if (!isAccessibilityServiceEnabled()) {
                openAccessibilitySettings()
                return@launch
            }

            ExamApplication.instance.appConfig.setSetupComplete(true)

            if (canDrawOverlays()) {
                startSidebarService()
            }

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
