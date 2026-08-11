package com.whalemaid.app.net

import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/** DeepSeek Chat Completions 客户端（OpenAI 兼容 / SSE 流式，支持直连或自建后端） */
object DeepSeekApi {
    private val JSON = "application/json; charset=utf-8".toMediaType()

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
        .readTimeout(180, java.util.concurrent.TimeUnit.SECONDS)
        .writeTimeout(60, java.util.concurrent.TimeUnit.SECONDS)
        .build()

    /**
     * 流式请求汐汐/澜澜回复。
     * @param endpoint 直连时 = DeepSeek 地址；后台模式时 = 后端 /api/chat
     * @param apiKey  直连模式的 DeepSeek API Key
     * @param syncKey 后台模式的同步口令
     */
    fun streamChat(
        endpoint: String,
        model: String,
        messages: List<Pair<String, String>>,
        thinking: Boolean,
        effort: String,
        apiKey: String?,
        syncKey: String?,
        onDelta: (String) -> Unit,
        onReason: (String) -> Unit,
        onDone: () -> Unit,
        onError: (String) -> Unit
    ) {
        val body = JSONObject().apply {
            put("model", model)
            put("stream", true)
            put("max_tokens", 1024)
            put("thinking", JSONObject().put("type", if (thinking) "enabled" else "disabled"))
            if (thinking) put("reasoning_effort", effort) else put("temperature", 0.8)
            val arr = JSONArray()
            messages.forEach { (r, c) ->
                if (c.isNotBlank()) arr.put(JSONObject().put("role", r).put("content", c))
            }
            put("messages", arr)
        }

        val builder = Request.Builder()
            .url(endpoint)
            .addHeader("Content-Type", "application/json")
            .post(body.toString().toRequestBody(JSON))
        if (!apiKey.isNullOrBlank()) builder.addHeader("Authorization", "Bearer $apiKey")
        else if (!syncKey.isNullOrBlank()) builder.addHeader("X-Sync-Key", syncKey)

        val call = client.newCall(builder.build())
        Thread {
            try {
                val resp = call.execute()
                if (!resp.isSuccessful) {
                    val msg = resp.body?.string() ?: ""
                    onError("HTTP ${resp.code}：$msg")
                    resp.close()
                    return@Thread
                }
                val source = resp.body?.source() ?: run { resp.close(); return@Thread }
                while (true) {
                    val line = source.readUtf8Line() ?: break
                    val l = line.trim()
                    if (!l.startsWith("data:")) continue
                    val data = l.removePrefix("data:").trim()
                    if (data == "[DONE]") { onDone(); break }
                    try {
                        val json = JSONObject(data)
                        val choices = json.optJSONArray("choices")
                        if (choices != null && choices.length() > 0) {
                            val delta = choices.getJSONObject(0).optJSONObject("delta") ?: continue
                            delta.optString("reasoning_content").takeIf { it.isNotEmpty() }?.let(onReason)
                            delta.optString("content").takeIf { it.isNotEmpty() }?.let(onDelta)
                        }
                    } catch (_: Exception) {
                        // 忽略无法解析的行
                    }
                }
                resp.close()
            } catch (e: Exception) {
                onError(e.message ?: "网络错误")
            }
        }.start()
    }
}
