/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 Kedhar Sairam
 */
package com.krafttools.chesskraft.ui.board

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
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
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import com.kraft.ui.tokens.KraftRadius
import com.kraft.ui.tokens.KraftSpacing
import com.krafttools.chesskraft.domain.ChessMove
import com.krafttools.chesskraft.domain.PieceCode
import com.krafttools.chesskraft.domain.PieceType
import com.krafttools.chesskraft.domain.Side

/**
 * Promotion picker. Four large icons, Queen pre-selected — one tap to
 * confirm the obvious, two taps to underpromote.
 */
@Composable
fun PromotionDialog(
    options: List<ChessMove>,
    side: Side,
    onChoose: (ChessMove) -> Unit,
    onDismiss: () -> Unit,
) {
    var selected by remember(options) {
        mutableStateOf(
            options.firstOrNull { it.promotion == PieceType.QUEEN } ?: options.firstOrNull(),
        )
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "Promote to",
                style = MaterialTheme.typography.titleLarge,
            )
        },
        text = {
            Column {
                Text(
                    text = "Your pawn reached the last rank. Queen is the usual pick.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(KraftSpacing.Spacing16))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                ) {
                    for (option in options.sortedByDescending { promoRank(it.promotion) }) {
                        val type = option.promotion ?: PieceType.QUEEN
                        val code = PieceCode.of(side, type)
                        val isSelected = option == selected
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .size(KraftSpacing.Spacing56)
                                .clip(RoundedCornerShape(KraftRadius.Standard))
                                .border(
                                    width = if (isSelected) {
                                        KraftSpacing.Spacing2
                                    } else {
                                        KraftSpacing.BorderWidth
                                    },
                                    color = if (isSelected) {
                                        MaterialTheme.colorScheme.primary
                                    } else {
                                        MaterialTheme.colorScheme.outline
                                    },
                                    shape = RoundedCornerShape(KraftRadius.Standard),
                                )
                                .clickable(
                                    role = Role.RadioButton,
                                    onClickLabel = "Promote to ${type.name.lowercase()}",
                                    onClick = { selected = option },
                                )
                                .semantics(mergeDescendants = true) {
                                    this.selected = isSelected
                                    contentDescription = if (isSelected) {
                                        "Promote to ${type.name.lowercase()}, selected."
                                    } else {
                                        "Promote to ${type.name.lowercase()}."
                                    }
                                },
                        ) {
                            Text(
                                text = glyphFor(code),
                                style = MaterialTheme.typography.headlineMedium,
                                textAlign = TextAlign.Center,
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { selected?.let(onChoose) },
                enabled = selected != null,
            ) {
                Text("Promote")
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                colors = ButtonDefaults.textButtonColors(
                    contentColor = MaterialTheme.colorScheme.onSurface,
                ),
            ) {
                Text("Cancel")
            }
        },
    )
}

private fun promoRank(type: PieceType?): Int = when (type) {
    PieceType.QUEEN -> 4
    PieceType.ROOK -> 3
    PieceType.BISHOP -> 2
    PieceType.KNIGHT -> 1
    else -> 0
}
