package com.allsocial.sealclone

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color

data class ColorPreset(
    val id: String,
    val name: String,
    val previewColor: Color,
    val colorPrimary: Color,
    val colorSecondary: Color,
    val colorTertiary: Color,
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
            id = "XTUBE_RED",
            name = "Crimson Neon",
            previewColor = Color(0xFFFF2A4D),
            colorPrimary = Color(0xFFFF2A4D),
            colorSecondary = Color(0xFFFF6080),
            colorTertiary = Color(0xFF5C0A18),
            primaryDark = Color(0xFFFF2A4D),
            onPrimaryDark = Color(0xFFFFFFFF),
            primaryContainerDark = Color(0xFF420B15),
            onPrimaryContainerDark = Color(0xFFFFD9DF),
            primaryLight = Color(0xFFC00028),
            onPrimaryLight = Color.White,
            primaryContainerLight = Color(0xFFFFD9DF),
            onPrimaryContainerLight = Color(0xFF40000A)
        ),
        ColorPreset(
            id = "CYAN",
            name = "Cyber Cyan",
            previewColor = Color(0xFF00E5FF),
            colorPrimary = Color(0xFF00E5FF),
            colorSecondary = Color(0xFF00B4D8),
            colorTertiary = Color(0xFF004555),
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
            name = "Mint Emerald",
            previewColor = Color(0xFF00E676),
            colorPrimary = Color(0xFF00E676),
            colorSecondary = Color(0xFF69F0AE),
            colorTertiary = Color(0xFF004D25),
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
            colorPrimary = Color(0xFFB388FF),
            colorSecondary = Color(0xFFD0BCFF),
            colorTertiary = Color(0xFF381E72),
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
            name = "Sunset Orange",
            previewColor = Color(0xFFFF7043),
            colorPrimary = Color(0xFFFF7043),
            colorSecondary = Color(0xFFFFAB91),
            colorTertiary = Color(0xFF5A1A04),
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
            name = "Electric Blue",
            previewColor = Color(0xFF40C4FF),
            colorPrimary = Color(0xFF40C4FF),
            colorSecondary = Color(0xFF80D8FF),
            colorTertiary = Color(0xFF00344F),
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
            name = "Sakura Pink",
            previewColor = Color(0xFFFF4081),
            colorPrimary = Color(0xFFFF4081),
            colorSecondary = Color(0xFFFF80AB),
            colorTertiary = Color(0xFF5C0028),
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
            colorPrimary = Color(0xFFFFD54F),
            colorSecondary = Color(0xFFFFE082),
            colorTertiary = Color(0xFF3F2E00),
            primaryDark = Color(0xFFFFE082),
            onPrimaryDark = Color(0xFF3F2E00),
            primaryContainerDark = Color(0xFF5B4300),
            onPrimaryContainerDark = Color(0xFFFFE082),
            primaryLight = Color(0xFF7B5800),
            onPrimaryLight = Color.White,
            primaryContainerLight = Color(0xFFFFDF9E),
            onPrimaryContainerLight = Color(0xFF261900)
        ),
        ColorPreset(
            id = "LIME",
            name = "Toxic Lime",
            previewColor = Color(0xFFAEEA00),
            colorPrimary = Color(0xFFAEEA00),
            colorSecondary = Color(0xFFC6FF00),
            colorTertiary = Color(0xFF2B4000),
            primaryDark = Color(0xFFC6FF00),
            onPrimaryDark = Color(0xFF283B00),
            primaryContainerDark = Color(0xFF3D5800),
            onPrimaryContainerDark = Color(0xFFE2FF7E),
            primaryLight = Color(0xFF4C6B00),
            onPrimaryLight = Color.White,
            primaryContainerLight = Color(0xFFD4FA66),
            onPrimaryContainerLight = Color(0xFF141F00)
        ),
        ColorPreset(
            id = "INDIGO",
            name = "Deep Indigo",
            previewColor = Color(0xFF536DFE),
            colorPrimary = Color(0xFF536DFE),
            colorSecondary = Color(0xFF8C9EFF),
            colorTertiary = Color(0xFF1A237E),
            primaryDark = Color(0xFF8C9EFF),
            onPrimaryDark = Color(0xFF001064),
            primaryContainerDark = Color(0xFF1A237E),
            onPrimaryContainerDark = Color(0xFFD1D9FF),
            primaryLight = Color(0xFF304FFE),
            onPrimaryLight = Color.White,
            primaryContainerLight = Color(0xFFD1D9FF),
            onPrimaryContainerLight = Color(0xFF001064)
        ),
        ColorPreset(
            id = "TEAL",
            name = "Arctic Teal",
            previewColor = Color(0xFF1DE9B6),
            colorPrimary = Color(0xFF1DE9B6),
            colorSecondary = Color(0xFF64FFDA),
            colorTertiary = Color(0xFF004D40),
            primaryDark = Color(0xFF64FFDA),
            onPrimaryDark = Color(0xFF00382E),
            primaryContainerDark = Color(0xFF005144),
            onPrimaryContainerDark = Color(0xFF80FFE4),
            primaryLight = Color(0xFF006B5B),
            onPrimaryLight = Color.White,
            primaryContainerLight = Color(0xFF80FFE4),
            onPrimaryContainerLight = Color(0xFF00201A)
        ),
        ColorPreset(
            id = "MONOCHROME",
            name = "Obsidian Dark",
            previewColor = Color(0xFFE0E0E0),
            colorPrimary = Color(0xFFE0E0E0),
            colorSecondary = Color(0xFF9E9E9E),
            colorTertiary = Color(0xFF212121),
            primaryDark = Color(0xFFE0E0E0),
            onPrimaryDark = Color(0xFF121212),
            primaryContainerDark = Color(0xFF2C2C2C),
            onPrimaryContainerDark = Color(0xFFFFFFFF),
            primaryLight = Color(0xFF37474F),
            onPrimaryLight = Color.White,
            primaryContainerLight = Color(0xFFECEFF1),
            onPrimaryContainerLight = Color(0xFF102027)
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
