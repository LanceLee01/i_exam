package com.examhelper.app.knowledge.db

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.UUID

@Entity(tableName = "source_files")
data class SourceFile(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val filePath: String,
    val fileName: String,
    val fileType: String = "txt",
    val contentHash: String = "",
    val importedAt: Long = System.currentTimeMillis(),
    val pageCount: Int = 0
)
