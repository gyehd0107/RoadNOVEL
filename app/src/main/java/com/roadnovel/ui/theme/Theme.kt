package com.roadnovel.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// 밤하늘 느낌의 다크 색상
val NightBg = Color(0xFF0B1020)
val NightSurface = Color(0xFF161D35)
val StarGold = Color(0xFFFFC857)
val SkyBlue = Color(0xFF7FB7FF)
val TextMain = Color(0xFFF2F4FA)
val TextSub = Color(0xFFA9B1C9)

/** 차량 화면용 최소 터치 크기 (CLAUDE.md: 76dp 이상) */
val MinTouch = 76.dp

private val Colors = darkColorScheme(
    primary = StarGold,
    onPrimary = NightBg,
    secondary = SkyBlue,
    background = NightBg,
    onBackground = TextMain,
    surface = NightSurface,
    onSurface = TextMain,
    onSurfaceVariant = TextSub,
)

// 운전 중에도 읽기 쉽게 큰 글씨
private val CarTypography = Typography(
    displayMedium = TextStyle(fontSize = 56.sp, fontWeight = FontWeight.Bold, letterSpacing = 4.sp),
    titleLarge = TextStyle(fontSize = 32.sp, fontWeight = FontWeight.SemiBold),
    bodyLarge = TextStyle(fontSize = 26.sp),
    labelLarge = TextStyle(fontSize = 26.sp, fontWeight = FontWeight.SemiBold),
)

/** 앱은 항상 다크 테마 (차 안 야간 눈부심 방지) */
@Composable
fun RoadNovelTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = Colors, typography = CarTypography, content = content)
}
