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
    primary = Color(0xFF1B7F4E),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFCDEFD9),
    onPrimaryContainer = Color(0xFF0B3A22),
    secondary = Color(0xFF4F6358),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFDCE9E0),
    onSecondaryContainer = Color(0xFF1B2D23),
    tertiary = Color(0xFF8A5A00),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFFFE2B0),
    onTertiaryContainer = Color(0xFF2D1B00),
    background = Color(0xFFF8F7F3),
    onBackground = Color(0xFF1A1C1A),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF1A1C1A),
    surfaceVariant = Color(0xFFE8EBE6),
    onSurfaceVariant = Color(0xFF444B46),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFF3F4F0),
    surfaceContainer = Color(0xFFEEF0EB),
    surfaceContainerHigh = Color(0xFFE8EBE5),
    surfaceContainerHighest = Color(0xFFE2E6DF),
    outline = Color(0xFF727972),
    outlineVariant = Color(0xFFC3C9C2),
    error = Color(0xFFB3261E),
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFF9DEDC),
    onErrorContainer = Color(0xFF410E0B),
)

private val DarkColors: ColorScheme = darkColorScheme(
    primary = Color(0xFF5BD68A),
    onPrimary = Color(0xFF00391C),
    primaryContainer = Color(0xFF1E4A31),
    onPrimaryContainer = Color(0xFFCDEFD9),
    secondary = Color(0xFFB4CCBC),
    onSecondary = Color(0xFF1F352A),
    secondaryContainer = Color(0xFF354B3F),
    onSecondaryContainer = Color(0xFFDCE9E0),
    tertiary = Color(0xFFFFBA45),
    onTertiary = Color(0xFF462B00),
    tertiaryContainer = Color(0xFF654000),
    onTertiaryContainer = Color(0xFFFFE2B0),
    background = Color(0xFF111411),
    onBackground = Color(0xFFE1E3DE),
    surface = Color(0xFF181C18),
    onSurface = Color(0xFFE1E3DE),
    surfaceVariant = Color(0xFF2B312C),
    onSurfaceVariant = Color(0xFFC0C8C1),
    surfaceContainerLowest = Color(0xFF0D100D),
    surfaceContainerLow = Color(0xFF181C18),
    surfaceContainer = Color(0xFF1C201C),
    surfaceContainerHigh = Color(0xFF262B26),
    surfaceContainerHighest = Color(0xFF313631),
    outline = Color(0xFF8A928B),
    outlineVariant = Color(0xFF414842),
    error = Color(0xFFF2B8B5),
    onError = Color(0xFF601410),
    errorContainer = Color(0xFF8C1D18),
    onErrorContainer = Color(0xFFF9DEDC),
)

private val AppTypography = Typography(
    headlineMedium = TextStyle(fontSize = 26.sp, lineHeight = 32.sp, fontWeight = FontWeight.SemiBold),
    titleLarge = TextStyle(fontSize = 21.sp, lineHeight = 27.sp, fontWeight = FontWeight.SemiBold),
    titleMedium = TextStyle(fontSize = 17.sp, lineHeight = 23.sp, fontWeight = FontWeight.SemiBold),
    titleSmall = TextStyle(fontSize = 15.sp, lineHeight = 20.sp, fontWeight = FontWeight.SemiBold),
    bodyLarge = TextStyle(fontSize = 17.sp, lineHeight = 24.sp),
    bodyMedium = TextStyle(fontSize = 15.sp, lineHeight = 21.sp),
    bodySmall = TextStyle(fontSize = 13.sp, lineHeight = 18.sp),
    labelLarge = TextStyle(fontSize = 15.sp, lineHeight = 20.sp, fontWeight = FontWeight.Medium),
    labelMedium = TextStyle(fontSize = 13.sp, lineHeight = 18.sp, fontWeight = FontWeight.Medium),
    labelSmall = TextStyle(fontSize = 12.sp, lineHeight = 16.sp, fontWeight = FontWeight.Medium),
)

private val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(6.dp),
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(14.dp),
    large = RoundedCornerShape(18.dp),
    extraLarge = RoundedCornerShape(26.dp),
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
