package com.diego.awedio.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

private val LightColorScheme = lightColorScheme(
    primary = NearBlack,
    onPrimary = Color.White,
    primaryContainer = ContainerGray,
    onPrimaryContainer = NearBlack,
    inversePrimary = OffWhite,

    secondary = Color(0xFF444444),
    onSecondary = Color.White,
    secondaryContainer = ContainerGray,
    onSecondaryContainer = NearBlack,

    tertiary = Color(0xFF666666),
    onTertiary = Color.White,
    tertiaryContainer = ContainerGray,
    onTertiaryContainer = NearBlack,

    background = Color.White,
    onBackground = NearBlack,
    surface = Color.White,
    onSurface = NearBlack,
    surfaceVariant = SurfaceGray,
    onSurfaceVariant = GrayText,

    surfaceContainerLowest = Color.White,
    surfaceContainerLow = Color(0xFFFAFAFA),
    surfaceContainer = Color(0xFFF6F6F6),
    surfaceContainerHigh = Color(0xFFF2F2F2),
    surfaceContainerHighest = Color(0xFFEEEEEE),

    inverseSurface = Color(0xFF2A2A2A),
    inverseOnSurface = Color(0xFFF5F5F5),

    outline = OutlineGray,
    outlineVariant = Color(0xFFEEEEEE),

    error = ErrorRed,
    onError = Color.White,
    errorContainer = Color(0xFFFCEBEB),
    onErrorContainer = ErrorRed,

    scrim = Color.Black
)

private val DarkColorScheme = darkColorScheme(
    primary = OffWhite,
    onPrimary = NearBlack,
    primaryContainer = ContainerGrayDark,
    onPrimaryContainer = OffWhite,
    inversePrimary = NearBlack,

    secondary = Color(0xFFBDBDBD),
    onSecondary = NearBlack,
    secondaryContainer = ContainerGrayDark,
    onSecondaryContainer = OffWhite,

    tertiary = Color(0xFF9E9E9E),
    onTertiary = NearBlack,
    tertiaryContainer = ContainerGrayDark,
    onTertiaryContainer = OffWhite,

    background = Color(0xFF0E0E0E),
    onBackground = OffWhite,
    surface = Color(0xFF141414),
    onSurface = OffWhite,
    surfaceVariant = SurfaceGrayDark,
    onSurfaceVariant = GrayTextDark,

    surfaceContainerLowest = Color(0xFF0E0E0E),
    surfaceContainerLow = Color(0xFF161616),
    surfaceContainer = Color(0xFF1A1A1A),
    surfaceContainerHigh = Color(0xFF1F1F1F),
    surfaceContainerHighest = Color(0xFF242424),

    inverseSurface = Color(0xFFF5F5F5),
    inverseOnSurface = Color(0xFF1A1A1A),

    outline = OutlineGrayDark,
    outlineVariant = Color(0xFF242424),

    error = ErrorRedDark,
    onError = OnErrorRedDark,
    errorContainer = Color(0xFF3A1512),
    onErrorContainer = ErrorRedDark,

    scrim = Color.Black
)

private val AwedioShapes = Shapes(
    extraSmall = RoundedCornerShape(6.dp),
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(14.dp),
    large = RoundedCornerShape(20.dp),
    extraLarge = RoundedCornerShape(28.dp)
)

@Composable
fun AwedioTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        shapes = AwedioShapes,
        content = content
    )
}
