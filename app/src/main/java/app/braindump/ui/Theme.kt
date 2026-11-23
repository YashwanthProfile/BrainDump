package app.braindump.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.braindump.data.ThemeMode

/** Each part of life has its own colour, used for its icon, charts and accents everywhere. */
object Hues {
    val Journal = Color(0xFFFF8F70)
}

// "Dusk": deep indigo night with warm coral light.
private val Dark = darkColorScheme(
    primary = Color(0xFFFFA58A),
    onPrimary = Color(0xFF3A1206),
    primaryContainer = Color(0xFF4A2A3A),
    onPrimaryContainer = Color(0xFFFFDDD3),
    secondary = Color(0xFFC3B5FF),
    onSecondary = Color(0xFF1F1750),
    secondaryContainer = Color(0xFF2E2A55),
    onSecondaryContainer = Color(0xFFE6E1FF),
    tertiary = Color(0xFF8C9EFF),
    background = Color(0xFF0E0F1C),
    onBackground = Color(0xFFECEAF6),
    surface = Color(0xFF0E0F1C),
    onSurface = Color(0xFFECEAF6),
    surfaceVariant = Color(0xFF222339),
    onSurfaceVariant = Color(0xFFA9A8C2),
    surfaceContainerLowest = Color(0xFF0A0B15),
    surfaceContainerLow = Color(0xFF161728),
    surfaceContainer = Color(0xFF1B1C30),
    surfaceContainerHigh = Color(0xFF222339),
    surfaceContainerHighest = Color(0xFF2A2B44),
    outline = Color(0xFF55567A),
    outlineVariant = Color(0xFF2E2F4A),
)

private val Light = lightColorScheme(
    primary = Color(0xFFDD5F42),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFFFDDD3),
    onPrimaryContainer = Color(0xFF3A1206),
    secondary = Color(0xFF6A5ACD),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFE6E1FF),
    onSecondaryContainer = Color(0xFF1F1750),
    tertiary = Color(0xFF5A6BD8),
    background = Color(0xFFFAF6F2),
    onBackground = Color(0xFF23212E),
    surface = Color(0xFFFAF6F2),
    onSurface = Color(0xFF23212E),
    surfaceVariant = Color(0xFFEDE6E0),
    onSurfaceVariant = Color(0xFF6B6878),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFFFFFFF),
    surfaceContainer = Color(0xFFF3EDE8),
    surfaceContainerHigh = Color(0xFFEDE6E0),
    surfaceContainerHighest = Color(0xFFE6DED8),
    outline = Color(0xFFB5AFBD),
    outlineVariant = Color(0xFFE2DAD4),
)

/** Pure black for AMOLED screens. */
private val Black = Dark.copy(
    background = Color(0xFF000000),
    surface = Color(0xFF000000),
    surfaceContainerLowest = Color(0xFF000000),
    surfaceContainerLow = Color(0xFF0D0D16),
    surfaceContainer = Color(0xFF12121D),
    surfaceContainerHigh = Color(0xFF191926),
    surfaceContainerHighest = Color(0xFF202030),
    surfaceVariant = Color(0xFF191926),
)

private val base = Typography()
private val AppTypography = Typography(
    displaySmall = base.displaySmall.copy(fontFamily = FontFamily.Serif, fontSize = 34.sp, lineHeight = 40.sp),
    headlineMedium = base.headlineMedium.copy(fontFamily = FontFamily.Serif, fontSize = 28.sp, lineHeight = 34.sp),
    headlineSmall = base.headlineSmall.copy(fontSize = 19.sp, lineHeight = 25.sp, fontWeight = FontWeight.Medium),
    titleLarge = base.titleLarge.copy(fontSize = 20.sp, lineHeight = 26.sp, fontWeight = FontWeight.SemiBold),
    titleMedium = base.titleMedium.copy(lineHeight = 22.sp, fontWeight = FontWeight.SemiBold),
    bodyLarge = base.bodyLarge.copy(fontSize = 16.sp, lineHeight = 24.sp),
    bodyMedium = base.bodyMedium.copy(fontSize = 14.sp, lineHeight = 20.sp),
    labelLarge = base.labelLarge.copy(letterSpacing = 0.2.sp),
)

private val AppShapes = Shapes(
    small = RoundedCornerShape(14.dp),
    medium = RoundedCornerShape(22.dp),
    large = RoundedCornerShape(28.dp),
)

@Composable
fun isDark(mode: ThemeMode): Boolean = when (mode) {
    ThemeMode.LIGHT -> false
    ThemeMode.DARK, ThemeMode.BLACK -> true
    ThemeMode.SYSTEM -> isSystemInDarkTheme()
}

@Composable
fun BrainDumpTheme(mode: ThemeMode, content: @Composable () -> Unit) {
    val colors = when {
        mode == ThemeMode.BLACK -> Black
        isDark(mode) -> Dark
        else -> Light
    }
    MaterialTheme(colorScheme = colors, typography = AppTypography, shapes = AppShapes, content = content)
}

val QuietText: TextStyle @Composable get() =
    MaterialTheme.typography.bodyMedium.copy(color = MaterialTheme.colorScheme.onSurfaceVariant)

/** How big your writing reads. 1.0 = 16sp; pinch on a day page or pick a size in Settings. */
val LocalTextScale = androidx.compose.runtime.compositionLocalOf { 1f }

/** Long-form reading text: serif, roomy, like a good blog post. */
val ProseText: TextStyle @Composable get() {
    val k = LocalTextScale.current
    return MaterialTheme.typography.bodyLarge.copy(fontFamily = FontFamily.Serif, fontSize = (16 * k).sp, lineHeight = (26 * k).sp)
}

/** Small uppercase label for dates and times. */
val MarkText: TextStyle @Composable get() =
    MaterialTheme.typography.labelMedium.copy(color = MaterialTheme.colorScheme.onSurfaceVariant, letterSpacing = 1.2.sp)
