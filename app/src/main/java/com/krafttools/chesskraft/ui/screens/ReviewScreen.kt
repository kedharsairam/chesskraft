/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 Kedhar Sairam
 */
package com.krafttools.chesskraft.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import com.kraft.ui.components.KraftTopBar
import com.kraft.ui.tokens.KraftSpacing
import com.krafttools.chesskraft.domain.Position
import com.krafttools.chesskraft.domain.Side
import com.krafttools.chesskraft.presentation.GameUiState
import com.krafttools.chesskraft.ui.board.ChessBoard

/**
 * Review. The finished game as a move list with step back/forward and flip.
 * Read-only board — taps do nothing. No eval bar in v1.
 */
@Composable
fun ReviewScreen(
    fens: List<String>,
    sans: List<String>,
    playerSide: Side,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var index by remember(fens) { mutableIntStateOf(fens.size - 1) }
    var flipped by remember { mutableStateOf(false) }
    val listState = rememberLazyListState()

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
            listState.animateScrollToItem((index.coerceIn(1, sans.size) - 1) / 2)
        }
    }

    Column(modifier = modifier.fillMaxSize()) {
        KraftTopBar(
            title = "Review",
            actions = {
                TextButton(onClick = onBack) {
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
            textAlign = TextAlign.Center,
            modifier = Modifier
                .fillMaxWidth()
                .semantics { contentDescription = "Review position. ${reviewStatus(index, sans.size)}" },
        )
        Spacer(Modifier.height(KraftSpacing.Spacing8))
        Row(
            horizontalArrangement = Arrangement.SpaceEvenly,
            modifier = Modifier.fillMaxWidth(),
        ) {
            TextButton(
                onClick = { index = (index - 1).coerceAtLeast(0) },
                enabled = index > 0,
                modifier = Modifier.height(KraftSpacing.Spacing48),
            ) {
                Text("Back")
            }
            TextButton(
                onClick = { flipped = !flipped },
                modifier = Modifier.height(KraftSpacing.Spacing48),
            ) {
                Text("Flip")
            }
            TextButton(
                onClick = { index = (index + 1).coerceAtMost(fens.size - 1) },
                enabled = index < fens.size - 1,
                modifier = Modifier.height(KraftSpacing.Spacing48),
            ) {
                Text("Forward")
            }
        }
        Spacer(Modifier.height(KraftSpacing.Spacing8))
        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = KraftSpacing.Spacing16),
        ) {
            itemsIndexed(sans.chunked(2)) { moveNumber, pair ->
                val plyBase = moveNumber * 2
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { index = (plyBase + pair.size).coerceAtMost(sans.size) }
                        .padding(vertical = KraftSpacing.Spacing8)
                        .semantics {
                            contentDescription = "Move ${moveNumber + 1}. ${pair.joinToString(", ")}."
                        },
                ) {
                    Text(
                        text = "${moveNumber + 1}.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f),
                    )
                    Text(
                        text = pair.getOrElse(0) { "" },
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.weight(2f),
                    )
                    Text(
                        text = pair.getOrElse(1) { "" },
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.weight(2f),
                    )
                }
            }
        }
    }
}

private fun reviewStatus(index: Int, totalPlies: Int): String {
    if (totalPlies == 0) return "No moves yet."
    val shown = index.coerceIn(0, totalPlies)
    return "Move $shown of $totalPlies."
}
