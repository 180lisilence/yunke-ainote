package com.yunke.ainote.data

import kotlinx.coroutines.flow.Flow
import java.util.UUID

class NoteRepository(private val dao: NoteDao) {

    val notes: Flow<List<NoteEntity>> = dao.observeActive()
    val trash: Flow<List<NoteEntity>> = dao.observeTrash()
    val folders: Flow<List<FolderEntity>> = dao.observeFolders()

    suspend fun getById(id: String): NoteEntity? = dao.getById(id)

    /** 关键词检索活跃笔记；无命中时回退最近几条，供模型自行判断。 */
    suspend fun searchNotes(query: String, limit: Int = 7): List<NoteEntity> {
        val q = query.trim()
        if (q.isEmpty()) return dao.recent(limit)
        val hit = dao.search("%$q%", limit)
        return hit.ifEmpty { dao.recent(limit) }
    }

    /** 新建或更新；更新前把旧正文留一档修订快照。 */
    suspend fun save(rawText: String, existing: NoteEntity?) {
        val now = System.currentTimeMillis()
        if (existing == null) {
            val note = NoteEntity(
                id = UUID.randomUUID().toString(),
                rawText = rawText,
                source = "text",
                createdAt = now,
                updatedAt = now,
            )
            dao.upsert(note)
        } else {
            if (existing.rawText != rawText) {
                dao.insertRevision(
                    RevisionEntity(
                        id = UUID.randomUUID().toString(),
                        noteId = existing.id,
                        content = existing.rawText,
                        createdAt = now,
                        reason = "edit",
                    ),
                )
            }
            dao.upsert(existing.copy(rawText = rawText, updatedAt = now))
        }
    }

    /** 按内容形态保存：类型 + 标题 + 正文 + 子项 JSON。 */
    suspend fun saveTyped(
        type: String,
        title: String,
        body: String,
        itemsJson: String?,
        existing: NoteEntity?,
    ) {
        val now = System.currentTimeMillis()
        val safeTitle = title.ifBlank { body.lineFirstOrNull() ?: "无标题" }
        if (existing == null) {
            dao.upsert(
                NoteEntity(
                    id = UUID.randomUUID().toString(),
                    rawText = body,
                    title = safeTitle,
                    type = type,
                    itemsJson = itemsJson,
                    source = "text",
                    createdAt = now,
                    updatedAt = now,
                ),
            )
        } else {
            if (existing.rawText != body) {
                dao.insertRevision(
                    RevisionEntity(
                        id = UUID.randomUUID().toString(),
                        noteId = existing.id,
                        content = existing.rawText,
                        createdAt = now,
                        reason = "edit",
                    ),
                )
            }
            dao.upsert(
                existing.copy(
                    rawText = body,
                    title = safeTitle,
                    type = type,
                    itemsJson = itemsJson,
                    updatedAt = now,
                ),
            )
        }
    }

    private fun String.lineFirstOrNull(): String? =
        this.lineSequence().firstOrNull { it.isNotBlank() }?.trim()?.take(20)?.ifBlank { null }

    /** AI 从对话中抽取后直接入库：标题 + 正文 + 结构化字段；返回新笔记 ID。 */
    suspend fun saveExtracted(
        title: String,
        content: String,
        tags: String?,
        people: String? = null,
        places: String? = null,
        mood: String? = null,
        todo: String? = null,
        eventDate: String? = null,
    ): String {
        val now = System.currentTimeMillis()
        // 事件日期换算后拼到正文开头；正文不再保留“今天/昨天”等相对词。
        val body = when {
            eventDate != null && !content.startsWith(eventDate) -> "$eventDate，$content"
            else -> content
        }
        val note = NoteEntity(
            id = UUID.randomUUID().toString(),
            rawText = body,
            cleanedText = body,
            title = title.ifBlank { body.take(20) },
            summary = body.take(60),
            tags = tags,
            people = people,
            places = places,
            mood = mood,
            todo = todo,
            source = "ai",
            createdAt = now,
            updatedAt = now,
        )
        dao.upsert(note)
        return note.id
    }

    suspend fun delete(id: String) = dao.softDelete(id, System.currentTimeMillis())

    suspend fun restore(id: String) = dao.restore(id, System.currentTimeMillis())

    suspend fun permanentDelete(id: String) = dao.permanentDelete(id)

    suspend fun togglePin(note: NoteEntity) =
        dao.setPinned(note.id, !note.isPinned, System.currentTimeMillis())

    fun revisions(noteId: String): Flow<List<RevisionEntity>> = dao.revisionsForNote(noteId)
}
