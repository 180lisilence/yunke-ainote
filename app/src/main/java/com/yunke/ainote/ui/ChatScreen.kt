package com.yunke.ainote.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.Send
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.yunke.ainote.llm.BuiltinStatus
import com.yunke.ainote.llm.ModelState
import com.yunke.ainote.settings.ChatParams
import com.yunke.ainote.settings.ChannelType
import com.yunke.ainote.settings.Presets
import com.yunke.ainote.settings.RemoteConfig
import com.yunke.ainote.settings.UsageSnapshot

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    messages: List<ChatMessage>,
    channel: ChannelType,
    onChannelChange: (ChannelType) -> Unit,
    modelState: ModelState,
    models: List<String>,
    currentModel: String?,
    builtinStatus: BuiltinStatus,
    builtinProgress: Float,
    lastError: String?,
    onLoadModel: (String) -> Unit,
    onReleaseModel: () -> Unit,
    remoteConfig: RemoteConfig,
    onSaveRemoteConfig: (RemoteConfig) -> Unit,
    params: ChatParams,
    onSaveParams: (ChatParams) -> Unit,
    usage: UsageSnapshot,
    onClearUsage: () -> Unit,
    onBack: () -> Unit,
    onSend: (String) -> Unit,
) {
    var input by rememberSaveable { mutableStateOf("") }
    var showRemoteDialog by rememberSaveable { mutableStateOf(false) }
    var showParamsDialog by rememberSaveable { mutableStateOf(false) }
    var showUsageDialog by rememberSaveable { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("AI 对话 · ${stateLabel(channel, modelState, remoteConfig)}") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    TextButton(onClick = { showUsageDialog = true }) {
                        Text("用量 ${usage.totalTokens}")
                    }
                    TextButton(onClick = { showParamsDialog = true }) { Text("参数") }
                },
            )
        },
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            ChannelSwitcher(channel = channel, onChannelChange = onChannelChange)

            when (channel) {
                ChannelType.LOCAL -> LocalModelBar(
                    state = modelState,
                    models = models,
                    currentModel = currentModel,
                    builtinStatus = builtinStatus,
                    builtinProgress = builtinProgress,
                    lastError = lastError,
                    onLoad = onLoadModel,
                    onRelease = onReleaseModel,
                )

                ChannelType.REMOTE -> RemoteBar(
                    cfg = remoteConfig,
                    onSettings = { showRemoteDialog = true },
                )
            }

            LazyColumn(
                Modifier.weight(1f).padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                items(messages, key = { it.id }) { msg ->
                    MessageBubble(msg)
                }
            }
            Row(
                Modifier.padding(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                OutlinedTextField(
                    value = input,
                    onValueChange = { input = it },
                    modifier = Modifier.weight(1f),
                    placeholder = { Text("问点什么…") },
                    maxLines = 4,
                )
                IconButton(onClick = {
                    if (input.isNotBlank()) {
                        onSend(input)
                        input = ""
                    }
                }) {
                    Icon(Icons.AutoMirrored.Outlined.Send, contentDescription = "发送")
                }
            }
        }
    }

    if (showRemoteDialog) {
        RemoteConfigDialog(
            initial = remoteConfig,
            onDismiss = { showRemoteDialog = false },
            onSave = {
                onSaveRemoteConfig(it)
                showRemoteDialog = false
            },
        )
    }

    if (showParamsDialog) {
        ParamsDialog(
            initial = params,
            onDismiss = { showParamsDialog = false },
            onSave = {
                onSaveParams(it)
                showParamsDialog = false
            },
        )
    }

    if (showUsageDialog) {
        UsageDialog(
            usage = usage,
            onDismiss = { showUsageDialog = false },
            onClear = onClearUsage,
        )
    }
}

