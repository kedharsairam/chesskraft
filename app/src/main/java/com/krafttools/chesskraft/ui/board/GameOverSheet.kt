/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 Kedhar Sairam
 */
package com.krafttools.chesskraft.ui.board

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import com.kraft.ui.tokens.KraftSpacing
import com.krafttools.chesskraft.domain.GameResult

/**
 * Game-over sheet. Plain words plus the reason — never a bare score — with
 * Rematch (colours swapped), a fresh New game, and Review.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GameOverSheet(
    result: GameResult,
    onRematch: () -> Unit,
    onNewGame: () -> Unit,
    onReview: () -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = KraftSpacing.Spacing24)
                .padding(bottom = KraftSpacing.Spacing32),
        ) {
            Text(
                text = result.title,
                style = MaterialTheme.typography.headlineMedium,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(KraftSpacing.Spacing8))
            Text(
                text = result.reason,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(KraftSpacing.Spacing24))
            Button(
                onClick = onRematch,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(KraftSpacing.Spacing48),
            ) {
                Text("Rematch — swap colours")
            }
            Spacer(Modifier.height(KraftSpacing.Spacing8))
            OutlinedButton(
                onClick = onNewGame,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(KraftSpacing.Spacing48),
            ) {
                Text("New game")
            }
            Spacer(Modifier.height(KraftSpacing.Spacing8))
            TextButton(
                onClick = onReview,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(KraftSpacing.Spacing48),
            ) {
                Text("Review the game")
            }
        }
    }
}
