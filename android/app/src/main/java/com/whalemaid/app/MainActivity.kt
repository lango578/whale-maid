package com.whalemaid.app

import android.content.Intent
import android.os.Bundle
import android.speech.RecognizerIntent
import android.speech.tts.TextToSpeech
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import com.whalemaid.app.data.ChatMessage
import com.whalemaid.app.data.ChatViewModel
import kotlinx.coroutines.delay
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.random.Random

private val OceanDark = darkColorScheme(
    primary = Color(0xFF2BB9C8),
    onPrimary = Color(0xFF062033),
    secondary = Color(0xFF64E3EF),
    onSecondary = Color(0xFF062033),
    background = Color(0xFF0B1D33),
    onBackground = Color(0xFFE8F4FB),
    surface = Color(0xFF132A47),
    onSurface = Color(0xFFE8F4FB),
    surfaceVariant = Color(0xFF1B3050),
    onSurfaceVariant = Color(0xFF9DB8CC),
    error = Color(0xFFB0526A)
)

class MainActivity : ComponentActivity() {

    private var tts: TextToSpeech? = null
    private var ttsReady = false
    private lateinit var voiceConsumer: (String) -> Unit

    private val voiceLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { r ->
        if (r.resultCode == RESULT_OK) {
            r.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()?.let { s ->
                if (::voiceConsumer.isInitialized) voiceConsumer(s)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        tts = TextToSpeech(this) { status ->
            ttsReady = status == TextToSpeech.SUCCESS
            tts?.language = java.util.Locale.CHINA
        }
        setContent { WhaleMaidApp() }
    }

    private fun speak(text: String) {
        if (!ttsReady || text.isBlank()) return
        tts?.speak(text.take(200), TextToSpeech.QUEUE_FLUSH, null, "wm")
    }

    private fun launchVoice() {
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "zh-CN")
            putExtra(RecognizerIntent.EXTRA_PROMPT, "和汐汐说点什么…")
        }
        try { voiceLauncher.launch(intent) }
        catch (e: Exception) { toast("此设备不支持语音识别") }
    }

    private fun toast(msg: String) = Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()

    @Composable
    private fun WhaleMaidApp() {
        val vm: ChatViewModel = viewModel()
        val messages by vm.messages.collectAsState()
        val busy by vm.busy.collectAsState()

        LaunchedEffect(Unit) { vm.loadHistory() }

        var input by remember { mutableStateOf("") }
        var mood by remember { mutableStateOf("idle") }
        var speaking by remember { mutableStateOf(false) }
        var shouldSpeak by remember { mutableStateOf(false) }
        var showSettings by remember { mutableStateOf(false) }
        var showGame by remember { mutableStateOf(false) }
        var mode by remember { mutableStateOf("chat") }
        var gameName by remember { mutableStateOf("") }
        var persona by remember { mutableStateOf(vm.persona) }

        DisposableEffect(Unit) {
            voiceConsumer = { s -> input = if (input.isBlank()) s else "$input $s" }
            onDispose {}
        }

        LaunchedEffect(mood) {
            if (mood != "idle") { delay(4000); mood = "idle" }
        }

        LaunchedEffect(messages) {
            val last = messages.lastOrNull() ?: return@LaunchedEffect
            if (shouldSpeak && !last.streaming && last.role == "assistant" && last.content.isNotEmpty()) {
                shouldSpeak = false
                speaking = false
                if (vm.speakOn) speak(last.content)
            } else if (!last.streaming) {
                speaking = false
            }
        }

        fun sendUser(text: String) {
            if (text.isBlank() || busy) return
            if (vm.apiKey.isBlank() && vm.connMode != "backend") { showSettings = true; toast("主人～先去设置填 API Key 嘛！"); return }
            if (vm.connMode == "backend" && vm.backendUrl.isBlank()) { showSettings = true; toast("后台模式请先在设置里填后端地址和同步口令"); return }
            vm.setMode(mode, gameName.ifBlank { vm.currentGame })
            shouldSpeak = true
            speaking = true
            vm.send(text)
        }

        MaterialTheme(colorScheme = OceanDark) {
            Surface(modifier = Modifier.fillMaxSize()) {
                Column(modifier = Modifier.fillMaxSize().imePadding()) {
                    TopBar(persona = persona,
                        onTogglePersona = {
                            val p = if (persona == "whale") "shark" else "whale"
                            persona = p
                            vm.savePref("persona", p)
                            mood = "idle"
                            toast(if (p == "shark") "🦈 已切换到鲨鱼娘·澜澜！" else "🐳 已切换到鲸鱼娘·汐汐～")
                        },
                        onSettings = { showSettings = true })
                    WhaleAvatar(mood = mood, speaking = speaking, persona = persona) { zone ->
                        mood = interactMood(zone)
                        toast(interactLine(zone))
                    }
                    ModeChips(mode = mode) { m ->
                        mode = m
                        if (m == "game") showGame = true
                    }
                    MessageList(messages)
                    InputBar(input, { input = it }, busy,
                        onSend = { sendUser(input.trim()); input = "" },
                        onVoice = { launchVoice() })
                }
            }
        }

        if (showSettings) SettingsDialog(vm,
            onSetPersona = { p -> persona = p },
            onPull = { pullCloud(vm) },
            onPush = { pushCloud(vm) },
            onDismiss = { showSettings = false })
        if (showGame) GameDialog(vm) { showGame = false }
    }

