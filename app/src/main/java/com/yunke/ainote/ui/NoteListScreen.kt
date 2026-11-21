package com.yunke.ainote.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.IconButton
import androidx.compose.material3.Icon
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.yunke.ainote.R
import com.yunke.ainote.data.NoteEntity
import com.yunke.ainote.data.NoteType
import com.yunke.ainote.data.toItems
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NoteListScreen(
    notes: List<NoteEntity>,
    onNew: () -> Unit,
    onOpen: (NoteEntity) -> Unit,
    onDelete: (String) -> Unit,
    onOpenChat: () -> Unit,
) {
    var query by rememberSaveable { mutableStateOf("") }

    val filtered = notes.filter { matchesQuery(it, query.trim()) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("云笺") },
                actions = {
                    TextButton(onClick = onOpenChat) { Text("AI 对话") }
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        modifier = Modifier.padding(end = 12.dp),
                    ) {
                        Image(
                            painter = painterResource(R.mipmap.ic_launcher),
                            contentDescription = "云客出品",
                            modifier = Modifier.size(20.dp),
                        )
                        Text(
                            "云客出品",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                },
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = onNew) {
                Icon(Icons.Outlined.Add, contentDescription = "新建笔记")
            }
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                placeholder = { Text("搜索标题、正文、标签…") },
                singleLine = true,
            )

            if (filtered.isEmpty()) {
                Box(
                    Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        if (notes.isEmpty()) "还没有笔记，点右下角记一条"
                        else "没有匹配「$query」的笔记",
                    )
                }
            } else {
                LazyColumn(
                    Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    items(filtered, key = { it.id }) { note ->
                        Card(Modifier.fillMaxWidth().clickable { onOpen(note) }) {
                            Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    ) {
                                        Text(noteTitle(note), style = MaterialTheme.typography.titleMedium)
                                        Text(
                                            NoteType.fromKey(note.type).label,
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.primary,
                                        )
                                    }
                                    Text(
                                        noteSubtitle(note),
                                        style = MaterialTheme.typography.bodyMedium,
                                        maxLines = 3,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                    Text(
                                        formatTime(note.updatedAt),
                                        style = MaterialTheme.typography.labelSmall,
                                    )
                                }
                                IconButton(onClick = { onDelete(note.id) }) {
                                    Icon(Icons.Outlined.Delete, contentDescription = "删除")
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun matchesQuery(note: NoteEntity, q: String): Boolean {
    if (q.isEmpty()) return true
    val haystack = listOfNotNull(
        note.title, note.rawText, note.cleanedText, note.tags,
        note.people, note.places, note.mood, note.todo,
    ).joinToString(" ").lowercase()
    return q.lowercase().all { it in haystack }
}

internal fun noteTitle(note: NoteEntity): String {
    note.title?.let { if (it.isNotBlank()) return it }
    val firstLine = note.rawText.lineFirstOrBlank()
    return if (firstLine.length > 20) firstLine.take(20) else firstLine.ifBlank { "无标题" }
}

private fun noteSubtitle(note: NoteEntity): String {
    val items = note.itemsJson.toItems()
    if (items.isEmpty()) return note.rawText
    return when (NoteType.fromKey(note.type)) {
        NoteType.COUNTER -> items.joinToString("、") { "${it.text} ${it.extra ?: 0}" }
        NoteType.LINKLIST -> items.joinToString("、") { it.text.ifBlank { it.extra ?: "" } }
        NoteType.PASSWORD, NoteType.NAMES, NoteType.TIMELINE, NoteType.NOTEBOOK ->
            items.joinToString("、") { "${it.text} ${it.extra ?: ""}".trim() }
        else -> {
            val done = items.count { it.done }
            val first = items.firstOrNull()?.text.orEmpty()
            "$done/${items.size} $first".trim()
        }
    }
}

private fun String.lineFirstOrBlank(): String =
    this.lineSequence().firstOrNull { it.isNotBlank() }?.trim().orEmpty()

private fun formatTime(ts: Long): String =
    SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date(ts))
