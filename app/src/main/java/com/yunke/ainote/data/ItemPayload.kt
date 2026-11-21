package com.yunke.ainote.data

import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

/**
 * 通用形态子项。不同内容形态复用同一结构：
 * - LIST/SHOPPING：text=条目，extra=品牌/价格，done=勾选
 * - COUNTER：text=标签，extra=数值文本
 * - LINKLIST：text=标题，extra=URL
 * - TIMELINE：text=备注，extra=日期 小时:分钟
 * - SNIPPETS：extra=语言
 */
data class ItemPayload(
    val id: String = UUID.randomUUID().toString(),
    val text: String,
    val extra: String? = null,
    val done: Boolean = false,
    val order: Int = 0,
)

fun List<ItemPayload>.toItemsJson(): String {
    val arr = JSONArray()
    forEachIndexed { i, it ->
        arr.put(
            JSONObject()
                .put("id", it.id)
                .put("text", it.text)
                .put("extra", it.extra)
                .put("done", it.done)
                .put("order", if (it.order == 0) i else it.order),
        )
    }
    return arr.toString()
}

fun String?.toItems(): List<ItemPayload> {
    if (this.isNullOrBlank()) return emptyList()
    return runCatching {
        val arr = JSONArray(this)
        (0 until arr.length()).map { idx ->
            val o = arr.getJSONObject(idx)
            ItemPayload(
                id = o.optString("id", UUID.randomUUID().toString()),
                text = o.optString("text", ""),
                extra = if (o.isNull("extra")) null else o.optString("extra"),
                done = o.optBoolean("done", false),
                order = o.optInt("order", idx),
            )
        }
    }.getOrDefault(emptyList())
}
