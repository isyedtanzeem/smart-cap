package com.example.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp

private val LightColorScheme = lightColorScheme(
    primary = EmeraldPrimary,
    onPrimary = Color.White,
    primaryContainer = EmeraldSoft,
    onPrimaryContainer = Color(0xFF043821),
    secondary = AmberFare,
    onSecondary = Color.White,
    secondaryContainer = AmberFareContainer,
    onSecondaryContainer = AmberFareOnContainer,
    tertiary = EmeraldBright,
    onTertiary = Color.White,
    background = LightBackground,
    onBackground = LightOnSurface,
    surface = LightSurface,
    onSurface = LightOnSurface,
    surfaceVariant = LightSurfaceVariant,
    onSurfaceVariant = LightOnSurfaceMuted,
    outline = LightOutline,
    error = CoralAlert,
    onError = Color.White,
    errorContainer = CoralAlertContainer,
    onErrorContainer = Color(0xFF6B1A12)
)

private val DarkColorScheme = darkColorScheme(
    primary = EmeraldGlow,
    onPrimary = Color(0xFF003820),
    primaryContainer = EmeraldDarkContainer,
    onPrimaryContainer = Color(0xFF9DF5C4),
    secondary = AmberFareBright,
    onSecondary = Color(0xFF3A2400),
    secondaryContainer = AmberDarkContainer,
    onSecondaryContainer = Color(0xFFFFE08A),
    tertiary = EmeraldBright,
    onTertiary = Color.White,
    background = DarkBackground,
    onBackground = DarkOnSurface,
    surface = DarkSurface,
    onSurface = DarkOnSurface,
    surfaceVariant = DarkSurfaceVariant,
    onSurfaceVariant = DarkOnSurfaceMuted,
    outline = DarkOutline,
    error = Color(0xFFFF897D),
    onError = Color(0xFF56110A),
    errorContainer = CoralDarkContainer,
    onErrorContainer = Color(0xFFFFDAD5)
)

val SmartCaptainShapes = Shapes(
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(22.dp),
    extraLarge = RoundedCornerShape(28.dp)
)

@Composable
fun SmartCaptainTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = SmartCaptainTypography,
        shapes = SmartCaptainShapes,
        content = content
    )
}
