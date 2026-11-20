package tw.cib.reader

import android.Manifest
import android.graphics.Bitmap
import android.os.Bundle
import android.util.Base64
import android.view.SurfaceView
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import io.agora.rtc2.ChannelMediaOptions
import io.agora.rtc2.Constants
import io.agora.rtc2.IRtcEngineEventHandler
import io.agora.rtc2.RtcEngine
import io.agora.rtc2.RtcEngineConfig
import io.agora.rtc2.video.VideoCanvas
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.util.concurrent.atomic.AtomicBoolean

class CallActivity : ComponentActivity() {
    private var engine: RtcEngine? = null
    private lateinit var localView: SurfaceView
    private lateinit var remoteView: SurfaceView
    private val live = mutableStateOf(false)
    private val cameraReady = mutableStateOf(false)
    private val cameraOn = mutableStateOf(true)
    private val ending = AtomicBoolean(false)
    private var joined = false
    private var citizenArmed = false
    private var pendingJoin = false

    private val permission = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { granted ->
        val staff = intent.getBooleanExtra("staff", false)
        if (!granted.values.all { it }) {
            cameraReady.value = false
            if (staff) {
                Toast.makeText(this, "需要攝影機與麥克風才能筆錄", Toast.LENGTH_LONG).show()
                finish()
            } else {
                Toast.makeText(this, "鏡頭未開啟，無法進入筆錄", Toast.LENGTH_LONG).show()
            }
            return@registerForActivityResult
        }
        if (pendingJoin) startEngine() else startPreview()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE)
        localView = SurfaceView(this).apply { setZOrderMediaOverlay(true) }
        remoteView = SurfaceView(this)
        val staff = intent.getBooleanExtra("staff", false)
        val caseId = intent.getStringExtra("caseId").orEmpty()
        setContent {
            InterviewRoom(
                staff = staff,
                caseId = caseId,
                localView = localView,
                remoteView = remoteView,
                live = live.value,
                cameraReady = cameraReady.value,
                cameraOn = cameraOn.value,
                onOpenCamera = { openCitizenCamera() },
                onBegin = { pendingJoin = true; askPermissions() },
                onJoin = { pendingJoin = true; if (cameraReady.value) startEngine() else askPermissions() },
                onStaffCamera = { enabled -> setStaffCamera(enabled) },
                onEnd = { endInterview() }
            )
        }
    }

    private fun askPermissions() {
        permission.launch(arrayOf(Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO))
    }

    private fun openCitizenCamera() {
        pendingJoin = false
        if (cameraReady.value) return
        askPermissions()
    }

    private fun ensureEngine() {
        if (engine != null) return
        val appId = intent.getStringExtra("appId").orEmpty()
        if (appId.isBlank()) return
        val config = RtcEngineConfig()
        config.mContext = applicationContext
        config.mAppId = appId
        config.mEventHandler = object : IRtcEngineEventHandler() {
            override fun onUserJoined(uid: Int, elapsed: Int) {
                runOnUiThread { engine?.setupRemoteVideo(VideoCanvas(remoteView, VideoCanvas.RENDER_MODE_FIT, uid)) }
            }

            override fun onLocalVideoStateChanged(source: Constants.VideoSourceType?, state: Int, error: Int) {
                val staff = intent.getBooleanExtra("staff", false)
                if (staff || ending.get()) return
                if (state == Constants.LOCAL_VIDEO_STREAM_STATE_CAPTURING || state == Constants.LOCAL_VIDEO_STREAM_STATE_ENCODING) {
                    cameraReady.value = true
                    cameraOn.value = true
                    if (joined) citizenArmed = true
                }
                if (joined && citizenArmed && (state == Constants.LOCAL_VIDEO_STREAM_STATE_STOPPED || state == Constants.LOCAL_VIDEO_STREAM_STATE_FAILED)) {
                    runOnUiThread {
                        Toast.makeText(this@CallActivity, "鏡頭已關閉，已離開筆錄", Toast.LENGTH_LONG).show()
                        endInterview()
                    }
                }
            }
        }
        engine = RtcEngine.create(config)
        engine?.enableVideo()
        engine?.enableAudio()
        engine?.setupLocalVideo(VideoCanvas(localView, VideoCanvas.RENDER_MODE_HIDDEN, 0))
    }

    private fun startPreview() {
        ensureEngine()
        engine?.startPreview()
        cameraReady.value = engine != null
        cameraOn.value = true
    }

    private fun setStaffCamera(enabled: Boolean) {
        cameraOn.value = enabled
        engine?.enableLocalVideo(enabled)
        engine?.muteLocalVideoStream(!enabled)
        if (enabled) engine?.startPreview() else engine?.stopPreview()
    }

    private fun startEngine() {
        val appId = intent.getStringExtra("appId").orEmpty()
        val token = intent.getStringExtra("token").orEmpty()
        val channel = intent.getStringExtra("channel").orEmpty()
        val account = intent.getStringExtra("account").orEmpty()
        val caseId = intent.getStringExtra("caseId").orEmpty()
        if (appId.isBlank() || channel.isBlank()) {
            Toast.makeText(this, "視訊資料不完整", Toast.LENGTH_LONG).show()
            finish()
            return
        }
        if (joined) return
        ensureEngine()
        engine?.startPreview()
        val options = ChannelMediaOptions()
        options.clientRoleType = Constants.CLIENT_ROLE_BROADCASTER
        options.channelProfile = Constants.CHANNEL_PROFILE_COMMUNICATION
        options.publishCameraTrack = true
        options.publishMicrophoneTrack = true
        options.autoSubscribeAudio = true
        options.autoSubscribeVideo = true
        engine?.joinChannelWithUserAccount(token, channel, account, options)
        joined = true
        cameraOn.value = true
        if (caseId.isNotBlank()) {
            Thread {
                val marked = runCatching {
                    ServiceApi.client.post("/api/cases/$caseId/statement", JSONObject().put("active", true))
                }.isSuccess
                if (marked) live.value = true
            }.start()
        }
    }

    private fun endInterview() {
        if (!ending.compareAndSet(false, true)) return
        val caseId = intent.getStringExtra("caseId").orEmpty()
        Thread {
            if (caseId.isNotBlank()) {
                runCatching { ServiceApi.client.post("/api/cases/$caseId/statement", JSONObject().put("active", false)) }
            }
            runOnUiThread {
                engine?.leaveChannel()
                finish()
            }
        }.start()
    }

    override fun onDestroy() {
        engine?.leaveChannel()
        RtcEngine.destroy()
        engine = null
        super.onDestroy()
    }
}

