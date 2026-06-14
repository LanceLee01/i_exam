package com.examhelper.app.knowledge.db

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "wikilinks",
    foreignKeys = [
        ForeignKey(
            entity = WikiPage::class,
            parentColumns = ["id"],
            childColumns = ["sourceId"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = WikiPage::class,
            parentColumns = ["id"],
            childColumns = ["targetId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["sourceId"]),
        Index(value = ["targetId"])
    ]
)
data class Wikilink(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val sourceId: Long,
    val targetId: Long,
    val label: String = ""
)
