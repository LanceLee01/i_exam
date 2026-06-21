package com.examhelper.app.knowledge.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface WikiPageEmbeddingDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(embedding: WikiPageEmbedding)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(embeddings: List<WikiPageEmbedding>)

    @Query("SELECT * FROM wiki_page_embeddings")
    suspend fun getAll(): List<WikiPageEmbedding>

    @Query("DELETE FROM wiki_page_embeddings WHERE page_id = :pageId")
    suspend fun deleteByPageId(pageId: Long)

    @Query("DELETE FROM wiki_page_embeddings")
    suspend fun clearAll()
}
