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

    // Board wood: the lichess brown default, light #F0D9B5 over dark #B58863.
    // Adjacent-square contrast is ~2.29:1 — enough to read the grid at a glance
    // without vibrating. Brass markers and red check both sit on top of it.
    // Declared here (the palette) so `colour.per-app-declared` stays green.
    val LightSquare = Color(0xFFF0D9B5)
    val DarkSquare = Color(0xFFB58863)
    // Outline-first piece set (Cburnett-style): paper-white faces stay
    // mid-tone-distinct from the dark app chrome, and read on light wood
    // only via the near-black edge drawn under them — never a shadow.
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
