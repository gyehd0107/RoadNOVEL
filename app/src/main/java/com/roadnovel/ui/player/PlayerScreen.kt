package com.roadnovel.ui.player

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.roadnovel.story.LineSource
import com.roadnovel.ui.PlayerUi
import com.roadnovel.ui.common.Avatar
import com.roadnovel.ui.common.LoadingAfterDelay
import androidx.compose.foundation.clickable
import com.roadnovel.ui.common.CardShape
import com.roadnovel.ui.common.SubButton
import com.roadnovel.ui.common.avatarFor
import com.roadnovel.ui.theme.RN

/** 재생 화면: 왼쪽 지도, 가운데 인물+자막, 아래 진행 바 */
@Composable
fun PlayerScreen(
    p: PlayerUi,
    moving: Boolean,
    soundOn: Boolean,
    onToggleSound: () -> Unit,
    onQuit: () -> Unit,
    modifier: Modifier = Modifier,
    /** 주행 중이면 신고 버튼 등 부가 기능을 숨김 */
    restricted: Boolean = false,
    reportedText: String? = null,
    onReport: () -> Unit = {},
) {
    val scene = p.story.scene(p.sceneId)
    Row(
        modifier.fillMaxSize().padding(start = 30.dp, end = 30.dp, top = 8.dp, bottom = 24.dp),
        horizontalArrangement = Arrangement.spacedBy(24.dp),
    ) {
        // 이야기 지도
        Column(
            Modifier
                .fillMaxHeight()
                .weight(0.33f)
                .clip(CardShape)
                .background(Color(0xCC0D1322))
                .border(1.dp, RN.Line, CardShape)
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row {
                Text("이야기 지도", style = MaterialTheme.typography.labelMedium, color = RN.Sub)
                Spacer(Modifier.weight(1f))
                Text("${p.lengthMin}분 이야기", style = MaterialTheme.typography.labelMedium, color = RN.Sub)
            }
            StoryMap(p.story, p.path, p.sceneId, p.lengthMin, Modifier.fillMaxWidth().weight(1f))
        }

        // 무대
        Column(Modifier.weight(0.67f).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                Text(
                    "CHAPTER ${scene.chapter}",
                    color = RN.Gold, fontSize = 16.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.5.sp,
                )
                Text(scene.title, style = MaterialTheme.typography.headlineMedium)
            }

            // 자막 (한 줄씩 부드럽게 바뀜)
            Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.CenterStart) {
                AnimatedContent(
                    targetState = p.caption,
                    transitionSpec = { fadeIn() togetherWith fadeOut() },
                    label = "caption",
                ) { cap ->
                    if (cap == null) {
                        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                            Text("이야기가 곧 시작돼요…", style = MaterialTheme.typography.bodyLarge, color = RN.Sub)
                            // 2초 넘게 첫 대사가 안 나오면 로딩 표시
                            LoadingAfterDelay(waiting = true, text = "이야기를 준비하고 있어요")
                        }
                    } else {
                        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                Avatar(avatarFor(cap.who))
                                Text(cap.name, style = MaterialTheme.typography.bodyLarge, color = RN.Sub)
                                // AI 추가 대사 자리에 나온 줄은 정차 중에 신고할 수 있음
                                val reportable = cap.source == LineSource.AI || cap.source == LineSource.EXTRA
                                if (reportable && !restricted && reportedText != cap.text) {
                                    Text(
                                        "🚩 이 대사 신고하기",
                                        color = RN.Sub,
                                        fontSize = 15.sp,
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(999.dp))
                                            .border(1.dp, RN.Line, RoundedCornerShape(999.dp))
                                            .clickable(onClick = onReport)
                                            .padding(horizontal = 16.dp, vertical = 12.dp),
                                    )
                                }
                                when (cap.source) {
                                    // 언어 모델이 실제로 만든 대사에만 표시 (미리 써 둔 추가 대사는 표시 안 함)
                                    LineSource.AI -> Tag("✨ AI", RN.Violet)
                                    LineSource.EXTRA -> Unit
                                    LineSource.INTERLUDE -> Tag("정차 이야기", RN.Teal)
                                    LineSource.SCENE -> Unit
                                }
                            }
                            Text(
                                cap.text,
                                fontFamily = MaterialTheme.typography.headlineMedium.fontFamily,
                                fontSize = 30.sp,
                                lineHeight = 46.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.heightIn(min = 100.dp),
                            )
                        }
                    }
                }
            }

            // 진행 바
            ProgressBar(p)

            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                SubButton(if (soundOn) "🔊 소리 켜짐" else "🔇 자막만", onClick = onToggleSound)
                SubButton("■ 이야기 그만하기", onClick = onQuit)
                Spacer(Modifier.weight(1f))
                Text(
                    if (moving) "주행 중: 선택은 음성으로 · 화면 조작 없음" else "주행 중에는 화면을 만질 필요가 없어요",
                    style = MaterialTheme.typography.labelMedium,
                    color = RN.Dim,
                )
                // 접힌 디버그 패널 자리
                Spacer(Modifier.width(280.dp))
            }
        }
    }
}

@Composable
private fun ProgressBar(p: PlayerUi) {
    val progress by animateFloatAsState(p.progress, label = "progress")
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row {
            Text(p.progressLabel, style = MaterialTheme.typography.labelMedium, color = RN.Sub)
            Spacer(Modifier.weight(1f))
            Text(p.progressRight, style = MaterialTheme.typography.labelMedium, color = RN.Sub)
        }
        Box(
            Modifier.fillMaxWidth().height(12.dp).clip(RoundedCornerShape(99.dp)).background(Color.White.copy(alpha = 0.07f))
        ) {
            Box(
                Modifier.fillMaxHeight().fillMaxWidth(progress).clip(RoundedCornerShape(99.dp)).background(RN.ProgressGradient)
            )
        }
    }
}

@Composable
private fun Tag(text: String, color: Color) {
    Text(
        text,
        color = color,
        fontSize = 13.sp,
        modifier = Modifier
            .border(1.dp, color.copy(alpha = 0.4f), RoundedCornerShape(999.dp))
            .padding(horizontal = 10.dp, vertical = 3.dp),
    )
}
