package com.jarvis.assistant.ui

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.EaseOutCubic
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jarvis.assistant.chat.ChatLog
import com.jarvis.assistant.chat.ChatMessage
import com.jarvis.assistant.chat.Role
import com.jarvis.assistant.core.ServiceLocator
import com.jarvis.assistant.ui.hud.AnswerCard
import com.jarvis.assistant.ui.hud.CompassStrip
import com.jarvis.assistant.ui.hud.CornerBrackets
import com.jarvis.assistant.ui.hud.CoreWaveform
import com.jarvis.assistant.ui.hud.HexGridOverlay
import com.jarvis.assistant.ui.hud.PartialTranscript
import com.jarvis.assistant.ui.hud.QuestionCard
import com.jarvis.assistant.ui.hud.ScanlinesOverlay
import com.jarvis.assistant.ui.hud.SourceChip
import com.jarvis.assistant.ui.hud.ToolChip
import com.jarvis.assistant.ui.hud.Vignette
import kotlinx.coroutines.delay
import kotlin.math.cos
import kotlin.math.sin

/**
 * The one screen, two modes — now Stark HUD edition:
 *  - Full chat (MainActivity): Lab console — hex grid, glass panels, Stark brackets
 *  - Overlay (AssistantActivity): Helmet HUD — spherical reactor, compass, scanlines, subtitle cards
 */
@Composable
fun ChatScreen(overlay: Boolean = false) {
    val vm = remember { ServiceLocator.viewModel }
        ?: return Text("JARVIS is initializing…", modifier = Modifier.padding(24.dp))
    val ui by vm.state.collectAsState()
    val messages by ServiceLocator.chatLog.messages.collectAsState()

    if (overlay) {
        HelmetHUD(vm, ui, messages)
    } else {
        StarkLabConsole(vm, ui, messages)
    }
}

