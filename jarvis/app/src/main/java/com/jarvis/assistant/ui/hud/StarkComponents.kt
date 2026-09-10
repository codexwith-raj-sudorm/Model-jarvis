package com.jarvis.assistant.ui.hud

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
import androidx.compose.animation.slideInHorizontally
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
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jarvis.assistant.ui.StarkCyan
import com.jarvis.assistant.ui.StarkCyanGlow
import com.jarvis.assistant.ui.StarkDim
import com.jarvis.assistant.ui.StarkIce
import kotlinx.coroutines.delay

// ---------------------------------------------------------------------------
// Stark pill — tool chip, source chip, controls
// ---------------------------------------------------------------------------
@Composable
fun ToolChip(text: String, done: Boolean = false, modifier: Modifier = Modifier) {
    val bg = if (done) StarkCyan.copy(alpha = 0.14f) else StarkCyan.copy(alpha = 0.08f)
    val border = if (done) StarkCyan.copy(alpha = 0.5f) else StarkCyan.copy(alpha = 0.25f)
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(50))
            .background(bg)
            .border(1.dp, border, RoundedCornerShape(50))
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        // spinner / check
        Box(modifier = Modifier.size(10.dp)) {
            if (!done) {
                val t = rememberInfiniteTransition(label = "chipSpin")
                val rot by t.animateFloat(0f, 360f, infiniteRepeatable(tween(700, easing = LinearEasing)), label = "spin")
                Canvas(Modifier.fillMaxWidth()) {
                    drawArc(
                        color = StarkCyan.copy(alpha = 0.25f),
                        startAngle = 0f, sweepAngle = 360f, useCenter = false,
                        style = Stroke(2.dp.toPx()),
                    )
                    drawArc(
                        color = StarkCyan,
                        startAngle = rot, sweepAngle = 90f, useCenter = false,
                        style = Stroke(2.dp.toPx(), cap = StrokeCap.Round),
                    )
                }
            } else {
                Canvas(Modifier.fillMaxWidth()) {
                    drawCircle(StarkCyan, radius = size.minDimension / 2f)
                    // tiny check drawn as line
                }
                Box(
                    Modifier
                        .size(10.dp)
                        .clip(CircleShape)
                        .background(StarkCyan),
                )
            }
        }
        Text(
            text,
            fontFamily = FontFamily.Monospace,
            fontSize = 10.5.sp,
            letterSpacing = 0.6.sp,
            color = StarkCyanGlow,
            lineHeight = 12.sp,
        )
    }
}

@Composable
fun SourceChip(text: String, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(50))
            .background(StarkCyan.copy(alpha = 0.08f))
            .border(1.dp, StarkCyan.copy(alpha = 0.22f), RoundedCornerShape(50))
            .padding(horizontal = 9.dp, vertical = 4.dp),
    ) {
        Text(
            "⛓ $text",
            fontFamily = FontFamily.Monospace,
            fontSize = 10.sp,
            letterSpacing = 0.8.sp,
            color = StarkCyanGlow,
        )
    }
}

// ---------------------------------------------------------------------------
// Subtitle cards — qcard / acard like film captions, with corner cut + glow
// ---------------------------------------------------------------------------
@Composable
fun QuestionCard(text: String, visible: Boolean) {
    AnimatedVisibility(
        visible = visible,
        enter = slideInVertically(tween(380, easing = EaseOutCubic)) { it / 3 } + fadeIn(tween(280)),
        exit = fadeOut(tween(180)),
    ) {
        Box(
            modifier = Modifier
                .widthIn(max = 330.dp)
                .clip(RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp, bottomStart = 16.dp, bottomEnd = 4.dp))
                .background(StarkCyan.copy(alpha = 0.11f))
                .border(1.dp, StarkCyan.copy(alpha = 0.28f), RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp, bottomStart = 16.dp, bottomEnd = 4.dp))
                .padding(horizontal = 14.dp, vertical = 10.dp),
        ) {
            Text(text, color = StarkCyanGlow, fontSize = 14.5.sp, lineHeight = 20.sp)
        }
    }
}

