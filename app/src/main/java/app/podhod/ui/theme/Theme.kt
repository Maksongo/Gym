package app.podhod.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.shape.RoundedCornerShape
import app.podhod.domain.ThemeMode

private val LightColors: ColorScheme = lightColorScheme(
    primary = Color(0xFF3F7D00),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFD9F5A8),
    onPrimaryContainer = Color(0xFF1B3300),
    secondary = Color(0xFF50604A),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFE0EBD6),
    onSecondaryContainer = Color(0xFF1C2A17),
    tertiary = Color(0xFFB45309),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFFFE0C2),
    onTertiaryContainer = Color(0xFF331500),
    background = Color(0xFFF4F5F2),
    onBackground = Color(0xFF14171A),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF14171A),
    surfaceVariant = Color(0xFFE6E9E3),
    onSurfaceVariant = Color(0xFF454B42),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFF8F9F6),
    surfaceContainer = Color(0xFFFFFFFF),
    surfaceContainerHigh = Color(0xFFEEF0EB),
    surfaceContainerHighest = Color(0xFFFFFFFF),
    outline = Color(0xFF787F74),
    outlineVariant = Color(0xFFD3D8CE),
    error = Color(0xFFBA1A1A),
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFFFDAD6),
    onErrorContainer = Color(0xFF410002),
)

/** Тёмная спортивная палитра: графит + электрический лайм. */
private val DarkColors: ColorScheme = darkColorScheme(
    primary = Color(0xFFC6F135),
    onPrimary = Color(0xFF1A2300),
    primaryContainer = Color(0xFF2C3A0A),
    onPrimaryContainer = Color(0xFFDDF98A),
    secondary = Color(0xFF9DA4B0),
    onSecondary = Color(0xFF14171C),
    secondaryContainer = Color(0xFF2A3018),
    onSecondaryContainer = Color(0xFFDDF98A),
    tertiary = Color(0xFFFF8A3D),
    onTertiary = Color(0xFF3A1600),
    tertiaryContainer = Color(0xFF4A2308),
    onTertiaryContainer = Color(0xFFFFD8BD),
    background = Color(0xFF0B0C0F),
    onBackground = Color(0xFFF2F3F5),
    surface = Color(0xFF14161B),
    onSurface = Color(0xFFF2F3F5),
    surfaceVariant = Color(0xFF22252C),
    onSurfaceVariant = Color(0xFF9AA0AB),
    surfaceContainerLowest = Color(0xFF08090B),
    surfaceContainerLow = Color(0xFF101216),
    surfaceContainer = Color(0xFF15181D),
    surfaceContainerHigh = Color(0xFF1C1F26),
    surfaceContainerHighest = Color(0xFF1C1F26),
    outline = Color(0xFF626976),
    outlineVariant = Color(0xFF2A2E36),
    error = Color(0xFFFF6B6B),
    onError = Color(0xFF3B0000),
    errorContainer = Color(0xFF5A1414),
    onErrorContainer = Color(0xFFFFD9D9),
)

private val AppTypography = Typography(
    headlineMedium = TextStyle(fontSize = 28.sp, lineHeight = 34.sp, fontWeight = FontWeight.ExtraBold),
    titleLarge = TextStyle(fontSize = 24.sp, lineHeight = 30.sp, fontWeight = FontWeight.ExtraBold),
    titleMedium = TextStyle(fontSize = 17.sp, lineHeight = 23.sp, fontWeight = FontWeight.Bold),
    titleSmall = TextStyle(fontSize = 15.sp, lineHeight = 20.sp, fontWeight = FontWeight.Bold),
    bodyLarge = TextStyle(fontSize = 17.sp, lineHeight = 24.sp),
    bodyMedium = TextStyle(fontSize = 15.sp, lineHeight = 21.sp),
    bodySmall = TextStyle(fontSize = 13.sp, lineHeight = 18.sp),
    labelLarge = TextStyle(fontSize = 15.sp, lineHeight = 20.sp, fontWeight = FontWeight.Bold),
    labelMedium = TextStyle(fontSize = 12.sp, lineHeight = 16.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 0.5.sp),
    labelSmall = TextStyle(fontSize = 11.sp, lineHeight = 15.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 0.5.sp),
)

private val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(18.dp),
    large = RoundedCornerShape(22.dp),
    extraLarge = RoundedCornerShape(30.dp),
)

@Composable
fun isDarkTheme(mode: ThemeMode): Boolean = when (mode) {
    ThemeMode.SYSTEM -> isSystemInDarkTheme()
    ThemeMode.LIGHT -> false
    ThemeMode.DARK -> true
}

@Composable
fun PodhodTheme(mode: ThemeMode, content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (isDarkTheme(mode)) DarkColors else LightColors,
        typography = AppTypography,
        shapes = AppShapes,
        content = content,
    )
}
