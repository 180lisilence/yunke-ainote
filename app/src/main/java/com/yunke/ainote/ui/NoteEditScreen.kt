package com.yunke.ainote.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.yunke.ainote.data.ItemPayload
import com.yunke.ainote.data.NoteEntity
import com.yunke.ainote.data.NoteType
import com.yunke.ainote.data.RevisionEntity
import com.yunke.ainote.data.toItems
import com.yunke.ainote.data.toItemsJson
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NoteEditScreen(
    existing: NoteEntity?,
    revisions: List<RevisionEntity>,
    onBack: () -> Unit,
    onSave: (type: String, title: String, body: String, itemsJson: String?, existing: NoteEntity?) -> Unit,
    onDelete: (String) -> Unit,
) {
    var title by remember { mutableStateOf(existing?.title ?: "") }
    var body by remember { mutableStateOf(existing?.rawText ?: "") }
    var type by remember { mutableStateOf(NoteType.fromKey(existing?.type)) }
    val items = remember { mutableStateListOf<ItemPayload>().apply { addAll(existing?.itemsJson.toItems()) } }
    var typeMenuOpen by remember { mutableStateOf(false) }

    val usesItems = type in itemTypes

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (existing == null) "新建${type.label}" else "编辑${type.label}") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Outlined.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    if (existing != null) {
                        IconButton(onClick = { onDelete(existing.id) }) {
                            Icon(Icons.Outlined.Delete, contentDescription = "删除")
                        }
                    }
                    TextButton(onClick = {
                        val itemsJson = if (usesItems) items.toItemsJson() else null
                        onSave(type.key, title.trim(), body.trim(), itemsJson, existing)
                    }) { Text("保存") }
                },
            )
        },
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // 形态选择
            ExposedDropdownMenuBox(expanded = typeMenuOpen, onExpandedChange = { typeMenuOpen = it }) {
                OutlinedTextField(
                    value = type.label,
                    onValueChange = {},
                    readOnly = true,
                    modifier = Modifier.fillMaxWidth().menuAnchor(),
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = typeMenuOpen) },
                    label = { Text("内容形态") },
                )
                DropdownMenu(expanded = typeMenuOpen, onDismissRequest = { typeMenuOpen = false }) {
                    itemTypes.forEach { t ->
                        DropdownMenuItem(
                            text = { Text(t.label) },
                            onClick = { type = t; typeMenuOpen = false },
                        )
                    }
                }
            }

            OutlinedTextField(
                value = title,
                onValueChange = { title = it },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("标题") },
                singleLine = true,
            )

            if (usesItems) {
                items.forEachIndexed { index, item ->
                    ItemRow(
                        item = item,
                        type = type,
                        onText = { items[index] = item.copy(text = it) },
                        onExtra = { items[index] = item.copy(extra = it) },
                        onToggle = { items[index] = item.copy(done = !item.done) },
                        onDelete = { items.removeAt(index) },
                        onCounter = { delta ->
                            val cur = item.extra?.toIntOrNull() ?: 0
                            items[index] = item.copy(extra = (cur + delta).toString())
                        },
                    )
                }
                TextButton(onClick = {
                    items.add(ItemPayload(text = "", extra = if (type == NoteType.COUNTER) "0" else null))
                }) {
                    Icon(Icons.Outlined.Add, contentDescription = "添加")
                    Text("添加一条")
                }
            } else {
                OutlinedTextField(
                    value = body,
                    onValueChange = { body = it },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = {
                        Text(when (type) {
                            NoteType.SNIPPETS -> "粘贴或输入代码…"
                            else -> "今天发生了什么？"
                        })
                    },
                    minLines = 8,
                )
            }

            if (revisions.isNotEmpty()) {
                Text("修订历史（${revisions.size}）", style = MaterialTheme.typography.titleSmall)
                revisions.forEach { rev ->
                    Column(
                        Modifier.fillMaxWidth().padding(vertical = 4.dp),
                        verticalArrangement = Arrangement.spacedBy(2.dp),
                    ) {
                        Text(formatTime(rev.createdAt), style = MaterialTheme.typography.labelSmall)
                        Text(rev.content, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
        }
    }
}

@Composable
private fun ItemRow(
    item: ItemPayload,
    type: NoteType,
    onText: (String) -> Unit,
    onExtra: (String) -> Unit,
    onToggle: () -> Unit,
    onDelete: () -> Unit,
    onCounter: (Int) -> Unit,
) {
    Row(
        Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (type == NoteType.LIST || type == NoteType.SHOPPING) {
            Checkbox(checked = item.done, onCheckedChange = { onToggle() })
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            OutlinedTextField(
                value = item.text,
                onValueChange = onText,
                modifier = Modifier.fillMaxWidth(),
                placeholder = {
                    Text(when (type) {
                        NoteType.LINKLIST -> "标题"
                        NoteType.COUNTER -> "计数标签"
                        NoteType.PASSWORD -> "标题 / 站名"
                        NoteType.NAMES -> "姓名"
                        NoteType.TIMELINE -> "备注"
                        else -> "条目"
                    })
                },
                singleLine = true,
            )
            when (type) {
                NoteType.LINKLIST -> OutlinedTextField(
                    value = item.extra ?: "",
                    onValueChange = onExtra,
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("https://…") },
                    singleLine = true,
                )
                NoteType.SHOPPING -> OutlinedTextField(
                    value = item.extra ?: "",
                    onValueChange = onExtra,
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("价格 / 品牌") },
                    singleLine = true,
                )
                NoteType.PASSWORD -> OutlinedTextField(
                    value = item.extra ?: "",
                    onValueChange = onExtra,
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("账号 · 密码 · 网址") },
                    singleLine = true,
                )
                NoteType.NAMES -> OutlinedTextField(
                    value = item.extra ?: "",
                    onValueChange = onExtra,
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("电话 / 邮箱") },
                    singleLine = true,
                )
                NoteType.TIMELINE -> OutlinedTextField(
                    value = item.extra ?: "",
                    onValueChange = onExtra,
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("日期  HH:mm") },
                    singleLine = true,
                )
                NoteType.COUNTER -> Row(verticalAlignment = Alignment.CenterVertically) {
                    TextButton(onClick = { onCounter(-1) }) { Text("-") }
                    Text(item.extra ?: "0", modifier = Modifier.padding(horizontal = 8.dp))
                    TextButton(onClick = { onCounter(1) }) { Text("+") }
                }
                else -> Unit
            }
        }
        IconButton(onClick = onDelete) {
            Icon(Icons.Outlined.Delete, contentDescription = "删除")
        }
    }
}

private val itemTypes = listOf(
    NoteType.NOTE,
    NoteType.LIST,
    NoteType.SHOPPING,
    NoteType.COUNTER,
    NoteType.LINKLIST,
    NoteType.PASSWORD,
    NoteType.NAMES,
    NoteType.NOTEBOOK,
    NoteType.TIMELINE,
    NoteType.SNIPPETS,
)

private fun formatTime(ts: Long): String =
    SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date(ts))
