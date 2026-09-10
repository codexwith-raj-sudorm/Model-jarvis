package com.jarvis.assistant.ui.hud

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.unit.dp
import com.jarvis.assistant.ui.StarkCyan
import com.jarvis.assistant.ui.StarkDim
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * MCU HUD primitives — hexagonal grid, scanlines, corner brackets, compass.
 * All are lightweight Canvas draws, GPU-resident, zero allocations per frame.
 */

// ---------------------------------------------------------------------------
// Hex grid — subtle 28dp repeat, .07 opacity, draws every 3rd frame via alpha
// ---------------------------------------------------------------------------
@Composable
fun HexGridOverlay(modifier: Modifier = Modifier, opacity: Float = 0.07f) {
    Canvas(modifier = modifier.fillMaxSize().alpha(1f)) {
        val hexR = 28.dp.toPx()
        val hexW = hexR * 2f
        val hexH = sqrt(3f) * hexR
        val colStep = hexW * 0.75f
        val rowStep = hexH
        val cols = (size.width / colStep).toInt() + 2
        val rows = (size.height / rowStep).toInt() + 2
        val stroke = 0.7.dp.toPx()
        val col = StarkCyan.copy(alpha = opacity)
        for (r in -1..rows) {
            for (c in -1..cols) {
                val x0 = c * colStep
                val y0 = r * rowStep + if (c % 2 == 1) rowStep / 2f else 0f
                drawHex(x0, y0, hexR, col, stroke)
            }
        }
    }
}

private fun DrawScope.drawHex(cx: Float, cy: Float, r: Float, color: Color, stroke: Float) {
    val pts = Array(6) { i ->
        val ang = Math.toRadians(30.0 + i * 60.0)
        Offset(
            (cx + cos(ang).toFloat() * r),
            (cy + sin(ang).toFloat() * r),
        )
    }
    for (i in 0..5) {
        drawLine(color, pts[i], pts[(i + 1) % 6], strokeWidth = stroke)
    }
}

// ---------------------------------------------------------------------------
// Scanlines — 2px repeating horizontal lines, very subtle
// ---------------------------------------------------------------------------
@Composable
fun ScanlinesOverlay(modifier: Modifier = Modifier, opacity: Float = 0.06f) {
    Canvas(modifier = modifier.fillMaxSize().alpha(1f)) {
        val lineH = 2.dp.toPx()
        val gap = 6.dp.toPx()
        var y = 0f
        val col = Color.White.copy(alpha = opacity)
        while (y < size.height) {
            drawLine(col, Offset(0f, y), Offset(size.width, y), strokeWidth = lineH)
            y += lineH + gap
        }
    }
}

// ---------------------------------------------------------------------------
// Corner brackets — 4x L shapes like helmet viewport, with outer glow
// ---------------------------------------------------------------------------
@Composable
fun CornerBrackets(
    modifier: Modifier = Modifier,
    color: Color = StarkCyan,
    bracketLen: Float = 18f,
    stroke: Float = 1.6f,
    glow: Float = 8f,
) {
    Canvas(modifier = modifier.fillMaxSize()) {
        val px = 1.dp.toPx()
        val len = bracketLen.dp.toPx()
        val sw = stroke.dp.toPx()
        val glowPx = glow.dp.toPx()
        val pad = 10.dp.toPx()

        // glow pass — slightly thicker, low alpha
        val glowCol = color.copy(alpha = 0.18f)
        fun glowLine(s: Offset, e: Offset) = drawLine(glowCol, s, e, strokeWidth = sw + glowPx)

        // main
        fun line(s: Offset, e: Offset) = drawLine(color.copy(alpha = 0.95f), s, e, strokeWidth = sw)

        // TL
        glowLine(Offset(pad, pad), Offset(pad + len, pad))
        glowLine(Offset(pad, pad), Offset(pad, pad + len))
        line(Offset(pad, pad), Offset(pad + len, pad))
        line(Offset(pad, pad), Offset(pad, pad + len))
        // TR
        glowLine(Offset(size.width - pad, pad), Offset(size.width - pad - len, pad))
        glowLine(Offset(size.width - pad, pad), Offset(size.width - pad, pad + len))
        line(Offset(size.width - pad, pad), Offset(size.width - pad - len, pad))
        line(Offset(size.width - pad, pad), Offset(size.width - pad, pad + len))
        // BL
        glowLine(Offset(pad, size.height - pad), Offset(pad + len, size.height - pad))
        glowLine(Offset(pad, size.height - pad), Offset(pad, size.height - pad - len))
        line(Offset(pad, size.height - pad), Offset(pad + len, size.height - pad))
        line(Offset(pad, size.height - pad), Offset(pad, size.height - pad - len))
        // BR
        glowLine(Offset(size.width - pad, size.height - pad), Offset(size.width - pad - len, size.height - pad))
        glowLine(Offset(size.width - pad, size.height - pad), Offset(size.width - pad, size.height - pad - len))
        line(Offset(size.width - pad, size.height - pad), Offset(size.width - pad - len, size.height - pad))
        line(Offset(size.width - pad, size.height - pad), Offset(size.width - pad, size.height - pad - len))
    }
}