// ---------------------------------------------------------------------------
// Stark Lab Console — full chat (MainActivity)
// ---------------------------------------------------------------------------
@Composable
private fun StarkLabConsole(
    vm: com.jarvis.assistant.chat.ChatViewModel,
    ui: com.jarvis.assistant.chat.ChatViewModel.UiState,
    messages: List<ChatMessage>,
) {
    var input by remember { mutableStateOf("") }
    var modelMenuOpen by remember { mutableStateOf(false) }
    var showDownloader by remember { mutableStateOf(false) }
    var showFetchLog by remember { mutableStateOf(false) }
    val listState = rememberLazyListState()

    LaunchedEffect(messages.size, ui.partialReply, ui.partialTranscript) {
        if (messages.isNotEmpty()) listState.animateScrollToItem(messages.size - 1)
    }

    Box(modifier = Modifier.fillMaxSize().background(StarkDeepSpace)) {
        // lab hologram layers
        HexGridOverlay(opacity = 0.06f)
        ScanlinesOverlay(opacity = 0.035f)
        Vignette()
        CornerBrackets(color = StarkCyan.copy(alpha = 0.42f), bracketLen = 14f, stroke = 1.2f)

        Column(modifier = Modifier.fillMaxSize()) {
            // ---- top bar: reactor + title + model + fetch ----
            Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    ReactorDot(pulsing = ui.speaking || ui.listening || ui.generating)
                    Spacer(Modifier.size(10.dp))
                    Text(
                        "J.A.R.V.I.S",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = StarkCyan,
                        letterSpacing = 2.sp,
                        fontFamily = FontFamily.Monospace,
                    )
                    // small system badge
                    Box(
                        modifier = Modifier
                            .padding(start = 8.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(StarkCyan.copy(alpha = 0.12f))
                            .border(0.7.dp, StarkCyan.copy(alpha = 0.28f), RoundedCornerShape(4.dp))
                            .padding(horizontal = 6.dp, vertical = 2.dp),
                    ) {
                        Text(
                            if (ui.generating) "THINKING" else if (ui.listening) "LISTENING" else "ONLINE",
                            fontFamily = FontFamily.Monospace,
                            fontSize = 8.sp,
                            letterSpacing = 1.2.sp,
                            color = StarkCyan,
                        )
                    }
                    Spacer(Modifier.weight(1f))
                    TextButton(onClick = { showFetchLog = true }) {
                        Text("🛰", style = MaterialTheme.typography.labelMedium)
                    }
                    TextButton(onClick = { modelMenuOpen = true }) {
                        Text(
                            ui.modelName ?: "no model",
                            style = MaterialTheme.typography.labelMedium,
                            color = StarkDim,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 11.sp,
                        )
                    }
                    DropdownMenu(expanded = modelMenuOpen, onDismissRequest = { modelMenuOpen = false }) {
                        ServiceLocator.modelManager.list().forEach { model ->
                            DropdownMenuItem(
                                text = { Text(model.name, fontFamily = FontFamily.Monospace, fontSize = 12.sp) },
                                onClick = { vm.switchModel(model); modelMenuOpen = false },
                            )
                        }
                        if (ServiceLocator.modelManager.list().isEmpty()) {
                            DropdownMenuItem(
                                text = { Text("no model installed yet", fontSize = 12.sp) },
                                onClick = {},
                            )
                        }
                        DropdownMenuItem(
                            text = { Text("⬇ download models…", fontSize = 12.sp) },
                            onClick = { modelMenuOpen = false; showDownloader = true },
                        )
                    }
                }

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 10.dp)) {
                    FilterChip(
                        selected = ui.webEnabled,
                        onClick = { vm.toggleWeb() },
                        label = { Text(if (ui.webEnabled) "🌐 WEB LINK" else "✈ OFFLINE", fontFamily = FontFamily.Monospace, fontSize = 11.sp, letterSpacing = 0.6.sp) },
                    )
                    FilterChip(
                        selected = ui.handsFree,
                        onClick = { vm.toggleHandsFree() },
                        label = { Text("🎧 HANDS-FREE", fontFamily = FontFamily.Monospace, fontSize = 11.sp) },
                    )
                    WakeChip(vm, ui)
                }

                // telemetry line — MCU alt / rng style crud
                Text(
                    buildString {
                        append(ui.engineStatus)
                        append("  ·  STT:${ui.sttName.ifBlank { "SYS" }}")
                        append("  ·  TTS:${ui.ttsName.ifBlank { "SYS" }}")
                        append("  ·  RNG EL 97%")
                    },
                    fontFamily = FontFamily.Monospace,
                    fontSize = 9.5.sp,
                    letterSpacing = 0.7.sp,
                    color = StarkDim.copy(alpha = 0.9f),
                    modifier = Modifier.padding(top = 6.dp),
                )
                // tiny compass under telemetry for lab view
                Box(modifier = Modifier.padding(top = 6.dp).alpha(0.85f)) {
                    CompassStrip(animated = ui.listening || ui.generating || ui.speaking)
                }
            }

            // ---- first-run glass banner ----
            if (ServiceLocator.modelManager.list().isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(StarkPanel.copy(alpha = 0.86f))
                        .border(1.dp, StarkCyan.copy(alpha = 0.18f), RoundedCornerShape(12.dp))
                        .padding(14.dp),
                ) {
                    Column {
                        Text(
                            "At your service — though I must confess, sir, I lack a brain.",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = StarkIce,
                        )
                        Text(
                            "Download a model over Wi-Fi (resumable, in-app), or run scripts/get_models.sh. Voice packs from scripts/get_voice_models.sh.",
                            style = MaterialTheme.typography.bodySmall,
                            color = StarkDim,
                        )
                        TextButton(onClick = { showDownloader = true }) {
                            Text("⬇ download a model", color = StarkCyan)
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))
            }

            // ---- messages — Stark bubbles ----
            LazyColumn(
                state = listState,
                modifier = Modifier.weight(1f).fillMaxWidth().padding(horizontal = 12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                items(messages) { msg -> MessageBubble(msg) }
                if (ui.partialReply.isNotEmpty()) {
                    item {
                        MessageBubble(ChatMessage(Role.ASSISTANT, ui.partialReply), streaming = true)
                    }
                }
                ui.toolStatus?.let {
                    item {
                        ToolChip(text = it, done = false)
                    }
                }
                if (ui.listening) {
                    item {
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(start = 4.dp)) {
                            Box(
                                modifier = Modifier
                                    .size(7.dp)
                                    .clip(CircleShape)
                                    .background(StarkCyan),
                            )
                            Spacer(Modifier.size(8.dp))
                            Text(
                                if (ui.partialTranscript.isBlank()) "LISTENING…" else "“${ui.partialTranscript}”",
                                fontFamily = FontFamily.Monospace,
                                fontSize = 12.sp,
                                letterSpacing = 0.5.sp,
                                color = StarkCyanGlow,
                            )
                        }
                    }
                }
            }

            // ---- input — pill + Stark border ----
            Row(
                modifier = Modifier.fillMaxWidth().padding(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                OutlinedTextField(
                    value = input,
                    onValueChange = { input = it },
                    modifier = Modifier.weight(1f),
                    placeholder = { Text("Ask J.A.R.V.I.S…", color = StarkDim, fontFamily = FontFamily.Monospace, fontSize = 13.sp) },
                    shape = RoundedCornerShape(14.dp),
                    singleLine = false,
                    maxLines = 4,
                )
                Spacer(Modifier.size(8.dp))
                if (ui.generating) {
                    IconButton(onClick = { vm.stopGeneration() }) {
                        Icon(Icons.Filled.Stop, contentDescription = "stop", tint = StarkCyan)
                    }
                } else if (ui.listening) {
                    IconButton(onClick = { vm.stopListening() }) {
                        Icon(Icons.Filled.Stop, contentDescription = "stop listening", tint = StarkCyan)
                    }
                } else {
                    IconButton(onClick = { vm.startListening() }) {
                        Icon(Icons.Filled.Mic, contentDescription = "speak", tint = StarkCyan)
                    }
                }
                IconButton(
                    onClick = { if (input.isNotBlank()) { vm.send(input); input = "" } },
                    enabled = input.isNotBlank() && !ui.generating,
                ) {
                    Icon(
                        Icons.AutoMirrored.Filled.Send,
                        contentDescription = "send",
                        tint = if (input.isNotBlank()) StarkCyan else StarkDim.copy(alpha = 0.45f),
                    )
                }
            }

            if (showDownloader) {
                ModelDownloadDialog(onDismiss = { showDownloader = false })
            }
            if (showFetchLog) {
                FetchLogSheet(onDismiss = { showFetchLog = false })
            }
        }
    }
}

