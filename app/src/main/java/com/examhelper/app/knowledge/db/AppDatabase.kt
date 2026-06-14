package com.examhelper.app.knowledge.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [
        WikiPage::class,
        WikiPageFts::class,
        Wikilink::class,
        SourceFile::class
    ],
    version = 1,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun wikiPageDao(): WikiPageDao
    abstract fun wikilinkDao(): WikilinkDao
    abstract fun sourceFileDao(): SourceFileDao

    companion object {
        private const val DB_NAME = "exam_helper_kb.db"

        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    DB_NAME
                )
                    .fallbackToDestructiveMigration()
                    .build()
                    .also { INSTANCE = it }
            }
        }
    }
}
