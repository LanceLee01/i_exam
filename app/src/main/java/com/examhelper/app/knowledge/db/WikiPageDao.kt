package com.examhelper.app.knowledge.db

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface WikiPageDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(page: WikiPage): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(pages: List<WikiPage>)

    @Update
    suspend fun update(page: WikiPage)

    @Delete
    suspend fun delete(page: WikiPage)

    @Query("DELETE FROM wiki_pages WHERE uid = :uid")
    suspend fun deleteByUid(uid: String)

    @Query("SELECT * FROM wiki_pages ORDER BY updatedAt DESC")
    suspend fun getAll(): List<WikiPage>

    @Query("SELECT * FROM wiki_pages ORDER BY updatedAt DESC")
    fun getAllFlow(): Flow<List<WikiPage>>

    @Query("SELECT * FROM wiki_pages WHERE id = :id")
    suspend fun getById(id: Long): WikiPage?

    @Query("SELECT * FROM wiki_pages WHERE uid = :uid")
    suspend fun getByUid(uid: String): WikiPage?

    @Query("SELECT * FROM wiki_pages WHERE pageType = :type ORDER BY updatedAt DESC")
    suspend fun getByType(type: String): List<WikiPage>

    @Query("SELECT * FROM wiki_pages WHERE tags LIKE '%' || :tag || '%'")
    suspend fun getByTag(tag: String): List<WikiPage>

    @Query("SELECT * FROM wiki_pages WHERE sources LIKE '%' || :sourceId || '%'")
    suspend fun getBySource(sourceId: String): List<WikiPage>

    @Query("SELECT COUNT(*) FROM wiki_pages")
    suspend fun getCount(): Int

    @Query("DELETE FROM wiki_pages")
    suspend fun clearAll()

    @Query("SELECT wiki_pages.* FROM wiki_pages JOIN wiki_pages_fts ON wiki_pages.id = wiki_pages_fts.rowid WHERE wiki_pages_fts MATCH :query")
    suspend fun searchFts(query: String): List<WikiPage>

    @Query("SELECT wiki_pages.* FROM wiki_pages JOIN wiki_pages_fts ON wiki_pages.id = wiki_pages_fts.rowid WHERE wiki_pages_fts MATCH :query LIMIT :limit")
    suspend fun searchFts(query: String, limit: Int): List<WikiPage>

    @Query("SELECT * FROM wiki_pages WHERE title LIKE '%' || :query || '%' OR summary LIKE '%' || :query || '%' LIMIT 20")
    suspend fun searchByTitleLike(query: String): List<WikiPage>
}
