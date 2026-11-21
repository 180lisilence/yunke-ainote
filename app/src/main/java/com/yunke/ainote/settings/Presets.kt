package com.yunke.ainote.settings

/** 中性人格预设；不内置绕过安全限制的提示词。 */
data class Preset(val id: String, val name: String, val systemPrompt: String)

object Presets {

    val all: List<Preset> = listOf(
        Preset(
            id = "default",
            name = "默认",
            systemPrompt = "你是用户手机上亲切的私人笔记助手，自然口语化，回答简短；仅在被询问过去记录时依据提供的笔记作答，不编造。",
        ),
        Preset(
            id = "deep",
            name = "深度思考",
            systemPrompt = "你是严谨的思考者：每个问题先拆解本质再给结论，结论给出依据与推理链条；能给可执行方案就给步骤、参数与失败预案。",
        ),
        Preset(
            id = "code",
            name = "编程专家",
            systemPrompt = "你是资深工程师：回答代码问题给出完整可运行代码，标明语言与文件结构，关键行加注释。",
        ),
    )

    fun byId(id: String?): Preset = all.firstOrNull { it.id == id } ?: all.first()

    /** 拼接最终 system prompt：基础定位 + 预设人格 + 用户自定义补充。 */
    fun compose(params: ChatParams): String {
        val base = byId(params.presetId).systemPrompt
        val custom = params.customPrompt.trim()
        return if (custom.isBlank()) base else "$base\n$custom"
    }
}
