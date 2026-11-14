package com.roadnovel.ui.player

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.roadnovel.ui.ChoiceUi
import com.roadnovel.ui.theme.RN

/** 선택지: 화면 전체를 덮는 큰 버튼들 (최대 3개) */
@Composable
fun ChoiceOverlay(c: ChoiceUi, onPick: (Int) -> Unit) {
    Column(
        Modifier
            .fillMaxSize()
            .background(Color(0xE605070C))
            // 뒤 화면이 눌리지 않게 막음
            .clickable(remember { MutableInteractionSource() }, indication = null) {}
            .padding(30.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(24.dp, Alignment.CenterVertically),
    ) {
        Text(
            c.question,
            style = MaterialTheme.typography.headlineMedium,
            textAlign = TextAlign.Center,
            modifier = Modifier.widthIn(max = 1000.dp),
        )
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            MicWave()
            Text("듣고 있어요… 말로 대답해 주세요", color = RN.Teal, fontSize = 18.sp)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(18.dp)) {
            c.options.forEachIndexed { i, label ->
                Column(
                    Modifier
                        .widthIn(min = 240.dp, max = 330.dp)
                        .heightIn(min = 130.dp)
                        .clip(RoundedCornerShape(20.dp))
                        .background(RN.Panel)
                        .border(2.dp, RN.Line, RoundedCornerShape(20.dp))
                        .clickable { onPick(i) }
                        .padding(18.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterVertically),
                ) {
                    Box(Modifier.size(38.dp).clip(CircleShape).background(RN.Gold), contentAlignment = Alignment.Center) {
                        Text("${i + 1}", color = RN.OnGold, fontWeight = FontWeight.Black, fontSize = 18.sp)
                    }
                    Text(label, style = MaterialTheme.typography.titleLarge, textAlign = TextAlign.Center)
                    Text("\"${i + 1}번\" 이라고 말해도 돼요", fontSize = 14.sp, color = RN.Sub)
                }
            }
        }
        Text(
            "${c.secondsLeft}초 안에 대답이 없으면 " +
                (if (c.attempt == 1) "한 번 더 물어볼게요" else "\"${c.options[c.defaultIndex]}\"(으)로 갈게요"),
            fontSize = 16.sp,
            color = RN.Sub,
        )
    }
}

@Composable
private fun MicWave() {
    val t = rememberInfiniteTransition(label = "mic")
    Row(horizontalArrangement = Arrangement.spacedBy(3.dp), verticalAlignment = Alignment.CenterVertically) {
        repeat(4) { i ->
            val h by t.animateFloat(
                initialValue = 5f, targetValue = 20f,
                animationSpec = infiniteRepeatable(tween(500, delayMillis = i * 150), RepeatMode.Reverse),
                label = "bar$i",
            )
            Box(Modifier.size(width = 4.dp, height = h.dp).clip(RoundedCornerShape(2.dp)).background(RN.Teal))
        }
        Box(Modifier.height(20.dp))
    }
}
