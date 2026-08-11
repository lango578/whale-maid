package com.whalemaid.app.data

/** 单条聊天消息 */
data class ChatMessage(
    val role: String,          // "user" | "assistant"
    val content: String,
    val reasoning: String = "",    // 汐汐的内心小剧场（思考内容）
    val streaming: Boolean = false // 是否正在流式生成
)
