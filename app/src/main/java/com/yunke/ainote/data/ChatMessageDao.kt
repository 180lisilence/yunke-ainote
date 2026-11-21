package com.yunke.ainote.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query

@Dao
interface ChatMessageDao {

    @Insert
    suspend fun insert(msg: ChatMessageEntity)

    @Query("SELECT * FROM chat_messages WHERE sessionId = :sessionId ORDER BY createdAt ASC")
    suspend fun loadForSession(sessionId: String): List<ChatMessageEntity>
}
