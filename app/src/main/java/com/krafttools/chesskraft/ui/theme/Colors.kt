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
    val Accent = Color(0xFF7FA650)
    val OnAccent = Color(0xFF1A240E)

    /**
     * Tournament gold. Felt-side decor only — board frame, last-move wash,
     * watermark — never actions or selections. Gold on green felt is the
     * tournament hall; green is the app talking to you.
     */
    val FeltGold = Color(0xFFD8B45A)

    // Chess.com-dark chrome: warm charcoal, not phone black. Three steps —
    // page, card, raised — separated by tint, finished with hairlines.
    val Page = Color(0xFF302C29)
    val Card = Color(0xFF3A352F)
    val CardRaised = Color(0xFF48423B)
    val Ink = Color(0xFFEDE8E0)
    val InkDim = Color(0xFFB3A99C)
    val Hairline = Color(0xFF57514A)
    val HairlineSoft = Color(0xFF453F39)
    val TileEdge = Color(0xFF4E4840)
    // Felt: tournament green over the charcoal page. Adjacent-square
    // contrast is ~2.8:1, the strongest mainstream default: the grid reads at
    // a glance without vibrating. Gold washes and the red check both sit on
    // top of it. Declared here (the palette) so `colour.per-app-declared`
    // stays green.
    val LightSquare = Color(0xFFEBECD0)
    val DarkSquare = Color(0xFF739552)
    // Cburnett art (see Pieces.kt + NOTICE): cream faces stay distinct from
    // the charcoal chrome; espresso reads on light felt directly.
    val PieceWhite = Color(0xFFF7F1DE)
    val PieceBlack = Color(0xFF2A2118)
    val PieceEdgeDark = Color(0xFF3A2C14)
    val PieceEdgeLight = Color(0xFFF7F1DE)
    // Coordinates whisper in the square's own family, darkened one step so
    // text stays readable on its own square. No chips, no backplates.
    val CoordOnLight = Color(0xFF4F7038)
    val CoordOnDark = Color(0xFFEEEED2)

    /**
     * The scheme. Chess.com-dark, not phone black: warm charcoal surfaces so
     * the felt sits *in* a room instead of floating in a void. The divergence
     * from the portfolio default is declared here, per
     * `colour.accent-per-app-allowed` — same roles, warmer values.
     */
    fun scheme(dark: Boolean) = if (dark) {
        darkColorScheme(
            primary = Accent,
            onPrimary = OnAccent,
            background = Page,
            surface = Page,
            surfaceVariant = Card,
            onSurface = Ink,
            onSurfaceVariant = InkDim,
            surfaceContainerLowest = Page,
            surfaceContainerLow = Card,
            surfaceContainer = Card,
            surfaceContainerHigh = CardRaised,
            surfaceContainerHighest = TileEdge,
            outline = Hairline,
            outlineVariant = HairlineSoft,
            error = Color(0xFFE0604E),
            onError = Color(0xFF2A0F0C),
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
