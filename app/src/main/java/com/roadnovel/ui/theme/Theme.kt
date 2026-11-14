package com.roadnovel.ui.theme

import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// 프로토타입(prototype/index.html)과 같은 밤하늘 색
object RN {
    val Bg = Color(0xFF0A0E18)
    val BgTop = Color(0xFF1A2340)
    val Panel = Color(0xFF121828)
    val Panel2 = Color(0xFF182036)
    val Line = Color(0xFF263049)
    val LineStrong = Color(0xFF3A4766)
    val Text = Color(0xFFEEF1F8)
    val Sub = Color(0xFF9AA5BF)
    val Dim = Color(0xFF5F6A85)
    val Gold = Color(0xFFF4B860)
    val Gold2 = Color(0xFFFFD89A)
    val Orange = Color(0xFFE8935A)
    val Teal = Color(0xFF5FD3C4)
    val Rose = Color(0xFFFF8A8A)
    val Violet = Color(0xFFC9B6FF)
    val OnGold = Color(0xFF1B1307)
    val OnTeal = Color(0xFF062421)
    val SelectedBg = Color(0xFF2A2214)

    val GoldGradient = Brush.linearGradient(listOf(Gold, Orange))
    val ProgressGradient = Brush.horizontalGradient(listOf(Teal, Gold))
}

/** 차량 화면용 최소 터치 크기 (CLAUDE.md: 76dp 이상) */
val MinTouch = 76.dp

/** 이야기 글씨(자막·제목)는 명조 느낌 */
val StoryFont = FontFamily.Serif

private val Colors = darkColorScheme(
    primary = RN.Gold,
    onPrimary = RN.OnGold,
    secondary = RN.Teal,
    background = RN.Bg,
    onBackground = RN.Text,
    surface = RN.Panel,
    onSurface = RN.Text,
    onSurfaceVariant = RN.Sub,
    outline = RN.Line,
)

// 운전 중에도 읽기 쉽게 큰 글씨
private val CarTypography = Typography(
    displayMedium = TextStyle(fontFamily = StoryFont, fontSize = 44.sp, lineHeight = 58.sp, fontWeight = FontWeight.Bold),
    headlineMedium = TextStyle(fontFamily = StoryFont, fontSize = 32.sp, lineHeight = 44.sp, fontWeight = FontWeight.Bold),
    titleLarge = TextStyle(fontSize = 26.sp, fontWeight = FontWeight.Bold),
    titleMedium = TextStyle(fontSize = 21.sp, fontWeight = FontWeight.Bold),
    bodyLarge = TextStyle(fontSize = 20.sp, lineHeight = 30.sp),
    bodyMedium = TextStyle(fontSize = 17.sp, lineHeight = 25.sp),
    labelLarge = TextStyle(fontSize = 22.sp, fontWeight = FontWeight.Bold),
    labelMedium = TextStyle(fontSize = 16.sp, fontWeight = FontWeight.Medium),
)

/** 앱은 항상 다크 테마 (차 안 야간 눈부심 방지) */
@Composable
fun RoadNovelTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = Colors, typography = CarTypography) {
        // 색을 따로 안 정한 글자도 밝게 보이도록 기본 글자색 지정
        CompositionLocalProvider(LocalContentColor provides RN.Text, content = content)
    }
}
