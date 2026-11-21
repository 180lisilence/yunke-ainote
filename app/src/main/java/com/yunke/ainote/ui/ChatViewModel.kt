package com.yunke.ainote.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.yunke.ainote.ai.ExtractedNote
import com.yunke.ainote.ai.NoteExtractor
import com.yunke.ainote.data.AppDatabase
import com.yunke.ainote.data.ChatMessageEntity
import com.yunke.ainote.data.NoteEntity
import com.yunke.ainote.data.NoteRepository
import com.yunke.ainote.llm.BuiltinStatus
import com.yunke.ainote.llm.ModelManager
import com.yunke.ainote.llm.ModelState
import com.yunke.ainote.remote.RemoteChatClient
import com.yunke.ainote.settings.ChatParams
import com.yunke.ainote.settings.ChannelType
import com.yunke.ainote.settings.Presets
import com.yunke.ainote.settings.RemoteConfig
import com.yunke.ainote.settings.SettingsStore
import com.yunke.ainote.settings.UsageRecord
import com.yunke.ainote.settings.UsageSnapshot
import com.yunke.ainote.settings.UsageStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

class ChatViewModel(app: Application) : AndroidViewModel(app) {

    private val settings = SettingsStore(app)
    private val manager = ModelManager(app)
    private val remoteClient = RemoteChatClient()
    private val repository = NoteRepository(AppDatabase.getInstance(app).noteDao())
    private val chatDao = AppDatabase.getInstance(app).chatMessageDao()
    private val usageStore = UsageStore(app)

    private val _messages = MutableStateFlow<List<ChatMessage>>(emptyList())
    val messages: StateFlow<List<ChatMessage>> = _messages.asStateFlow()

    // 本地模型状态
    val modelState: StateFlow<ModelState> = manager.state
    val currentModel: StateFlow<String?> = manager.currentModel
    val builtinStatus: StateFlow<BuiltinStatus> = manager.builtinStatus
    val builtinProgress: StateFlow<Float> = manager.builtinProgress
    val lastError: StateFlow<String?> = manager.lastError
    private val _models = MutableStateFlow<List<String>>(emptyList())
    val models: StateFlow<List<String>> = _models.asStateFlow()

    // 通道与云端配置
    private val _channel = MutableStateFlow(settings.channel)
    val channel: StateFlow<ChannelType> = _channel.asStateFlow()

    private val _remoteConfig = MutableStateFlow(settings.remoteConfig())
    val remoteConfig: StateFlow<RemoteConfig> = _remoteConfig.asStateFlow()

    // 生成参数 / 人格预设 / 用量
    private val _params = MutableStateFlow(settings.chatParams())
    val params: StateFlow<ChatParams> = _params.asStateFlow()

    private val _usage = MutableStateFlow(usageStore.snapshot())
    val usage: StateFlow<UsageSnapshot> = _usage.asStateFlow()

    init {
        refreshModels()
        loadHistory()
        viewModelScope.launch(Dispatchers.IO) {
            manager.ensureBuiltin()
            refreshModels()
            if (_channel.value == ChannelType.LOCAL) {
                manager.autoLoadBuiltin()
            }
        }
    }

    fun refreshModels() {
        _models.value = manager.listModels().map { it.name }
    }

    fun setChannel(channel: ChannelType) {
        _channel.value = channel
        settings.channel = channel
        if (channel == ChannelType.LOCAL) {
            viewModelScope.launch(Dispatchers.IO) { manager.autoLoadBuiltin() }
        }
    }

    fun saveRemoteConfig(cfg: RemoteConfig) {
        settings.saveRemoteConfig(cfg)
        _remoteConfig.value = cfg
    }

    fun saveChatParams(p: ChatParams) {
        settings.saveChatParams(p)
        _params.value = p
    }

    fun refreshUsage() {
        _usage.value = usageStore.snapshot()
    }

    fun clearUsage() {
        usageStore.clear()
        refreshUsage()
    }

    fun loadModel(name: String) {
        val file = File(manager.modelDir, name)
        viewModelScope.launch(Dispatchers.IO) {
            manager.load(file.absolutePath)
            refreshModels()
        }
    }

