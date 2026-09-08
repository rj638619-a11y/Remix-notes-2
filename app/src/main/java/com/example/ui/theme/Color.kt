package com.example.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

// Light Mode Tokens from CSS
val BgLight = Color(0xFFF4F1EA)
val CardLight = Color(0xFFFFFFFF)
val TxLight = Color(0xFF1D1B16)
val Tx2Light = Color(0xFF6F6A5C)
val Tx3Light = Color(0xFFA59E8D)
val HairLight = Color(0x1C463A19) // rgba(70,58,25,.11)
val AccentLight = Color(0xFF8F6400)
val Accent2Color = Color(0xFFF2B90C) // Amber Gold
val ChipOnBgLight = Color(0x2BF2B90C) // rgba(242,185,12,.17)
val ChipOnTxLight = Color(0xFF7A5B00)
val GlassLight = Color(0x99FFFFFF) // rgba(255,255,255,.60)
val GlassBrdLight = Color(0x8CFFFFFF) // rgba(255,255,255,.55)
val GlassHiLight = Color(0xCCFFFFFF) // rgba(255,255,255,.80)
val FieldLight = Color(0x1A786E55) // rgba(120,110,85,.10)
val MarkLight = Color(0xFFFFE9A3)
val DangerColorLight = Color(0xFFE5484D)
val ShadowLight = Color(0x1A3C2D05) // rgba(60,45,5,.10)

// Dark Mode Tokens from CSS
val BgDark = Color(0xFF0A0A0C)
val CardDark = Color(0xFF17171B)
val TxDark = Color(0xFFF1EFE8)
val Tx2Dark = Color(0xFFA4A094)
val Tx3Dark = Color(0xFF6D695E)
val HairDark = Color(0x17FFFFFF) // rgba(255,255,255,.09)
val AccentDark = Color(0xFFF0BC3A)
val ChipOnBgDark = Color(0x26F0BC3A) // rgba(240,188,58,.15)
val ChipOnTxDark = Color(0xFFF0BC3A)
val GlassDark = Color(0x9E16161B) // rgba(22,22,27,.62)
val GlassBrdDark = Color(0x21FFFFFF) // rgba(255,255,255,.13)
val GlassHiDark = Color(0x29FFFFFF) // rgba(255,255,255,.16)
val FieldDark = Color(0x14FFFFFF) // rgba(255,255,255,.08)
val MarkDark = Color(0xFF5C4708)
val DangerColorDark = Color(0xFFFF6369)
val ShadowDark = Color(0x66000000)

@Immutable
data class GlassCustomColors(
    val bg: Color,
    val card: Color,
    val text: Color,
    val textSecondary: Color,
    val textTertiary: Color,
    val hairline: Color,
    val accent: Color,
    val accentSecondary: Color,
    val chipOnBg: Color,
    val chipOnTx: Color,
    val glass: Color,
    val glassBorder: Color,
    val glassHighlight: Color,
    val field: Color,
    val mark: Color,
    val danger: Color,
    val shadow: Color,
    val isDark: Boolean,
    val isReduced: Boolean
)

val LightGlassColors = GlassCustomColors(
    bg = BgLight,
    card = CardLight,
    text = TxLight,
    textSecondary = Tx2Light,
    textTertiary = Tx3Light,
    hairline = HairLight,
    accent = AccentLight,
    accentSecondary = Accent2Color,
    chipOnBg = ChipOnBgLight,
    chipOnTx = ChipOnTxLight,
    glass = GlassLight,
    glassBorder = GlassBrdLight,
    glassHighlight = GlassHiLight,
    field = FieldLight,
    mark = MarkLight,
    danger = DangerColorLight,
    shadow = ShadowLight,
    isDark = false,
    isReduced = false
)

val DarkGlassColors = GlassCustomColors(
    bg = BgDark,
    card = CardDark,
    text = TxDark,
    textSecondary = Tx2Dark,
    textTertiary = Tx3Dark,
    hairline = HairDark,
    accent = AccentDark,
    accentSecondary = Accent2Color,
    chipOnBg = ChipOnBgDark,
    chipOnTx = ChipOnTxDark,
    glass = GlassDark,
    glassBorder = GlassBrdDark,
    glassHighlight = GlassHiDark,
    field = FieldDark,
    mark = MarkDark,
    danger = DangerColorDark,
    shadow = ShadowDark,
    isDark = true,
    isReduced = false
)

val LocalGlassColors = staticCompositionLocalOf { LightGlassColors }
