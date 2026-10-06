/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 Kedhar Sairam
 */
package com.krafttools.chesskraft.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import com.kraft.ui.components.KraftTopBar
import com.kraft.ui.tokens.KraftSpacing
import com.krafttools.chesskraft.domain.PieceCode
import com.krafttools.chesskraft.domain.PieceType
import com.krafttools.chesskraft.domain.Side
import com.krafttools.chesskraft.domain.pieceValue
import com.krafttools.chesskraft.presentation.GameViewModel
import com.krafttools.chesskraft.presentation.SoundPlayer
import com.krafttools.chesskraft.ui.board.ChessBoard
import com.krafttools.chesskraft.ui.board.GameOverSheet
import com.krafttools.chesskraft.ui.board.PromotionDialog
import com.krafttools.chesskraft.ui.board.glyphFor
import kotlinx.coroutines.delay

/**
 * The Game screen: captured strip, board, plain-English status, and the
 * toolbar — Undo / Hint / New / Resign / Flip, plus Sound up top.
 */
@Composable
fun GameScreen(
    viewModel: GameViewModel,
    onRematch: () -> Unit,
    onNewGame: () -> Unit,
    onOpenReview: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.state.collectAsState()
    val haptics = LocalHapticFeedback.current
    val context = LocalContext.current
    var confirmResign by remember { mutableStateOf(false) }
    var sheetOpen by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        if (viewModel.soundPlayer == null) {
            viewModel.soundPlayer = SoundPlayer(context).also {
                it.enabled = viewModel.state.value.soundOn
            }
        }
    }
    LaunchedEffect(state.result) {
        if (state.result != null) sheetOpen = true
    }

    // Haptics: tick on pick-up, thock on a move, double-tick on check.
    // Illegal taps stay silent — the board shakes instead.
    var previous by remember { mutableStateOf(state) }
    LaunchedEffect(state) {
        if (state.pieces != previous.pieces) {
            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
        } else if (state.selected != null && previous.selected == null) {
            haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        }
        if (state.checkSquare != null && previous.checkSquare == null) {
            delay(CheckHapticGapMs)
            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
            delay(CheckHapticGapMs)
            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
        }
        previous = state
    }

    Column(modifier = modifier.fillMaxSize()) {
        KraftTopBar(
            title = "ChessKraft",
            actions = {
                TextButton(onClick = viewModel::toggleSound) {
                    Text(if (state.soundOn) "Sound on" else "Muted")
                }
            },
        )

        CapturedStrip(
            label = capturedLabel(viewModel.playerSide.opponent(), state.capturedByWhite, state.capturedByBlack),
            pieces = if (viewModel.playerSide == Side.WHITE) {
                state.capturedByBlack
            } else {
                state.capturedByWhite
            },
            victimSide = viewModel.playerSide,
        )

        ChessBoard(
            state = state,
            onTap = viewModel::onTap,
            onDrop = viewModel::onDrop,
            modifier = Modifier.padding(horizontal = KraftSpacing.Spacing16),
        )

        CapturedStrip(
            label = capturedLabel(viewModel.playerSide, state.capturedByWhite, state.capturedByBlack),
            pieces = if (viewModel.playerSide == Side.WHITE) {
                state.capturedByWhite
            } else {
                state.capturedByBlack
            },
            victimSide = viewModel.playerSide.opponent(),
        )

        Text(
            text = if (state.aiThinking) "Thinking…" else state.statusText,
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .fillMaxWidth()
                .padding(
                    horizontal = KraftSpacing.Spacing16,
                    vertical = KraftSpacing.Spacing8,
                )
                .semantics { contentDescription = "Status. ${state.statusText}" },
        )

        Spacer(Modifier.weight(1f))

        ToolbarRow(
            canUndo = state.canUndo,
            onUndo = viewModel::undo,
            onHint = viewModel::hint,
            onNew = onNewGame,
            onResign = { confirmResign = true },
            onFlip = viewModel::flip,
        )
        Spacer(Modifier.height(KraftSpacing.Spacing16))
    }

    if (state.pendingPromotion.isNotEmpty()) {
        PromotionDialog(
            options = state.pendingPromotion,
            side = state.sideToMove,
            onChoose = viewModel::onPromote,
            onDismiss = viewModel::dismissPromotion,
        )
    }

    if (confirmResign) {
        AlertDialog(
            onDismissRequest = { confirmResign = false },
            title = { Text("Resign this game?") },
            text = { Text("Your opponent takes the point. You can still review the game after.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmResign = false
                        viewModel.resign()
                    },
                ) {
                    Text("Resign")
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmResign = false }) {
                    Text("Keep playing")
                }
            },
        )
    }

    val result = state.result
    if (result != null && sheetOpen) {
        GameOverSheet(
            result = result,
            onRematch = onRematch,
            onNewGame = onNewGame,
            onReview = onOpenReview,
            onDismiss = { sheetOpen = false },
        )
    }
}

@Composable
private fun CapturedStrip(label: String, pieces: List<PieceType>, victimSide: Side) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = KraftSpacing.Spacing16, vertical = KraftSpacing.Spacing4)
            .semantics { contentDescription = label },
    ) {
        Text(
            text = pieces.joinToString(" ") { glyphFor(PieceCode.of(victimSide, it)) },
            style = MaterialTheme.typography.bodyLarge,
            maxLines = 1,
            modifier = Modifier.weight(1f),
        )
        val material = pieces.sumOf { pieceValue(it) } / 100
        if (material > 0) {
            Text(
                text = "+$material",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

private fun capturedLabel(
    forSide: Side,
    capturedByWhite: List<PieceType>,
    capturedByBlack: List<PieceType>,
): String {
    val pieces = if (forSide == Side.WHITE) capturedByWhite else capturedByBlack
    val who = if (forSide == Side.WHITE) "White" else "Black"
    if (pieces.isEmpty()) return "$who has captured nothing yet."
    return "$who captured: ${pieces.joinToString(", ") { it.name.lowercase() }}."
}

@Composable
private fun ToolbarRow(
    canUndo: Boolean,
    onUndo: () -> Unit,
    onHint: () -> Unit,
    onNew: () -> Unit,
    onResign: () -> Unit,
    onFlip: () -> Unit,
) {
    Row(
        horizontalArrangement = Arrangement.SpaceEvenly,
        modifier = Modifier.fillMaxWidth(),
    ) {
        ToolbarButton("Undo", "Take back your last move", canUndo, onUndo)
        ToolbarButton("Hint", "Show a suggested move", true, onHint)
        ToolbarButton("New", "Start over", true, onNew)
        ToolbarButton("Resign", "Give up this game", true, onResign)
        ToolbarButton("Flip", "Turn the board around", true, onFlip)
    }
}

@Composable
private fun ToolbarButton(
    label: String,
    description: String,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    TextButton(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier
            .size(KraftSpacing.Spacing48)
            .semantics { contentDescription = "$label. $description." },
    ) {
        Text(label, style = MaterialTheme.typography.labelMedium)
    }
}

private const val CheckHapticGapMs = 90L