    fun releaseModel() {
        manager.release()
    }

    fun send(text: String) {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return

        // 本轮之前的历史（此时列表尚未加入本轮消息）
        val history = recentHistory(skipLastUser = false)

        val userMsg = ChatMessage(role = "user", content = trimmed)
        val replyMsg = ChatMessage(role = "assistant", content = "")
        val replyId = replyMsg.id
        _messages.update { it + userMsg + replyMsg }

        viewModelScope.launch(Dispatchers.IO) {
            persist(userMsg)

            // 1) 抽取待记事项入库（失败不阻塞）
            val saved = runCatching { extractAndSave(trimmed) }.getOrNull()

            // 2) 检索本地笔记作为问答证据；排除本轮刚存的本条，不把自己当依据
            val evidence = runCatching {
                repository.searchNotes(trimmed).filterNot { it.id == saved?.id }
            }.getOrDefault(emptyList())
            val qaPrompt = buildQaPrompt(trimmed, evidence, history)

            val p = _params.value
            val systemPrompt = Presets.compose(p)

            var reply: String
            var reasoning: String? = null
            var usage: UsageRecord? = null

            try {
                when (_channel.value) {
                    ChannelType.LOCAL -> {
                        if (manager.state.value != ModelState.LOADED) {
                            val reason = manager.lastError.value
                            throw IllegalStateException(
                                if (reason != null) "本地模型未加载：$reason" else "本地模型未加载"
                            )
                        }
                        val raw = manager.chat(qaPrompt)
                        val (think, body) = splitThink(raw)
                        reasoning = think
                        reply = body.ifBlank { think ?: raw }
                    }

                    ChannelType.REMOTE -> {
                        val cfg = settings.remoteConfig()
                        if (!cfg.isReady) throw IllegalStateException("云端未配置")
                        if (p.streamEnabled) {
                            var lastReasoning: String? = null
                            usage = runCatching {
                                remoteClient.chatStream(
                                    cfg, systemPrompt, qaPrompt,
                                    p.temperature, p.maxTokens,
                                ) { c, r ->
                                    lastReasoning = r.ifBlank { null }
                                    updateMessage(replyId) { it.copy(content = c, reasoning = lastReasoning) }
                                }
                            }.getOrNull()
                            reply = _messages.value.firstOrNull { it.id == replyId }?.content ?: ""
                            reasoning = lastReasoning
                        } else {
                            reply = remoteClient.chat(cfg, systemPrompt, qaPrompt)
                        }
                    }
                }
            } catch (t: Throwable) {
                reply = "[推理失败] ${t.message}"
            }

            val sources = evidence.map { noteTitleOf(it) }.distinct()
            val finalReply = buildFinalReply(reply, saved?.note?.title, sources)
            updateMessage(replyId) { it.copy(content = finalReply, reasoning = reasoning) }

            val persisted = _messages.value.firstOrNull { it.id == replyId } ?: replyMsg
            persist(persisted)

            usage?.let {
                usageStore.record(it.model, it.promptTokens, it.completionTokens)
                refreshUsage()
            }
        }
    }

    private fun updateMessage(id: String, transform: (ChatMessage) -> ChatMessage) {
        _messages.update { list -> list.map { if (it.id == id) transform(it) else it } }
    }

    /** 抽取专用的单轮整段补全。 */
    private fun completeOnce(prompt: String): String = when (_channel.value) {
        ChannelType.LOCAL -> {
            if (manager.state.value != ModelState.LOADED) throw IllegalStateException("本地模型未加载")
            manager.chat(prompt)
        }

        ChannelType.REMOTE -> {
            val cfg = settings.remoteConfig()
            if (!cfg.isReady) throw IllegalStateException("云端未配置")
            remoteClient.chat(cfg, Presets.compose(_params.value), prompt)
        }
    }

    private suspend fun extractAndSave(userText: String): Saved? {
        val note = NoteExtractor.extract(userText) { completeOnce(it) } ?: return null
        val id = repository.saveExtracted(
            title = note.title,
            content = note.content,
            tags = note.tags,
            people = note.people,
            places = note.places,
            mood = note.mood,
            todo = note.todo,
            eventDate = note.eventDate,
        )
        return Saved(note, id)
    }

