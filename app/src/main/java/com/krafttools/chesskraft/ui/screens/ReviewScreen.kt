/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 Kedhar Sairam
 */
package com.krafttools.chesskraft.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import com.kraft.ui.components.KraftTopBar
import com.kraft.ui.motion.rememberReduceMotion
import com.kraft.ui.tokens.KraftRadius
import com.kraft.ui.tokens.KraftSpacing
import com.krafttools.chesskraft.domain.Position
import com.krafttools.chesskraft.domain.Side
import com.krafttools.chesskraft.presentation.GameUiState
import com.krafttools.chesskraft.ui.board.ChessBoard

/**
 * Review. The finished game as a move list with step back/forward and flip.
 * Read-only board — taps do nothing. No eval bar in v1.
 *
 * Static text is always on-surface; the only coloured element is the marker
 * on the row showing the current position.
 */
@Composable
fun ReviewScreen(
    fens: List<String>,
    sans: List<String>,
    playerSide: Side,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (fens.isEmpty()) {
        EmptyReview(onBack = onBack, modifier = modifier)
        return
    }
    var index by remember(fens) { mutableIntStateOf(fens.size - 1) }
    var flipped by remember { mutableStateOf(false) }
    val listState = rememberLazyListState()
    val reduceMotion = rememberReduceMotion()

    val position = remember(fens, index) {
        Position.fromFen(fens[index.coerceIn(fens.indices)]).getOrNull()
            ?: Position.start()
    }
    val reviewState = remember(position, flipped) {
        GameUiState(
            pieces = position.board.toList(),
            sideToMove = position.sideToMove,
            playerSide = playerSide,
            flipped = flipped,
            statusText = reviewStatus(index, sans.size),
            sans = sans.take(index.coerceIn(0, sans.size)),
        )
    }

    LaunchedEffect(index) {
        if (sans.isNotEmpty()) {
            val row = (index.coerceIn(1, sans.size) - 1) / 2
            if (reduceMotion) {
                listState.scrollToItem(row)
            } else {
                listState.animateScrollToItem(row)
            }
        }
    }

    Column(modifier = modifier.fillMaxSize()) {
        KraftTopBar(
            title = "Review",
            actions = {
                TextButton(
                    onClick = onBack,
                    colors = ButtonDefaults.textButtonColors(
                        contentColor = MaterialTheme.colorScheme.onSurface,
                    ),
                    modifier = Modifier.semantics(mergeDescendants = true) {
                        contentDescription = "Done. Back to home."
                    },
                ) {
                    Text("Done")
                }
            },
        )
        ChessBoard(
            state = reviewState,
            onTap = {},
            onDrop = { _, _ -> },
            interactive = false,
            modifier = Modifier.padding(horizontal = KraftSpacing.Spacing16),
        )
        Spacer(Modifier.height(KraftSpacing.Spacing8))
        Text(
            text = reviewStatus(index, sans.size),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .fillMaxWidth()
                .semantics(mergeDescendants = true) {
                    contentDescription = "Review position. ${reviewStatus(index, sans.size)}"
                },
        )
        Spacer(Modifier.height(KraftSpacing.Spacing8))
        Row(
            horizontalArrangement = Arrangement.SpaceEvenly,
            modifier = Modifier.fillMaxWidth(),
        ) {
            TextButton(
                onClick = { index = (index - 1).coerceAtLeast(0) },
                enabled = index > 0,
                colors = ButtonDefaults.textButtonColors(
                    contentColor = MaterialTheme.colorScheme.onSurface,
                ),
                modifier = Modifier
                    .height(KraftSpacing.Spacing48)
                    .semantics(mergeDescendants = true) {
                        contentDescription = "Back. Show the previous move."
                    },
            ) {
                Text("Back")
            }
            TextButton(
                onClick = { flipped = !flipped },
                colors = ButtonDefaults.textButtonColors(
                    contentColor = MaterialTheme.colorScheme.onSurface,
                ),
                modifier = Modifier
                    .height(KraftSpacing.Spacing48)
                    .semantics(mergeDescendants = true) {
                        contentDescription = "Flip. Turn the board around."
                    },
            ) {
                Text("Flip")
            }
            TextButton(
                onClick = { index = (index + 1).coerceAtMost(fens.size - 1) },
                enabled = index < fens.size - 1,
                colors = ButtonDefaults.textButtonColors(
                    contentColor = MaterialTheme.colorScheme.onSurface,
                ),
                modifier = Modifier
                    .height(KraftSpacing.Spacing48)
                    .semantics(mergeDescendants = true) {
                        contentDescription = "Forward. Show the next move."
                    },
            ) {
                Text("Forward")
            }
        }
        Spacer(Modifier.height(KraftSpacing.Spacing8))
        if (sans.isEmpty()) {
            Text(
                text = "No moves yet — the game ended before it began.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = KraftSpacing.Spacing16),
            )
        } else {
            val rows = remember(sans) { sans.chunked(2) }
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxSize()
                    .navigationBarsPadding()
                    .padding(bottom = KraftSpacing.Spacing8)
                    .padding(horizontal = KraftSpacing.Spacing16),
            ) {
                itemsIndexed(rows, key = { rowNumber, _ -> rowNumber }) { moveNumber, pair ->
                    val plyBase = moveNumber * 2
                    MoveRow(
                        moveNumber = moveNumber,
                        pair = pair,
                        current = isCurrentRow(index, plyBase, pair.size, sans.size),
                        onJump = { index = (plyBase + pair.size).coerceAtMost(sans.size) },
                    )
                }
            }
        }
    }
}