    private fun pullCloud(vm: ChatViewModel) {
        if (vm.backendUrl.isBlank()) { toast("先在设置里填后端地址哦"); return }
        Thread {
            try {
                val b = okhttp3.Request.Builder().url(vm.backendUrl.trimEnd('/') + "/api/state")
                if (vm.syncKey.isNotBlank()) b.addHeader("X-Sync-Key", vm.syncKey)
                okhttp3.OkHttpClient().newCall(b.get().build()).execute().use { resp ->
                    if (!resp.isSuccessful) throw Exception("HTTP ${resp.code}")
                    val json = org.json.JSONObject(resp.body?.string() ?: "{}")
                    val arr = json.optJSONArray("history")
                    val list = mutableListOf<ChatMessage>()
                    if (arr != null) for (i in 0 until arr.length()) {
                        val o = arr.getJSONObject(i)
                        list.add(ChatMessage(o.getString("role"), o.getString("content")))
                    }
                    val p = json.optString("persona", "")
                    runOnUiThread {
                        vm.importHistory(list)
                        if (p == "whale" || p == "shark") { vm.savePref("persona", p) }
                        toast("✅ 已从云端拉取")
                    }
                }
            } catch (e: Exception) { runOnUiThread { toast("拉取失败：" + e.message) } }
        }.start()
    }

    private fun pushCloud(vm: ChatViewModel) {
        if (vm.backendUrl.isBlank()) { toast("先在设置里填后端地址哦"); return }
        Thread {
            try {
                val body = vm.exportState().toRequestBody("application/json; charset=utf-8".toMediaType())
                val b = okhttp3.Request.Builder()
                    .url(vm.backendUrl.trimEnd('/') + "/api/state")
                    .post(body)
                if (vm.syncKey.isNotBlank()) b.addHeader("X-Sync-Key", vm.syncKey)
                okhttp3.OkHttpClient().newCall(b.build()).execute().use { resp ->
                    if (!resp.isSuccessful) throw Exception("HTTP ${resp.code}")
                    runOnUiThread { toast("✅ 已同步到云端") }
                }
            } catch (e: Exception) { runOnUiThread { toast("同步失败：" + e.message) } }
        }.start()
    }

