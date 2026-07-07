package com.example.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

import androidx.compose.material3.ColorScheme

fun getDynamicColorScheme(seedColorHex: String, isDark: Boolean): ColorScheme {
    val baseColor = try {
        if (seedColorHex.startsWith("#")) {
            val cleanHex = seedColorHex.removePrefix("#")
            if (cleanHex.length == 6) {
                Color(android.graphics.Color.parseColor("#$cleanHex"))
            } else if (cleanHex.length == 8) {
                Color(android.graphics.Color.parseColor("#$cleanHex"))
            } else {
                Color(0xFF6750A4)
            }
        } else {
            when (seedColorHex.uppercase()) {
                "PURPLE" -> Color(0xFF6750A4)
                "GREEN" -> Color(0xFF386A20)
                "BLUE" -> Color(0xFF0061A4)
                "RED" -> Color(0xFFBA1A1A)
                "SLATE" -> Color(0xFF435B95)
                else -> Color(0xFF6750A4)
            }
        }
    } catch (e: Exception) {
        Color(0xFF6750A4)
    }

    // Convert to HSV for smart scaling
    val hsv = FloatArray(3)
    android.graphics.Color.colorToHSV(
        android.graphics.Color.argb(
            255,
            (baseColor.red * 255).toInt(),
            (baseColor.green * 255).toInt(),
            (baseColor.blue * 255).toInt()
        ), hsv
    )
    val h = hsv[0]
    val s = hsv[1]
    val v = hsv[2]

    return if (isDark) {
        // Dark Mode: soft, eye-safe, colorful but desaturated primary
        val primaryColor = Color(android.graphics.Color.HSVToColor(floatArrayOf(h, (s * 0.65f).coerceIn(0.2f, 0.45f), 0.95f)))
        val onPrimaryColor = Color(android.graphics.Color.HSVToColor(floatArrayOf(h, (s * 0.9f).coerceIn(0.6f, 0.9f), 0.25f)))
        val primaryContainerColor = Color(android.graphics.Color.HSVToColor(floatArrayOf(h, (s * 0.8f).coerceIn(0.4f, 0.7f), 0.35f)))
        val onPrimaryContainerColor = Color(android.graphics.Color.HSVToColor(floatArrayOf(h, (s * 0.3f).coerceIn(0.1f, 0.25f), 0.95f)))
        
        darkColorScheme(
            primary = primaryColor,
            onPrimary = onPrimaryColor,
            primaryContainer = primaryContainerColor,
            onPrimaryContainer = onPrimaryContainerColor,
            secondary = TextDarkSecondary,
            tertiary = SuccessGreen,
            error = ErrorCoral,
            onError = OnErrorCoral,
            background = BackgroundDark,
            surface = SurfaceDark,
            surfaceVariant = SurfaceVariantDark,
            onBackground = TextDark,
            onSurface = TextDark,
            onSurfaceVariant = TextDarkSecondary
        )
    } else {
        // Light Mode: rich, vibrant primary, elegant pastel container
        val primaryColor = Color(android.graphics.Color.HSVToColor(floatArrayOf(h, s.coerceAtLeast(0.65f), v.coerceIn(0.45f, 0.75f))))
        val onPrimaryColor = Color(0xFFFFFFFF)
        val primaryContainerColor = Color(android.graphics.Color.HSVToColor(floatArrayOf(h, (s * 0.25f).coerceIn(0.08f, 0.18f), 0.98f)))
        val onPrimaryContainerColor = Color(android.graphics.Color.HSVToColor(floatArrayOf(h, (s * 0.95f).coerceAtLeast(0.7f), 0.25f)))

        lightColorScheme(
            primary = primaryColor,
            onPrimary = onPrimaryColor,
            primaryContainer = primaryContainerColor,
            onPrimaryContainer = onPrimaryContainerColor,
            secondary = TextLightSecondary,
            tertiary = SuccessGreen,
            error = ErrorCoral,
            onError = OnErrorCoral,
            background = BackgroundLight,
            surface = SurfaceLight,
            surfaceVariant = SurfaceVariantLight,
            onBackground = TextLight,
            onSurface = TextLight,
            onSurfaceVariant = TextLightSecondary
        )
    }
}

@Composable
fun MyApplicationTheme(
  darkTheme: Boolean = isSystemInDarkTheme(),
  colorTheme: String = "#6750A4",
  dynamicColor: Boolean = false,
  content: @Composable () -> Unit,
) {
  val colorScheme =
    when {
      dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
        val context = LocalContext.current
        if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
      }

      else -> getDynamicColorScheme(colorTheme, darkTheme)
    }

  MaterialTheme(colorScheme = colorScheme, typography = Typography, content = content)
}
