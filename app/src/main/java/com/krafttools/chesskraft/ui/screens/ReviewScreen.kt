/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 Kedhar Sairam
 */
package com.krafttools.chesskraft.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import com.kraft.ui.tokens.KraftRadius
import com.kraft.ui.tokens.KraftSpacing
import com.kraft.ui.tokens.KraftTypeScale
import com.krafttools.chesskraft.domain.GameReviewResult
import com.krafttools.chesskraft.domain.MoveVerdict
import com.krafttools.chesskraft.domain.ReviewedMove
import com.krafttools.chesskraft.domain.Side
import com.krafttools.chesskraft.ui.theme.ChessKraftColors

/**
 * Post-game review: what happened and why it mattered.
 *
 * Verdict first, in plain words, because the reader is learning. Colour is
 * redundant — every verdict ships a word, so a colourblind reader and a
 * screen-reader user get the same information. Accuracy is a chess.com-style
 * approximation (see GameReview's KDoc), stated as such rather than implied
 * to be the identical metric.
 */
@Composable
fun ReviewScreen(
    review: GameReviewResult,
    playerSide: Side,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .statusBarsPadding(),
    ) {
        ReviewHeader(review, playerSide)
        if (review.moves.isEmpty()) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier.fillMaxSize(),
            ) {
                Text(
                    text = "No moves to review.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else {
            LazyColumn(
                contentPadding = androidx.compose.foundation.layout.PaddingValues(
                    horizontal = KraftSpacing.ScreenEdge,
                    vertical = KraftSpacing.Spacing8,
                ),
                modifier = Modifier.fillMaxSize(),
            ) {
                items(review.moves.size) { index ->
                    val move = review.moves[index]
                    ReviewRow(move, isPlayerMove = move.isPlayerMove(playerSide))
                }
            }
        }
    }
}

@Composable
private fun ReviewHeader(review: GameReviewResult, playerSide: Side) {
    Column(modifier = Modifier.padding(horizontal = KraftSpacing.ScreenEdge)) {
        Text(
            text = "Game review",
            style = MaterialTheme.typography.displayLarge.copy(
                fontWeight = FontWeight.Bold,
            ),
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
        )
        Spacer(Modifier.height(KraftSpacing.Spacing4))
        Text(
            text = "Your accuracy against the engine's best moves.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(KraftSpacing.Spacing16))
        Row(horizontalArrangement = Arrangement.spacedBy(KraftSpacing.Spacing8)) {
            AccuracyTile(
                label = "You",
                accuracy = review.accuracyFor(playerSide),
                tone = ChessKraftColors.Accent,
            )
            AccuracyTile(
                label = "Computer",
                accuracy = review.accuracyFor(playerSide.opponent()),
                tone = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.height(KraftSpacing.Spacing16))
        VerdictLegend()
        Spacer(Modifier.height(KraftSpacing.Spacing8))
    }
}

@Composable
private fun AccuracyTile(label: String, accuracy: Int, tone: androidx.compose.ui.graphics.Color) {
    Column(
        modifier = Modifier
            .clip(RoundedCornerShape(KraftRadius.Standard))
            .background(MaterialTheme.colorScheme.surfaceContainerLow)
            .border(
                KraftSpacing.BorderWidth,
                MaterialTheme.colorScheme.outlineVariant,
                RoundedCornerShape(KraftRadius.Standard),
            )
            .padding(horizontal = KraftSpacing.Spacing16, vertical = KraftSpacing.Spacing12)
            .semantics(mergeDescendants = true) {
                contentDescription = "$label accuracy $accuracy percent."
            },
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
        )
        Text(
            text = "$accuracy%",
            style = MaterialTheme.typography.headlineMedium,
            color = tone,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
        )
    }
}

/** Every verdict, spelled out, once — so the colours below need no decoder. */
@Composable
private fun VerdictLegend() {
    Column(verticalArrangement = Arrangement.spacedBy(KraftSpacing.Spacing4)) {
        for (verdict in MoveVerdict.entries) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                VerdictDot(verdict)
                Spacer(Modifier.size(KraftSpacing.Spacing8))
                Text(
                    text = verdict.label(),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                )
            }
        }
    }
}

@Composable
private fun ReviewRow(move: ReviewedMove, isPlayerMove: Boolean) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = KraftSpacing.Spacing6)
            .semantics(mergeDescendants = true) {
                contentDescription = buildString {
                    append("Move ${move.number} ${move.san}, ${move.verdict.label()}. ")
                    if (isPlayerMove) append("Yours. ") else append("Computer's. ")
                    move.bestSan?.let { append("Better was $it.") }
                }
            },
    ) {
        Text(
            text = "${move.number}.",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            modifier = Modifier.width(VerdictNumberWidth),
        )
        Column(Modifier.weight(1f)) {
            Text(
                text = move.san,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
            )
            if (!isPlayerMove || move.verdict == MoveVerdict.BEST || move.bestSan == null) {
                Spacer(Modifier.height(KraftSpacing.Spacing2))
            } else {
                Text(
                    text = "Better: ${move.bestSan}",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                )
            }
        }
        VerdictDot(move.verdict)
        Spacer(Modifier.size(KraftSpacing.Spacing8))
        Text(
            text = move.verdict.label(),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
        )
    }
}

/** Dot + word, never dot alone. */
@Composable
private fun VerdictDot(verdict: MoveVerdict) {
    Box(
        modifier = Modifier
            .size(VerdictDotSize)
            .clip(RoundedCornerShape(KraftRadius.Pill))
            .background(verdictColor(verdict)),
    )
}

private fun ReviewedMove.isPlayerMove(playerSide: Side): Boolean =
    (ply % 2 == 0) == (playerSide == Side.WHITE)

private val VerdictDotSize = KraftSpacing.Spacing8
private val VerdictNumberWidth = KraftSpacing.Spacing32

internal fun MoveVerdict.label(): String = when (this) {
    MoveVerdict.BEST -> "best"
    MoveVerdict.GOOD -> "good"
    MoveVerdict.INACCURACY -> "inaccuracy"
    MoveVerdict.MISTAKE -> "mistake"
    MoveVerdict.BLUNDER -> "blunder"
}

/** Green for praise, warm neutral for nitpicks, red for real damage. */
@Composable
internal fun verdictColor(verdict: MoveVerdict): androidx.compose.ui.graphics.Color = when (verdict) {
    MoveVerdict.BEST -> ChessKraftColors.VerdictBest
    MoveVerdict.GOOD -> ChessKraftColors.VerdictGood
    MoveVerdict.INACCURACY -> ChessKraftColors.VerdictInaccuracy
    MoveVerdict.MISTAKE -> ChessKraftColors.VerdictMistake
    MoveVerdict.BLUNDER -> MaterialTheme.colorScheme.error
}

internal fun GameReviewResult.accuracyFor(side: Side): Int =
    if (side == Side.WHITE) accuracyWhite else accuracyBlack