@Composable
private fun ChannelSwitcher(
    channel: ChannelType,
    onChannelChange: (ChannelType) -> Unit,
) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        ChannelType.values().forEach { c ->
            val selected = channel == c
            Button(
                onClick = { onChannelChange(c) },
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (selected) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.surfaceVariant,
                    contentColor = if (selected) MaterialTheme.colorScheme.onPrimary
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                ),
            ) {
                Text(if (c == ChannelType.LOCAL) "本地模型" else "云端 API")
            }
        }
    }
}

@Composable
private fun LocalModelBar(
    state: ModelState,
    models: List<String>,
    currentModel: String?,
    builtinStatus: BuiltinStatus,
    builtinProgress: Float,
    lastError: String?,
    onLoad: (String) -> Unit,
    onRelease: () -> Unit,
) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        when (builtinStatus) {
            BuiltinStatus.COPYING -> {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        "内置模型解压中 ${(builtinProgress * 100).toInt()}%",
                        style = MaterialTheme.typography.bodySmall,
                    )
                    LinearProgressIndicator(
                        progress = { builtinProgress.coerceIn(0f, 1f) },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }

            BuiltinStatus.ERROR -> Text(
                "内置模型就位失败，请返回重进",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
            )

            else -> when (state) {
            ModelState.LOADED -> {
                Text(
                    "已加载：${currentModel ?: ""}",
                    style = MaterialTheme.typography.bodySmall,
                )
                TextButton(onClick = onRelease) { Text("卸载") }
            }

            ModelState.LOADING -> Text("模型加载中…", style = MaterialTheme.typography.bodySmall)

            else -> {
                Column(
                    Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Box {
                        TextButton(onClick = { expanded = true }) {
                            Text(if (models.isEmpty()) "未找到 GGUF 模型" else "选择模型")
                        }
                        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                            if (models.isEmpty()) {
                                DropdownMenuItem(
                                    text = { Text("models 目录无 .gguf 文件") },
                                    onClick = { expanded = false },
                                )
                            } else {
                                models.forEach { name ->
                                    DropdownMenuItem(
                                        text = { Text(name) },
                                        onClick = {
                                            expanded = false
                                            onLoad(name)
                                        },
                                    )
                                }
                            }
                        }
                    }
                    if (!lastError.isNullOrBlank()) {
                        Text(
                            "加载失败：$lastError",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                }
            }
            }
        }
    }
}

@Composable
private fun RemoteBar(
    cfg: RemoteConfig,
    onSettings: () -> Unit,
) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            if (cfg.isReady) "已配置：${cfg.modelName}" else "未配置云端接口",
            style = MaterialTheme.typography.bodySmall,
        )
        TextButton(onClick = onSettings) { Text("设置") }
    }
}

@Composable
private fun RemoteConfigDialog(
    initial: RemoteConfig,
    onDismiss: () -> Unit,
    onSave: (RemoteConfig) -> Unit,
) {
    var base by rememberSaveable { mutableStateOf(initial.baseUrl) }
    var key by rememberSaveable { mutableStateOf(initial.apiKey) }
    var model by rememberSaveable { mutableStateOf(initial.modelName) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("云端接口设置") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = base,
                    onValueChange = { base = it },
                    label = { Text("接口地址") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = key,
                    onValueChange = { key = it },
                    label = { Text("API Key") },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = model,
                    onValueChange = { model = it },
                    label = { Text("模型名") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onSave(RemoteConfig(base, key, model)) }) {
                Text("保存")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        },
    )
}

@Composable
private fun MessageBubble(msg: ChatMessage) {
    val isUser = msg.role == "user"
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start,
    ) {
        Column(horizontalAlignment = if (isUser) Alignment.End else Alignment.Start) {
            val reasoning = msg.reasoning
            if (!reasoning.isNullOrBlank()) {
                Text(
                    reasoning,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                )
            }
            Surface(
                shape = MaterialTheme.shapes.large,
                color = if (isUser) {
                    MaterialTheme.colorScheme.primaryContainer
                } else {
                    MaterialTheme.colorScheme.surfaceVariant
                },
            ) {
                val pending = msg.role != "user" && msg.content.isBlank()
                Text(
                    if (pending) "正在思考…" else msg.content,
                    style = if (pending) MaterialTheme.typography.bodySmall else MaterialTheme.typography.bodyLarge,
                    color = if (pending) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(12.dp),
                )
            }
        }
    }
}

