package ir.alifaniani.hawklyx.ui.theme

import android.app.Activity
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val HawklyxColorScheme = darkColorScheme(
    primary = HawklyxEmerald,
    onPrimary = HawklyxDarkBackground,
    primaryContainer = HawklyxSurfaceVariant,
    onPrimaryContainer = HawklyxEmerald,
    secondary = HawklyxEmerald,
    onSecondary = HawklyxDarkBackground,
    tertiary = HawklyxAmber,
    onTertiary = HawklyxDarkBackground,
    background = HawklyxDarkBackground,
    onBackground = HawklyxTextPrimary,
    surface = HawklyxSurface,
    onSurface = HawklyxTextPrimary,
    surfaceVariant = HawklyxSurfaceVariant,
    onSurfaceVariant = HawklyxTextSecondary,
    outline = HawklyxBorder,
    error = HawklyxRed,
    onError = HawklyxTextPrimary
)

@Composable
fun HAWKLYXTheme(
    content: @Composable () -> Unit
) {
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? Activity)?.window
            if (window != null) {
                window.statusBarColor = HawklyxDarkBackground.toArgb()
                window.navigationBarColor = HawklyxDarkBackground.toArgb()
                WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = false
                WindowCompat.getInsetsController(window, view).isAppearanceLightNavigationBars = false
            }
        }
    }

    MaterialTheme(
        colorScheme = HawklyxColorScheme,
        typography = Typography,
        content = content
    )
}