/** Every URL JARVIS has fetched — the visible half of the privacy contract. */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
private fun FetchLogSheet(onDismiss: () -> Unit) {
    val entries = ServiceLocator.web.accessSnapshot()
    val timeFmt = java.text.SimpleDateFormat("d MMM HH:mm", java.util.Locale.ENGLISH)

    androidx.compose.material3.ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.padding(horizontal = 20.dp)) {
            Text(
                "What left the phone",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = StarkIce,
            )
            Text(
                "Every URL JARVIS fetched this session (newest last). Wake word, speech and the LLM never touch the network.",
                style = MaterialTheme.typography.bodySmall,
                color = StarkDim,
            )
            Spacer(Modifier.height(12.dp))
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.height(320.dp),
            ) {
                if (entries.isEmpty()) {
                    item {
                        Text(
                            "nothing yet — offline or no web tools used",
                            style = MaterialTheme.typography.bodySmall,
                            color = StarkDim,
                        )
                    }
                }
                items(entries) { e ->
                    val host = try {
                        android.net.Uri.parse(e.url).host ?: e.url
                    } catch (_: Exception) {
                        e.url
                    }
                    Column {
                        Text(host, style = MaterialTheme.typography.bodyMedium, color = StarkIce)
                        Text(
                            "${timeFmt.format(java.util.Date(e.timestamp))} · " +
                                "${if (e.fromCache) "from cache" else "network"} · " +
                                "${e.bytes / 1024} KB",
                            style = MaterialTheme.typography.labelSmall,
                            color = StarkDim,
                        )
                    }
                }
            }
            Spacer(Modifier.height(20.dp))
        }
    }
}

@Composable
private fun WakeChip(vm: com.jarvis.assistant.chat.ChatViewModel, ui: com.jarvis.assistant.chat.ChatViewModel.UiState) {
    val permLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted -> vm.setWake(granted) }

    FilterChip(
        selected = ui.wakeArmed,
        onClick = {
            if (!ui.wakeArmed && Build.VERSION.SDK_INT >= 33) {
                permLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            } else {
                vm.setWake(!ui.wakeArmed)
            }
        },
        label = { Text(if (ui.wakeArmed) "👂 WAKE ARMED" else "👂 WAKE OFF", fontFamily = FontFamily.Monospace, fontSize = 11.sp) },
    )
}

