package com.yunke.ainote.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface NoteDao {

    @Query("SELECT * FROM notes WHERE deletedAt IS NULL ORDER BY isPinned DESC, updatedAt DESC")
    fun observeActive(): Flow<List<NoteEntity>>

    @Query("SELECT * FROM notes WHERE deletedAt IS NOT NULL ORDER BY deletedAt DESC")
    fun observeTrash(): Flow<List<NoteEntity>>

    @Query("SELECT * FROM folders ORDER BY createdAt ASC")
    fun observeFolders(): Flow<List<FolderEntity>>

    @Query("SELECT * FROM notes WHERE id = :id")
    suspend fun getById(id: String): NoteEntity?

    @Query("SELECT * FROM notes WHERE deletedAt IS NULL AND " +
        "(rawText LIKE :q OR title LIKE :q OR tags LIKE :q OR people LIKE :q OR places LIKE :q) " +
        "ORDER BY isPinned DESC, updatedAt DESC LIMIT :limit")
    suspend fun search(q: String, limit: Int): List<NoteEntity>

    @Query("SELECT * FROM notes WHERE deletedAt IS NULL ORDER BY updatedAt DESC LIMIT :limit")
    suspend fun recent(limit: Int): List<NoteEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(note: NoteEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertFolder(folder: FolderEntity)

    @Query("UPDATE notes SET deletedAt = :ts WHERE id = :id")
    suspend fun softDelete(id: String, ts: Long)

    @Query("UPDATE notes SET deletedAt = NULL, updatedAt = :ts WHERE id = :id")
    suspend fun restore(id: String, ts: Long)

    @Query("DELETE FROM notes WHERE id = :id")
    suspend fun permanentDelete(id: String)

    @Query("UPDATE notes SET isPinned = :pinned, updatedAt = :ts WHERE id = :id")
    suspend fun setPinned(id: String, pinned: Boolean, ts: Long)

    @Insert
    suspend fun insertRevision(rev: RevisionEntity)

    @Query("SELECT * FROM revisions WHERE noteId = :noteId ORDER BY createdAt DESC")
    fun revisionsForNote(noteId: String): Flow<List<RevisionEntity>>
}
