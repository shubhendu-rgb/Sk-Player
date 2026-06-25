package com.example.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext

import androidx.compose.ui.graphics.Color

private val DarkColorScheme =
  darkColorScheme(
    primary = ElegantDarkAccent,
    onPrimary = ElegantDarkOnAccent,
    secondary = ElegantDarkTextSecondary,
    onSecondary = ElegantDarkTextPrimary,
    background = ElegantDarkBg,
    onBackground = ElegantDarkTextPrimary,
    surface = ElegantDarkSurface,
    onSurface = ElegantDarkTextPrimary,
    surfaceVariant = ElegantDarkSurfaceCard,
    onSurfaceVariant = ElegantDarkTextSecondary,
    outline = ElegantDarkBorder
  )

private val PureLightColorScheme =
  lightColorScheme(
    primary = Color(0xFF6200EE),
    onPrimary = Color(0xFFFFFFFF),
    secondary = Color(0xFF495057),
    onSecondary = Color(0xFF212529),
    background = Color(0xFFF8F9FA),
    onBackground = Color(0xFF212529),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF212529),
    surfaceVariant = Color(0xFFE9ECEF),
    onSurfaceVariant = Color(0xFF495057),
    outline = Color(0xFFCED4DA)
  )

private val BasicLightColorScheme =
  lightColorScheme(
    primary = Color(0xFF000000),
    onPrimary = Color(0xFFFFFFFF),
    secondary = Color(0xFF495057),
    onSecondary = Color(0xFF000000),
    background = Color(0xFFFFFFFF),
    onBackground = Color(0xFF000000),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF000000),
    surfaceVariant = Color(0xFFF1F3F4),
    onSurfaceVariant = Color(0xFF000000),
    outline = Color(0xFFE0E0E0)
  )

private val DawnGlowColorScheme =
  lightColorScheme(
    primary = Color(0xFFFF7043), // Deep Orange
    onPrimary = Color(0xFFFFFFFF),
    secondary = Color(0xFFFFB74D), // Orange Light
    onSecondary = Color(0xFF3E2723),
    background = Color(0xFFFFF3E0), // Orange very light
    onBackground = Color(0xFF3E2723),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF3E2723),
    surfaceVariant = Color(0xFFFFE0B2),
    onSurfaceVariant = Color(0xFF5D4037),
    outline = Color(0xFFD7CCC8)
  )

private val OceanBreezeColorScheme =
  lightColorScheme(
    primary = Color(0xFF26A69A), // Teal
    onPrimary = Color(0xFFFFFFFF),
    secondary = Color(0xFF4DD0E1), // Cyan
    onSecondary = Color(0xFF004D40),
    background = Color(0xFFE0F2F1), // Teal very light
    onBackground = Color(0xFF004D40),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF004D40),
    surfaceVariant = Color(0xFFB2DFDB),
    onSurfaceVariant = Color(0xFF00695C),
    outline = Color(0xFF80CBC4)
  )

private val SpringMorningColorScheme =
  lightColorScheme(
    primary = Color(0xFF66BB6A), // Green Light
    onPrimary = Color(0xFFFFFFFF),
    secondary = Color(0xFFFFCA28), // Amber Light
    onSecondary = Color(0xFF1B5E20),
    background = Color(0xFFF1F8E9), // Green very light
    onBackground = Color(0xFF1B5E20),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF1B5E20),
    surfaceVariant = Color(0xFFDCEDC8),
    onSurfaceVariant = Color(0xFF33691E),
    outline = Color(0xFFAED581)
  )

private val CosmicSlateColorScheme =
  darkColorScheme(
    primary = Color(0xFF88C0D0),
    onPrimary = Color(0xFF2E3440),
    secondary = Color(0xFFD8DEE9),
    onSecondary = Color(0xFFECEFF4),
    background = Color(0xFF1E222A),
    onBackground = Color(0xFFECEFF4),
    surface = Color(0xFF242936),
    onSurface = Color(0xFFECEFF4),
    surfaceVariant = Color(0xFF2E3440),
    onSurfaceVariant = Color(0xFFECEFF4),
    outline = Color(0xFF3B4252)
  )

private val SunsetAmberColorScheme =
  darkColorScheme(
    primary = Color(0xFFFFB300),
    onPrimary = Color(0xFF3E2723),
    secondary = Color(0xFFEAD2C6),
    onSecondary = Color(0xFFFBEFEA),
    background = Color(0xFF1B1512),
    onBackground = Color(0xFFFBEFEA),
    surface = Color(0xFF241C18),
    onSurface = Color(0xFFFBEFEA),
    surfaceVariant = Color(0xFF2E241E),
    onSurfaceVariant = Color(0xFFEAD2C6),
    outline = Color(0xFF4E3629)
  )

