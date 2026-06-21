package com.examhelper.app.knowledge.db

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "wiki_page_embeddings",
    foreignKeys = [ForeignKey(
        entity = WikiPage::class,
        parentColumns = ["id"],
        childColumns = ["page_id"],
        onDelete = ForeignKey.CASCADE
    )],
    indices = [Index(value = ["page_id"], unique = true)]
)
data class WikiPageEmbedding(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "page_id") val pageId: Long,
    val embedding: ByteArray
) {

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is WikiPageEmbedding) return false
        return id == other.id && pageId == other.pageId && embedding.contentEquals(other.embedding)
    }

    override fun hashCode(): Int {
        var result = id.hashCode()
        result = 31 * result + pageId.hashCode()
        result = 31 * result + embedding.contentHashCode()
        return result
    }

    companion object {
        fun floatArrayToBytes(arr: FloatArray): ByteArray {
            val buf = java.nio.ByteBuffer.allocate(arr.size * 4)
            buf.order(java.nio.ByteOrder.LITTLE_ENDIAN)
            arr.forEach { buf.putFloat(it) }
            return buf.array()
        }

        fun bytesToFloatArray(bytes: ByteArray): FloatArray {
            val buf = java.nio.ByteBuffer.wrap(bytes)
            buf.order(java.nio.ByteOrder.LITTLE_ENDIAN)
            val arr = FloatArray(bytes.size / 4)
            for (i in arr.indices) arr[i] = buf.getFloat()
            return arr
        }
    }
}
