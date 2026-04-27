package com.obrynex.studyguard.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext

private val DarkColorScheme = darkColorScheme(
    primary        = StudyBlue,
    onPrimary      = androidx.compose.ui.graphics.Color.White,
    primaryContainer = StudyBlueDark,
    secondary      = StudyGreen,
    tertiary       = StudyAmber,
    background     = SurfaceDark,
    surface        = SurfaceCard,
    onSurface      = androidx.compose.ui.graphics.Color.White,
    onSurfaceVariant = OnSurfaceDim,
    error          = StudyRed
)

private val LightColorScheme = lightColorScheme(
    primary        = StudyBlueDark,
    onPrimary      = androidx.compose.ui.graphics.Color.White,
    primaryContainer = StudyBlueLight,
    secondary      = StudyGreen,
    tertiary       = StudyAmber,
    error          = StudyRed
)

@Composable
fun StudyGuardTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColorScheme
        else      -> LightColorScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography  = Typography,
        content     = content
    )
}