    private data class Saved(val note: ExtractedNote, val id: String)

    private fun buildQaPrompt(
        question: String,
        notes: List<NoteEntity>,
        history: List<ChatMessage>,
    ): String {
        val sb = StringBuilder()
        sb.appendLine("先判断用户这句话的意图：")
        sb.appendLine("- 问候、闲聊、问你能做什么：正常自然回应，不要翻笔记，也不要说“本地没有记录”。")
        sb.appendLine("- 询问过去记下的事：再参考下面【相关笔记】，只依据笔记内容回答，不编造；笔记里确实没有时，简短说“这条我这边没有记下来”，不要生硬套话。")
        sb.appendLine("- 用户在交代一件要记的事：自然确认已记下即可，不用复述整条指令。")
        sb.appendLine("当前时间：${nowText()}（用于理解“今天/昨天/明天”等说法）。")

        if (history.isNotEmpty()) {
            sb.appendLine()
            sb.appendLine("【前文对话】")
            history.forEach { m ->
                sb.appendLine(if (m.role == "user") "用户：${m.content.take(160)}" else "助手：${m.content.take(160)}")
            }
        }

        if (notes.isEmpty()) {
            sb.appendLine()
            sb.appendLine("【相关笔记】（本地暂无其他笔记记录）")
        } else {
            sb.appendLine()
            sb.appendLine("【相关笔记】")
            notes.take(5).forEach {
                sb.appendLine("- ${formatDate(it.updatedAt)} ${noteTitleOf(it)}：${it.rawText.take(120)}")
            }
        }

        sb.appendLine()
        sb.appendLine("【用户问题】$question")
        return sb.toString()
    }

    private fun buildFinalReply(reply: String, savedTitle: String?, sources: List<String>): String {
        var out = reply
        if (savedTitle != null) out += "\n📝 已存入笔记：$savedTitle"
        if (sources.isNotEmpty()) out += "\n📎 依据：${sources.joinToString("、")}"
        return out
    }

    /** 把 Qwen3 风格的 `<think>...</think>` 思维链从正文拆出；返回 (思维链, 正文)。 */
    private fun splitThink(raw: String): Pair<String?, String> {
        val m = Regex("<think>([\\s\\S]*?)</think>").find(raw) ?: return null to raw.trim()
        val think = m.groupValues[1].trim().ifBlank { null }
        val body = raw.substringAfter("</think>").trim()
        return think to body
    }

    private fun recentHistory(skipLastUser: Boolean): List<ChatMessage> {
        val all = _messages.value
        val base = if (skipLastUser && all.lastOrNull()?.role == "user") all.dropLast(1) else all
        return base.takeLast(_params.value.ctxRounds * 2)
    }

    private fun noteTitleOf(note: NoteEntity): String {
        note.title?.let { if (it.isNotBlank()) return it }
        return note.rawText.lineSequence().firstOrNull { it.isNotBlank() }?.trim()
            ?.take(20) ?: "无标题"
    }

    private fun formatDate(ts: Long): String =
        SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date(ts))

    private fun nowText(): String =
        SimpleDateFormat("yyyy-MM-dd HH:mm EEEE", Locale.getDefault()).format(Date())

    private fun loadHistory() {
        viewModelScope.launch(Dispatchers.IO) {
            val history = runCatching { chatDao.loadForSession(SESSION_ID) }.getOrDefault(emptyList())
            _messages.value = history.map {
                val (think, body) = splitThink(it.content)
                ChatMessage(
                    role = it.role,
                    content = body.ifBlank { think ?: it.content },
                    reasoning = think,
                    timestamp = it.createdAt,
                )
            }
        }
    }

    private suspend fun persist(msg: ChatMessage) {
        runCatching {
            chatDao.insert(
                ChatMessageEntity(
                    id = UUID.randomUUID().toString(),
                    sessionId = SESSION_ID,
                    role = msg.role,
                    content = msg.content,
                    createdAt = msg.timestamp,
                ),
            )
        }
    }

    companion object {
        private const val SESSION_ID = "default"
    }
}
