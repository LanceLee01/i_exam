package com.examhelper.app.knowledge.db

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update

@Dao
interface SourceFileDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(file: SourceFile)

    @Update
    suspend fun update(file: SourceFile)

    @Delete
    suspend fun delete(file: SourceFile)

    @Query("SELECT * FROM source_files ORDER BY importedAt DESC")
    suspend fun getAll(): List<SourceFile>

    @Query("SELECT * FROM source_files WHERE id = :id")
    suspend fun getById(id: String): SourceFile?

    @Query("SELECT * FROM source_files WHERE filePath = :path")
    suspend fun getByPath(path: String): SourceFile?

    @Query("SELECT * FROM source_files WHERE contentHash = :hash LIMIT 1")
    suspend fun getByHash(hash: String): SourceFile?

    @Query("DELETE FROM source_files")
    suspend fun clearAll()
}
