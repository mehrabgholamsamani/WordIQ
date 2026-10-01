package com.wordiq.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.dp

val BackgroundPrimary = Color(0xFFF5F8FC)
val BackgroundSecondary = Color(0xFFEDF3F9)
val Surface = Color(0xFFFFFFFF)
val SurfaceElevated = Color(0xFFFFFFFF)
val BluePrimary = Color(0xFF1768D5)
val BluePressed = Color(0xFF0F56B7)
val BlueSoft = Color(0xFFE4EFFD)
val TextPrimary = Color(0xFF14233A)
val TextSecondary = Color(0xFF5B6E87)
val TextTertiary = Color(0xFF8291A5)
val DividerSoft = Color(0xFFE4EAF1)
val ErrorSoft = Color(0xFFFFE9E8)
val SuccessSoft = Color(0xFFE7F3EF)

object Spacing {
    val XS = 4.dp
    val S = 8.dp
    val M = 12.dp
    val L = 16.dp
    val XL = 20.dp
    val XXL = 28.dp
    val XXXL = 36.dp
}

object Radius {
    val Small = 12.dp
    val Medium = 18.dp
    val Large = 24.dp
    val Pill = 100.dp
}

object MotionTokens {
    const val PressedScale = 0.98f
    const val Damping = 0.92f
    const val Stiffness = 650f
}

private val WordIqColors = lightColorScheme(
    primary = BluePrimary,
    onPrimary = Color.White,
    primaryContainer = BlueSoft,
    onPrimaryContainer = TextPrimary,
    secondary = Color(0xFF486B92),
    background = BackgroundPrimary,
    onBackground = TextPrimary,
    surface = Surface,
    onSurface = TextPrimary,
    surfaceVariant = BackgroundSecondary,
    onSurfaceVariant = TextSecondary,
    outline = DividerSoft,
    errorContainer = ErrorSoft,
)

private val WordIqTypography = Typography(
    displaySmall = TextStyle(fontSize = 38.sp, lineHeight = 43.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.4).sp),
    headlineLarge = TextStyle(fontSize = 30.sp, lineHeight = 36.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.2).sp),
    headlineMedium = TextStyle(fontSize = 24.sp, lineHeight = 30.sp, fontWeight = FontWeight.Bold),
    titleLarge = TextStyle(fontSize = 20.sp, lineHeight = 26.sp, fontWeight = FontWeight.SemiBold),
    titleMedium = TextStyle(fontSize = 17.sp, lineHeight = 23.sp, fontWeight = FontWeight.SemiBold),
    bodyLarge = TextStyle(fontSize = 17.sp, lineHeight = 25.sp),
    bodyMedium = TextStyle(fontSize = 15.sp, lineHeight = 22.sp),
    labelLarge = TextStyle(fontSize = 15.sp, lineHeight = 20.sp, fontWeight = FontWeight.SemiBold),
    labelMedium = TextStyle(fontSize = 13.sp, lineHeight = 18.sp, fontWeight = FontWeight.SemiBold),
)

@Composable
fun WordIqTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = WordIqColors, typography = WordIqTypography, content = content)
}