private val RoomNavy = Color(0xFF0C243F)
private val RoomPaper = Color(0xFFF4F6F8)
private val RoomRed = Color(0xFFD42121)

@Composable
private fun InterviewRoom(
    staff: Boolean,
    caseId: String,
    localView: SurfaceView,
    remoteView: SurfaceView,
    live: Boolean,
    cameraReady: Boolean,
    cameraOn: Boolean,
    onOpenCamera: () -> Unit,
    onBegin: () -> Unit,
    onJoin: () -> Unit,
    onStaffCamera: (Boolean) -> Unit,
    onEnd: () -> Unit
) {
    var consented by remember { mutableStateOf(staff) }
    var shrunk by remember { mutableStateOf(false) }
    var confirmEnd by remember { mutableStateOf(false) }
    var dossier by remember { mutableStateOf<JSONObject?>(null) }
    val scope = rememberCoroutineScope()
    LaunchedEffect(staff) { if (staff) onBegin() }
    BackHandler {
        if (staff && consented && !shrunk) shrunk = true
    }
    LaunchedEffect(live) {
        if (!live) return@LaunchedEffect
        while (true) {
            delay(4000)
            val idle = withContext(Dispatchers.IO) {
                runCatching {
                    val row = if (staff) {
                        ServiceApi.client.get("/api/cases").getJSONArray("cases").objects().find { it.optString("id") == caseId }
                    } else {
                        ServiceApi.client.get("/api/me").optJSONObject("case")
                    }
                    row?.optString("interviewStatus") == "idle"
                }.getOrDefault(false)
            }
            if (idle) {
                Toast.makeText(localView.context, "筆錄已結束", Toast.LENGTH_LONG).show()
                onEnd()
                return@LaunchedEffect
            }
        }
    }
    LaunchedEffect(shrunk) {
        if (!shrunk || dossier != null) return@LaunchedEffect
        dossier = withContext(Dispatchers.IO) {
            runCatching {
                if (staff) {
                    ServiceApi.client.get("/api/cases").getJSONArray("cases").objects().find { it.optString("id") == caseId }
                } else {
                    ServiceApi.client.get("/api/me").optJSONObject("case")
                }
            }.getOrNull()
        }
    }
    if (!consented) {
        ConsentGate(localView, cameraReady, onOpenCamera) { consented = true; onJoin() }
        return
    }
    Column(Modifier.fillMaxSize().background(RoomPaper).statusBarsPadding().navigationBarsPadding()) {
        Text(
            "線上筆錄中",
            modifier = Modifier.fillMaxWidth().background(RoomRed).padding(vertical = 8.dp),
            color = Color.White,
            fontWeight = FontWeight.Bold,
            fontSize = 14.sp,
            letterSpacing = 2.sp
        )
        Box(
            Modifier
                .fillMaxWidth()
                .then(if (shrunk) Modifier.height(230.dp) else Modifier.weight(1f))
                .background(RoomNavy)
        ) {
            AndroidView(factory = { detach(remoteView) }, modifier = Modifier.fillMaxSize())
            AndroidView(
                factory = { detach(localView) },
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(12.dp)
                    .size(if (shrunk) 92.dp else 138.dp, if (shrunk) 122.dp else 184.dp)
                    .clip(RoundedCornerShape(14.dp))
            )
        }
        if (shrunk) {
            Column(
                Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text("案件公文", color = RoomNavy, fontWeight = FontWeight.Bold, fontSize = 20.sp)
                DocumentBlock("案件", dossier?.optString("documentCase").orEmpty().ifBlank { dossier?.optString("caseTitle").orEmpty() })
                DocumentBlock("公文", dossier?.optString("documentOfficial").orEmpty())
                DocumentBlock("內容", dossier?.optString("documentContent").orEmpty().ifBlank { dossier?.optString("caseSummary").orEmpty() })
            }
        }
        Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = { shrunk = !shrunk },
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF153656))
                ) { Text(if (shrunk) "放大筆錄" else "看案件公文") }
                Button(
                    onClick = { confirmEnd = true },
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(containerColor = RoomRed)
                ) { Text("結束筆錄") }
            }
            Button(
                onClick = {
                    if (staff) {
                        val next = !cameraOn
                        onStaffCamera(next)
                        if (!next) shrunk = true
                    } else {
                        onEnd()
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF5C6B7A))
            ) { Text(if (staff && cameraOn) "關閉鏡頭" else if (staff) "開啟鏡頭" else "關閉鏡頭並離開") }
        }
    }
    if (confirmEnd) {
        AlertDialog(
            onDismissRequest = { confirmEnd = false },
            title = { Text("結束筆錄") },
            text = {
                Text(if (staff) "結束筆錄會關閉鏡頭與麥克風，並回到案件公文。" else "結束筆錄會關閉鏡頭。下次必須重新閱讀說明並簽名。")
            },
            confirmButton = {
                TextButton(onClick = {
                    confirmEnd = false
                    scope.launch { onEnd() }
                }) { Text("結束") }
            },
            dismissButton = { TextButton(onClick = { confirmEnd = false }) { Text("取消") } }
        )
    }
}

