/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 Kedhar Sairam
 */
package com.krafttools.chesskraft.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import com.krafttools.chesskraft.ui.board.PieceMark
import com.kraft.ui.tokens.KraftRadius
import com.kraft.ui.tokens.KraftSpacing
import com.kraft.ui.tokens.KraftTypeScale
import com.krafttools.chesskraft.domain.PieceCode
import com.krafttools.chesskraft.domain.PieceType
import com.krafttools.chesskraft.domain.Side
import com.krafttools.chesskraft.domain.pieceValue
import com.krafttools.chesskraft.presentation.GameUiState
import com.krafttools.chesskraft.presentation.GameViewModel
import com.krafttools.chesskraft.presentation.SoundPlayer
import com.krafttools.chesskraft.ui.board.ChessBoard
import com.krafttools.chesskraft.ui.board.GameOverSheet
import com.krafttools.chesskraft.ui.board.PieceMark
import com.krafttools.chesskraft.ui.board.PromotionDialog
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

    Column(modifier = modifier.fillMaxSize().navigationBarsPadding()) {
        // Local top bar, not the foundation's: the foundation bar's default-ink
        // title renders nothing visible on this screen (node present, zero
        // pixels — reported to the foundation track), while every explicit-ink
        // text on this screen draws fine. Explicit ink here, no mystery.
        GameTopBar(
            soundOn = state.soundOn,
            onToggleSound = viewModel::toggleSound,
        )

        // The board is sized by the smaller incoming dimension: full width in
        // portrait, the height budget in landscape with the strips and status
        // reflowing into a side column.
        BoxWithConstraints(Modifier.fillMaxWidth().weight(1f)) {
            val landscape = maxWidth > maxHeight
            val boardSide = minOf(maxWidth * BoardLandscapeFraction, maxHeight)
            if (!landscape) {
                Column(
                    verticalArrangement = Arrangement.Center,
                    modifier = Modifier.fillMaxSize(),
                ) {
                    BoardChrome(
                        state = state,
                        playerSide = viewModel.playerSide,
                        statusText = if (state.aiThinking) "Thinking…" else state.statusText,
                        onTap = viewModel::onTap,
                        onDrop = viewModel::onDrop,
                    )
                }
            } else {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    ChessBoard(
                        state = state,
                        onTap = viewModel::onTap,
                        onDrop = viewModel::onDrop,
                        modifier = Modifier
                            .size(boardSide)
                            .padding(start = KraftSpacing.Spacing16),
                    )
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .padding(horizontal = KraftSpacing.Spacing16),
                        verticalArrangement = Arrangement.Center,
                    ) {
                        SideChrome(
                            state = state,
                            playerSide = viewModel.playerSide,
                            statusText = if (state.aiThinking) "Thinking…" else state.statusText,
                        )
                    }
                }
            }
        }

        ToolbarRow(
            canUndo = state.canUndo,
            onUndo = viewModel::undo,
            onHint = viewModel::hint,
            onNew = onNewGame,
            onResign = { confirmResign = true },
            onFlip = viewModel::flip,
            modifier = Modifier
                .navigationBarsPadding()
                .padding(bottom = KraftSpacing.Spacing8),
        )
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
                Button(
                    onClick = {
                        confirmResign = false
                        viewModel.resign()
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error,
                        contentColor = MaterialTheme.colorScheme.onError,
                    ),
                ) {
                    Text("Resign")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { confirmResign = false },
                    colors = ButtonDefaults.textButtonColors(
                        contentColor = MaterialTheme.colorScheme.onSurface,
                    ),
                ) {
                    Text("Keep Playing")
                }
            },
        )
    }

    val result = state.result
    if (result != null && sheetOpen) {
        GameOverSheet(
            result = result,
            moveCount = state.sans.size,
            onRematch = onRematch,
            onNewGame = onNewGame,
            onReview = onOpenReview,
            onDismiss = { sheetOpen = false },
        )
    }
}

/**
 * Compact bar: brass knight + title left, quiet sound action right. All ink
 * explicit — see the call-site note about the foundation bar.
 */
@Composable
private fun GameTopBar(soundOn: Boolean, onToggleSound: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(horizontal = KraftSpacing.ScreenEdge, vertical = KraftSpacing.Spacing8),
    ) {
        Text(
            text = "♞",
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.primary,
        )
        Spacer(Modifier.size(KraftSpacing.Spacing8))
        Text(
            text = "ChessKraft",
            style = MaterialTheme.typography.titleLarge.copy(
                fontWeight = FontWeight.SemiBold,
            ),
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            modifier = Modifier.weight(1f),
        )
        TextButton(
            onClick = onToggleSound,
            colors = ButtonDefaults.textButtonColors(
                contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
            ),
            modifier = Modifier
                .defaultMinSize(minHeight = KraftSpacing.Spacing48)
                .semantics(mergeDescendants = true) {
                    contentDescription = if (soundOn) "Mute move sounds." else "Unmute move sounds."
                },
        ) {
            Text(
                text = if (soundOn) "Sound on" else "Muted",
                style = MaterialTheme.typography.labelMedium,
            )
        }
    }
}

