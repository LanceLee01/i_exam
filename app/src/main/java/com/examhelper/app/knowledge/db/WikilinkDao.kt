package com.examhelper.app.knowledge.db

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface WikilinkDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(link: Wikilink): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(links: List<Wikilink>)

    @Delete
    suspend fun delete(link: Wikilink)

    @Query("SELECT * FROM wikilinks WHERE sourceId = :sourceId")
    suspend fun getBySource(sourceId: Long): List<Wikilink>

    @Query("SELECT * FROM wikilinks WHERE targetId = :targetId")
    suspend fun getByTarget(targetId: Long): List<Wikilink>

    @Query("SELECT * FROM wikilinks WHERE sourceId = :pageId OR targetId = :pageId")
    suspend fun getAllForPage(pageId: Long): List<Wikilink>

    @Query("DELETE FROM wikilinks WHERE sourceId = :pageId OR targetId = :pageId")
    suspend fun deleteAllForPage(pageId: Long)

    @Query("DELETE FROM wikilinks")
    suspend fun clearAll()
}