// ---------------------------------------------------------------------------
// Helmet HUD — voice-first overlay (AssistantActivity)
// MCU helmet: spherical HUD, compass, hex grid, scanlines, brackets, reactor
// ---------------------------------------------------------------------------
@Composable
private fun HelmetHUD(
    vm: com.jarvis.assistant.chat.ChatViewModel,
    ui: com.jarvis.assistant.chat.ChatViewModel.UiState,
    messages: List<ChatMessage>,
) {
    LaunchedEffect(Unit) { vm.onOverlayOpened() }

    // summit state for animation
    var summonTick by remember { mutableStateOf(0) }
    LaunchedEffect(Unit) { summonTick = 1 }

    // keyboard toggle
    var kbdOpen by remember { mutableStateOf(false) }
    var kbdInput by remember { mutableStateOf("") }

    // idle collapse
    var minimized by remember { mutableStateOf(false) }
    LaunchedEffect(ui.listening, ui.generating, ui.speaking, ui.partialTranscript, ui.partialReply) {
        if (ui.listening || ui.generating || ui.speaking) {
            minimized = false
        } else {
            delay(45000)
            if (!ui.listening && !ui.generating && !ui.speaking) minimized = true
        }
    }

    // derive HUD mood
    val hudState = when {
        ui.generating -> "thinking"
        ui.speaking -> "speaking"
        ui.listening -> "listening"
        else -> "idle"
    }
    val stateColor = when (hudState) {
        "thinking" -> StarkCyan
        "speaking", "listening" -> StarkCyanGlow
        else -> StarkDim
    }

    if (minimized) {
        // collapsed orb — tiny bottom-right, tap to restore
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xCC050A14))
                .clickable { minimized = false },
            contentAlignment = Alignment.BottomEnd,
        ) {
            Box(modifier = Modifier.padding(18.dp).alpha(0.72f)) {
                StarkArcReactor(active = false, speaking = false, generating = false, compact = true)
            }
        }
        return
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .clickable {
                // tap anywhere: barge or restore focus
                if (ui.speaking) vm.bargeIn() else if (!ui.listening && !ui.generating) vm.startListening()
            }
            .background(Color(0xD6050A14)), // StarkDeepSpace @ 84% — film helmet tint
    ) {
        // hologram layers
        HexGridOverlay(opacity = 0.08f)
        ScanlinesOverlay(opacity = 0.06f)
        Vignette()
        CornerBrackets(color = StarkCyan, bracketLen = 18f, stroke = 1.4f, glow = 10f)

        // summon edge glow — animate once
        SummonEdgeGlow(tick = summonTick)

        Column(modifier = Modifier.fillMaxSize()) {
            // ---- helmet top — status row + compass ----
            Column(
                modifier = Modifier.fillMaxWidth().padding(top = 14.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                // summon banner — fades 2.2s
                SummonBanner(tick = summonTick)

                // status row — mono telemetry
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.padding(horizontal = 18.dp, vertical = 6.dp).alpha(0.98f),
                ) {
                    Box(modifier = Modifier.size(7.dp).clip(CircleShape).background(StarkCyan))
                    Text(hudState, fontFamily = FontFamily.Monospace, fontSize = 10.5.sp, letterSpacing = 1.4.sp, color = StarkDim)
                    Text("·", color = StarkDim.copy(alpha = 0.5f), fontSize = 10.sp)
                    Text(ui.sttName.ifBlank { "sherpa-asr" }, fontFamily = FontFamily.Monospace, fontSize = 10.sp, letterSpacing = 1.sp, color = StarkDim)
                    Text("·", color = StarkDim.copy(alpha = 0.5f))
                    Text(if (ui.webEnabled) "🌐 LINK" else "✈ OFFLINE", fontFamily = FontFamily.Monospace, fontSize = 10.sp, color = if (ui.webEnabled) StarkCyanGlow else StarkDim)
                    Text("·", color = StarkDim.copy(alpha = 0.5f))
                    Text(ui.modelName ?: "no-model", fontFamily = FontFamily.Monospace, fontSize = 10.sp, color = StarkDim)
                }

                // compass strip — centered, subtle
                Box(modifier = Modifier.padding(top = 2.dp).alpha(0.9f)) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CompassStrip(animated = ui.listening || ui.generating)
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.padding(top = 2.dp)) {
                            listOf("W", "NW", "N", "NE", "E").forEach { l ->
                                Text(l, fontFamily = FontFamily.Monospace, fontSize = 7.sp, letterSpacing = 1.sp, color = if (l == "N") StarkCyan else StarkDim.copy(alpha = 0.7f))
                            }
                        }
                    }
                }

                // secondary telemetry — alt / rng mock like film
                Text(
                    "ALT  0.42  ·  MACH  0.00  ·  RNG EL  ·  PWR  97%",
                    fontFamily = FontFamily.Monospace,
                    fontSize = 8.5.sp,
                    letterSpacing = 1.sp,
                    color = StarkDim.copy(alpha = 0.65f),
                    modifier = Modifier.padding(top = 4.dp),
                )
            }

            // ---- conversation — centered subtitle stack ----
            Column(
                modifier = Modifier.weight(1f).fillMaxWidth().padding(horizontal = 22.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.Bottom,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                // partial always on top when listening
                if (hudState == "listening" || ui.partialTranscript.isNotBlank()) {
                    PartialTranscript(text = ui.partialTranscript, visible = ui.listening)
                    Spacer(Modifier.height(10.dp))
                }

                // question card shows when we have sent text and are thinking/speaking
                val lastUser = messages.lastOrNull { it.role == Role.USER }?.content
                val showQ = lastUser != null && (ui.generating || ui.speaking || ui.toolStatus != null) && !ui.listening
                if (showQ && lastUser != null) {
                    QuestionCard(text = lastUser, visible = true)
                    Spacer(Modifier.height(10.dp))
                }

                // tool chip
                AnimatedVisibility(
                    visible = ui.toolStatus != null,
                    enter = slideInVertically(tween(300, easing = EaseOutCubic)) { it / 4 } + fadeIn(tween(200)),
                    exit = fadeOut(tween(160)),
                ) {
                    ui.toolStatus?.let {
                        // done when toolStatus contains ✓ or speaking
                        val done = it.contains("✓") || hudState == "speaking"
                        ToolChip(text = it, done = done)
                    }
                }
                if (ui.toolStatus != null) Spacer(Modifier.height(10.dp))

                // answer card — streaming typewriter
                val answerVisible = ui.generating || ui.speaking || ui.partialReply.isNotBlank()
                val source = messages.lastOrNull { it.role == Role.ASSISTANT && !it.source.isNullOrBlank() }?.source
                if (answerVisible) {
                    // typewriter for final answer when speaking
                    val displayed = if (ui.speaking && ui.partialReply.isBlank()) {
                        messages.lastOrNull { it.role == Role.ASSISTANT }?.content ?: ""
                    } else ui.partialReply
                    if (displayed.isNotBlank()) {
                        AnswerCard(text = displayed, source = if (ui.generating) null else source, visible = true, streaming = ui.generating)
                    }
                }

                // history fallback when idle — translucent recent turns as fallback
                if (!ui.listening && !ui.generating && ui.partialReply.isBlank() && (ui.toolStatus == null) && messages.isNotEmpty()) {
                    // show last assistant answer as subtitle if no live state
                    val recent = messages.takeLast(ChatLog.OVERLAY_PREVIEW_TURNS)
                    if (recent.isNotEmpty() && !answerVisible) {
                        Spacer(Modifier.height(6.dp))
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.alpha(0.78f)) {
                            recent.takeLast(2).forEach { msg ->
                                Text(
                                    "${if (msg.role == Role.USER) "YOU" else "JARVIS"}: ${msg.content.take(130)}",
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 10.sp,
                                    letterSpacing = 0.4.sp,
                                    color = StarkDim,
                                    maxLines = 2,
                                )
                            }
                        }
                    }
                }
            }

            // ---- orb zone — state word + reactor ----
            Column(
                modifier = Modifier.fillMaxWidth().padding(bottom = 6.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    hudState.uppercase(),
                    fontFamily = FontFamily.Monospace,
                    fontSize = 11.sp,
                    letterSpacing = 4.8.sp,
                    color = stateColor,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.padding(bottom = 10.dp),
                )
                Box(contentAlignment = Alignment.Center) {
                    // pings behind reactor when listening
                    if (hudState == "listening") {
                        PingRings()
                    }
                    StarkArcReactor(
                        active = ui.listening,
                        speaking = ui.speaking,
                        generating = ui.generating,
                    )
                }
            }

            // ---- controls ----
            Row(
                modifier = Modifier.fillMaxWidth().padding(bottom = 14.dp, top = 8.dp),
                horizontalArrangement = Arrangement.Center,
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    HudControlPill("■ STOP") { vm.stopGeneration() }
                    HudControlPill("⌨ TYPE") { kbdOpen = !kbdOpen }
                    HudControlPill("✕ CLOSE") { vm.onOverlayClosed() }
                }
            }
        }

        // ---- slide-up keyboard ----
        AnimatedVisibility(
            visible = kbdOpen,
            enter = slideInVertically(tween(320, easing = EaseOutCubic)) { it } + fadeIn(tween(200)),
            exit = slideInVertically(tween(260)) { it } + fadeOut(tween(180)),
            modifier = Modifier.align(Alignment.BottomCenter),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(StarkPanel.copy(alpha = 0.98f))
                    .border(1.dp, StarkCyan.copy(alpha = 0.14f), RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp))
                    .padding(12.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = kbdInput,
                        onValueChange = { kbdInput = it },
                        modifier = Modifier.weight(1f),
                        placeholder = { Text("Ask J.A.R.V.I.S…", fontFamily = FontFamily.Monospace, fontSize = 13.sp, color = StarkDim) },
                        shape = RoundedCornerShape(14.dp),
                        singleLine = false,
                        maxLines = 3,
                    )
                    Spacer(Modifier.size(8.dp))
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .background(StarkCyan)
                            .clickable {
                                if (kbdInput.isNotBlank()) { vm.send(kbdInput); kbdInput = ""; kbdOpen = false }
                            },
                        contentAlignment = Alignment.Center,
                    ) {
                        Text("➤", color = StarkDeepSpace, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Summon FX — edge glow + banner like film boot
// ---------------------------------------------------------------------------
@Composable
private fun SummonEdgeGlow(tick: Int) {
    // animate once on tick change
    var visible by remember(tick) { mutableStateOf(true) }
    LaunchedEffect(tick) {
        visible = true
        delay(1100)
        visible = false
    }
    val t = rememberInfiniteTransition(label = "edge")
    val glowA by t.animateFloat(0.18f, 0.42f, infiniteRepeatable(tween(700), RepeatMode.Reverse), label = "glowA")
    if (visible) {
        Canvas(Modifier.fillMaxSize()) {
            drawRect(
                color = StarkCyan.copy(alpha = glowA * 0.35f),
                style = Stroke(width = 3.dp.toPx()),
            )
            // inner bloom
            drawRect(
                brush = Brush.radialGradient(
                    colors = listOf(StarkCyan.copy(alpha = 0.10f), Color.Transparent),
                    center = center,
                    radius = size.minDimension * 0.92f,
                ),
            )
        }
    }
}

@Composable
private fun SummonBanner(tick: Int) {
    var show by remember(tick) { mutableStateOf(true) }
    LaunchedEffect(tick) {
        show = true
        delay(2200)
        show = false
    }
    AnimatedVisibility(
        visible = show,
        enter = slideInVertically(tween(500, easing = EaseOutCubic)) { it / 3 } + fadeIn(tween(400)),
        exit = fadeOut(tween(420)),
    ) {
        Text(
            "◈ J.A.R.V.I.S ◈",
            fontFamily = FontFamily.Monospace,
            fontSize = 12.sp,
            letterSpacing = 4.2.sp,
            color = StarkCyanGlow,
            modifier = Modifier.padding(vertical = 4.dp),
        )
    }
}

@Composable
private fun PingRings() {
    val t = rememberInfiniteTransition(label = "ping")
    val s1 by t.animateFloat(0.52f, 1.35f, infiniteRepeatable(tween(2400, easing = LinearEasing)), label = "s1")
    val a1 by t.animateFloat(0.7f, 0f, infiniteRepeatable(tween(2400, easing = LinearEasing)), label = "a1")
    val s2 by t.animateFloat(0.52f, 1.35f, infiniteRepeatable(tween(2400, easing = LinearEasing), delayMillis = 1200), label = "s2")
    val a2 by t.animateFloat(0.7f, 0f, infiniteRepeatable(tween(2400, easing = LinearEasing), delayMillis = 1200), label = "a2")
    Canvas(Modifier.size(190.dp)) {
        val r = size.minDimension / 2f
        drawCircle(StarkCyan.copy(alpha = a1 * 0.6f), radius = r * s1, center = center, style = Stroke(1.2.dp.toPx()))
        drawCircle(StarkCyan.copy(alpha = a2 * 0.6f), radius = r * s2, center = center, style = Stroke(1.2.dp.toPx()))
    }
}

@Composable
private fun HudControlPill(text: String, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(Color(0xFF0F1E33).copy(alpha = 0.84f))
            .border(1.dp, StarkDim.copy(alpha = 0.26f), RoundedCornerShape(50))
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 9.dp),
    ) {
        Text(text, fontFamily = FontFamily.Monospace, fontSize = 11.sp, letterSpacing = 1.sp, color = StarkDim)
    }
}

