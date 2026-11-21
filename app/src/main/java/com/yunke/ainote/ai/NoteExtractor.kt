package com.yunke.ainote.ai

import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/** AI 从对话中抽取出来的一条待记笔记。 */
data class ExtractedNote(
    val title: String,
    val content: String,
    val tags: String?,
    val people: String? = null,
    val places: String? = null,
    val mood: String? = null,
    val todo: String? = null,
    /** 换算后的事件日期 yyyy-MM-dd；空表示未指明。 */
    val eventDate: String? = null,
)

/**
 * 调用当前通道的模型，从用户一句话里判断是否存在要记成笔记的事项，
 * 按固定 JSON 契约抽取；不需要记或解析失败时返回 null（不阻塞对话）。
 */
object NoteExtractor {

    private const val INSTRUCTION_PREFIX =
        "你是私人笔记的记录助手。判断用户这句话里有没有需要记下来的事项" +
            "（事件、安排、待办、感想、人物、地点等）。\n" +
            "当前时间上下文：\n"

    private const val INSTRUCTION_SUFFIX =
        "\n抽取要求：\n" +
            "1. 把“今天/昨天/前天/明天/这周/上周”等相对时间换算成上面给出的具体日期，" +
            "填到 date 字段；事件时间不明确时 date 留空。\n" +
            "2. content 写成一句通顺、完整的记事白话，不要保留“日期是…内容是…”这种命令句式；" +
            "正文里不要再出现“今天/昨天”这类相对词，时间用换算后的日期。\n" +
            "3. title 用不超过 20 字概括这件事，不要把整句命令当标题。\n" +
            "只输出 JSON，不要解释：\n" +
            "- 不需要记：{\"save\":false}\n" +
            "- 需要记：{\"save\":true,\"title\":\"标题\",\"content\":\"通顺正文\"," +
            "\"date\":\"yyyy-MM-dd 或空\",\"tags\":\"标签1,标签2\"," +
            "\"people\":\"人物名,无则空\",\"places\":\"地点,无则空\"," +
            "\"mood\":\"情绪,无则空\",\"todo\":\"待办事项,无则空\"}\n" +
            "用户原话："

    /**
     * @param llm 一次补全调用：入参提示词，返回模型文本。
     * @param now 当前时间戳，便于换算相对日期；默认系统时间。
     */
    fun extract(
        userText: String,
        now: Long = System.currentTimeMillis(),
        llm: (String) -> String,
    ): ExtractedNote? {
        val resp = runCatching { llm(INSTRUCTION_PREFIX + timeContext(now) + INSTRUCTION_SUFFIX + userText) }
            .getOrNull() ?: return null
        val json = extractJson(resp) ?: return null
        return runCatching {
            val obj = JSONObject(json)
            if (!obj.optBoolean("save", false)) return null
            val title = obj.optString("title").trim()
            val content = obj.optString("content").trim()
            if (content.isEmpty()) return null
            val date = obj.optString("date").trim().ifBlank { null }
            ExtractedNote(
                title = title.ifBlank { content.take(20) },
                content = content,
                tags = obj.optString("tags").trim().ifBlank { null },
                people = obj.optString("people").trim().ifBlank { null },
                places = obj.optString("places").trim().ifBlank { null },
                mood = obj.optString("mood").trim().ifBlank { null },
                todo = obj.optString("todo").trim().ifBlank { null },
                eventDate = date,
            )
        }.getOrNull()
    }

    private fun timeContext(now: Long): String {
        val day = SimpleDateFormat("yyyy-MM-dd", Locale.CHINA)
        val week = SimpleDateFormat("EEEE", Locale.CHINA)

        fun offset(days: Int): String {
            val c = Calendar.getInstance().apply { timeInMillis = now; add(Calendar.DAY_OF_YEAR, days) }
            return day.format(c.time)
        }

        return buildString {
            appendLine("- 现在：${day.format(Date(now))} ${week.format(Date(now))}")
            appendLine("- 昨天：${offset(-1)}；前天：${offset(-2)}；明天：${offset(1)}")
        }
    }

    private fun extractJson(text: String): String? {
        val s = text.trim()
        val left = s.indexOf('{')
        val right = s.lastIndexOf('}')
        if (left < 0 || right <= left) return null
        return s.substring(left, right + 1)
    }
}
