package com.yunke.ainote.data

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.PrimaryKey

/** 条目修订历史：每次保存正文留一档快照。 */
@Entity(
    tableName = "revisions",
    foreignKeys = [ForeignKey(
        entity = NoteEntity::class,
        parentColumns = ["id"],
        childColumns = ["noteId"],
        onDelete = ForeignKey.CASCADE,
    )],
)
data class RevisionEntity(
    @PrimaryKey val id: String,
    val noteId: String,
    val content: String,
    val createdAt: Long,
    val reason: String = "edit",
)
