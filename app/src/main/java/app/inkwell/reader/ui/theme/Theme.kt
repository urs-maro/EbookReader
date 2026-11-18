package app.inkwell.reader.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight

private val Accent = Color(0xFFC9A24B)

private val LightColors = lightColorScheme(
    primary = Accent,
    background = Color(0xFFFBF8F3),
    surface = Color(0xFFFFFFFF),
    onBackground = Color(0xFF1C1B19),
    onSurface = Color(0xFF1C1B19),
)

private val DarkColors = darkColorScheme(
    primary = Accent,
    background = Color(0xFF14161A),
    surface = Color(0xFF1B1E23),
    onBackground = Color(0xFFD9DBDF),
    onSurface = Color(0xFFD9DBDF),
)

private val AppTypography = Typography(
    headlineMedium = TextStyle(fontFamily = LiterataFamily, fontWeight = FontWeight.Bold),
    titleLarge = TextStyle(fontFamily = LiterataFamily, fontWeight = FontWeight.SemiBold),
    titleMedium = TextStyle(fontFamily = InterFamily, fontWeight = FontWeight.Medium),
    bodyLarge = TextStyle(fontFamily = InterFamily),
    bodyMedium = TextStyle(fontFamily = InterFamily),
    labelLarge = TextStyle(fontFamily = InterFamily, fontWeight = FontWeight.Medium),
)

@Composable
fun InkwellTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (isSystemInDarkTheme()) DarkColors else LightColors,
        typography = AppTypography,
        content = content,
    )
}
