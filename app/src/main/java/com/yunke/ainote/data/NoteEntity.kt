package com.yunke.ainote.data

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * 记忆条目。M1 仅落地原文与基础字段；
 * cleanedText / title / summary / tags / people / places / mood / todo
 * 预留供 M3 本地 LLM 结构化整理填充。
 */
@Entity(tableName = "notes")
data class NoteEntity(
    @PrimaryKey val id: String,
    val rawText: String,
    val cleanedText: String? = null,
    val title: String? = null,
    val summary: String? = null,
    val tags: String? = null,
    val people: String? = null,
    val places: String? = null,
    val mood: String? = null,
    val todo: String? = null,
    /** text / voice */
    val source: String = "text",
    /** 内容形态，见 [NoteType]；旧数据默认 NOTE。 */
    val type: String = NoteType.NOTE.key,
    val folderId: String? = null,
    val isPinned: Boolean = false,
    val sortOrder: Int = 0,
    val isEncrypted: Boolean = false,
    val encryptionSalt: String? = null,
    /** 形态子项 JSON 数组（清单/购物/计数/书签/时间线等）。 */
    val itemsJson: String? = null,
    val createdAt: Long,
    val updatedAt: Long,
    /** 软删除时间戳；null 表示未删除 */
    val deletedAt: Long? = null,
)