@Composable
private fun DocumentBlock(label: String, value: String) {
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(Color.White).padding(12.dp)) {
        Text(label, color = Color(0xFF5C6B7A), fontSize = 12.sp, fontWeight = FontWeight.Bold)
        Text(value.ifBlank { "未填寫" }, color = Color(0xFF1C2B3A), lineHeight = 22.sp, modifier = Modifier.padding(top = 4.dp))
    }
}

private fun detach(view: SurfaceView): SurfaceView {
    (view.parent as? android.view.ViewGroup)?.removeView(view)
    return view
}

@Composable
private fun ConsentGate(localView: SurfaceView, cameraReady: Boolean, onOpenCamera: () -> Unit, onEnter: () -> Unit) {
    LaunchedEffect(Unit) { onOpenCamera() }
    val strokes = remember { mutableStateListOf<List<Offset>>() }
    var canvasWidth by remember { mutableStateOf(1f) }
    var canvasHeight by remember { mutableStateOf(1f) }
    var agreed by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()
    val activity = androidx.compose.ui.platform.LocalContext.current as CallActivity
    val signed = strokes.any { it.size > 1 }
    Column(
        Modifier.fillMaxSize().background(RoomPaper).statusBarsPadding().navigationBarsPadding().verticalScroll(rememberScrollState()).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("線上筆錄同意書", color = RoomNavy, fontWeight = FontWeight.Bold, fontSize = 24.sp)
        Box(Modifier.fillMaxWidth().height(180.dp).clip(RoundedCornerShape(12.dp)).background(RoomNavy)) {
            AndroidView(factory = { detach(localView) }, modifier = Modifier.fillMaxSize())
        }
        Text(
            if (cameraReady) "鏡頭已開啟。請閱讀同意書、勾選並簽名。" else "正在開啟鏡頭。鏡頭沒有開啟之前，不能進入筆錄。",
            color = if (cameraReady) Color(0xFF146447) else RoomRed,
            lineHeight = 22.sp
        )
        if (!cameraReady) {
            Button(onClick = onOpenCamera, colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF153656))) { Text("重新開啟鏡頭") }
        }
        Text(CONSENT, color = Color(0xFF1C2B3A), lineHeight = 24.sp)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Checkbox(checked = agreed, onCheckedChange = { agreed = it })
            Text("我已閱讀並同意線上筆錄說明", fontWeight = FontWeight.Bold, color = RoomNavy)
        }
        Text("保證筆錄是如實回答", color = Color(0xFF9B2424), fontWeight = FontWeight.Bold)
        Canvas(
            Modifier
                .fillMaxWidth()
                .height(180.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(Color.White)
                .border(1.dp, Color(0xFF99BBBB), RoundedCornerShape(8.dp))
                .pointerInput(Unit) {
                    detectDragGestures(
                        onDragStart = { strokes.add(listOf(it)) },
                        onDrag = { change, _ ->
                            if (strokes.isNotEmpty()) strokes[strokes.lastIndex] = strokes.last() + change.position
                        }
                    )
                }
        ) {
            canvasWidth = size.width
            canvasHeight = size.height
            val path = Path()
            strokes.forEach { stroke ->
                if (stroke.isEmpty()) return@forEach
                path.moveTo(stroke.first().x, stroke.first().y)
                stroke.drop(1).forEach { path.lineTo(it.x, it.y) }
            }
            drawPath(path, Color(0xFF16375B), style = Stroke(width = 6f, cap = StrokeCap.Round))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
                onClick = { strokes.clear() },
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFE8EEF7), contentColor = RoomNavy)
            ) { Text("清除") }
            Button(
                onClick = {
                    if (!agreed || !signed || !cameraReady || busy) return@Button
                    busy = true
                    error = ""
                    val image = signatureDataUrl(strokes.toList(), canvasWidth, canvasHeight)
                    val caseId = activity.intent.getStringExtra("caseId").orEmpty()
                    scope.launch {
                        error = runCatching {
                            withContext(Dispatchers.IO) {
                                ServiceApi.client.post(
                                    "/api/cases/$caseId/messages",
                                    JSONObject().put("body", "民眾已閱讀並同意線上筆錄說明，宣誓本次所言均屬事實，並完成簽名。")
                                )
                                runCatching {
                                    ServiceApi.client.post(
                                        "/api/cases/$caseId/files",
                                        JSONObject().put("fileName", "interview-signature.png").put("dataUrl", image).put("kind", "upload")
                                    )
                                }
                            }
                            onEnter()
                            ""
                        }.getOrElse { it.message ?: "無法儲存簽名" }
                        busy = false
                    }
                },
                enabled = agreed && signed && cameraReady && !busy,
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0071BC))
            ) { Text(if (busy) "儲存中" else "同意並進入筆錄") }
        }
        if (error.isNotBlank()) Text(error, color = RoomRed, lineHeight = 22.sp)
    }
}

