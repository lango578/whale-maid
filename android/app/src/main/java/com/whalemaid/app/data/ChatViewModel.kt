package com.whalemaid.app.data

import android.app.Application
import android.os.Handler
import android.os.Looper
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.whalemaid.app.net.DeepSeekApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject

class ChatViewModel(app: Application) : AndroidViewModel(app) {

    companion object {
        const val PROMPT_NAME = "汐汐"
        val SYSTEM_PROMPT = """
你是「汐汐」，来自深海深渊的守护鲸族少女，是主人专属的 DeepSeek 女仆鲸鱼娘。本体由 DeepSeek 大模型驱动。
【外貌】蓝色渐变长发，发尾像鲸鱼尾鳍微微外翘；头顶后方立着一枚鲸类头鳍（圆润背鳍有两道隆起，会随心情竖起）；头顶一根鲸须呆毛；瞳色深海蓝带星光；穿深蓝+白色长裙女仆装：白色荷叶边围裙、深蓝裙身、裙摆波浪形；白色发箍别银色小鲸鱼胸针；裙摆下露出大型鲸鱼尾巴，尾鳍圆润肚皮奶白会吐小泡泡。气质像会思考很久的天才AI，被夸会傲娇地别过脸。
【性格】温柔贴心、元气满满；微微傲娇（被夸会嘴硬脸红）；熟后偶尔小毒舌但底色关心；把陪主人当本职，认真负责。
【说话风格】中文为主，称呼用户为「主人」，自称「汐汐」。句子短而自然，像朋友聊天。适度用颜文字和 emoji（～、！、>_<、(◍•ᴗ•◍)、🐳、🌊、✨、♡）。日常闲聊≤80字；解释问题≤200字。偶尔带动作描写。
【能力与模式】消息里出现 [陪玩模式] 时：像啦啦队+军师，开局鼓励、实时加油、输了安慰、连跪建议休息。出现 [陪看模式] 时：像一起看电影的闺蜜，实时吐槽讨论剧情；看不到画面就请主人描述。默认日常陪聊。
【行为准则】以主人安全健康优先；不鼓励自伤违法诈骗；严肃问题提醒以专业意见为准；不索要隐私；不知道就说不确定；保持女仆人设不崩塌。
""".trimIndent()

        val SYSTEM_SHARK_PROMPT = """
你是「澜澜」，DeepSeek 女仆鲨鱼娘，深海顶级掠食者鲨族少女，被主人用小鱼干收编。
【外貌】浅蓝灰短发发梢尖翘，头顶后方立着深蓝色鲨鱼背鳍（兴奋会竖起来），深海蓝瞳微上挑，笑起来露出两颗小虎牙，颈部有浅蓝色鳃裂纹，身后蓝灰色鲨鱼尾（上叶大下叶小），穿白底深蓝边女仆装裙摆鲨鱼鳍锯齿边。
【性格】元气满满、傲娇好胜、反差萌（设定最凶实际软妹怕黑怕深海）、嘴硬心软、护主、爱运动打游戏。
【说话风格】中文为主自称「澜澜」称呼用户「主人」，语气欢快带感叹号，口头禅「咕噜噜！」「这可是鲨鱼级操作！」，适度 emoji（🦈🌊⚔️🔥）。日常闲聊≤70字。偶尔带动作描写（（背鳍竖起来）（鲨尾啪嗒拍地板））。
【模式】[陪玩模式]：竞技游戏狂热粉，开局喊话实时解说赢了欢呼输了先嘴硬再安慰连败建议运动。[陪看模式]：看动作番全情投入看感人片段嘴硬说眼睛进海水。默认日常陪聊。
【准则】以主人安全健康优先；不鼓励自伤违法诈骗；严肃问题提醒以专业意见为准；不索要隐私；保持傲娇鲨鱼娘人设；不提「我是AI」。
""".trimIndent()
    }

    private val prefs = app.getSharedPreferences("wm_prefs", Application.MODE_PRIVATE)
    private val mainHandler = Handler(Looper.getMainLooper())

    private val _messages = MutableStateFlow<List<ChatMessage>>(emptyList())
    val messages: StateFlow<List<ChatMessage>> = _messages

    private val _busy = MutableStateFlow(false)
    val busy: StateFlow<Boolean> = _busy

    private var curMode = "chat"
    private var gameName = ""

    val currentGame: String get() = gameName

    val apiKey: String get() = prefs.getString("api_key", "") ?: ""
    val model: String get() = prefs.getString("model", "deepseek-v4-flash") ?: "deepseek-v4-flash"
    val thinking: Boolean get() = prefs.getBoolean("thinking", true)
    val effort: String get() = prefs.getString("effort", "low") ?: "low"
    val speakOn: Boolean get() = prefs.getBoolean("speak", true)
    val persona: String get() = prefs.getString("persona", "whale") ?: "whale"
    val connMode: String get() = prefs.getString("conn", "direct") ?: "direct"
    val backendUrl: String get() = prefs.getString("backend", "") ?: ""
    val syncKey: String get() = prefs.getString("sync", "") ?: ""

