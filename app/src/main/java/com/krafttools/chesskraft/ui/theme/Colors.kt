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
     * The accent: tournament brass.
     *
     * Chess is wood boards, brass tournament clocks, and warm hall light — not an
     * instrument readout. Brass reads as deliberate and calm on a dark board and is
     * deliberately unlike the sibling accents (KraftTools instrument-cyan, kraft-ui
     * amber, PulseKraft blue-violet) so the apps are never confused on a phone screen.
     */
    val Accent = Color(0xFFD8B45A)
    val OnAccent = Color(0xFF241A08)

    // Board wood, desaturated so brass markers and red check read instantly.
    // Declared here (the palette) so `colour.per-app-declared` stays green.
    val LightSquare = Color(0xFFD8C6A3)
    val DarkSquare = Color(0xFF7C6247)
    val PieceWhite = Color(0xFFF5EFE2)
    val PieceBlack = Color(0xFF1B1712)

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
            outline = KraftColorSchemes.Dark.outline,
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
            outline = KraftColorSchemes.Light.outline,
            error = KraftColorSchemes.Light.error,
        )
    }
}