// ---------------------------------------------------------------------------
// shared pieces — Stark bubbles
// ---------------------------------------------------------------------------
@Composable
private fun MessageBubble(msg: ChatMessage, streaming: Boolean = false) {
    val isUser = msg.role == Role.USER
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start,
    ) {
        Column(horizontalAlignment = if (isUser) Alignment.End else Alignment.Start) {
            SelectionContainer {
                Box(
                    modifier = Modifier
                        .widthIn(max = 320.dp)
                        .clip(
                            RoundedCornerShape(
                                topStart = 16.dp,
                                topEnd = 16.dp,
                                bottomStart = if (isUser) 16.dp else 4.dp,
                                bottomEnd = if (isUser) 4.dp else 16.dp,
                            ),
                        )
                        .background(
                            if (isUser) StarkCyan.copy(alpha = 0.16f)
                            else StarkPanel.copy(alpha = 0.92f),
                        )
                        .border(
                            1.dp,
                            if (isUser) StarkCyan.copy(alpha = 0.32f) else StarkDim.copy(alpha = 0.16f),
                            RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp, bottomStart = if (isUser) 16.dp else 4.dp, bottomEnd = if (isUser) 4.dp else 16.dp),
                        )
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                ) {
                    Text(
                        text = msg.content + if (streaming) " ▍" else "",
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (isUser) StarkCyanGlow else StarkIce,
                        fontSize = 14.sp,
                        lineHeight = 19.sp,
                    )
                }
            }
            if (!streaming && !msg.source.isNullOrBlank() && msg.role == Role.ASSISTANT) {
                Spacer(Modifier.height(4.dp))
                SourceChip(text = msg.source!!)
            }
        }
    }
}