@Composable
private fun MoveRow(
    moveNumber: Int,
    pair: List<String>,
    current: Boolean,
    onJump: () -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = KraftSpacing.Spacing48)
            .then(
                if (current) {
                    Modifier.background(
                        color = MaterialTheme.colorScheme.surfaceContainerHigh,
                        shape = RoundedCornerShape(KraftRadius.Standard),
                    )
                } else {
                    Modifier
                },
            )
            .clickable(
                onClickLabel = "Show the position after ${pair.joinToString(" and ")}",
                onClick = onJump,
            )
            .padding(
                horizontal = KraftSpacing.Spacing8,
                vertical = KraftSpacing.Spacing4,
            )
            .semantics(mergeDescendants = true) {
                contentDescription = if (current) {
                    "Move ${moveNumber + 1}, current position. ${pair.joinToString(", ")}."
                } else {
                    "Move ${moveNumber + 1}. ${pair.joinToString(", ")}."
                }
            },
    ) {
        Text(
            text = "${moveNumber + 1}.",
            style = MaterialTheme.typography.bodyMedium.copy(
                fontWeight = if (current) FontWeight.SemiBold else FontWeight.Normal,
            ),
            color = if (current) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
            modifier = Modifier.weight(1f),
        )
        Text(
            text = pair.getOrElse(0) { "" },
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(2f),
        )
        Text(
            text = pair.getOrElse(1) { "" },
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(2f),
        )
    }
}

@Composable
private fun EmptyReview(onBack: () -> Unit, modifier: Modifier = Modifier) {
    Column(modifier = modifier.fillMaxSize()) {
        KraftTopBar(title = "Review")
        Text(
            text = "Nothing to review yet.",
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .fillMaxWidth()
                .padding(
                    horizontal = KraftSpacing.Spacing16,
                    vertical = KraftSpacing.Spacing24,
                ),
        )
        Text(
            text = "Play at least one move and the game will show up here.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = KraftSpacing.Spacing16),
        )
        Spacer(Modifier.height(KraftSpacing.Spacing24))
        TextButton(
            onClick = onBack,
            colors = ButtonDefaults.textButtonColors(
                contentColor = MaterialTheme.colorScheme.onSurface,
            ),
            modifier = Modifier
                .fillMaxWidth()
                .height(KraftSpacing.Spacing48),
        ) {
            Text("Back")
        }
    }
}

/** True when [index] sits on the position this row ends at. */
private fun isCurrentRow(index: Int, plyBase: Int, pairSize: Int, totalPlies: Int): Boolean {
    val rowEnd = (plyBase + pairSize).coerceAtMost(totalPlies)
    return index == rowEnd
}

private fun reviewStatus(index: Int, totalPlies: Int): String {
    if (totalPlies == 0) return "No moves yet."
    val shown = index.coerceIn(0, totalPlies)
    return "Move $shown of $totalPlies."
}
