package com.sonas.actiontracker.ui

import android.content.Context
import android.content.res.Configuration
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
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

/** The app's look: follow Android's dark mode (default), or always light / always dark. */
enum class ThemeMode(val label: String) { SYSTEM("System default"), LIGHT("Light"), DARK("Dark") }

/** The theme choice, kept in the "ui" prefs (no schema change) and observable from Compose. */
object ThemePref {
    private const val KEY = "theme"
    private fun prefs(context: Context) = context.getSharedPreferences("ui", Context.MODE_PRIVATE)

    fun read(context: Context): ThemeMode =
        runCatching { ThemeMode.valueOf(prefs(context).getString(KEY, null) ?: "") }.getOrDefault(ThemeMode.SYSTEM)

    /** The current choice for the app UI; [load] fills it at start-up. */
    var mode by mutableStateOf(ThemeMode.SYSTEM)
        private set

    fun load(context: Context) { mode = read(context) }

    fun set(context: Context, value: ThemeMode) {
        prefs(context).edit().putString(KEY, value.name).apply()
        mode = value
    }

    /** Whether [mode] means dark right now, given the system setting (for code outside Compose). */
    fun isDark(context: Context, value: ThemeMode = read(context)): Boolean = when (value) {
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
        ThemeMode.SYSTEM -> (context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
            Configuration.UI_MODE_NIGHT_YES
    }
}

/**
 * "Fresh" palette: airy off-white canvas, an indigo → sky → teal accent gradient,
 * soft pastel glows and white glass surfaces. In dark mode the same roles map to a deep ink
 * canvas, slate glass and slightly brighter accents; [AppTheme] flips [dark], and every read
 * of a colour is snapshot state, so the whole UI (custom colours included) follows.
 */
object Fresh {
    var dark by mutableStateOf(false)
        internal set

    private fun pick(light: Long, night: Long) = Color(if (dark) night else light)

    val Canvas get() = pick(0xFFF6F7FC, 0xFF0F1120)
    val Indigo get() = pick(0xFF5B5FEF, 0xFF7C80FF)
    val Teal get() = pick(0xFF12B5A6, 0xFF2CC7B8)
    val Amber get() = pick(0xFFD98200, 0xFFF0A030)
    val Sky get() = pick(0xFF1E90E8, 0xFF4DA9F2)
    val Green get() = pick(0xFF16A36A, 0xFF34C185)
    val Coral get() = pick(0xFFE5484D, 0xFFFF6B6E)
    val Text get() = pick(0xFF1B1D33, 0xFFE9EAF6)
    val TextDim get() = pick(0xFF6A6E8C, 0xFF9EA2C2)
    val Glass get() = pick(0xCCFFFFFF, 0xCC1D2035)
    val GlassStrong get() = pick(0xFFFFFFFF, 0xFF1D2035)
    val Rim get() = pick(0x1A1B1D33, 0x24FFFFFF)
    /** Sunken tint laid over the aurora behind the list, so the white panels and rows lift off it. */
    val Recess get() = pick(0xB3E4E7F3, 0xB3070812)
    /** God task card: a solid saffron wash with a gold rim and gold label. */
    val Gold get() = pick(0xFFB7791F, 0xFFF0C35A)
    val GoldWash get() = pick(0xFFFFF4DB, 0xFF33280F)
    val GoldRim get() = pick(0xFFF0C35A, 0xFF8F6B1E)

    val accent: Brush get() = Brush.linearGradient(listOf(Indigo, Sky, Teal))
    val accentHorizontal: Brush get() = Brush.horizontalGradient(listOf(Indigo, Sky, Teal))
}

private val FreshColors get() = lightColorScheme(
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

private val FreshDarkColors get() = darkColorScheme(
    primary = Fresh.Indigo,
    onPrimary = Color.White,
    primaryContainer = Color(0xFF2E3170),
    onPrimaryContainer = Color(0xFFE1E2FF),
    secondary = Fresh.Teal,
    onSecondary = Color(0xFF00201C),
    secondaryContainer = Color(0xFF0F4A44),
    onSecondaryContainer = Color(0xFFD3F5F0),
    tertiary = Fresh.Sky,
    onTertiary = Color(0xFF00213A),
    tertiaryContainer = Color(0xFF12405F),
    onTertiaryContainer = Color(0xFFD6EBFF),
    error = Fresh.Coral,
    onError = Color(0xFF410006),
    errorContainer = Color(0xFF6B1A1D),
    onErrorContainer = Color(0xFFFFDAD8),
    background = Fresh.Canvas,
    onBackground = Fresh.Text,
    surface = Fresh.Canvas,
    onSurface = Fresh.Text,
    surfaceVariant = Color(0xFF2A2D44),
    onSurfaceVariant = Fresh.TextDim,
    outline = Color(0xFF6F7390),
    outlineVariant = Color(0xFF34374F),
    inverseSurface = Color(0xFFE9EAF6),
    inverseOnSurface = Color(0xFF1B1D33),
    inversePrimary = Color(0xFF5B5FEF),
    surfaceDim = Color(0xFF0F1120),
    surfaceBright = Color(0xFF2E3149),
    surfaceContainerLowest = Color(0xFF0A0C18),
    surfaceContainerLow = Color(0xFF171A2C),
    surfaceContainer = Color(0xFF1B1E31),
    surfaceContainerHigh = Color(0xFF23263B),
    surfaceContainerHighest = Color(0xFF2C2F46),
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

/**
 * Fresh theme, light or dark per [ThemePref.mode] (System follows Android's dark mode).
 * Dialogs, sheets and pickers pick it up through MaterialTheme.
 */
@Composable
fun AppTheme(content: @Composable () -> Unit) {
    val dark = when (ThemePref.mode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }
    // Set before anything below reads a Fresh colour, so the first frame is already right.
    if (Fresh.dark != dark) Fresh.dark = dark
    val colors = if (dark) FreshDarkColors else FreshColors
    MaterialTheme(colorScheme = colors, typography = AppTypography, shapes = AppShapes, content = content)
}

/** Frosted-glass surface: a faint white tint with a light rim, like a pane over the aurora. */
fun Modifier.glass(shape: Shape, strong: Boolean = false): Modifier =
    this.clip(shape)
        .background(if (strong) Fresh.GlassStrong else Fresh.Glass)
        .border(1.dp, Fresh.Rim, shape)

/**
 * Solid white panel for the stats bar and the bottom dock: lifted off the list with a soft indigo
 * shadow, so the three zones (stats, list, controls) read as separate layers.
 */
fun Modifier.panel(shape: Shape): Modifier =
    this.shadow(24.dp, shape, ambientColor = Fresh.Indigo.copy(alpha = 0.32f), spotColor = Fresh.Indigo.copy(alpha = 0.38f))
        .background(Fresh.GlassStrong, shape)

/** A control on a panel: a soft canvas-tinted well with a rim, so it stands out from the white panel. */
fun Modifier.well(shape: Shape): Modifier =
    this.clip(shape)
        .background(Fresh.Canvas)
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
