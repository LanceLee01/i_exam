package com.examhelper.app

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import com.examhelper.app.data.AppConfig
import com.examhelper.app.knowledge.KnowledgeBaseManager
import com.examhelper.app.knowledge.db.AppDatabase

class ExamApplication : Application() {

    lateinit var appConfig: AppConfig
        private set
    lateinit var database: AppDatabase
        private set

    override fun onCreate() {
        super.onCreate()
        instance = this
        appConfig = AppConfig(this)
        database = AppDatabase.getInstance(this)
        KnowledgeBaseManager.init(this)
        createNotificationChannel()
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            getString(R.string.notification_channel_sidebar),
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = getString(R.string.notification_sidebar_running)
            setShowBadge(false)
        }
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(channel)
    }

    companion object {
        const val CHANNEL_ID = "exam_helper_sidebar"

        lateinit var instance: ExamApplication
            private set
    }
}
