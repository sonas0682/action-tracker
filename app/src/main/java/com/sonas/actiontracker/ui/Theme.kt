package com.sonas.actiontracker.ui

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.cos
import kotlin.math.sin

/**
 * "Midnight" palette: near-black canvas, a violet → magenta → amber accent gradient,
 * electric cyan for "today", and translucent glass surfaces.
 */
object Midnight {
    val Canvas = Color(0xFF0A0A12)
    val Violet = Color(0xFF8B5CFF)
    val Magenta = Color(0xFFFF4FB8)
    val Amber = Color(0xFFFFB547)
    val Cyan = Color(0xFF3EE6FF)
    val Lime = Color(0xFFB8FF5C)
    val Coral = Color(0xFFFF6B6B)
    val Text = Color(0xFFF4F2FF)
    val TextDim = Color(0xFFA3A0BF)
    val Glass = Color(0x14FFFFFF)
    val GlassStrong = Color(0x24FFFFFF)
    val Rim = Color(0x1FFFFFFF)

    val accent: Brush get() = Brush.linearGradient(listOf(Violet, Magenta, Amber))
    val accentHorizontal: Brush get() = Brush.horizontalGradient(listOf(Violet, Magenta, Amber))
    val rim: Brush get() = Brush.linearGradient(listOf(Color(0x40FFFFFF), Color(0x08FFFFFF), Color(0x26FFFFFF)))
}

private val MidnightColors = darkColorScheme(
    primary = Midnight.Violet,
    onPrimary = Color.White,
    primaryContainer = Color(0xFF2A1F55),
    onPrimaryContainer = Color(0xFFE6DDFF),
    secondary = Midnight.Magenta,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFF3A1838),
    onSecondaryContainer = Color(0xFFFFD8EE),
    tertiary = Midnight.Cyan,
    onTertiary = Color(0xFF00222A),
    tertiaryContainer = Color(0xFF0D3640),
    onTertiaryContainer = Color(0xFFC8F6FF),
    error = Midnight.Coral,
    onError = Color(0xFF2A0000),
    errorContainer = Color(0xFF4A1518),
    onErrorContainer = Color(0xFFFFDAD6),
    background = Midnight.Canvas,
    onBackground = Midnight.Text,
    surface = Midnight.Canvas,
    onSurface = Midnight.Text,
    surfaceVariant = Color(0xFF221F33),
    onSurfaceVariant = Midnight.TextDim,
    outline = Color(0xFF4A4763),
    outlineVariant = Color(0xFF2C2940),
    inverseSurface = Midnight.Text,
    inverseOnSurface = Midnight.Canvas,
    inversePrimary = Color(0xFF5B34D6),
    surfaceDim = Midnight.Canvas,
    surfaceBright = Color(0xFF2A2740),
    surfaceContainerLowest = Color(0xFF07070D),
    surfaceContainerLow = Color(0xFF12111D),
    surfaceContainer = Color(0xFF181726),
    surfaceContainerHigh = Color(0xFF1F1D30),
    surfaceContainerHighest = Color(0xFF28263B),
)

private val base = Typography()

private val AppTypography = Typography(
    displayLarge = base.displayLarge.copy(fontWeight = FontWeight.Black, letterSpacing = (-2).sp),
    displayMedium = base.displayMedium.copy(fontWeight = FontWeight.Black, letterSpacing = (-1.5).sp),
    displaySmall = base.displaySmall.copy(fontWeight = FontWeight.ExtraBold, letterSpacing = (-1).sp),
    headlineLarge = base.headlineLarge.copy(fontWeight = FontWeight.ExtraBold, letterSpacing = (-1).sp),
    headlineMedium = base.headlineMedium.copy(fontWeight = FontWeight.ExtraBold, letterSpacing = (-0.5).sp),
    headlineSmall = base.headlineSmall.copy(fontWeight = FontWeight.Bold, letterSpacing = (-0.25).sp),
    titleLarge = base.titleLarge.copy(fontWeight = FontWeight.Bold),
    titleMedium = base.titleMedium.copy(fontWeight = FontWeight.Bold),
    titleSmall = base.titleSmall.copy(fontWeight = FontWeight.Bold),
    bodyLarge = base.bodyLarge.copy(fontWeight = FontWeight.Medium),
    labelLarge = base.labelLarge.copy(fontWeight = FontWeight.Bold, letterSpacing = 0.2.sp),
    labelMedium = base.labelMedium.copy(fontWeight = FontWeight.SemiBold),
    labelSmall = base.labelSmall.copy(fontWeight = FontWeight.Bold, letterSpacing = 1.2.sp),
)

private val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(10.dp),
    small = RoundedCornerShape(14.dp),
    medium = RoundedCornerShape(20.dp),
    large = RoundedCornerShape(28.dp),
    extraLarge = RoundedCornerShape(36.dp),
)

/** Dark-first Midnight theme. Dialogs, sheets and pickers pick it up through MaterialTheme. */
@Composable
fun AppTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = MidnightColors, typography = AppTypography, shapes = AppShapes, content = content)
}

/** Frosted-glass surface: a faint white tint with a light rim, like a pane over the aurora. */
fun Modifier.glass(shape: Shape, strong: Boolean = false): Modifier =
    this.clip(shape)
        .background(if (strong) Midnight.GlassStrong else Midnight.Glass)
        .border(1.dp, Midnight.rim, shape)

/** Full-screen canvas with slowly drifting violet, magenta and cyan glows behind the content. */
@Composable
fun AuroraBackground(modifier: Modifier = Modifier, content: @Composable BoxScope.() -> Unit) {
    val drift = rememberInfiniteTransition(label = "aurora")
    val t by drift.animateFloat(
        initialValue = 0f, targetValue = (2 * Math.PI).toFloat(),
        animationSpec = infiniteRepeatable(tween(24_000, easing = LinearEasing), RepeatMode.Restart),
        label = "t"
    )
    Box(
        modifier
            .fillMaxSize()
            .background(Midnight.Canvas)
            .drawBehind {
                val w = size.width
                val h = size.height
                fun glow(color: Color, cx: Float, cy: Float, r: Float) = drawCircle(
                    Brush.radialGradient(listOf(color, Color.Transparent), center = Offset(cx, cy), radius = r),
                    radius = r, center = Offset(cx, cy)
                )
                glow(Midnight.Violet.copy(alpha = 0.40f), w * (0.15f + 0.10f * cos(t)), h * (0.08f + 0.04f * sin(t)), w * 0.85f)
                glow(Midnight.Magenta.copy(alpha = 0.26f), w * (0.95f + 0.08f * sin(t)), h * (0.22f + 0.05f * cos(t)), w * 0.70f)
                glow(Midnight.Cyan.copy(alpha = 0.12f), w * (0.30f + 0.12f * sin(t * 2)), h * (0.92f + 0.03f * cos(t)), w * 0.80f)
            },
        content = content
    )
}
