package com.yunke.ainote.settings

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

data class UsageRecord(
    val model: String,
    val time: Long,
    val promptTokens: Int,
    val completionTokens: Int,
)

data class UsageSnapshot(
    val totalPrompt: Int,
    val totalCompletion: Int,
    val recent: List<UsageRecord>,
) {
    val totalTokens: Int get() = totalPrompt + totalCompletion
}

/** 本地用量统计：累计 tokens + 最近请求记录（仅存本机）。 */
class UsageStore(context: Context) {

    private val sp = context.getSharedPreferences("ainote_usage", Context.MODE_PRIVATE)

    @Synchronized
    fun record(model: String, promptTokens: Int, completionTokens: Int) {
        val totalPrompt = sp.getInt(KEY_TOTAL_PROMPT, 0) + promptTokens
        val totalCompletion = sp.getInt(KEY_TOTAL_COMPLETION, 0) + completionTokens

        val recent = currentRecent().toMutableList()
        recent.add(0, UsageRecord(model, System.currentTimeMillis(), promptTokens, completionTokens))
        while (recent.size > MAX_RECENT) recent.removeAt(recent.size - 1)

        sp.edit()
            .putInt(KEY_TOTAL_PROMPT, totalPrompt)
            .putInt(KEY_TOTAL_COMPLETION, totalCompletion)
            .putString(KEY_RECENT, encode(recent))
            .apply()
    }

    fun snapshot(): UsageSnapshot = UsageSnapshot(
        totalPrompt = sp.getInt(KEY_TOTAL_PROMPT, 0),
        totalCompletion = sp.getInt(KEY_TOTAL_COMPLETION, 0),
        recent = currentRecent(),
    )

    @Synchronized
    fun clear() {
        sp.edit().clear().apply()
    }

    private fun currentRecent(): List<UsageRecord> {
        val raw = sp.getString(KEY_RECENT, null) ?: return emptyList()
        return runCatching {
            val arr = JSONArray(raw)
            (0 until arr.length()).map { i ->
                val o = arr.getJSONObject(i)
                UsageRecord(
                    model = o.optString("model", ""),
                    time = o.optLong("time", 0L),
                    promptTokens = o.optInt("prompt", 0),
                    completionTokens = o.optInt("completion", 0),
                )
            }
        }.getOrDefault(emptyList())
    }

    private fun encode(records: List<UsageRecord>): String {
        val arr = JSONArray()
        records.forEach { r ->
            arr.put(
                JSONObject()
                    .put("model", r.model)
                    .put("time", r.time)
                    .put("prompt", r.promptTokens)
                    .put("completion", r.completionTokens),
            )
        }
        return arr.toString()
    }

    private companion object {
        const val KEY_TOTAL_PROMPT = "total_prompt"
        const val KEY_TOTAL_COMPLETION = "total_completion"
        const val KEY_RECENT = "recent"
        const val MAX_RECENT = 20
    }
}
