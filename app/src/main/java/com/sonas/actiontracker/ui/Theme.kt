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
import androidx.compose.material3.lightColorScheme
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
 * "Fresh" palette: airy off-white canvas, an indigo → sky → teal accent gradient,
 * soft pastel glows and white glass surfaces.
 */
object Fresh {
    val Canvas = Color(0xFFF6F7FC)
    val Indigo = Color(0xFF5B5FEF)
    val Teal = Color(0xFF12B5A6)
    val Amber = Color(0xFFD98200)
    val Sky = Color(0xFF1E90E8)
    val Green = Color(0xFF16A36A)
    val Coral = Color(0xFFE5484D)
    val Text = Color(0xFF1B1D33)
    val TextDim = Color(0xFF6A6E8C)
    val Glass = Color(0xCCFFFFFF)
    val GlassStrong = Color(0xFFFFFFFF)
    val Rim = Color(0x1A1B1D33)

    val accent: Brush get() = Brush.linearGradient(listOf(Indigo, Sky, Teal))
    val accentHorizontal: Brush get() = Brush.horizontalGradient(listOf(Indigo, Sky, Teal))
}

private val FreshColors = lightColorScheme(
    primary = Fresh.Indigo,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFE1E2FF),
    onPrimaryContainer = Color(0xFF14165E),
    secondary = Fresh.Teal,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFD3F5F0),
    onSecondaryContainer = Color(0xFF00201C),
    tertiary = Fresh.Sky,
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFD6EBFF),
    onTertiaryContainer = Color(0xFF00213A),
    error = Fresh.Coral,
    onError = Color.White,
    errorContainer = Color(0xFFFFDAD8),
    onErrorContainer = Color(0xFF410006),
    background = Fresh.Canvas,
    onBackground = Fresh.Text,
    surface = Fresh.Canvas,
    onSurface = Fresh.Text,
    surfaceVariant = Color(0xFFE6E7F2),
    onSurfaceVariant = Fresh.TextDim,
    outline = Color(0xFF9396B0),
    outlineVariant = Color(0xFFD5D7E6),
    inverseSurface = Fresh.Text,
    inverseOnSurface = Fresh.Canvas,
    inversePrimary = Color(0xFFBEC1FF),
    surfaceDim = Color(0xFFDDDFEC),
    surfaceBright = Color.White,
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = Color(0xFFF9FAFE),
    surfaceContainer = Color(0xFFF1F2F9),
    surfaceContainerHigh = Color(0xFFEBECF5),
    surfaceContainerHighest = Color(0xFFE5E7F1),
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

/** Light Fresh theme. Dialogs, sheets and pickers pick it up through MaterialTheme. */
@Composable
fun AppTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = FreshColors, typography = AppTypography, shapes = AppShapes, content = content)
}

/** Frosted-glass surface: a faint white tint with a light rim, like a pane over the aurora. */
fun Modifier.glass(shape: Shape, strong: Boolean = false): Modifier =
    this.clip(shape)
        .background(if (strong) Fresh.GlassStrong else Fresh.Glass)
        .border(1.dp, Fresh.Rim, shape)

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
            .background(Fresh.Canvas)
            .drawBehind {
                val w = size.width
                val h = size.height
                fun glow(color: Color, cx: Float, cy: Float, r: Float) = drawCircle(
                    Brush.radialGradient(listOf(color, Color.Transparent), center = Offset(cx, cy), radius = r),
                    radius = r, center = Offset(cx, cy)
                )
                glow(Fresh.Indigo.copy(alpha = 0.16f), w * (0.15f + 0.10f * cos(t)), h * (0.08f + 0.04f * sin(t)), w * 0.85f)
                glow(Fresh.Teal.copy(alpha = 0.14f), w * (0.95f + 0.08f * sin(t)), h * (0.22f + 0.05f * cos(t)), w * 0.70f)
                glow(Fresh.Sky.copy(alpha = 0.12f), w * (0.30f + 0.12f * sin(t * 2)), h * (0.92f + 0.03f * cos(t)), w * 0.80f)
            },
        content = content
    )
}
