/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 Kedhar Sairam
 */
package com.krafttools.chesskraft.ui.theme

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color
import com.kraft.ui.theme.KraftColorSchemes

/**
 * The app's palette. This is the one place a colour literal is allowed to live — see
 * `colour.per-app-declared`.
 *
 * Structural colours come from the foundation so ChessKraft reads as a Kraft app.
 * Only the accent is local.
 */
object ChessKraftColors {

    /**
     * The accent: chess green.
     *
     * The app speaks chess.com's visual language, and in that language green
     * means go — Play, selected, your turn. #81B64C with near-black ink
     * (6.6:1) where chess.com itself ships white-on-green below AA; same hue
     * family, honest contrast. Deliberately unlike the sibling accents
     * (KraftTools cyan, kraft-ui amber, PulseKraft blue-violet) so the apps
     * are never confused on a phone screen.
     */
    val Accent = Color(0xFF81B64C)
    val OnAccent = Color(0xFF16210C)

    /**
     * Tournament gold. Felt-side decor only — board frame, last-move wash,
     * watermark — never actions or selections. Gold on green felt is the
     * tournament hall; green is the app talking to you.
     */
    val FeltGold = Color(0xFFD8B45A)

    // Board felt: tournament green — light #EEEED2 over dark #769656.
    // Adjacent-square contrast is ~2.8:1, the strongest mainstream default:
    // the grid reads at a glance without vibrating. Brass washes and the
    // red check both sit on top of it. Declared here (the palette) so
    // `colour.per-app-declared` stays green.
    val LightSquare = Color(0xFFEEEED2)
    val DarkSquare = Color(0xFF769656)
    // Our own vector set (see Pieces.kt): cream faces stay mid-tone-distinct
    // from the dark chrome; espresso reads on light felt directly. Edges are
    // drawn strokes — never shadows, never font glyphs.
    val PieceWhite = Color(0xFFF7F1DE)
    val PieceBlack = Color(0xFF2A2118)
    val PieceEdgeDark = Color(0xFF3A2C14)
    val PieceEdgeLight = Color(0xFFF7F1DE)
    // Coordinates whisper in the square's own family, darkened one step so
    // text stays readable on its own square. No chips, no backplates.
    val CoordOnLight = Color(0xFF4F7038)
    val CoordOnDark = Color(0xFFEEEED2)

    /**
     * The scheme. Structural colours come from the foundation so a change there reaches every
     * app; only the accent is local.
     */
    fun scheme(dark: Boolean) = if (dark) {
        darkColorScheme(
            primary = Accent,
            onPrimary = OnAccent,
            background = KraftColorSchemes.Dark.background,
            surface = KraftColorSchemes.Dark.surface,
            surfaceVariant = KraftColorSchemes.Dark.surfaceVariant,
            onSurface = KraftColorSchemes.Dark.onSurface,
            onSurfaceVariant = KraftColorSchemes.Dark.onSurfaceVariant,
            surfaceContainerLowest = KraftColorSchemes.Dark.surfaceContainerLowest,
            surfaceContainerLow = KraftColorSchemes.Dark.surfaceContainerLow,
            surfaceContainer = KraftColorSchemes.Dark.surfaceContainer,
            surfaceContainerHigh = KraftColorSchemes.Dark.surfaceContainerHigh,
            surfaceContainerHighest = KraftColorSchemes.Dark.surfaceContainerHighest,
            outline = KraftColorSchemes.Dark.outline,
            outlineVariant = KraftColorSchemes.Dark.outlineVariant,
            error = KraftColorSchemes.Dark.error,
        )
    } else {
        lightColorScheme(
            primary = Accent,
            onPrimary = OnAccent,
            background = KraftColorSchemes.Light.background,
            surface = KraftColorSchemes.Light.surface,
            surfaceVariant = KraftColorSchemes.Light.surfaceVariant,
            onSurface = KraftColorSchemes.Light.onSurface,
            onSurfaceVariant = KraftColorSchemes.Light.onSurfaceVariant,
            surfaceContainerLowest = KraftColorSchemes.Light.surfaceContainerLowest,
            surfaceContainerLow = KraftColorSchemes.Light.surfaceContainerLow,
            surfaceContainer = KraftColorSchemes.Light.surfaceContainer,
            surfaceContainerHigh = KraftColorSchemes.Light.surfaceContainerHigh,
            surfaceContainerHighest = KraftColorSchemes.Light.surfaceContainerHighest,
            outline = KraftColorSchemes.Light.outline,
            outlineVariant = KraftColorSchemes.Light.outlineVariant,
            error = KraftColorSchemes.Light.error,
        )
    }
}
