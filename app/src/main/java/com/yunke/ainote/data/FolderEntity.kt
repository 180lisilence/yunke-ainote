package com.yunke.ainote.data

import androidx.room.Entity
import androidx.room.PrimaryKey

/** 文件夹（支持父子层级），对齐 4 号文件夹体系。 */
@Entity(tableName = "folders")
data class FolderEntity(
    @PrimaryKey val id: String,
    val name: String,
    val parentId: String? = null,
    val color: String? = null,
    val createdAt: Long,
    val updatedAt: Long,
)