private val ForestGreenColorScheme =
  darkColorScheme(
    primary = Color(0xFF81C784),
    onPrimary = Color(0xFF1B5E20),
    secondary = Color(0xFFD2E6D8),
    onSecondary = Color(0xFFEDF5EF),
    background = Color(0xFF111713),
    onBackground = Color(0xFFEDF5EF),
    surface = Color(0xFF18221B),
    onSurface = Color(0xFFEDF5EF),
    surfaceVariant = Color(0xFF223026),
    onSurfaceVariant = Color(0xFFD2E6D8),
    outline = Color(0xFF2E4536)
  )

private val DeepPurpleColorScheme =
  darkColorScheme(
    primary = Color(0xFFBB86FC),
    onPrimary = Color(0xFF3700B3),
    secondary = Color(0xFFDFD1F0),
    onSecondary = Color(0xFFF3EEFA),
    background = Color(0xFF14111B),
    onBackground = Color(0xFFF3EEFA),
    surface = Color(0xFF1E1929),
    onSurface = Color(0xFFF3EEFA),
    surfaceVariant = Color(0xFF2A223A),
    onSurfaceVariant = Color(0xFFDFD1F0),
    outline = Color(0xFF41325D)
  )

@Composable
fun MyApplicationTheme(
  themeMode: String = "MY_THEME",
  customFontUri: String? = null,
  content: @Composable () -> Unit,
) {
  val context = LocalContext.current
  val colorScheme =
    when (themeMode) {
      "LIGHT" -> PureLightColorScheme
      "BASIC_LIGHT" -> BasicLightColorScheme
      "MY_THEME_LIGHT" -> {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
          dynamicLightColorScheme(context)
        } else {
          PureLightColorScheme
        }
      }
      "DAWN_GLOW" -> DawnGlowColorScheme
      "OCEAN_BREEZE" -> OceanBreezeColorScheme
      "SPRING_MORNING" -> SpringMorningColorScheme
      "COSMIC" -> CosmicSlateColorScheme
      "AMBER" -> SunsetAmberColorScheme
      "FOREST" -> ForestGreenColorScheme
      "PURPLE" -> DeepPurpleColorScheme
      "MY_THEME" -> {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
          dynamicDarkColorScheme(context)
        } else {
          DarkColorScheme
        }
      }
      else -> {
        if (themeMode == "DARK") {
          DarkColorScheme
        } else {
          if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            dynamicDarkColorScheme(context)
          } else {
            DarkColorScheme
          }
        }
      }
    }

  val customFontFamily = androidx.compose.runtime.remember(customFontUri) {
    if (customFontUri != null && java.io.File(customFontUri).exists()) {
        try {
            androidx.compose.ui.text.font.FontFamily(androidx.compose.ui.text.font.Font(java.io.File(customFontUri)))
        } catch(e: Exception) { null }
    } else null
  }

  val finalTypography = if (customFontFamily != null) {
      androidx.compose.material3.Typography(
          displayLarge = Typography.displayLarge.copy(fontFamily = customFontFamily),
          displayMedium = Typography.displayMedium.copy(fontFamily = customFontFamily),
          displaySmall = Typography.displaySmall.copy(fontFamily = customFontFamily),
          headlineLarge = Typography.headlineLarge.copy(fontFamily = customFontFamily),
          headlineMedium = Typography.headlineMedium.copy(fontFamily = customFontFamily),
          headlineSmall = Typography.headlineSmall.copy(fontFamily = customFontFamily),
          titleLarge = Typography.titleLarge.copy(fontFamily = customFontFamily),
          titleMedium = Typography.titleMedium.copy(fontFamily = customFontFamily),
          titleSmall = Typography.titleSmall.copy(fontFamily = customFontFamily),
          bodyLarge = Typography.bodyLarge.copy(fontFamily = customFontFamily),
          bodyMedium = Typography.bodyMedium.copy(fontFamily = customFontFamily),
          bodySmall = Typography.bodySmall.copy(fontFamily = customFontFamily),
          labelLarge = Typography.labelLarge.copy(fontFamily = customFontFamily),
          labelMedium = Typography.labelMedium.copy(fontFamily = customFontFamily),
          labelSmall = Typography.labelSmall.copy(fontFamily = customFontFamily)
      )
  } else Typography

  MaterialTheme(colorScheme = colorScheme, typography = finalTypography, content = content)
}
