/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 Kedhar Sairam
 */
package com.krafttools.chesskraft.ui.board

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import com.kraft.ui.tokens.KraftRadius
import com.kraft.ui.tokens.KraftSpacing
import com.kraft.ui.tokens.KraftTypeScale
import com.krafttools.chesskraft.domain.GameResult
import com.krafttools.chesskraft.domain.PieceCode
import com.krafttools.chesskraft.domain.PieceType
import com.krafttools.chesskraft.domain.Side
import com.krafttools.chesskraft.ui.screens.fullMoves

/**
 * Game-over sheet. The result leads — a short headline, one sub-line, and the
 * one number that matters (moves played) — then Rematch, New Game, Review.
 * Rematch is the single filled action; nothing here is dead.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GameOverSheet(
    result: GameResult,
    moveCount: Int,
    onRematch: () -> Unit,
    onNewGame: () -> Unit,
    onReview: () -> Unit,
    reviewRunning: Boolean,
    onDismiss: () -> Unit,
) {
    // skipPartiallyExpanded: the sheet used to open half-height and put the
    // Review button below the fold, so the one thing this screen exists for was
    // invisible until the player discovered the sheet could be dragged.
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                // Scrollable as well, because a large font scale can push the
                // last button off even a fully expanded sheet, and a control
                // that cannot be reached is not a control.
                .verticalScroll(rememberScrollState())
                .padding(horizontal = KraftSpacing.ScreenEdge)
                .navigationBarsPadding()
                .padding(bottom = KraftSpacing.Spacing8),
        ) {
            ResultEmblem()
            Spacer(Modifier.height(KraftSpacing.Spacing16))
            Text(
                text = result.title,
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(KraftSpacing.Spacing4))
            Text(
                text = result.reason,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(KraftSpacing.Spacing8))
            Text(
                text = movesLine(moveCount),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(KraftSpacing.Spacing24))
            Button(
                onClick = onRematch,
                shape = RoundedCornerShape(KraftRadius.Medium),
                colors = ButtonDefaults.buttonColors(),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(KraftSpacing.Spacing56),
            ) {
                Text(
                    text = "Rematch",
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontSize = KraftTypeScale.Callout,
                        fontWeight = FontWeight.SemiBold,
                    ),
                )
            }
            Spacer(Modifier.height(KraftSpacing.Spacing8))
            OutlinedButton(
                onClick = onNewGame,
                shape = RoundedCornerShape(KraftRadius.Medium),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(KraftSpacing.Spacing56),
            ) {
                Text(
                    text = "New Game",
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontSize = KraftTypeScale.Callout,
                        fontWeight = FontWeight.SemiBold,
                    ),
                )
            }
            Spacer(Modifier.height(KraftSpacing.Spacing4))
            TextButton(
                onClick = onReview,
                enabled = !reviewRunning,
                colors = ButtonDefaults.textButtonColors(
                    contentColor = MaterialTheme.colorScheme.onSurface,
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(KraftSpacing.Spacing48),
            ) {
                Text(if (reviewRunning) "Reading the game…" else "Review")
            }
        }
    }
}

/**
 * The result, enthroned: the king in a gold-ringed circle. One emblem for
 * every ending — the words carry win/loss/draw, never the color.
 */
@Composable
private fun ResultEmblem() {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(ResultEmblemSize)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                .border(
                    KraftSpacing.BorderWidth,
                    MaterialTheme.colorScheme.primary,
                    CircleShape,
                ),
        ) {
            PieceMark(
                code = PieceCode.of(Side.WHITE, PieceType.KING),
                modifier = Modifier.size(ResultKingSize),
            )
        }
    }
}

private val ResultEmblemSize = KraftSpacing.Spacing64 * 2
private val ResultKingSize = KraftSpacing.Spacing64

private fun movesLine(plyCount: Int): String = when (fullMoves(plyCount)) {
    0 -> "No moves played."
    1 -> "One move played."
    else -> "${fullMoves(plyCount)} moves played."
}
