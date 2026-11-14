package com.roadnovel.ui.player

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.roadnovel.ui.common.GoButton
import com.roadnovel.ui.common.LoadingAfterDelay
import com.roadnovel.ui.common.SubButton
import com.roadnovel.ui.theme.RN

/** "이야기를 여기서 멈출까요?" — 저장하고 끝내기 / 저장 안 함 / 계속 듣기 */
@Composable
fun QuitDialog(onSave: () -> Unit, onDiscard: () -> Unit, onContinue: () -> Unit, saving: Boolean = false) {
    Box(
        Modifier
            .fillMaxSize()
            .background(Color(0xD905070C))
            .clickable(remember { MutableInteractionSource() }, indication = null) {},
        contentAlignment = Alignment.Center,
    ) {
        val shape = RoundedCornerShape(24.dp)
        Column(
            Modifier
                .widthIn(max = 760.dp)
                .clip(shape)
                .background(RN.Panel)
                .border(1.dp, RN.Line, shape)
                .padding(horizontal = 40.dp, vertical = 34.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            Text("이야기를 여기서 멈출까요?", fontSize = 30.sp, fontWeight = FontWeight.Bold)
            Text(
                "지금까지의 장면, 선택, 얻은 물건을 저장하면\n다음 주행에서 이어서 들을 수 있어요.",
                color = RN.Sub, fontSize = 19.sp, lineHeight = 29.sp, textAlign = TextAlign.Center,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                GoButton("저장하고 끝내기", onClick = onSave)
                SubButton("저장 안 함", onClick = onDiscard)
                SubButton("계속 듣기", onClick = onContinue)
            }
            LoadingAfterDelay(waiting = saving, text = "저장하고 있어요")
        }
    }
}