private fun signatureDataUrl(strokes: List<List<Offset>>, width: Float, height: Float): String {
    val bitmap = Bitmap.createBitmap(800, 300, Bitmap.Config.ARGB_8888)
    val canvas = android.graphics.Canvas(bitmap)
    canvas.drawColor(android.graphics.Color.WHITE)
    val paint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
        color = android.graphics.Color.parseColor("#16375B")
        style = android.graphics.Paint.Style.STROKE
        strokeWidth = 5f
        strokeCap = android.graphics.Paint.Cap.ROUND
    }
    val sx = 800f / width.coerceAtLeast(1f)
    val sy = 300f / height.coerceAtLeast(1f)
    strokes.forEach { stroke ->
        if (stroke.size < 2) return@forEach
        val path = android.graphics.Path()
        path.moveTo(stroke.first().x * sx, stroke.first().y * sy)
        stroke.drop(1).forEach { path.lineTo(it.x * sx, it.y * sy) }
        canvas.drawPath(path, paint)
    }
    val bytes = ByteArrayOutputStream()
    bitmap.compress(Bitmap.CompressFormat.PNG, 100, bytes)
    return "data:image/png;base64," + Base64.encodeToString(bytes.toByteArray(), Base64.NO_WRAP)
}

private const val CONSENT = """本次以視訊方式製作筆錄，過程會使用攝影機與麥克風。請本人親自回答，不要由他人代為陳述。

本案屬於偵查中案件，偵查內容不公開。請勿截圖、錄影，也不要將筆錄內容告訴無關的人。

本人宣誓：本次筆錄所言均屬事實，並無虛偽、隱匿或誤導。

筆錄時鏡頭必須保持開啟。關閉鏡頭即離開筆錄室，要再做必須重新閱讀本說明並簽名。您與服務人員都可以按「結束筆錄」。緊急危難請立即撥打 110。

勾選「我已閱讀並同意線上筆錄說明」並簽名，表示您已閱讀以上內容，同意進入線上筆錄。"""
