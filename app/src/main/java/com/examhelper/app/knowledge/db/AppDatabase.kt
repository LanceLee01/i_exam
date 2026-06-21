package com.examhelper.app.knowledge.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        WikiPage::class,
        WikiPageFts::class,
        Wikilink::class,
        SourceFile::class,
        WikiPageEmbedding::class
    ],
    version = 2,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun wikiPageDao(): WikiPageDao
    abstract fun wikilinkDao(): WikilinkDao
    abstract fun sourceFileDao(): SourceFileDao
    abstract fun wikiPageEmbeddingDao(): WikiPageEmbeddingDao

    companion object {
        private const val DB_NAME = "exam_helper_kb.db"

        @Volatile
        private var INSTANCE: AppDatabase? = null

        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS wiki_page_embeddings (
                        id INTEGER PRIMARY KEY AUTOINCREMENT,
                        page_id INTEGER NOT NULL,
                        embedding BLOB NOT NULL,
                        FOREIGN KEY (page_id) REFERENCES wiki_pages(id) ON DELETE CASCADE
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    """
                    CREATE UNIQUE INDEX IF NOT EXISTS index_wiki_page_embeddings_page_id
                    ON wiki_page_embeddings (page_id)
                    """.trimIndent()
                )
            }
        }

        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    DB_NAME
                )
                    .addMigrations(MIGRATION_1_2)
                    .build()
                    .also { INSTANCE = it }
            }
        }
    }
}
