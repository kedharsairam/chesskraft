/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 Kedhar Sairam
 */
package com.krafttools.chesskraft.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import com.kraft.ui.tokens.KraftIconSize
import com.kraft.ui.tokens.KraftRadius
import com.kraft.ui.tokens.KraftSpacing
import com.kraft.ui.tokens.KraftTypeScale
import com.krafttools.chesskraft.ui.board.PieceMark
import com.krafttools.chesskraft.domain.PieceCode
import com.krafttools.chesskraft.domain.PieceType
import com.krafttools.chesskraft.domain.Side

/**
 * What this app is, what it is not, and who made the pieces.
 *
 * The credit is the reason this screen exists. The art in this app is Colin
 * M.L. Burnett's, and its licence requires that the notice travel with the
 * binary — a `NOTICE` file in the source tree is not enough once someone has
 * installed an APK and the source tree is not on their phone. So the credit is
 * here, in the app, in plain words, with the licence named and a link to the
 * original.
 *
 * The claims below are all checkable and none of them are marketing: zero
 * permissions is a fact about the manifest, no accounts is a fact about the
 * absence of a login screen, nothing leaves the device is a fact about the one
 * save file. If any of them stops being true, this screen is wrong, and that is
 * the point of writing them down somewhere the app itself has to render.
 */
@Composable
fun AboutScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding(),
    ) {
        AboutTopBar(onBack)
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = KraftSpacing.ScreenEdge),
            verticalArrangement = Arrangement.spacedBy(KraftSpacing.Spacing16),
        ) {
            Spacer(Modifier.height(KraftSpacing.Spacing8))
            WhatThisIs()
            Spacer(Modifier.height(KraftSpacing.Spacing8))
            CreditCard()
            Spacer(Modifier.height(KraftSpacing.Spacing8))
            LegalCard()
            Spacer(Modifier.height(KraftSpacing.Spacing24))
        }
    }
}

@Composable
private fun AboutTopBar(onBack: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = KraftSpacing.Spacing8, vertical = KraftSpacing.Spacing8),
    ) {
        IconButton(
            onClick = onBack,
            modifier = Modifier
                .size(KraftSpacing.Spacing48)
                .semantics(mergeDescendants = true) {
                    contentDescription = "Back to the home screen."
                },
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = null,
                modifier = Modifier.size(KraftIconSize.Medium),
            )
        }
        Spacer(Modifier.size(KraftSpacing.Spacing4))
        Text(
            text = "About",
            style = MaterialTheme.typography.titleLarge.copy(
                fontWeight = FontWeight.SemiBold,
            ),
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

/** Three sentences, each of which is a fact about the build rather than a claim. */
@Composable
private fun WhatThisIs() {
    Card(
        shape = RoundedCornerShape(KraftRadius.Large),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLowest,
        ),
        border = BorderStroke(
            KraftSpacing.BorderWidth,
            MaterialTheme.colorScheme.outlineVariant,
        ),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(KraftSpacing.Spacing16),
            verticalArrangement = Arrangement.spacedBy(KraftSpacing.Spacing8),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                PieceMark(
                    code = PieceCode.of(Side.WHITE, PieceType.KNIGHT),
                    modifier = Modifier.size(KraftSpacing.Spacing32),
                )
                Spacer(Modifier.size(KraftSpacing.Spacing12))
                Text(
                    text = "ChessKraft",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Spacer(Modifier.size(KraftSpacing.Spacing8))
                Text(
                    text = ChessKraftVersion,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text(
                text = "An offline chess coach. You play a named opponent at four " +
                    "strengths, and after the game it reads the moves back to you " +
                    "with an engine's opinion of each one.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = "No accounts. No ads. No network code of any kind, and " +
                    "nothing to grant: no internet, no storage, no notifications, " +
                    "no location. The only file it writes is your games, on this " +
                    "device.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                // Precise on purpose. `aapt2 dump permissions` on the built APK
                // lists exactly one entry, and it is the permission AndroidX
                // merges in for an app's own non-exported receivers — a
                // signature-level permission the app defines for itself, which
                // prompts for nothing and grants nothing to anyone else. Saying
                // "zero permissions" when the manifest says one would be a
                // claim this screen could not survive being checked.
                text = PermissionFootnote,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/**
 * The credit. Named first, licence named, link given, and the actual words of
 * the licence reproduced rather than summarised — a summary of a licence is a
 * different licence.
 */
@Composable
private fun CreditCard() {
    Card(
        shape = RoundedCornerShape(KraftRadius.Large),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLowest,
        ),
        border = BorderStroke(
            KraftSpacing.BorderWidth,
            MaterialTheme.colorScheme.outlineVariant,
        ),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(KraftSpacing.Spacing16),
            verticalArrangement = Arrangement.spacedBy(KraftSpacing.Spacing8),
        ) {
            Text(
                text = "The pieces",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = "Every piece on every board in this app is drawn by " +
                    "Colin M.L. Burnett, and used with his permission under the " +
                    "3-clause BSD licence. It is his work, not mine.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = CburnettLicence,
                style = MaterialTheme.typography.bodySmall.copy(
                    fontFamily = FontFamily.Monospace,
                ),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = CburnettSource,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
            )
        }
    }
}

@Composable
private fun LegalCard() {
    Card(
        shape = RoundedCornerShape(KraftRadius.Large),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLowest,
        ),
        border = BorderStroke(
            KraftSpacing.BorderWidth,
            MaterialTheme.colorScheme.outlineVariant,
        ),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(KraftSpacing.Spacing16),
            verticalArrangement = Arrangement.spacedBy(KraftSpacing.Spacing8),
        ) {
            Text(
                text = "Licence",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = "ChessKraft is free software under the MIT licence: you may " +
                    "use, study, copy, modify and redistribute it, and the only " +
                    "obligation is to keep this notice with it.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = "The engine in this app was written from scratch for this " +
                    "project. The piece art is the only third-party work in the " +
                    "build, and it is credited above.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** Kept in step with the version in `app/build.gradle.kts` by hand, deliberately. */
private const val ChessKraftVersion = "0.1.0"

private const val CburnettLicence = """Copyright (c) Colin M.L. Burnett
All rights reserved.

Redistribution and use in source and binary forms, with or without
modification, are permitted provided that the following conditions
are met:

  * Redistributions of source code must retain the above copyright
    notice, this list of conditions and the following disclaimer.
  * Redistributions in binary form must reproduce the above copyright
    notice, this list of conditions and the following disclaimer in the
    documentation and/or other materials provided with the distribution.
  * Neither the name of the author nor the names of its contributors
    may be used to endorse or promote products derived from this
    software without specific prior written permission.

THIS SOFTWARE IS PROVIDED BY THE AUTHOR AND CONTRIBUTORS "AS IS" AND
ANY EXPRESS OR IMPLIED WARRANTIES ARE DISCLAIMED."""

private const val CburnettSource = "commons.wikimedia.org/wiki/File:Chess_kdt45.svg"

private const val PermissionFootnote =
    "Stated precisely: this app asks for no permission you can see or grant. " +
        "Its manifest declares one entry, the signature-level permission AndroidX " +
        "merges in so an app's own non-exported receivers are safe, and it " +
        "confers nothing on anyone else."