@Composable
fun AnswerCard(text: String, source: String?, visible: Boolean, streaming: Boolean = false) {
    AnimatedVisibility(
        visible = visible,
        enter = slideInVertically(tween(420, easing = EaseOutCubic)) { it / 3 } + fadeIn(tween(300)),
        exit = fadeOut(tween(180)),
    ) {
        Column(modifier = Modifier.widthIn(max = 330.dp)) {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp, bottomStart = 4.dp, bottomEnd = 16.dp))
                    .background(Color(0xFF0F1E33).copy(alpha = 0.88f))
                    .border(1.dp, StarkDim.copy(alpha = 0.18f), RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp, bottomStart = 4.dp, bottomEnd = 16.dp))
                    .padding(horizontal = 14.dp, vertical = 10.dp),
            ) {
                // subtle outer glow
                Text(
                    text + if (streaming) " ▍" else "",
                    color = StarkIce,
                    fontSize = 14.5.sp,
                    lineHeight = 21.sp,
                )
            }
            if (!source.isNullOrBlank() && !streaming) {
                Spacer(Modifier.height(7.dp))
                SourceChip(source)
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Partial transcript — large, centered, caret blink
// ---------------------------------------------------------------------------
@Composable
fun PartialTranscript(text: String, visible: Boolean) {
    AnimatedVisibility(visible = visible, enter = fadeIn(tween(220)), exit = fadeOut(tween(160))) {
        Row(
            modifier = Modifier
                .widthIn(max = 340.dp)
                .padding(horizontal = 12.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text.ifBlank { "listening…" },
                color = StarkIce,
                fontSize = 17.sp,
                lineHeight = 24.sp,
                fontWeight = FontWeight.Normal,
            )
            if (text.isNotBlank()) {
                Spacer(Modifier.width(3.dp))
                val t = rememberInfiniteTransition(label = "caret")
                val a by t.animateFloat(1f, 0.15f, infiniteRepeatable(tween(520), RepeatMode.Reverse), label = "caretA")
                Box(
                    Modifier
                        .size(width = 2.dp, height = 18.dp)
                        .alpha(a)
                        .background(StarkCyan),
                )
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Waveform bars inside core — 22 bars, Stark envelope
// ---------------------------------------------------------------------------
@Composable
fun CoreWaveform(active: Boolean, level: Float = 0.6f) {
    val bars = 22
    val transition = rememberInfiniteTransition(label = "wave")
    val phases = remember { List(bars) { (it * 37) % 100 / 100f } }
    val anim by transition.animateFloat(0f, 1f, infiniteRepeatable(tween(1100, easing = LinearEasing)), label = "waveAnim")

    Row(
        modifier = Modifier.size(width = 64.dp, height = 36.dp),
        horizontalArrangement = Arrangement.spacedBy(2.5.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        for (i in 0 until bars) {
            val env = kotlin.math.sin((i / bars.toFloat()) * Math.PI).toFloat() // arc envelope
            val jitter = if (active) 0.55f else 0.12f
            val base = if (active) level else 0.18f
            val v = base + (kotlin.math.sin((anim * 6.28f + phases[i] * 10).toDouble()).toFloat() * jitter * 0.5f)
            val h = (6 + (v.coerceIn(0f, 1f) * env * 28f)).coerceIn(6f, 34f)
            val alpha = 0.55f + v * 0.45f
            Box(
                Modifier
                    .width(2.6.dp)
                    .height(h.dp)
                    .clip(RoundedCornerShape(1.2.dp))
                    .alpha(alpha.coerceIn(0.45f, 1f))
                    .background(Color(0xFF04222B).copy(alpha = 0.95f)),
            )
        }
    }
}

// ---------------------------------------------------------------------------
// Stark state word — monospace tracked, color shifts
// ---------------------------------------------------------------------------
@Composable
fun StateWord(state: String, color: Color) {
    Text(
        state.uppercase(),
        fontFamily = FontFamily.Monospace,
        fontSize = 11.sp,
        letterSpacing = 4.8.sp,
        color = color,
        fontWeight = FontWeight.Medium,
    )
}

// ---------------------------------------------------------------------------
// Controls pills
// ---------------------------------------------------------------------------
@Composable
fun HudPill(text: String, active: Boolean = false, onClick: () -> Unit) {
    val bg = if (active) StarkCyan.copy(alpha = 0.14f) else Color(0xFF0F1E33).copy(alpha = 0.82f)
    val border = if (active) StarkCyan else StarkDim.copy(alpha = 0.28f)
    val fg = if (active) StarkCyan else StarkDim
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(bg)
            .border(1.dp, border, RoundedCornerShape(50))
            .padding(horizontal = 16.dp, vertical = 9.dp)
            .then(Modifier.clickable(onClick = onClick)),
    ) {
        Text(
            text,
            fontFamily = FontFamily.Monospace,
            fontSize = 11.sp,
            letterSpacing = 1.sp,
            color = fg,
        )
    }
}

// ---------------------------------------------------------------------------
// Boot typing helper — typewriter for answer
// ---------------------------------------------------------------------------
@Composable
fun TypewriterText(full: String, enabled: Boolean, onDone: (() -> Unit)? = null): String {
    var shown by remember(full) { mutableStateOf(if (enabled) "" else full) }
    LaunchedEffect(full, enabled) {
        if (!enabled) { shown = full; return@LaunchedEffect }
        shown = ""
        for (i in 1..full.length) {
            shown = full.take(i)
            delay(18) // ~26ms in spec, 18ms is snappier on 60hz
        }
        onDone?.invoke()
    }
    return shown
}


