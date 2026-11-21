package com.yunke.ainote.remote

import com.yunke.ainote.settings.RemoteConfig
import com.yunke.ainote.settings.UsageRecord
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

class RemoteException(message: String) : Exception(message)

/**
 * OpenAI 兼容云端对话客户端（非流式，整段返回）。
 * 兼容 DeepSeek / 其他 OpenAI 协议端点：POST {base}/chat/completions。
 */
class RemoteChatClient {

    fun chat(cfg: RemoteConfig, systemPrompt: String, userPrompt: String): String {
        val messages = JSONArray().apply {
            put(JSONObject().put("role", "system").put("content", systemPrompt))
            put(JSONObject().put("role", "user").put("content", userPrompt))
        }
        val body = JSONObject()
            .put("model", cfg.modelName)
            .put("messages", messages)
            .put("temperature", 0.7)
            .put("stream", false)

        val url = URL(joinUrl(cfg.baseUrl, "/chat/completions"))
        val conn = (url.openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = 15000
            readTimeout = 60000
            setRequestProperty("Content-Type", "application/json")
            setRequestProperty("Authorization", "Bearer ${cfg.apiKey}")
            setRequestProperty("Accept", "application/json")
            doOutput = true
        }

        conn.outputStream.use { os ->
            os.write(body.toString().toByteArray(Charsets.UTF_8))
        }

        val code = conn.responseCode
        val stream = if (code in 200..299) conn.inputStream else conn.errorStream
        val text = stream?.bufferedReader()?.use { it.readText() } ?: ""
        conn.disconnect()

        if (code !in 200..299) {
            val msg = runCatching {
                JSONObject(text).getJSONObject("error").getString("message")
            }.getOrNull() ?: "HTTP $code"
            throw RemoteException(msg)
        }

        val root = JSONObject(text)
        val choices = root.optJSONArray("choices")
            ?: throw RemoteException("响应缺少 choices 字段")
        val first = choices.getJSONObject(0)
        val message = first.optJSONObject("message")
        val content = message?.optString("content", "") ?: first.optString("text", "")
        return content.ifBlank { "（云端返回空内容）" }
    }

    /**
     * SSE 流式补全：逐段回调累积的正文与思维链，结束返回用量。
     * 兼容 DeepSeek / 其他 OpenAI 兼容端点；reasoning_content 为思维链字段。
     */
    fun chatStream(
        cfg: RemoteConfig,
        systemPrompt: String,
        userPrompt: String,
        temperature: Float,
        maxTokens: Int,
        onDelta: (content: String, reasoning: String) -> Unit,
    ): UsageRecord? {
        val messages = JSONArray().apply {
            put(JSONObject().put("role", "system").put("content", systemPrompt))
            put(JSONObject().put("role", "user").put("content", userPrompt))
        }
        val body = JSONObject()
            .put("model", cfg.modelName)
            .put("messages", messages)
            .put("temperature", temperature)
            .put("max_tokens", maxTokens)
            .put("stream", true)

        val url = URL(joinUrl(cfg.baseUrl, "/chat/completions"))
        val conn = (url.openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = 15000
            readTimeout = 60000
            setRequestProperty("Content-Type", "application/json")
            setRequestProperty("Authorization", "Bearer ${cfg.apiKey}")
            setRequestProperty("Accept", "text/event-stream")
            doOutput = true
        }

        conn.outputStream.use { os -> os.write(body.toString().toByteArray(Charsets.UTF_8)) }

        val code = conn.responseCode
        val stream = if (code in 200..299) conn.inputStream else conn.errorStream
        if (code !in 200..299) {
            val text = stream?.bufferedReader()?.use { it.readText() } ?: ""
            val msg = runCatching {
                JSONObject(text).getJSONObject("error").getString("message")
            }.getOrNull() ?: "HTTP $code"
            conn.disconnect()
            throw RemoteException(msg)
        }

        var content = ""
        var reasoning = ""
        var usagePrompt = 0
        var usageCompletion = 0
        var hasUsage = false

        stream?.bufferedReader()?.useLines { lines ->
            lines.forEach { line ->
                if (!line.startsWith("data:")) return@forEach
                val data = line.removePrefix("data:").trim()
                if (data.isBlank() || data == "[DONE]") return@forEach
                runCatching {
                    val root = JSONObject(data)
                    root.optJSONObject("usage")?.let { u ->
                        usagePrompt = u.optInt("prompt_tokens", usagePrompt)
                        usageCompletion = u.optInt("completion_tokens", usageCompletion)
                        hasUsage = true
                    }
                    val choices = root.optJSONArray("choices") ?: return@runCatching
                    if (choices.length() > 0) {
                        val first = choices.getJSONObject(0)
                        val delta = first.optJSONObject("delta")
                            ?: first.optJSONObject("message")
                        delta?.let { d ->
                            content += d.optString("content", "")
                            reasoning += d.optString("reasoning_content", "")
                        }
                        onDelta(content, reasoning)
                    }
                }
            }
        }
        conn.disconnect()

        return if (hasUsage) {
            UsageRecord(cfg.modelName, System.currentTimeMillis(), usagePrompt, usageCompletion)
        } else null
    }

    private fun joinUrl(base: String, path: String): String {
        val b = base.trim().removeSuffix("/")
        return if (b.endsWith(path)) b else b + path
    }
}
