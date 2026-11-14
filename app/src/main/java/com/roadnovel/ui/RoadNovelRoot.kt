package com.roadnovel.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.roadnovel.ui.common.Chip
import com.roadnovel.ui.common.Dot
import com.roadnovel.ui.common.NightSky
import com.roadnovel.ui.ending.EndingScreen
import com.roadnovel.ui.home.HomeScreen
import com.roadnovel.ui.player.ChoiceOverlay
import com.roadnovel.ui.player.DebugPanel
import com.roadnovel.ui.player.PlayerScreen
import com.roadnovel.ui.player.QuitDialog
import com.roadnovel.ui.select.SelectScreen
import com.roadnovel.ui.theme.RN
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.delay

/** 앱의 맨 바깥 화면: 배경 + 상단 바 + 지금 화면 + 겹치는 것들(선택지, 디버그, 알림) */
@Composable
fun RoadNovelRoot(vm: RoadNovelViewModel = hiltViewModel()) {
    val ui by vm.ui.collectAsState()

    // 뒤로 가기: 재생 중이면 "저장할까요?", 아니면 홈으로
    BackHandler(enabled = ui.screen != ScreenId.HOME) {
        if (ui.screen == ScreenId.PLAYER) vm.quitStory() else vm.goHome()
    }

    Box(Modifier.fillMaxSize().background(RN.Bg)) {
        NightSky()
        Column(Modifier.fillMaxSize().safeDrawingPadding()) {
            TopBar(ui, onLogoLongPress = vm::showDebug)
            Box(Modifier.weight(1f)) {
                Crossfade(ui.screen, label = "screen") { screen ->
                    when (screen) {
                        ScreenId.HOME -> HomeScreen(
                            resume = ui.resume,
                            badges = ui.badges,
                            onResume = vm::resumeStory,
                            onNewStory = vm::goSelect,
                        )
                        ScreenId.SELECT -> SelectScreen(
                            ui = ui,
                            onSelectStory = vm::selectStory,
                            onSelectLength = vm::selectLength,
                            onBack = vm::goHome,
                            onStart = vm::startStory,
                        )
                        ScreenId.PLAYER -> ui.player?.let {
                            PlayerScreen(
                                it,
                                moving = ui.moving,
                                soundOn = ui.soundOn,
                                onToggleSound = vm::toggleSound,
                                onQuit = vm::quitStory,
                            )
                        }
                        ScreenId.ENDING -> ui.ending?.let { EndingScreen(it, onHome = vm::goHome) }
                    }
                }
            }
        }

        // 선택지 (화면 전체를 덮음)
        val choice = ui.player?.choice
        AnimatedVisibility(ui.screen == ScreenId.PLAYER && choice != null, enter = fadeIn(), exit = fadeOut()) {
            choice?.let { ChoiceOverlay(it, onPick = vm::pickChoice) }
        }

        // "저장할까요?" 대화상자
        AnimatedVisibility(ui.screen == ScreenId.PLAYER && ui.quitDialog, enter = fadeIn(), exit = fadeOut()) {
            QuitDialog(
                onSave = { vm.finishQuit(save = true) },
                onDiscard = { vm.finishQuit(save = false) },
                onContinue = vm::cancelQuit,
            )
        }

        // 디버그 패널 (재생 화면 오른쪽 아래)
        if (ui.screen == ScreenId.PLAYER && !ui.debugHidden) {
            DebugPanel(
                open = ui.debugOpen,
                speedKmh = ui.speedKmh,
                timeScale = ui.timeScale,
                etaMin = ui.etaMin,
                stats = ui.player?.stats,
                onToggle = vm::toggleDebug,
                onHide = vm::hideDebug,
                onForceKill = vm::forceKill,
                testAudios = if (ui.player?.choice != null) ui.testAudios.map { it to (RoadNovelViewModel.TEST_AUDIO_LABELS[it] ?: it) } else emptyList(),
                onTestAudio = vm::sendTestAudio,
                vehicleSource = ui.vehicleSource,
                onVehicleSource = vm::setVehicleSource,
                pleosStatus = ui.pleosStatus,
                gear = ui.gear?.label,
                odometerKm = ui.odometerKm,
                aiStatus = ui.aiStatus,
                onStartDemo = vm::startDemo,
                onSpeed = vm::setSpeed,
                onTimeScale = vm::setTimeScale,
                onEta = vm::setEta,
                modifier = Modifier.align(Alignment.BottomEnd).safeDrawingPadding().padding(16.dp),
            )
        }

        // 알림 한 줄
        AnimatedVisibility(
            ui.toast != null,
            enter = fadeIn(), exit = fadeOut(),
            modifier = Modifier.align(Alignment.TopCenter).safeDrawingPadding().padding(top = 130.dp),
        ) {
            Text(
                ui.toast.orEmpty(),
                color = Color(0xFFD6FFF9),
                fontSize = 18.sp,
                modifier = Modifier
                    .clip(RoundedCornerShape(14.dp))
                    .background(Color(0xFF0E3B37))
                    .border(1.dp, RN.Teal, RoundedCornerShape(14.dp))
                    .padding(horizontal = 22.dp, vertical = 12.dp),
            )
        }

        ui.loadError?.let {
            Text("이야기를 불러오지 못했어요: $it", color = RN.Rose, modifier = Modifier.align(Alignment.Center))
        }
    }
}

@Composable
private fun TopBar(ui: UiState, onLogoLongPress: () -> Unit) {
    val moving = ui.moving
    Row(
        Modifier.fillMaxWidth().padding(start = 30.dp, end = 30.dp, top = 16.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Text(
            buildAnnotatedString {
                append("ROAD ")
                withStyle(SpanStyle(color = RN.Gold)) { append("NOVEL") }
            },
            fontWeight = FontWeight.Black,
            letterSpacing = 2.5.sp,
            fontSize = 18.sp,
            color = RN.Text,
            modifier = Modifier.pointerInput(Unit) { detectTapGestures(onLongPress = { onLogoLongPress() }) },
        )
        Spacer(Modifier.weight(1f))
        Chip {
            Dot(if (moving) RN.Teal else RN.Rose)
            val gearText = ui.gear?.let { " (${it.label})" }.orEmpty()
            val speedText = ui.liveSpeedKmh?.let { " ${it.toInt()}km/h" }.orEmpty()
            Text(if (moving) "주행 중$speedText$gearText" else "정차 중$gearText", color = RN.Sub, fontSize = 15.sp)
        }
        Chip {
            Text("🧭 도착까지", color = RN.Sub, fontSize = 15.sp)
            Text(ui.etaMin?.let { "${it}분" } ?: "-", color = RN.Text, fontSize = 15.sp, fontWeight = FontWeight.Bold)
        }
        Chip { Clock() }
    }
}

@Composable
private fun Clock() {
    val fmt = remember { DateTimeFormatter.ofPattern("HH:mm") }
    var now by remember { mutableStateOf(LocalTime.now().format(fmt)) }
    LaunchedEffect(Unit) {
        while (true) {
            now = LocalTime.now().format(fmt)
            delay(5_000)
        }
    }
    Text(now, color = RN.Sub, fontSize = 15.sp)
}