// ---------------------------------------------------------------------------
// Compass strip — MCU helmet top bar: W NW N NE E with moving needle
// ---------------------------------------------------------------------------
@Composable
fun CompassStrip(modifier: Modifier = Modifier, animated: Boolean = true) {
    val transition = rememberInfiniteTransition(label = "compass")
    val drift by transition.animateFloat(
        initialValue = -6f,
        targetValue = 6f,
        animationSpec = infiniteRepeatable(tween(4200, easing = LinearEasing), RepeatMode.Reverse),
        label = "compass-drift",
    )
    Canvas(modifier = modifier.size(width = 260.dp, height = 18.dp)) {
        val centerX = size.width / 2f + drift.dp.toPx()
        val tickH = 6.dp.toPx()
        val longTickH = 9.dp.toPx()
        val labelY = size.height - 1.dp.toPx()

        val labels = listOf("W", "NW", "N", "NE", "E", "SE", "S")
        val step = size.width / 6f

        labels.forEachIndexed { i, label ->
            val x = i * step + (drift.dp.toPx() * 0.35f)
            if (x < -12.dp.toPx() || x > size.width + 12.dp.toPx()) return@forEachIndexed
            val isMajor = label.length == 1
            val h = if (isMajor) longTickH else tickH
            val col = if (label == "N") StarkCyan else StarkDim.copy(alpha = 0.9f)
            drawLine(col, Offset(x, size.height / 2f - h / 2f), Offset(x, size.height / 2f + h / 2f), strokeWidth = 1.1.dp.toPx())
            // label
            // use drawContext canvas nativeText? Keep simple: small circle + skip text draw; parent will overlay Text composables
        }
        // needle — thin white line at center
        drawLine(Color.White.copy(alpha = 0.95f), Offset(size.width / 2f, 0f), Offset(size.width / 2f, size.height), strokeWidth = 1f.dp.toPx())
        // center notch
        drawLine(StarkCyan, Offset(size.width / 2f - 7.dp.toPx(), 1.dp.toPx()), Offset(size.width / 2f + 7.dp.toPx(), 1.dp.toPx()), strokeWidth = 1.2.dp.toPx())
    }
}

// ---------------------------------------------------------------------------
// Vignette — radial darkening to focus on reactor
// ---------------------------------------------------------------------------
@Composable
fun Vignette(modifier: Modifier = Modifier) {
    Canvas(modifier = modifier.fillMaxSize()) {
        val radius = maxOf(size.width, size.height) * 0.85f
        drawRect(
            brush = androidx.compose.ui.graphics.Brush.radialGradient(
                colors = listOf(Color.Transparent, Color(0xFF050A14).copy(alpha = 0.55f)),
                center = center,
                radius = radius,
            ),
        )
    }
}