/** Small status dot for the top bar — now Stark cyan glow */
@Composable
private fun ReactorDot(pulsing: Boolean) {
    val transition = rememberInfiniteTransition(label = "reactor")
    val alpha by transition.animateFloat(
        initialValue = 0.35f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(if (pulsing) 500 else 1600, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "reactor-alpha",
    )
    val glow = if (pulsing) alpha else 0.9f
    Box(
        modifier = Modifier
            .size(12.dp)
            .alpha(glow)
            .clip(CircleShape)
            .background(StarkCyan)
            .border(1.dp, StarkCyanGlow.copy(alpha = 0.6f), CircleShape),
    )
}

/**
 * Stark arc reactor — HUD-grade: Stark palette, 190dp, coil tint #073C4B,
 * 36-tick bezel, radial glow, core waveform, wobble. Rotation speed carries state.
 */
@Composable
private fun StarkArcReactor(active: Boolean, speaking: Boolean, generating: Boolean, compact: Boolean = false) {
    val transition = rememberInfiniteTransition(label = "arc")
    val spinSeconds = when {
        generating -> 2.2f
        speaking -> 4.5f
        active -> 8f
        else -> 22f
    }
    val rotation by transition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(tween((spinSeconds * 1000).toInt(), easing = LinearEasing)),
        label = "arc-rot",
    )
    val counter by transition.animateFloat(
        initialValue = 360f,
        targetValue = 0f,
        animationSpec = infiniteRepeatable(tween(16000, easing = LinearEasing)),
        label = "arc-counter",
    )
    val pulse by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(650, easing = LinearEasing), RepeatMode.Reverse),
        label = "arc-pulse",
    )

    val ringColor = when {
        speaking -> StarkCyanGlow
        generating -> StarkCyan
        active -> Color(0xFFA5F3FC)
        else -> StarkDim.copy(alpha = 0.85f)
    }
    val sizeDp = if (compact) 72.dp else 190.dp
    val canvasMod = Modifier.size(sizeDp)

    Box(modifier = canvasMod, contentAlignment = Alignment.Center) {
        Canvas(canvasMod) {
            val c = center
            val r = size.minDimension / 2f
            val px = 1.dp.toPx()

            // radial glow behind everything — Stark bloom
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(ringColor.copy(alpha = 0.28f), Color.Transparent),
                    center = c,
                    radius = r * 0.95f,
                ),
                radius = r * 0.95f,
                center = c,
            )

            // bezel: 36 ticks
            val tickLen = 4f * px
            val tickR = r - 2f * px
            repeat(36) { i ->
                val a = Math.toRadians((i * 10).toDouble())
                val ca = kotlin.math.cos(a).toFloat()
                val sa = kotlin.math.sin(a).toFloat()
                val sx = c.x + (tickR - tickLen) * ca
                val sy = c.y + (tickR - tickLen) * sa
                val ex = c.x + tickR * ca
                val ey = c.y + tickR * sa
                drawLine(
                    color = ringColor.copy(alpha = 0.28f),
                    start = Offset(sx, sy),
                    end = Offset(ex, ey),
                    strokeWidth = 1.2f * px,
                    cap = StrokeCap.Round,
                )
            }

            fun ring(radius: Float, angle: Float, segments: Int, duty: Float, width: Float, alpha: Float) {
                val stroke = Stroke(width = width * px, cap = StrokeCap.Butt)
                val sweep = 360f / segments
                repeat(segments) { i ->
                    drawArc(
                        color = ringColor.copy(alpha = alpha),
                        startAngle = angle + i * sweep,
                        sweepAngle = sweep * duty,
                        useCenter = false,
                        topLeft = Offset(c.x - radius, c.y - radius),
                        size = androidx.compose.ui.geometry.Size(radius * 2, radius * 2),
                        style = stroke,
                    )
                }
            }

            // outer coil — 10 Stark coils, deep fill
            ring(r - 13f * px, rotation, segments = 10, duty = 0.66f, width = 6.5f, alpha = 0.96f)
            // inner detail
            ring(r - 32f * px, counter, segments = 24, duty = 0.38f, width = 2.4f, alpha = 0.52f)

            // core wobble
            val wobble = if (speaking) {
                (0.10 * kotlin.math.sin(pulse * 2.0 * Math.PI) + 0.05 * kotlin.math.sin(pulse * 5.3 * Math.PI)).toFloat()
            } else {
                0.05f * kotlin.math.sin(pulse * 2.0 * Math.PI).toFloat()
            }
            val coreR = (r * 0.30f) * (1f + wobble) * if (generating) 1.08f else 1f

            // triad slots
            val slotR = coreR + 9f * px
            repeat(3) { i ->
                drawArc(
                    color = ringColor.copy(alpha = 0.82f),
                    startAngle = rotation * -1.5f + i * 120f + 12f,
                    sweepAngle = 96f,
                    useCenter = false,
                    topLeft = Offset(c.x - slotR, c.y - slotR),
                    size = androidx.compose.ui.geometry.Size(slotR * 2, slotR * 2),
                    style = Stroke(width = 3.8f * px, cap = StrokeCap.Round),
                )
            }

            // heart — Stark gradient
            drawCircle(color = ringColor.copy(alpha = 0.9f), radius = coreR * 0.62f, center = c)
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(Color.White.copy(alpha = 0.88f), ringColor.copy(alpha = 0.0f)),
                    center = c,
                    radius = coreR,
                ),
                radius = coreR,
                center = c,
            )
            // inner coil dots 8x like film
            if (!compact) {
                repeat(8) { i ->
                    val ang = Math.toRadians((i * 45).toDouble())
                    val rad = coreR * 0.38f
                    val x = c.x + kotlin.math.cos(ang).toFloat() * rad
                    val y = c.y + kotlin.math.sin(ang).toFloat() * rad
                    drawCircle(Color(0xFF04222B), radius = 1.6f * px, center = Offset(x, y))
                }
            }
        }
        // waveform overlay centered — only when not compact and active/speaking
        if (!compact && (active || speaking)) {
            CoreWaveform(active = speaking || active, level = if (speaking) 0.75f else 0.55f)
        }
    }
}