@Composable
private fun BoardChrome(
    state: GameUiState,
    playerSide: Side,
    statusText: String,
    onTap: (Int) -> Unit,
    onDrop: (Int, Int) -> Unit,
) {
    CapturedStrip(
        label = capturedLabel(playerSide.opponent(), state.capturedByWhite, state.capturedByBlack),
        pieces = if (playerSide == Side.WHITE) {
            state.capturedByBlack
        } else {
            state.capturedByWhite
        },
        victimSide = playerSide,
    )
    ChessBoard(
        state = state,
        onTap = onTap,
        onDrop = onDrop,
        modifier = Modifier.padding(horizontal = KraftSpacing.Spacing16),
    )
    CapturedStrip(
        label = capturedLabel(playerSide, state.capturedByWhite, state.capturedByBlack),
        pieces = if (playerSide == Side.WHITE) {
            state.capturedByWhite
        } else {
            state.capturedByBlack
        },
        victimSide = playerSide.opponent(),
    )
    StatusLine(statusText)
}

@Composable
private fun SideChrome(
    state: GameUiState,
    playerSide: Side,
    statusText: String,
) {
    CapturedStrip(
        label = capturedLabel(playerSide.opponent(), state.capturedByWhite, state.capturedByBlack),
        pieces = if (playerSide == Side.WHITE) {
            state.capturedByBlack
        } else {
            state.capturedByWhite
        },
        victimSide = playerSide,
    )
    CapturedStrip(
        label = capturedLabel(playerSide, state.capturedByWhite, state.capturedByBlack),
        pieces = if (playerSide == Side.WHITE) {
            state.capturedByWhite
        } else {
            state.capturedByBlack
        },
        victimSide = playerSide.opponent(),
    )
    StatusLine(statusText)
}

@Composable
private fun StatusLine(statusText: String) {
    Text(
        text = statusText,
        style = MaterialTheme.typography.bodyLarge,
        color = MaterialTheme.colorScheme.onSurface,
        textAlign = TextAlign.Center,
        modifier = Modifier
            .fillMaxWidth()
            .padding(
                horizontal = KraftSpacing.Spacing16,
                vertical = KraftSpacing.Spacing8,
            )
            .semantics(mergeDescendants = true) {
                contentDescription = "Status. $statusText"
            },
    )
}

@Composable
private fun CapturedStrip(label: String, pieces: List<PieceType>, victimSide: Side) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = KraftSpacing.Spacing16, vertical = KraftSpacing.Spacing4)
            .semantics(mergeDescendants = true) { contentDescription = label },
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(KraftSpacing.Spacing2),
            modifier = Modifier.weight(1f),
        ) {
            for (type in pieces) {
                PieceMark(
                    code = PieceCode.of(victimSide, type),
                    modifier = Modifier.size(KraftSpacing.Spacing24),
                )
            }
        }
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

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ToolbarRow(
    canUndo: Boolean,
    onUndo: () -> Unit,
    onHint: () -> Unit,
    onNew: () -> Unit,
    onResign: () -> Unit,
    onFlip: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // One segmented bar, not five floating labels: a hairline container with
    // dividers reads as a single instrument strip. Wraps at font-scale 2.0
    // via FlowRow instead of clipping.
    Card(
        shape = RoundedCornerShape(KraftRadius.Large),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        ),
        border = BorderStroke(
            KraftSpacing.BorderWidth,
            MaterialTheme.colorScheme.outlineVariant,
        ),
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = KraftSpacing.ScreenEdge),
    ) {
        FlowRow(
            horizontalArrangement = Arrangement.SpaceEvenly,
            modifier = Modifier.fillMaxWidth(),
        ) {
            ToolbarButton("Undo", "Take back your last move", canUndo, onUndo)
            ToolbarButton("Hint", "Show a suggested move", true, onHint)
            ToolbarButton("New", "Start a new game", true, onNew)
            ToolbarButton("Resign", "Give up this game", true, onResign, destructive = true)
            ToolbarButton("Flip", "Turn the board around", true, onFlip)
        }
    }
}

@Composable
private fun ToolbarButton(
    label: String,
    description: String,
    enabled: Boolean,
    onClick: () -> Unit,
    destructive: Boolean = false,
) {
    TextButton(
        onClick = onClick,
        enabled = enabled,
        colors = ButtonDefaults.textButtonColors(
            contentColor = if (destructive) {
                MaterialTheme.colorScheme.error
            } else {
                MaterialTheme.colorScheme.onSurface
            },
            disabledContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
        ),
        modifier = Modifier
            .defaultMinSize(
                minWidth = KraftSpacing.Spacing48,
                minHeight = KraftSpacing.Spacing48,
            )
            .semantics(mergeDescendants = true) {
                contentDescription = "$label. $description."
            },
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium.copy(
                fontWeight = FontWeight.SemiBold,
            ),
        )
    }
}

/** Board share of the width budget in landscape; the rest is strips + status. */
private const val BoardLandscapeFraction = 0.62f
private const val CheckHapticGapMs = 90L
