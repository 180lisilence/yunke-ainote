package com.yunke.ainote.ui

import java.util.UUID

data class ChatMessage(
    val role: String, // user / assistant
    val content: String,
    val reasoning: String? = null,
    val timestamp: Long = System.currentTimeMillis(),
    val id: String = UUID.randomUUID().toString(),
)