    @Composable
    private fun TopBar(persona: String, onTogglePersona: () -> Unit, onSettings: () -> Unit) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically) {
            Text(if (persona == "shark") "🦈" else "🐳", fontSize = 22.sp)
            Spacer(Modifier.width(8.dp))
            Text(if (persona == "shark") "鲨鱼娘·澜澜" else "鲸鱼娘·汐汐", fontSize = 18.sp,
                fontWeight = FontWeight.Bold, color = Color(0xFF8FE7F2))
            Spacer(Modifier.weight(1f))
            Text(if (persona == "shark") "🐳" else "🦈", fontSize = 18.sp,
                modifier = Modifier.padding(8.dp).pointerInput(Unit) { detectTapGestures { onTogglePersona() } })
            Text("⚙️", fontSize = 20.sp, modifier = Modifier.padding(6.dp).pointerInput(Unit) { detectTapGestures { onSettings() } })
        }
    }



    @Composable
    private fun ModeChips(mode: String, onMode: (String) -> Unit) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp), horizontalArrangement = Arrangement.Center) {
            listOf("chat" to "💬 日常", "game" to "🎮 陪玩", "watch" to "🎬 陪看").forEach { (m, label) ->
                Surface(
                    shape = RoundedCornerShape(50),
                    color = if (mode == m) Color(0xFF2BB9C8) else Color(0xFF16293F),
                    modifier = Modifier.padding(horizontal = 4.dp).pointerInput(Unit) { detectTapGestures { onMode(m) } }
                ) {
                    Text(label, color = if (mode == m) Color(0xFF062033) else Color(0xFFCFEAF3),
                        fontSize = 12.sp, fontWeight = FontWeight.Medium,
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 7.dp))
                }
            }
        }
    }

    @Composable
    private fun MessageList(messages: List<ChatMessage>) {
        val listState = rememberLazyListState()
        LaunchedEffect(messages.size) {
            if (messages.isNotEmpty()) listState.animateScrollToItem(messages.size - 1)
        }
        LazyColumn(state = listState, modifier = Modifier.fillMaxWidth().weight(1f).padding(horizontal = 10.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(messages.size) { i -> MessageBubble(messages[i]) }
        }
    }

    @Composable
    private fun MessageBubble(m: ChatMessage) {
        val isUser = m.role == "user"
        Column(Modifier.fillMaxWidth(), horizontalAlignment = if (isUser) Alignment.End else Alignment.Start) {
            Surface(
                shape = RoundedCornerShape(15.dp, 15.dp, if (isUser) 4.dp else 15.dp, if (isUser) 15.dp else 4.dp),
                color = if (isUser) Color(0xFF3A66A8) else Color(0xFF16293F)
            ) {
                Column(Modifier.padding(horizontal = 12.dp, vertical = 9.dp)) {
                    val show = if (m.content.isEmpty()) (if (m.streaming) "…" else "（无回复）") else m.content
                    Text(show, color = Color(0xFFE8F4FB), fontSize = 14.sp, lineHeight = 21.sp)
                    if (m.reasoning.isNotBlank()) {
                        Text("💭 ${m.reasoning}", color = Color(0xFF8FC9DD), fontSize = 11.sp,
                            lineHeight = 16.sp, modifier = Modifier.padding(top = 5.dp))
                    }
                }
            }
        }
    }

    @Composable
    private fun InputBar(input: String, onInput: (String) -> Unit, busy: Boolean,
                         onSend: () -> Unit, onVoice: () -> Unit) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = input, onValueChange = onInput,
                modifier = Modifier.weight(1f),
                placeholder = { Text(if (busy) "汐汐正在想…" else "和汐汐说点什么…") },
                shape = RoundedCornerShape(22.dp),
                maxLines = 3,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = Color(0xFF0A1A2E),
                    unfocusedContainerColor = Color(0xFF0A1A2E),
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent
                )
            )
            Text("🎤", fontSize = 20.sp, modifier = Modifier.padding(6.dp).pointerInput(Unit) { detectTapGestures { onVoice() } })
            Text("➤", fontSize = 20.sp, color = Color(0xFF2BB9C8), modifier = Modifier.padding(8.dp).pointerInput(Unit) { detectTapGestures { onSend() } })
        }
    }

    @Composable
    private fun SettingsDialog(vm: ChatViewModel, onSetPersona: (String) -> Unit,
                               onPull: () -> Unit, onPush: () -> Unit, onDismiss: () -> Unit) {
        var key by remember { mutableStateOf(vm.apiKey) }
        var model by remember { mutableStateOf(vm.model) }
        var think by remember { mutableStateOf(vm.thinking) }
        var effort by remember { mutableStateOf(vm.effort) }
        var pers by remember { mutableStateOf(vm.persona) }
        var conn by remember { mutableStateOf(vm.connMode) }
        var backend by remember { mutableStateOf(vm.backendUrl) }
        var synck by remember { mutableStateOf(vm.syncKey) }
        AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text("⚙️ 设置") },
            text = {
                Column {
                    Text("人格（点击切换）", color = Color(0xFF9DB8CC), fontSize = 12.sp)
                    Row(Modifier.padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Surface(shape = RoundedCornerShape(50),
                            color = if (pers == "whale") Color(0xFF2BB9C8) else Color(0xFF16293F),
                            modifier = Modifier.pointerInput(Unit) { detectTapGestures { pers = "whale" } }) {
                            Text("🐳 汐汐", color = Color(0xFFE8F4FB), fontSize = 13.sp,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp))
                        }
                        Surface(shape = RoundedCornerShape(50),
                            color = if (pers == "shark") Color(0xFF2BB9C8) else Color(0xFF16293F),
                            modifier = Modifier.pointerInput(Unit) { detectTapGestures { pers = "shark" } }) {
                            Text("🦈 澜澜", color = Color(0xFFE8F4FB), fontSize = 13.sp,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp))
                        }
                    }
                    Text("连接方式", color = Color(0xFF9DB8CC), fontSize = 12.sp, modifier = Modifier.padding(top = 12.dp))
                    Row(Modifier.padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Surface(shape = RoundedCornerShape(50),
                            color = if (conn == "direct") Color(0xFF2BB9C8) else Color(0xFF16293F),
                            modifier = Modifier.pointerInput(Unit) { detectTapGestures { conn = "direct" } }) {
                            Text("直连（前端填 Key）", color = Color(0xFFE8F4FB), fontSize = 12.sp,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp))
                        }
                        Surface(shape = RoundedCornerShape(50),
                            color = if (conn == "backend") Color(0xFF2BB9C8) else Color(0xFF16293F),
                            modifier = Modifier.pointerInput(Unit) { detectTapGestures { conn = "backend" } }) {
                            Text("后台模式", color = Color(0xFFE8F4FB), fontSize = 12.sp,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp))
                        }
                    }
                    Text("API Key（直连模式，platform.deepseek.com 获取）", color = Color(0xFF9DB8CC), fontSize = 12.sp, modifier = Modifier.padding(top = 12.dp))
                    OutlinedTextField(value = key, onValueChange = { key = it },
                        modifier = Modifier.fillMaxWidth().padding(top = 4.dp), singleLine = true,
                        placeholder = { Text("sk-...") })
                    Text("后端地址（后台模式，如 http://192.168.1.100:8787）", color = Color(0xFF9DB8CC), fontSize = 12.sp, modifier = Modifier.padding(top = 10.dp))
                    OutlinedTextField(value = backend, onValueChange = { backend = it },
                        modifier = Modifier.fillMaxWidth().padding(top = 4.dp), singleLine = true)
                    Text("同步口令（后台模式，后端 config.json 的 syncKey）", color = Color(0xFF9DB8CC), fontSize = 12.sp, modifier = Modifier.padding(top = 10.dp))
                    OutlinedTextField(value = synck, onValueChange = { synck = it },
                        modifier = Modifier.fillMaxWidth().padding(top = 4.dp), singleLine = true)
                    Text("模型", color = Color(0xFF9DB8CC), fontSize = 12.sp, modifier = Modifier.padding(top = 10.dp))
                    OutlinedTextField(value = model, onValueChange = { model = it },
                        modifier = Modifier.fillMaxWidth().padding(top = 4.dp), singleLine = true,
                        placeholder = { Text("deepseek-v4-flash") })
                    Row(Modifier.padding(top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text("思考模式（显示内心小剧场）", fontSize = 13.sp)
                        Spacer(Modifier.weight(1f))
                        Text(if (think) "✔" else "✘", fontSize = 18.sp,
                            color = if (think) Color(0xFF2BB9C8) else Color(0xFF9DB8CC),
                            modifier = Modifier.pointerInput(Unit) { detectTapGestures { think = !think } })
                    }
                    Text("思考强度 low / high / max", color = Color(0xFF9DB8CC), fontSize = 12.sp, modifier = Modifier.padding(top = 10.dp))
                    OutlinedTextField(value = effort, onValueChange = { effort = it },
                        modifier = Modifier.fillMaxWidth().padding(top = 4.dp), singleLine = true,
                        placeholder = { Text("low") })
                    Row(Modifier.padding(top = 14.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text("⬇️ 拉取", fontSize = 14.sp, modifier = Modifier.pointerInput(Unit) { detectTapGestures { onPull() } })
                        Text("⬆️ 同步", fontSize = 14.sp, modifier = Modifier.pointerInput(Unit) { detectTapGestures { onPush() } })
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    vm.savePref("api_key", key.trim())
                    vm.savePref("model", model.trim().ifEmpty { "deepseek-v4-flash" })
                    vm.savePref("thinking", think)
                    vm.savePref("effort", effort.trim().ifEmpty { "low" })
                    vm.savePref("persona", pers)
                    vm.savePref("conn", conn)
                    vm.savePref("backend", backend.trim().trimEnd('/'))
                    vm.savePref("sync", synck.trim())
                    onSetPersona(pers)
                    onDismiss()
                }) { Text("保存") }
            },
            dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } }
        )
    }

    @Composable
    private fun GameDialog(vm: ChatViewModel, onDismiss: () -> Unit) {
        var name by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text("🎮 汐汐陪你打游戏") },
            text = {
                Column {
                    Text("告诉汐汐你在玩什么游戏，她会变成你的啦啦队+军师：", color = Color(0xFF9DB8CC), fontSize = 13.sp)
                    OutlinedTextField(value = name, onValueChange = { name = it },
                        modifier = Modifier.fillMaxWidth().padding(top = 6.dp), singleLine = true,
                        placeholder = { Text("例如：王者荣耀 / 原神 / 蛋仔派对") })
                    Row(Modifier.padding(top = 14.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text("🏆 赢了", fontSize = 15.sp, modifier = Modifier.pointerInput(Unit) {
                            detectTapGestures {
                                if (vm.apiKey.isNotBlank()) vm.send("（赢了）刚打完一局赢啦！快跟我庆祝！")
                                onDismiss()
                            }
                        })
                        Text("😢 输了", fontSize = 15.sp, modifier = Modifier.pointerInput(Unit) {
                            detectTapGestures {
                                if (vm.apiKey.isNotBlank()) vm.send("（输了）刚打完一局输了…安慰安慰我")
                                onDismiss()
                            }
                        })
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val n = name.trim()
                    if (n.isNotEmpty()) {
                        vm.setMode("game", n)
                        if (vm.apiKey.isNotBlank()) vm.send("（开始陪玩）我们开始玩《$n》啦，汐汐给我点开局鼓励！")
                    }
                    onDismiss()
                }) { Text("开始陪玩") }
            },
            dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } }
        )
    }

    private fun interactMood(zone: String): String = when (zone) {
        "head" -> "happy"; "cheek" -> "blush"; "tail" -> "shy"; else -> "happy"
    }

    private fun interactLine(zone: String): String = when (zone) {
        "head" -> listOf("呜哇！别揉头啦！>_<", "那、那就让你摸一下下啦～", "（尾巴开心地晃）呜噜噜～").random()
        "cheek" -> listOf("呀！戳脸颊犯规啦！", "(◍•ᴗ•◍) 嘿嘿", "呜～会戳出小酒窝的！").random()
        "tail" -> listOf("呜哇！！尾巴是禁区！>////<", "（疯狂甩尾）").random()
        else -> listOf("嘿嘿～（蹭蹭）", "主人今天心情很好嘛～").random()
    }

    private fun hitZone(x: Float, y: Float): String {
        if (sqrt((x - 150f) * (x - 150f) + (y - 182f) * (y - 182f)) < 74f) return "head"
        if (sqrt((x - 110f) * (x - 110f) + (y - 204f) * (y - 204f)) < 28f) return "cheek"
        if (sqrt((x - 190f) * (x - 190f) + (y - 204f) * (y - 204f)) < 28f) return "cheek"
        if (y > 372f) return "tail"
        return "body"
    }

    @Composable
    private fun WhaleAvatar(mood: String, speaking: Boolean, persona: String, onHit: (String) -> Unit) {
        val infinite = rememberInfiniteTransition(label = "wm")
        val t by infinite.animateFloat(0f, 100f,
            animationSpec = infiniteRepeatable(tween(100000, easing = LinearEasing), RepeatMode.Restart),
            label = "t")
        var blink by remember { mutableStateOf(false) }
        LaunchedEffect(Unit) {
            while (true) {
                delay(Random.nextLong(2200, 5200))
                blink = true
                delay(140)
                blink = false
            }
        }
        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            Canvas(modifier = Modifier
                .fillMaxWidth()
                .height(230.dp)
                .pointerInput(Unit) {
                    detectTapGestures { offset ->
                        val x = offset.x / size.width * 300f
                        val y = offset.y / size.height * 460f
                        onHit(hitZone(x, y))
                    }
                }
            ) {
                drawWhale(t.value, blink, mood, speaking, persona)
            }
            Text(if (persona == "shark") "点我：摸背鳍 / 戳脸 / 摸尾巴 会咬人哦～" else "点我：摸头 / 戳脸 / 挠尾巴 会害羞哦～",
                color = Color(0xFF9DB8CC), fontSize = 11.sp)
        }
    }

    // ================= 原生 Canvas 手绘 =================
    private fun DrawScope.drawWhale(t: Float, blink: Boolean, mood: String, speaking: Boolean, persona: String) {
        if (persona == "shark") { drawSharkAll(t, blink, mood, speaking); return }
        val s = size.width / 300f
        val bob = sin(t * 2f) * 3f
        val squash = if (mood == "happy") 0.97f else 1f
        withTransform({ scale(s, s) }) {
            withTransform({
                translate(0f, bob)
                scale(2f - squash, squash, pivot = Offset(150f, 240f))
            }) {
                drawBubbles(t)
                drawOval(Color(0x4D000000), topLeft = Offset(88f, 444f), size = Size(124f, 18f))
                drawTail(t, mood)
                drawDress()
                drawArms()
                drawHead(t, blink, mood, speaking)
            }
        }
    }

    private fun DrawScope.drawSharkAll(t: Float, blink: Boolean, mood: String, speaking: Boolean) {
        val s = size.width / 300f
        val bob = sin(t * 2f) * 3f
        withTransform({ scale(s, s) }) {
            withTransform({ translate(0f, bob) }) {
                drawBubbles(t)
                drawOval(Color(0x4D000000), topLeft = Offset(88f, 444f), size = Size(124f, 18f))
                // 鲨尾
                rotate(sin(t * 1.4f) * 0.06f, Offset(150f, 400f)) {
                    val stem = Path().apply { moveTo(134f,330f); quadraticTo(136f,370f,140f,404f); quadraticTo(150f,412f,160f,404f); quadraticTo(164f,370f,166f,330f); close() }
                    drawPath(stem, Color(0xFF7D98B4))
                    val up = Path().apply { moveTo(140f,400f); quadraticTo(104f,420f,92f,448f); quadraticTo(84f,462f,106f,458f); quadraticTo(128f,446f,150f,440f); close() }
                    drawPath(up, Color(0xFF7D98B4))
                    val low = Path().apply { moveTo(160f,400f); quadraticTo(184f,410f,194f,428f); quadraticTo(200f,438f,184f,438f); quadraticTo(168f,430f,154f,424f); close() }
                    drawPath(low, Color(0xFF7D98B4))
                    val bel = Path().apply { moveTo(145f,332f); quadraticTo(147f,370f,150f,404f); quadraticTo(153f,370f,155f,332f); close() }
                    drawPath(bel, Color(0xFFEEF4FA))
                }
                // 白裙+蓝边
                val dress = Path().apply { moveTo(112f,298f); quadraticTo(94f,330f,118f,350f); lineTo(182f,350f); quadraticTo(206f,330f,188f,298f); close() }
                drawPath(dress, Color(0xFFF6F9FD))
                val bodice = Path().apply { moveTo(134f,258f); quadraticTo(150f,268f,166f,258f); quadraticTo(162f,286f,150f,292f); quadraticTo(138f,286f,134f,258f); close() }
                drawPath(bodice, Color(0xFF274B6D))
                drawLine(Color(0xFF274B6D), Offset(112f,346f), Offset(188f,346f), strokeWidth = 4f)
                val bow = Path().apply { moveTo(150f,296f); quadraticTo(135f,290f,128f,300f); quadraticTo(142f,300f,148f,306f); quadraticTo(158f,300f,172f,300f); quadraticTo(165f,290f,150f,296f); close() }
                drawPath(bow, Color(0xFF2A7DB8))
                // 手臂
                val armL = Path().apply { moveTo(116f,268f); quadraticTo(104f,284f,108f,302f) }
                drawPath(armL, Color(0xFFFBEEF1), style = Stroke(9f))
                val armR = Path().apply { moveTo(184f,268f); quadraticTo(196f,284f,192f,302f) }
                drawPath(armR, Color(0xFFFBEEF1), style = Stroke(9f))
                drawCircle(Color(0xFFEEF7FB), radius = 11f, center = Offset(116f,266f))
                drawCircle(Color(0xFFEEF7FB), radius = 11f, center = Offset(184f,266f))
                drawSharkHead(t, blink, mood, speaking)
            }
        }
    }
    private fun DrawScope.drawSharkHead(t: Float, blink: Boolean, mood: String, speaking: Boolean) {
        val fins = Path().apply {
            moveTo(150f,120f); quadraticTo(128f,96f,118f,74f); quadraticTo(138f,92f,142f,96f)
            quadraticTo(146f,82f,150f,92f); quadraticTo(154f,82f,158f,96f); quadraticTo(162f,92f,182f,74f); quadraticTo(172f,96f,150f,120f); close()
        }
        drawPath(fins, Color(0xFF7C9DBD))
        val hair = Path().apply {
            moveTo(150f,120f); quadraticTo(100f,122f,92f,158f); quadraticTo(84f,200f,80f,240f); quadraticTo(78f,262f,92f,264f)
            quadraticTo(100f,230f,98f,180f); quadraticTo(120f,236f,150f,236f); quadraticTo(180f,236f,202f,180f); quadraticTo(200f,230f,208f,264f)
            quadraticTo(222f,262f,220f,240f); quadraticTo(216f,200f,208f,158f); quadraticTo(200f,122f,150f,120f); close()
        }
        drawPath(hair, Color(0xFF7C9DBD))
        drawRect(Color(0xFFFBEEF1), topLeft = Offset(141f,224f), size = Size(18f,30f))
        drawCircle(Color(0xFFFBEEF1), radius = 57f, center = Offset(150f,182f))
        val gillL = Path().apply { moveTo(142f,232f); quadraticTo(136f,240f,140f,248f) }
        drawPath(gillL, Color(0x88FFFFFF), style = Stroke(1.6f))
        val gillR = Path().apply { moveTo(158f,232f); quadraticTo(164f,240f,160f,248f) }
        drawPath(gillR, Color(0x88FFFFFF), style = Stroke(1.6f))
        val bangs = Path().apply {
            moveTo(96f,162f); quadraticTo(100f,120f,150f,116f); quadraticTo(200f,120f,204f,162f)
            lineTo(196f,152f); lineTo(188f,150f); lineTo(182f,140f); lineTo(174f,146f); lineTo(166f,132f); lineTo(158f,142f)
            lineTo(150f,128f); lineTo(142f,142f); lineTo(134f,132f); lineTo(126f,146f); lineTo(118f,140f); lineTo(112f,150f); lineTo(104f,152f); close()
        }
        drawPath(bangs, Color(0xFF4A6C93))
        drawArc(Color.White, 180f, 55f, false, Offset(94f,126f), Size(112f,112f), style = Stroke(12f))
        if (mood == "happy") {
            val he1 = Path().apply { moveTo(122f,188f); quadraticTo(130f,196f,138f,188f) }
            val he2 = Path().apply { moveTo(162f,188f); quadraticTo(170f,196f,178f,188f) }
            drawPath(he1, Color(0xFF0E3350), style = Stroke(2.6f))
            drawPath(he2, Color(0xFF0E3350), style = Stroke(2.6f))
        } else if (blink) {
            drawLine(Color(0xFF0E3350), Offset(122f,190f), Offset(138f,190f), strokeWidth = 2.2f)
            drawLine(Color(0xFF0E3350), Offset(162f,190f), Offset(178f,190f), strokeWidth = 2.2f)
        } else {
            drawSharkEyeK(130f,190f)
            drawSharkEyeK(170f,190f)
        }
        val blushA = if (mood == "blush" || mood == "happy") 0.5f else 0.22f
        val blushC = Color(0xFFFF7890).copy(alpha = blushA)
        drawOval(blushC, topLeft = Offset(102f,200f), size = Size(16f,9f))
        drawOval(blushC, topLeft = Offset(182f,200f), size = Size(16f,9f))
        if (speaking) {
            val m = 3f + kotlin.math.abs(sin(t * 10f)) * 3f
            drawOval(Color(0xFFA3445E), topLeft = Offset(144.5f,220f - m), size = Size(11f, m * 2f))
        } else {
            drawArc(Color(0xFFB0526A), 35f, 110f, false, Offset(144f,208f), Size(12f,12f), style = Stroke(2.2f))
            val f1 = Path().apply { moveTo(144f,212f); lineTo(142f,219f); lineTo(147f,215f); close() }
            val f2 = Path().apply { moveTo(156f,212f); lineTo(158f,219f); lineTo(153f,215f); close() }
            drawPath(f1, Color.White)
            drawPath(f2, Color.White)
        }
    }

    private fun DrawScope.drawSharkEyeK(x: Float, y: Float) {
        drawCircle(Color(0xFF16405F), radius = 9.6f, center = Offset(x, y))
        drawCircle(Color(0xFF3EC6D6), radius = 7f, center = Offset(x, y))
        drawCircle(Color(0xFF0B4A70), radius = 4.8f, center = Offset(x, y))
        drawCircle(Color.White, radius = 2.6f, center = Offset(x - 3f, y - 3f))
        drawCircle(Color.White, radius = 1.3f, center = Offset(x + 3f, y + 2.5f))
    }

    private fun DrawScope.drawBubbles(t: Float) {

        for (i in 0 until 14) {
            val phase = i * 2.399f
            val yy = 460f - ((t * 24f + phase * 17f) % 420f)
            val xx = 95f + ((sin(phase + t * 0.8f) + 1f) * 0.5f * 110f)
            val r = 2f + (phase % 4f)
            drawCircle(Color(0x999FE9F5), radius = r, center = Offset(xx, yy), style = Stroke(1.2f))
        }
    }

    private fun DrawScope.drawTail(t: Float, mood: String) {
        val sway = sin(t * 1.4f) * 0.06f + (if (mood == "shy") 0.1f else 0f)
        rotate(sway, Offset(150f, 400f)) {
            val stem = Path().apply {
                moveTo(130f, 328f)
                quadraticTo(134f, 370f, 138f, 402f)
                quadraticTo(150f, 412f, 162f, 402f)
                quadraticTo(166f, 370f, 170f, 328f)
                close()
            }
            drawPath(stem, Color(0xFF2C7DA0))
            val lf = Path().apply {
                moveTo(138f, 400f)
                quadraticTo(110f, 426f, 88f, 444f)
                quadraticTo(74f, 456f, 98f, 462f)
                quadraticTo(122f, 458f, 150f, 440f)
                close()
            }
            drawPath(lf, Color(0xFF2C7DA0))
            val rf = Path().apply {
                moveTo(162f, 400f)
                quadraticTo(190f, 426f, 212f, 444f)
                quadraticTo(226f, 456f, 202f, 462f)
                quadraticTo(178f, 458f, 150f, 440f)
                close()
            }
            drawPath(rf, Color(0xFF2C7DA0))
            val bel = Path().apply {
                moveTo(144f, 330f)
                quadraticTo(146f, 370f, 150f, 404f)
                quadraticTo(154f, 370f, 156f, 330f)
                close()
            }
            drawPath(bel, Color(0xFFEAF7FD))
        }
    }

    private fun DrawScope.drawDress() {
        val dress = Path().apply {
            moveTo(114f, 300f)
            quadraticTo(96f, 330f, 120f, 350f)
            lineTo(180f, 350f)
            quadraticTo(204f, 330f, 186f, 300f)
            close()
        }
        drawPath(dress, Color(0xFF232F55))
        val apron = Path().apply {
            moveTo(132f, 278f)
            lineTo(168f, 278f)
            lineTo(178f, 352f)
            quadraticTo(150f, 360f, 122f, 352f)
            close()
        }
        drawPath(apron, Color(0xFFFBFDFF))
        val bow = Path().apply {
            moveTo(150f, 286f)
            quadraticTo(135f, 280f, 128f, 290f)
            quadraticTo(142f, 290f, 148f, 296f)
            quadraticTo(158f, 290f, 172f, 290f)
            quadraticTo(165f, 280f, 150f, 286f)
            close()
        }
        drawPath(bow, Color(0xFF2BB9C8))
        drawCircle(Color(0xFF2BB9C8), radius = 3.4f, center = Offset(150f, 290f))
    }

    private fun DrawScope.drawArms() {
        val armL = Path().apply { moveTo(116f, 268f); quadraticTo(104f, 284f, 108f, 302f) }
        drawPath(armL, Color(0xFFFFE9DE), style = Stroke(9f))
        val armR = Path().apply { moveTo(184f, 268f); quadraticTo(196f, 284f, 192f, 302f) }
        drawPath(armR, Color(0xFFFFE9DE), style = Stroke(9f))
        drawCircle(Color(0xFFEEF7FB), radius = 11f, center = Offset(116f, 266f))
        drawCircle(Color(0xFFEEF7FB), radius = 11f, center = Offset(184f, 266f))
        drawCircle(Color(0xFFFFE9DE), radius = 5.5f, center = Offset(107f, 304f))
        drawCircle(Color(0xFFFFE9DE), radius = 5.5f, center = Offset(193f, 304f))
    }

    private fun DrawScope.drawHead(t: Float, blink: Boolean, mood: String, speaking: Boolean) {
        val hair = Path().apply {
            moveTo(150f, 118f)
            quadraticTo(96f, 120f, 88f, 160f)
            quadraticTo(76f, 220f, 72f, 280f)
            quadraticTo(70f, 315f, 84f, 320f)
            quadraticTo(98f, 250f, 92f, 180f)
            quadraticTo(120f, 238f, 150f, 236f)
            quadraticTo(180f, 238f, 208f, 180f)
            quadraticTo(202f, 250f, 216f, 320f)
            quadraticTo(230f, 315f, 228f, 280f)
            quadraticTo(224f, 220f, 212f, 160f)
            quadraticTo(204f, 120f, 150f, 118f)
            close()
        }
        drawPath(hair, Color(0xFF1F6B94))
        drawRect(Color(0xFFFFE9DE), topLeft = Offset(141f, 224f), size = Size(18f, 30f))
        drawCircle(Color(0xFFFFE9DE), radius = 57f, center = Offset(150f, 182f))

        val bangs = Path().apply {
            moveTo(95f, 160f)
            quadraticTo(98f, 120f, 150f, 116f)
            quadraticTo(202f, 120f, 205f, 160f)
            quadraticTo(196f, 150f, 186f, 156f)
            quadraticTo(180f, 138f, 170f, 148f)
            quadraticTo(166f, 130f, 156f, 142f)
            quadraticTo(150f, 126f, 144f, 142f)
            quadraticTo(134f, 130f, 130f, 148f)
            quadraticTo(120f, 138f, 114f, 156f)
            quadraticTo(104f, 150f, 95f, 160f)
            close()
        }
        drawPath(bangs, Color(0xFF113A5C))
        drawArc(Color(0xFFFFFFFF), 180f, 55f, false, Offset(94f, 126f), Size(112f, 112f), style = Stroke(12f))
        val bow = Path().apply {
            moveTo(108f, 142f)
            quadraticTo(92f, 132f, 96f, 150f)
            quadraticTo(106f, 150f, 110f, 158f)
            quadraticTo(120f, 150f, 124f, 150f)
            quadraticTo(120f, 132f, 108f, 142f)
            close()
        }
        drawPath(bow, Color(0xFF2BB9C8))

        if (mood == "happy") {
            val he1 = Path().apply { moveTo(122f, 188f); quadraticTo(130f, 196f, 138f, 188f) }
            val he2 = Path().apply { moveTo(162f, 188f); quadraticTo(170f, 196f, 178f, 188f) }
            drawPath(he1, Color(0xFF0E3350), style = Stroke(2.6f))
            drawPath(he2, Color(0xFF0E3350), style = Stroke(2.6f))
        } else if (blink) {
            drawLine(Color(0xFF0E3350), Offset(122f, 190f), Offset(138f, 190f), strokeWidth = 2.2f)
            drawLine(Color(0xFF0E3350), Offset(162f, 190f), Offset(178f, 190f), strokeWidth = 2.2f)
        } else {
            drawEye(130f, 190f)
            drawEye(170f, 190f)
        }

        val blushA = if (mood == "blush" || mood == "happy") 0.5f else 0.24f
        val blushC = Color(0xFFFF7890).copy(alpha = blushA)
        drawOval(blushC, topLeft = Offset(101f, 199f), size = Size(18f, 10f))
        drawOval(blushC, topLeft = Offset(181f, 199f), size = Size(18f, 10f))

        if (speaking) {
            val m = 3f + kotlin.math.abs(sin(t * 10f)) * 3f
            drawOval(Color(0xFFA3445E), topLeft = Offset(144.5f, 220f - m), size = Size(11f, m * 2f))
        } else {
            drawArc(Color(0xFFB0526A), 35f, 110f, false, Offset(144f, 208f), Size(12f, 12f), style = Stroke(2.2f))
        }
    }

    private fun DrawScope.drawEye(x: Float, y: Float) {
        drawCircle(Color(0xFF16405F), radius = 10f, center = Offset(x, y))
        drawCircle(Color(0xFF3EC6D6), radius = 7.5f, center = Offset(x, y))
        drawCircle(Color(0xFF0B4A70), radius = 5f, center = Offset(x, y))
        drawCircle(Color.White, radius = 2.8f, center = Offset(x - 3f, y - 3f))
        drawCircle(Color.White, radius = 1.4f, center = Offset(x + 3f, y + 2.5f))
    }
}



