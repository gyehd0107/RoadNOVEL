package com.roadnovel.ui.common

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.roadnovel.ui.theme.MinTouch
import com.roadnovel.ui.theme.RN
import kotlin.random.Random

val CardShape = RoundedCornerShape(18.dp)
val ButtonShape = RoundedCornerShape(14.dp)

/** 밤하늘 배경 (오른쪽 위가 살짝 밝은 그라데이션 + 별) */
@Composable
fun NightSky(modifier: Modifier = Modifier) {
    val stars = remember {
        val r = Random(7)
        List(90) { Triple(r.nextFloat(), r.nextFloat(), r.nextFloat()) }
    }
    Canvas(modifier.fillMaxSize()) {
        drawRect(
            Brush.radialGradient(
                colors = listOf(RN.BgTop, RN.Bg),
                center = Offset(size.width * 0.8f, -size.height * 0.1f),
                radius = size.maxDimension * 0.75f,
            )
        )
        stars.forEach { (x, y, s) ->
            drawCircle(
                color = Color.White.copy(alpha = 0.15f + s * 0.4f),
                radius = 0.6f + s * 1.6f,
                center = Offset(x * size.width, y * size.height),
            )
        }
    }
}

/** 둥근 카드 (테두리 있음). onClick이 있으면 누를 수 있다 */
@Composable
fun Panel(
    modifier: Modifier = Modifier,
    background: Brush = Brush.linearGradient(listOf(RN.Panel, RN.Panel)),
    border: Color? = RN.Line,
    borderWidth: Dp = 1.dp,
    shape: Shape = CardShape,
    enabled: Boolean = true,
    onClick: (() -> Unit)? = null,
    padding: PaddingValues = PaddingValues(horizontal = 24.dp, vertical = 20.dp),
    content: @Composable ColumnScope.() -> Unit,
) {
    var m = modifier.clip(shape).background(background)
    if (border != null) m = m.border(BorderStroke(borderWidth, border), shape)
    if (onClick != null) m = m.clickable(enabled = enabled, onClick = onClick)
    if (!enabled) m = m.alpha(0.45f)
    Column(m.padding(padding), verticalArrangement = Arrangement.spacedBy(8.dp), content = content)
}

/** 금색 그라데이션 큰 버튼 (주요 동작) */
@Composable
fun GoButton(text: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Box(
        modifier
            .heightIn(min = MinTouch)
            .clip(ButtonShape)
            .background(RN.GoldGradient)
            .clickable(onClick = onClick)
            .padding(horizontal = 40.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, style = MaterialTheme.typography.labelLarge, color = RN.OnGold)
    }
}

/** 어두운 보조 버튼 */
@Composable
fun SubButton(text: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Box(
        modifier
            .heightIn(min = MinTouch)
            .clip(ButtonShape)
            .background(RN.Panel2)
            .border(1.dp, RN.Line, ButtonShape)
            .clickable(onClick = onClick)
            .padding(horizontal = 28.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, style = MaterialTheme.typography.titleMedium, color = RN.Text)
    }
}

/** 상단 바의 알약 모양 정보 */
@Composable
fun Chip(modifier: Modifier = Modifier, content: @Composable RowScope.() -> Unit) {
    Row(
        modifier
            .clip(RoundedCornerShape(999.dp))
            .background(Color.White.copy(alpha = 0.05f))
            .border(1.dp, RN.Line, RoundedCornerShape(999.dp))
            .padding(horizontal = 14.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        content = content,
    )
}

@Composable
fun Dot(color: Color) {
    Box(Modifier.size(10.dp).clip(CircleShape).background(color))
}

/** 원 안에 이모지 (인물 얼굴) */
@Composable
fun Avatar(emoji: String, size: Dp = 52.dp) {
    Box(
        Modifier.size(size).clip(CircleShape).background(RN.Panel2).border(1.dp, RN.Line, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Text(emoji, style = MaterialTheme.typography.titleLarge)
    }
}

/** 인물 id → 얼굴 이모지 (이야기마다 다르면 여기에 추가) */
fun avatarFor(who: String): String = when (who) {
    "lumi" -> "🦊"
    "bolt" -> "🐢"
    "giant" -> "🌫️"
    else -> "📖"
}

fun formatTime(sec: Double): String {
    val s = sec.toInt().coerceAtLeast(0)
    return if (s >= 3600) "%d:%02d:%02d".format(s / 3600, s / 60 % 60, s % 60)
    else "%d:%02d".format(s / 60, s % 60)
}
