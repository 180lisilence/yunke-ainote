package com.yunke.ainote.data

import androidx.room.Entity
import androidx.room.PrimaryKey

/** 对话会话消息（MVP 单会话，sessionId 固定）。 */
@Entity(tableName = "chat_messages")
data class ChatMessageEntity(
    @PrimaryKey val id: String,
    val sessionId: String,
    val role: String,       // user / assistant
    val content: String,
    val refs: String? = null,
    val createdAt: Long,
)
