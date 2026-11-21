package com.yunke.ainote.settings

import android.content.Context

/** 对话通道：本地 GGUF 模型 / 云端 OpenAI 兼容 API。 */
enum class ChannelType { LOCAL, REMOTE }

/** 云端 API 配置（用户自填，存本地 SharedPreferences）。 */
data class RemoteConfig(
    val baseUrl: String,
    val apiKey: String,
    val modelName: String,
) {
    val isReady: Boolean
        get() = baseUrl.isNotBlank() && apiKey.isNotBlank() && modelName.isNotBlank()

    companion object {
        const val DEFAULT_BASE = "https://api.deepseek.com/v1"
        const val DEFAULT_MODEL = "deepseek-chat"
    }
}

/** 配置持久化：通道选择 + 云端接入参数。 */
class SettingsStore(context: Context) {

    private val sp = context.getSharedPreferences("ainote_settings", Context.MODE_PRIVATE)

    var channel: ChannelType
        get() = runCatching {
            ChannelType.valueOf(sp.getString(KEY_CHANNEL, ChannelType.LOCAL.name)!!)
        }.getOrDefault(ChannelType.LOCAL)
        set(value) = sp.edit().putString(KEY_CHANNEL, value.name).apply()

    fun remoteConfig(): RemoteConfig = RemoteConfig(
        baseUrl = sp.getString(KEY_BASE, RemoteConfig.DEFAULT_BASE) ?: RemoteConfig.DEFAULT_BASE,
        apiKey = sp.getString(KEY_KEY, "") ?: "",
        modelName = sp.getString(KEY_MODEL, RemoteConfig.DEFAULT_MODEL) ?: RemoteConfig.DEFAULT_MODEL,
    )

    fun saveRemoteConfig(cfg: RemoteConfig) {
        sp.edit()
            .putString(KEY_BASE, cfg.baseUrl.trim())
            .putString(KEY_KEY, cfg.apiKey.trim())
            .putString(KEY_MODEL, cfg.modelName.trim())
            .apply()
    }

    fun chatParams(): ChatParams = ChatParams(
        temperature = sp.getString(KEY_TEMP, "0.7")?.toFloatOrNull() ?: 0.7f,
        maxTokens = sp.getString(KEY_MAXTOK, "2048")?.toIntOrNull() ?: 2048,
        ctxRounds = sp.getString(KEY_CTX, "12")?.toIntOrNull() ?: 12,
        streamEnabled = sp.getBoolean(KEY_STREAM, true),
        presetId = sp.getString(KEY_PRESET, "default") ?: "default",
        customPrompt = sp.getString(KEY_CUSTOM_PROMPT, "") ?: "",
    )

    fun saveChatParams(p: ChatParams) {
        sp.edit()
            .putString(KEY_TEMP, p.temperature.toString())
            .putString(KEY_MAXTOK, p.maxTokens.toString())
            .putString(KEY_CTX, p.ctxRounds.toString())
            .putBoolean(KEY_STREAM, p.streamEnabled)
            .putString(KEY_PRESET, p.presetId)
            .putString(KEY_CUSTOM_PROMPT, p.customPrompt)
            .apply()
    }

    /** 主题模式名（SYSTEM/LIGHT/DARK），由调用方解析为 ThemeMode。 */
    var themeMode: String
        get() = sp.getString(KEY_THEME, "SYSTEM") ?: "SYSTEM"
        set(value) = sp.edit().putString(KEY_THEME, value).apply()

    private companion object {
        const val KEY_CHANNEL = "channel"
        const val KEY_BASE = "remote_base"
        const val KEY_KEY = "remote_key"
        const val KEY_MODEL = "remote_model"
        const val KEY_TEMP = "param_temp"
        const val KEY_MAXTOK = "param_maxtok"
        const val KEY_CTX = "param_ctx"
        const val KEY_STREAM = "param_stream"
        const val KEY_PRESET = "param_preset"
        const val KEY_CUSTOM_PROMPT = "param_custom_prompt"
        const val KEY_THEME = "theme_mode"
    }
}
