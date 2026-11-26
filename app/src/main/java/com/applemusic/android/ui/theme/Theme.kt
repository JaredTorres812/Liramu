package com.applemusic.android.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialExpressiveTheme
import androidx.compose.material3.MotionScheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.applemusic.android.data.settings.AppFontOption

private val AppleMusicRed = Color(0xFFFA243C)
private val AppleMusicRedContainerDark = Color(0xFF5C0A18)
private val AppleMusicRedContainerLight = Color(0xFFFFD9DC)

private val DarkColorScheme = darkColorScheme(
    primary = AppleMusicRed,
    onPrimary = Color.White,
    primaryContainer = AppleMusicRedContainerDark,
    onPrimaryContainer = Color(0xFFFFD9DC),
    secondary = Color(0xFFFF8994),
    onSecondary = Color(0xFF4F0010),
    tertiary = Color(0xFFFFB3B9),
    background = Color(0xFF0E0E10),
    onBackground = Color(0xFFF5F5F7),
    surface = Color(0xFF141416),
    onSurface = Color(0xFFF5F5F7),
    surfaceContainerLow = Color(0xFF1A1A1D),
    surfaceContainer = Color(0xFF202024),
    surfaceContainerHigh = Color(0xFF2A2A2F),
    surfaceContainerHighest = Color(0xFF34343B),
    onSurfaceVariant = Color(0xFFB4B4BC),
    outlineVariant = Color(0xFF2E2E34)
)

private val LightColorScheme = lightColorScheme(
    primary = AppleMusicRed,
    onPrimary = Color.White,
    primaryContainer = AppleMusicRedContainerLight,
    onPrimaryContainer = Color(0xFF410009),
    secondary = Color(0xFF765659),
    onSecondary = Color.White,
    background = Color(0xFFFAFAFC),
    onBackground = Color(0xFF1A1A1D),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF1A1A1D),
    surfaceContainerLow = Color(0xFFF5F5F7),
    surfaceContainer = Color(0xFFEEEEF2),
    surfaceContainerHigh = Color(0xFFE6E6EC),
    surfaceContainerHighest = Color(0xFFDCDCE4),
    onSurfaceVariant = Color(0xFF63636A),
    outlineVariant = Color(0xFFE0E0E6)
)

private val ExpressiveShapes = Shapes(
    extraSmall = RoundedCornerShape(6.dp),
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(14.dp),
    large = RoundedCornerShape(18.dp),
    extraLarge = RoundedCornerShape(20.dp)
)

private fun typographyFor(fontOption: AppFontOption): Typography {
    val family = when (fontOption) {
        AppFontOption.SYSTEM -> FontFamily.Default
        AppFontOption.GEOMETRIC -> FontFamily.SansSerif
        AppFontOption.SERIF_EDITORIAL -> FontFamily.Serif
        AppFontOption.STUDIO_MONO -> FontFamily.Monospace
    }
    val extraTracking = when (fontOption) {
        AppFontOption.GEOMETRIC -> 0.4.sp
        AppFontOption.STUDIO_MONO -> (-0.2).sp
        else -> 0.sp
    }
    return Typography(
        displaySmall = TextStyle(
            fontFamily = family,
            fontSize = 36.sp,
            lineHeight = 42.sp,
            fontWeight = FontWeight.ExtraBold,
            letterSpacing = (-0.8).sp
        ),
        headlineLarge = TextStyle(
            fontFamily = family,
            fontSize = 34.sp,
            lineHeight = 40.sp,
            fontWeight = FontWeight.ExtraBold,
            letterSpacing = (-0.6).sp
        ),
        headlineMedium = TextStyle(
            fontFamily = family,
            fontSize = 26.sp,
            lineHeight = 32.sp,
            fontWeight = FontWeight.ExtraBold
        ),
        titleLarge = TextStyle(
            fontFamily = family,
            fontSize = 22.sp,
            lineHeight = 28.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = (-0.3).sp
        ),
        titleMedium = TextStyle(
            fontFamily = family,
            fontSize = 16.sp,
            lineHeight = 22.sp,
            fontWeight = FontWeight.SemiBold,
            letterSpacing = extraTracking
        ),
        titleSmall = TextStyle(
            fontFamily = family,
            fontSize = 14.sp,
            lineHeight = 19.sp,
            fontWeight = FontWeight.Bold
        ),
        bodyLarge = TextStyle(
            fontFamily = family,
            fontSize = 16.sp,
            lineHeight = 22.sp,
            fontWeight = FontWeight.Normal,
            letterSpacing = extraTracking
        ),
        bodyMedium = TextStyle(
            fontFamily = family,
            fontSize = 14.sp,
            lineHeight = 19.sp,
            fontWeight = FontWeight.Normal,
            letterSpacing = extraTracking
        ),
        bodySmall = TextStyle(
            fontFamily = family,
            fontSize = 12.sp,
            lineHeight = 16.sp,
            fontWeight = FontWeight.Normal
        ),
        labelLarge = TextStyle(
            fontFamily = family,
            fontSize = 14.sp,
            lineHeight = 18.sp,
            fontWeight = FontWeight.SemiBold
        ),
        labelMedium = TextStyle(
            fontFamily = family,
            fontSize = 12.sp,
            lineHeight = 16.sp,
            fontWeight = FontWeight.Medium
        ),
        labelSmall = TextStyle(
            fontFamily = family,
            fontSize = 11.sp,
            lineHeight = 14.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 0.6.sp
        )
    )
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun AppleMusicTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = true,
    fontOption: AppFontOption = AppFontOption.SYSTEM,
    content: @Composable () -> Unit
) {
    val baseScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    val colorScheme = if (darkTheme) {
        baseScheme.copy(
            background = Color.Black,
            surface = Color.Black
        )
    } else {
        baseScheme.copy(
            background = Color.White,
            surface = Color.White
        )
    }

    MaterialExpressiveTheme(
        colorScheme = colorScheme,
        motionScheme = MotionScheme.expressive(),
        shapes = ExpressiveShapes,
        typography = typographyFor(fontOption),
        content = content
    )
}
