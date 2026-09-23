package ru.krugzaboty.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight

// Тёплая палитра «заботы»; контрасты ≥4.5 (docs/02 §18).
private val LightScheme = lightColorScheme(
    primary = Color(0xFF7A5C43), onPrimary = Color(0xFFFFFFFF),
    secondary = Color(0xFFC96F4A), onSecondary = Color(0xFFFFFFFF),
    background = Color(0xFFFBF7F1), onBackground = Color(0xFF1F1A15),
    surface = Color(0xFFFFFBF6), onSurface = Color(0xFF1F1A15),
    surfaceVariant = Color(0xFFEDE3D6), onSurfaceVariant = Color(0xFF4C443B),
    error = Color(0xFF8C4A3B),
)
private val DarkScheme = darkColorScheme(
    primary = Color(0xFFD7BB9F), onPrimary = Color(0xFF3E2C1B),
    secondary = Color(0xFFE8A184), onSecondary = Color(0xFF46200F),
    background = Color(0xFF191512), onBackground = Color(0xFFECE2D8),
    surface = Color(0xFF211C18), onSurface = Color(0xFFECE2D8),
)

/** Крупный базовый кегль — аудитория 45+, плюс системный scaled font до 200%. */
private val KrugTypography = Typography().let { base ->
    base.copy(
        headlineMedium = base.headlineMedium.copy(fontWeight = FontWeight.SemiBold, lineHeight = base.headlineMedium.lineHeight),
        titleLarge = base.titleLarge.copy(fontWeight = FontWeight.SemiBold),
        bodyLarge = base.bodyLarge.copy(lineHeight = base.bodyLarge.lineHeight),
    )
}

@Composable
fun KrugTheme(dark: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = if (dark) DarkScheme else LightScheme, typography = KrugTypography, content = content)
}
