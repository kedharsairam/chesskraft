/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 Kedhar Sairam
 */
package com.krafttools.chesskraft.ui.board

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import com.kraft.ui.tokens.KraftIconSize
import com.kraft.ui.tokens.KraftRadius
import com.kraft.ui.tokens.KraftSpacing
import com.kraft.ui.tokens.KraftTypeScale
import com.krafttools.chesskraft.domain.ChessMove
import com.krafttools.chesskraft.domain.PieceCode
import com.krafttools.chesskraft.domain.PieceType
import com.krafttools.chesskraft.domain.Side

/**
 * Promotion picker: four options, Queen chosen already.
 *
 * A pawn that reaches the last rank has four legal futures and one that is
 * almost always right, so the dialog starts on Queen and a player who takes
 * what it offers is done in one tap. Underpromotion is two taps away and says
 * so on its face — the line under the title, and the other three tiles sitting
 * there with the same weight as the Queen — so the choice is a decision rather
 * than an escape from a default.
 *
 * Three things this art has to survive, because a dialog this rare is the one
 * piece of UI that is most likely to be wrong:
 *
 * - **48dp per option, and it says which is which.** Every tile is at least
 *   [KraftSpacing.Spacing64] tall and a quarter of the row wide, each carrying
 *   the Cburnett piece and its letter — the bundled art, the same marks the
 *   board and the player strips use, so the piece you are looking at is the
 *   piece that lands there.
 * - **Selection by shape and label, never by colour.** The chosen tile is the
 *   only one with a filled disc, a thicker border, a tick and a semibold
 *   letter; the other three are hollow with a hairline. On a greyscale screen,
 *   or to a player who cannot separate green from charcoal, chosen still means
 *   chosen.
 * - **A name and a state for every option.** Each tile merges into one
 *   TalkBack node reading "Promote to rook, selected", and
 *   [androidx.compose.foundation.selection.selectable] carries the radio
 *   semantics, so the state is announced rather than inferred from a border.
 *
 * The confirm button names the piece, so the last thing read before a move is
 * committed is the move itself.
 */
@Composable
fun PromotionDialog(
    options: List<ChessMove>,
    side: Side,
    onChoose: (ChessMove) -> Unit,
    onDismiss: () -> Unit,
) {
    val ordered = remember(options) { options.sortedByDescending { promoRank(it.promotion) } }
    var selected by remember(ordered) {
        mutableStateOf(
            ordered.firstOrNull { it.promotion == PieceType.QUEEN } ?: ordered.firstOrNull(),
        )
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "Promote to",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )
        },
        text = {
            Column {
                Text(
                    text = "Your pawn reached the last rank. Queen is almost " +
                        "always right, so it is already selected.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(KraftSpacing.Spacing16))
                Row(
                    horizontalArrangement = Arrangement.spacedBy(KraftSpacing.Spacing8),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    for (option in ordered) {
                        val type = option.promotion ?: PieceType.QUEEN
                        PromotionOption(
                            type = type,
                            side = side,
                            selected = option == selected,
                            onClick = { selected = option },
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }
        },
        confirmButton = {
            val chosen = selected?.promotion
            Button(
                onClick = { selected?.let(onChoose) },
                enabled = selected != null,
                shape = RoundedCornerShape(KraftRadius.Medium),
                colors = ButtonDefaults.buttonColors(),
                modifier = Modifier.heightIn(min = KraftSpacing.Spacing48),
            ) {
                Text(
                    text = if (chosen != null) {
                        "Promote to ${typeWord(chosen)}"
                    } else {
                        "Promote"
                    },
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontSize = KraftTypeScale.Callout,
                        fontWeight = FontWeight.SemiBold,
                    ),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                colors = ButtonDefaults.textButtonColors(
                    contentColor = MaterialTheme.colorScheme.onSurface,
                ),
                modifier = Modifier.heightIn(min = KraftSpacing.Spacing48),
            ) {
                Text("Cancel")
            }
        },
    )
}

/**
 * One of the four: the piece, its letter, and — when chosen — a filled disc,
 * a tick, a thicker border and the semibold letter.
 *
 * The tick badge sits outside the tile's own bounds on purpose. Inside it, a
 * 2dp border and a green fill are the only differences left between chosen and
 * not, and the whole point of the badge is that the difference is a shape
 * rather than a tint.
 */
@Composable
private fun PromotionOption(
    type: PieceType,
    side: Side,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(KraftRadius.Standard)
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier
            .heightIn(min = KraftSpacing.Spacing64)
            .selectable(
                selected = selected,
                role = Role.RadioButton,
                onClick = onClick,
            )
            .clip(shape)
            .background(
                if (selected) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.surfaceContainerHigh
                },
            )
            .border(
                width = if (selected) {
                    KraftSpacing.Spacing2
                } else {
                    KraftSpacing.BorderWidth
                },
                color = if (selected) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.outline
                },
                shape = shape,
            )
            .padding(
                horizontal = KraftSpacing.Spacing4,
                vertical = KraftSpacing.Spacing8,
            )
            .semantics(mergeDescendants = true) {
                contentDescription = "Promote to ${typeWord(type).lowercase()}" +
                    if (selected) ", selected" else ""
            },
    ) {
        Box(contentAlignment = Alignment.Center) {
            PieceMark(
                code = PieceCode.of(side, type),
                modifier = Modifier.size(KraftSpacing.Spacing40),
            )
            if (selected) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .size(KraftSpacing.Spacing16)
                        .clip(RoundedCornerShape(KraftRadius.Pill))
                        .background(MaterialTheme.colorScheme.primary),
                ) {
                    Icon(
                        imageVector = Icons.Filled.Check,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.size(KraftIconSize.Tiny),
                    )
                }
            }
        }
        Spacer(Modifier.height(KraftSpacing.Spacing4))
        Text(
            text = typeLetter(type),
            style = MaterialTheme.typography.labelMedium.copy(
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
            ),
            color = if (selected) {
                MaterialTheme.colorScheme.onPrimary
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
            maxLines = 1,
            textAlign = TextAlign.Center,
        )
    }
}

/** The letter under the piece: the same shape a notation column uses. */
private fun typeLetter(type: PieceType): String = when (type) {
    PieceType.QUEEN -> "Q"
    PieceType.ROOK -> "R"
    PieceType.BISHOP -> "B"
    PieceType.KNIGHT -> "N"
    else -> "?"
}

/** Spoken as a word. A letter alone is "cue" or nothing at all. */
private fun typeWord(type: PieceType): String = when (type) {
    PieceType.QUEEN -> "Queen"
    PieceType.ROOK -> "Rook"
    PieceType.BISHOP -> "Bishop"
    PieceType.KNIGHT -> "Knight"
    else -> "Piece"
}

private fun promoRank(type: PieceType?): Int = when (type) {
    PieceType.QUEEN -> 4
    PieceType.ROOK -> 3
    PieceType.BISHOP -> 2
    PieceType.KNIGHT -> 1
    else -> 0
}