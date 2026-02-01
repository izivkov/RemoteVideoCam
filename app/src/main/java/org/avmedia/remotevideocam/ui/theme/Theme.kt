
package org.avmedia.remotevideocam.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

// Modern, expressive color palette
private val PrimaryDark = Color(0xFF7C4DFF) // Vibrant purple
private val PrimaryLight = Color(0xFF651FFF)
private val SecondaryDark = Color(0xFF00E5FF) // Cyan accent
private val SecondaryLight = Color(0xFF00B8D4)
private val TertiaryDark = Color(0xFFFF4081) // Pink accent
private val TertiaryLight = Color(0xFFF50057)

private val SurfaceDark = Color(0xFF121212)
private val SurfaceVariantDark = Color(0xFF1E1E1E)
private val BackgroundDark = Color(0xFF0A0A0A)

private val SurfaceLight = Color(0xFFFEFEFE)
private val SurfaceVariantLight = Color(0xFFF5F5F5)
private val BackgroundLight = Color(0xFFFFFFFF)

private val OnPrimaryDark = Color(0xFFFFFFFF)
private val OnSecondaryDark = Color(0xFF000000)
private val OnBackgroundDark = Color(0xFFE1E1E1)
private val OnSurfaceDark = Color(0xFFE1E1E1)

private val OnPrimaryLight = Color(0xFFFFFFFF)
private val OnSecondaryLight = Color(0xFF000000)
private val OnBackgroundLight = Color(0xFF1C1B1F)
private val OnSurfaceLight = Color(0xFF1C1B1F)

// Camera recording indicator
val RecordingRed = Color(0xFFFF1744)

// Connection status colors
val ConnectedGreen = Color(0xFF00E676)
val DisconnectedOrange = Color(0xFFFF9100)
val WaitingBlue = Color(0xFF2979FF)

// Glassmorphism colors
val GlassWhite = Color(0x33FFFFFF)
val GlassDark = Color(0x4D000000)

private val DarkColorScheme = darkColorScheme(
    primary = PrimaryDark,
    onPrimary = OnPrimaryDark,
    primaryContainer = Color(0xFF3700B3),
    onPrimaryContainer = Color(0xFFE8DAFF),
    secondary = SecondaryDark,
    onSecondary = OnSecondaryDark,
    secondaryContainer = Color(0xFF004D61),
    onSecondaryContainer = Color(0xFFB8EAFF),
    tertiary = TertiaryDark,
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFF7A0042),
    onTertiaryContainer = Color(0xFFFFD9E3),
    error = Color(0xFFCF6679),
    onError = Color(0xFF000000),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6),
    background = BackgroundDark,
    onBackground = OnBackgroundDark,
    surface = SurfaceDark,
    onSurface = OnSurfaceDark,
    surfaceVariant = SurfaceVariantDark,
    onSurfaceVariant = Color(0xFFCAC4D0),
    outline = Color(0xFF938F99),
    outlineVariant = Color(0xFF49454F),
    scrim = Color(0xFF000000),
    inverseSurface = Color(0xFFE6E1E5),
    inverseOnSurface = Color(0xFF313033),
    inversePrimary = Color(0xFF6750A4)
)

private val LightColorScheme = lightColorScheme(
    primary = PrimaryLight,
    onPrimary = OnPrimaryLight,
    primaryContainer = Color(0xFFEADDFF),
    onPrimaryContainer = Color(0xFF21005D),
    secondary = SecondaryLight,
    onSecondary = OnSecondaryLight,
    secondaryContainer = Color(0xFFB8EAFF),
    onSecondaryContainer = Color(0xFF001F28),
    tertiary = TertiaryLight,
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFFFD9E3),
    onTertiaryContainer = Color(0xFF3E001D),
    error = Color(0xFFB3261E),
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFF9DEDC),
    onErrorContainer = Color(0xFF410E0B),
    background = BackgroundLight,
    onBackground = OnBackgroundLight,
    surface = SurfaceLight,
    onSurface = OnSurfaceLight,
    surfaceVariant = SurfaceVariantLight,
    onSurfaceVariant = Color(0xFF49454F),
    outline = Color(0xFF79747E),
    outlineVariant = Color(0xFFCAC4D0),
    scrim = Color(0xFF000000),
    inverseSurface = Color(0xFF313033),
    inverseOnSurface = Color(0xFFF4EFF4),
    inversePrimary = Color(0xFFD0BCFF)
)

@Composable
fun RemoteVideoCamTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    // Dynamic color is available on Android 12+
    dynamicColor: Boolean = true,
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
    
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = Color.Transparent.toArgb()
            window.navigationBarColor = Color.Transparent.toArgb()
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !darkTheme
            WindowCompat.getInsetsController(window, view).isAppearanceLightNavigationBars = !darkTheme
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