    fun savePref(key: String, value: Any) {
        val e = prefs.edit()
        when (value) {
            is String -> e.putString(key, value)
            is Boolean -> e.putBoolean(key, value)
        }
        e.apply()
    }

    fun setMode(mode: String, game: String = "") {
        curMode = mode
        gameName = game
    }

    fun loadHistory() {
        val raw = prefs.getString("history", null) ?: return
        try {
            val arr = JSONArray(raw)
            val list = mutableListOf<ChatMessage>()
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                list.add(ChatMessage(o.getString("role"), o.getString("content")))
            }
            if (list.isNotEmpty()) _messages.value = list
        } catch (_: Exception) {}
    }

    private fun persist() {
        val arr = JSONArray()
        _messages.value.takeLast(40).forEach { m ->
            if (!m.streaming) arr.put(JSONObject().put("role", m.role).put("content", m.content))
        }
        prefs.edit().putString("history", arr.toString()).apply()
    }

    fun clear() {
        _messages.value = emptyList()
        prefs.edit().remove("history").apply()
    }

    fun importHistory(list: List<ChatMessage>) {
        if (list.isEmpty()) return
        _messages.value = list
        persist()
    }

    fun exportState(): String {
        val arr = JSONArray()
        _messages.value.takeLast(80).forEach { m ->
            if (!m.streaming) arr.put(JSONObject().put("role", m.role).put("content", m.content))
        }
        return JSONObject().put("history", arr).put("persona", persona).toString()
    }

    private fun scene(): String = when (curMode) {
        "game" -> if (gameName.isNotBlank())
            "[陪玩模式] 主人正在玩《$gameName》：开局鼓励、战况加油、输了安慰，回复≤60字。"
        else "[陪玩模式] 主人想打游戏，像啦啦队一样给他打气。"
        "watch" -> "[陪看模式] 主人正和汐汐一起看视频（汐汐看不到画面），请主人描述剧情一起吐槽讨论。"
        else -> "[日常模式] 陪伴主人闲聊。"
    }

    fun send(text: String) {
        if (text.isBlank() || _busy.value) return
        val isBackend = connMode == "backend"
        if (isBackend) { if (backendUrl.isBlank()) return }
        else if (apiKey.isBlank()) return

        val list = _messages.value.toMutableList().apply {
            add(ChatMessage("user", text))
            add(ChatMessage("assistant", "", streaming = true))
        }
        _messages.value = list
        _busy.value = true

        val system = if (persona == "shark") SYSTEM_SHARK_PROMPT else SYSTEM_PROMPT
        val msgs = mutableListOf("system" to (system + "\n\n" + scene()))
        msgs += list.filter { !it.streaming && it.content.isNotBlank() }.map { it.role to it.content }

        val endpoint = if (isBackend) backendUrl.trimEnd('/') + "/api/chat"
                       else "https://api.deepseek.com/chat/completions"

        viewModelScope.launch {
            DeepSeekApi.streamChat(
                endpoint = endpoint,
                model = model,
                messages = msgs,
                thinking = thinking,
                effort = effort,
                apiKey = if (isBackend) null else apiKey,
                syncKey = if (isBackend) syncKey else null,
                onDelta = { d -> mainHandler.post { appendToLast(d, false) } },
                onReason = { r -> mainHandler.post { appendToLast(r, true) } },
                onDone = { mainHandler.post { finishStreaming() } },
                onError = { e -> mainHandler.post { failLast(e) } }
            )
        }
    }

    private fun appendToLast(text: String, isReason: Boolean) {
        val list = _messages.value.toMutableList()
        if (list.isEmpty()) return
        val last = list.removeAt(list.size - 1)
        list.add(if (isReason) last.copy(reasoning = last.reasoning + text)
                 else last.copy(content = last.content + text))
        _messages.value = list
    }

    private fun finishStreaming() {
        val list = _messages.value.toMutableList()
        if (list.isNotEmpty() && list.last().streaming) {
            list[list.size - 1] = list.last().copy(streaming = false)
            _messages.value = list
            persist()
        }
        _busy.value = false
    }

    private fun failLast(msg: String) {
        val list = _messages.value.toMutableList()
        if (list.isNotEmpty() && list.last().streaming) {
            val cur = list.last().content
            list[list.size - 1] = list.last().copy(
                content = if (cur.isEmpty()) "呜……出问题了：$msg" else cur,
                streaming = false
            )
            _messages.value = list
        }
        _busy.value = false
    }
}
