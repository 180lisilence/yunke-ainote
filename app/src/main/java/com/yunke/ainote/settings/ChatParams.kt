package com.yunke.ainote.settings

/** 对话生成参数与人格预设选择。 */
data class ChatParams(
    val temperature: Float = 0.7f,
    val maxTokens: Int = 2048,
    val ctxRounds: Int = 4,
    val streamEnabled: Boolean = true,
    val presetId: String = "default",
    val customPrompt: String = "",
)
