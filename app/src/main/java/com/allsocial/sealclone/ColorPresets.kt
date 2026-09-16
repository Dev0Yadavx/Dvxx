package com.allsocial.sealclone

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color

data class ColorPreset(
    val id: String,
    val name: String,
    val previewColor: Color,
    val primaryDark: Color,
    val onPrimaryDark: Color,
    val primaryContainerDark: Color,
    val onPrimaryContainerDark: Color,
    val primaryLight: Color,
    val onPrimaryLight: Color,
    val primaryContainerLight: Color,
    val onPrimaryContainerLight: Color
)

object ColorPresetRegistry {
    val presets = listOf(
        ColorPreset(
            id = "CYAN",
            name = "Seal Cyan",
            previewColor = Color(0xFF00E5FF),
            primaryDark = Color(0xFF00E5FF),
            onPrimaryDark = Color(0xFF00363D),
            primaryContainerDark = Color(0xFF004F58),
            onPrimaryContainerDark = Color(0xFF8CF8FF),
            primaryLight = Color(0xFF006876),
            onPrimaryLight = Color.White,
            primaryContainerLight = Color(0xFF8CF8FF),
            onPrimaryContainerLight = Color(0xFF002025)
        ),
        ColorPreset(
            id = "EMERALD",
            name = "Emerald Green",
            previewColor = Color(0xFF00E676),
            primaryDark = Color(0xFF00E676),
            onPrimaryDark = Color(0xFF003919),
            primaryContainerDark = Color(0xFF005327),
            onPrimaryContainerDark = Color(0xFF7BFFB0),
            primaryLight = Color(0xFF006D34),
            onPrimaryLight = Color.White,
            primaryContainerLight = Color(0xFF8CF8BB),
            onPrimaryContainerLight = Color(0xFF00210B)
        ),
        ColorPreset(
            id = "VIOLET",
            name = "Neon Purple",
            previewColor = Color(0xFFB388FF),
            primaryDark = Color(0xFFD0BCFF),
            onPrimaryDark = Color(0xFF381E72),
            primaryContainerDark = Color(0xFF4F378B),
            onPrimaryContainerDark = Color(0xFFEADDFF),
            primaryLight = Color(0xFF6750A4),
            onPrimaryLight = Color.White,
            primaryContainerLight = Color(0xFFEADDFF),
            onPrimaryContainerLight = Color(0xFF21005D)
        ),
        ColorPreset(
            id = "CORAL",
            name = "Sunset Coral",
            previewColor = Color(0xFFFF7043),
            primaryDark = Color(0xFFFF8A65),
            onPrimaryDark = Color(0xFF5A1A04),
            primaryContainerDark = Color(0xFF7B2609),
            onPrimaryContainerDark = Color(0xFFFFDBCF),
            primaryLight = Color(0xFFBD3600),
            onPrimaryLight = Color.White,
            primaryContainerLight = Color(0xFFFFDBCF),
            onPrimaryContainerLight = Color(0xFF3B0D00)
        ),
        ColorPreset(
            id = "BLUE",
            name = "Ocean Blue",
            previewColor = Color(0xFF40C4FF),
            primaryDark = Color(0xFF80D8FF),
            onPrimaryDark = Color(0xFF00344F),
            primaryContainerDark = Color(0xFF004C71),
            onPrimaryContainerDark = Color(0xFFC2E8FF),
            primaryLight = Color(0xFF006495),
            onPrimaryLight = Color.White,
            primaryContainerLight = Color(0xFFC2E8FF),
            onPrimaryContainerLight = Color(0xFF001E30)
        ),
        ColorPreset(
            id = "ROSE",
            name = "Neon Rose",
            previewColor = Color(0xFFFF4081),
            primaryDark = Color(0xFFFF80AB),
            onPrimaryDark = Color(0xFF5C0028),
            primaryContainerDark = Color(0xFF7E0039),
            onPrimaryContainerDark = Color(0xFFFFD8E4),
            primaryLight = Color(0xFFB90059),
            onPrimaryLight = Color.White,
            primaryContainerLight = Color(0xFFFFD8E4),
            onPrimaryContainerLight = Color(0xFF3E001A)
        ),
        ColorPreset(
            id = "AMBER",
            name = "Amber Gold",
            previewColor = Color(0xFFFFD54F),
            primaryDark = Color(0xFFFFE082),
            onPrimaryDark = Color(0xFF3F2E00),
            primaryContainerDark = Color(0xFF5B4300),
            onPrimaryContainerDark = Color(0xFFFFE082),
            primaryLight = Color(0xFF7B5800),
            onPrimaryLight = Color.White,
            primaryContainerLight = Color(0xFFFFDF9E),
            onPrimaryContainerLight = Color(0xFF261900)
        )
    )

    fun getPreset(id: String): ColorPreset =
        presets.firstOrNull { it.id.equals(id, ignoreCase = true) } ?: presets.first()

    fun buildColorScheme(preset: ColorPreset, isDark: Boolean): ColorScheme {
        return if (isDark) {
            darkColorScheme(
                primary = preset.primaryDark,
                onPrimary = preset.onPrimaryDark,
                primaryContainer = preset.primaryContainerDark,
                onPrimaryContainer = preset.onPrimaryContainerDark,
                background = Color(0xFF0A0E17),
                onBackground = Color(0xFFE1E7F5),
                surface = Color(0xFF141A26),
                onSurface = Color(0xFFE1E7F5),
                surfaceVariant = Color(0xFF1F2637),
                onSurfaceVariant = Color(0xFFC4CAD4),
                outline = Color(0xFF434D5E)
            )
        } else {
            lightColorScheme(
                primary = preset.primaryLight,
                onPrimary = preset.onPrimaryLight,
                primaryContainer = preset.primaryContainerLight,
                onPrimaryContainer = preset.onPrimaryContainerLight,
                background = Color(0xFFF7F9FC),
                onBackground = Color(0xFF13171F),
                surface = Color(0xFFFFFFFF),
                onSurface = Color(0xFF13171F),
                surfaceVariant = Color(0xFFE2E7F0),
                onSurfaceVariant = Color(0xFF434D5E),
                outline = Color(0xFFB8C2D1)
            )
        }
    }
}
