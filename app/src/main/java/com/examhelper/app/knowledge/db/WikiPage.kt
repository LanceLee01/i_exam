package com.examhelper.app.knowledge.db

import androidx.room.Entity
import androidx.room.Fts4
import androidx.room.Index
import androidx.room.PrimaryKey
import java.util.UUID

@Entity(
    tableName = "wiki_pages",
    indices = [Index(value = ["uid"], unique = true)]
)
data class WikiPage(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val uid: String = UUID.randomUUID().toString(),
    val title: String,
    val content: String,
    val summary: String = "",
    val pageType: String = "concept",
    val tags: String = "",
    val sources: String = "",
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)

@Fts4(contentEntity = WikiPage::class)
@Entity(tableName = "wiki_pages_fts")
data class WikiPageFts(
    val title: String,
    val content: String,
    val summary: String
)