@Composable
private fun ParamsDialog(
    initial: ChatParams,
    onDismiss: () -> Unit,
    onSave: (ChatParams) -> Unit,
) {
    var temperature by rememberSaveable { mutableStateOf(initial.temperature) }
    var maxTokens by rememberSaveable { mutableStateOf(initial.maxTokens.toString()) }
    var ctxRounds by rememberSaveable { mutableStateOf(initial.ctxRounds) }
    var stream by rememberSaveable { mutableStateOf(initial.streamEnabled) }
    var presetId by rememberSaveable { mutableStateOf(initial.presetId) }
    var customPrompt by rememberSaveable { mutableStateOf(initial.customPrompt) }
    var presetMenu by rememberSaveable { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("对话参数") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("发散程度 ${String.format("%.2f", temperature)}")
                Slider(
                    value = temperature,
                    onValueChange = { temperature = it },
                    valueRange = 0f..2f,
                    steps = 39,
                )

                OutlinedTextField(
                    value = maxTokens,
                    onValueChange = { maxTokens = it.filter(Char::isDigit) },
                    label = { Text("最大输出 tokens") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )

                Text("携带上下文轮数 $ctxRounds")
                Slider(
                    value = ctxRounds.toFloat(),
                    onValueChange = { ctxRounds = it.toInt() },
                    valueRange = 0f..40f,
                    steps = 39,
                )

                Row(
                    Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text("流式输出")
                    Switch(checked = stream, onCheckedChange = { stream = it })
                }

                Box {
                    TextButton(onClick = { presetMenu = true }) {
                        Text("人格：${Presets.byId(presetId).name}")
                    }
                    DropdownMenu(expanded = presetMenu, onDismissRequest = { presetMenu = false }) {
                        Presets.all.forEach { p ->
                            DropdownMenuItem(
                                text = { Text(p.name) },
                                onClick = { presetId = p.id; presetMenu = false },
                            )
                        }
                    }
                }

                OutlinedTextField(
                    value = customPrompt,
                    onValueChange = { customPrompt = it },
                    label = { Text("自定义补充提示词（可留空）") },
                    minLines = 3,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(onClick = {
                onSave(
                    initial.copy(
                        temperature = temperature,
                        maxTokens = maxTokens.toIntOrNull() ?: 2048,
                        ctxRounds = ctxRounds,
                        streamEnabled = stream,
                        presetId = presetId,
                        customPrompt = customPrompt,
                    ),
                )
            }) { Text("保存") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
}

@Composable
private fun UsageDialog(
    usage: UsageSnapshot,
    onDismiss: () -> Unit,
    onClear: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("用量统计") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("累计：输入 ${usage.totalPrompt} · 输出 ${usage.totalCompletion} · 合计 ${usage.totalTokens}")
                if (usage.recent.isEmpty()) {
                    Text("暂无最近请求", style = MaterialTheme.typography.bodySmall)
                } else {
                    usage.recent.forEach { r ->
                        Text(
                            "${r.model} · 输入 ${r.promptTokens} / 输出 ${r.completionTokens}",
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onClear(); onDismiss() }) { Text("清空统计") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("关闭") } },
    )
}

private fun stateLabel(
    channel: ChannelType,
    modelState: ModelState,
    cfg: RemoteConfig,
): String = when (channel) {
    ChannelType.LOCAL -> when (modelState) {
        ModelState.IDLE -> "本地 · 未加载"
        ModelState.LOADING -> "本地 · 加载中"
        ModelState.LOADED -> "本地 · 已加载"
        ModelState.ERROR -> "本地 · 加载失败"
    }

    ChannelType.REMOTE -> if (cfg.isReady) "云端 · 已配置" else "云端 · 未配置"
}
