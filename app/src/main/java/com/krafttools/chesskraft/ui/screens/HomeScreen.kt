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
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.kraft.ui.components.KraftTopBar
import com.kraft.ui.tokens.KraftSpacing
import com.krafttools.chesskraft.domain.Side
import com.krafttools.chesskraft.engine.Difficulty

/** Which colour the player picks up. Random is flipped for a coin at kickoff. */
enum class SideChoice {
    WHITE,
    BLACK,
    RANDOM,
}

/**
 * Home / New Game. Play plus three choices — difficulty, colour, flip —
 * and nothing else: no clocks, no ratings, no theme picker.
 */
@Composable
fun HomeScreen(
    onPlay: (Difficulty, Side, Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    var difficulty by remember { mutableStateOf(Difficulty.CASUAL) }
    var sideChoice by remember { mutableStateOf(SideChoice.WHITE) }
    var flipped by remember { mutableStateOf(false) }

    Column(modifier = modifier.fillMaxSize()) {
        KraftTopBar(title = "ChessKraft")
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = KraftSpacing.Spacing24),
            verticalArrangement = Arrangement.Center,
        ) {
            Text(
                text = "A quiet game of chess.",
                style = MaterialTheme.typography.headlineMedium,
            )
            Spacer(Modifier.height(KraftSpacing.Spacing8))
            Text(
                text = "Offline, no account, no clock. Just you and the board.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(KraftSpacing.Spacing24))

            SectionLabel("Strength")
            Spacer(Modifier.height(KraftSpacing.Spacing8))
            Row(horizontalArrangement = Arrangement.spacedBy(KraftSpacing.Spacing8)) {
                for (option in Difficulty.entries) {
                    FilterChip(
                        selected = difficulty == option,
                        onClick = { difficulty = option },
                        label = { Text(difficultyName(option)) },
                        modifier = Modifier.height(KraftSpacing.Spacing48),
                    )
                }
            }
            Spacer(Modifier.height(KraftSpacing.Spacing16))

            SectionLabel("You play")
            Spacer(Modifier.height(KraftSpacing.Spacing8))
            Row(horizontalArrangement = Arrangement.spacedBy(KraftSpacing.Spacing8)) {
                for (option in SideChoice.entries) {
                    FilterChip(
                        selected = sideChoice == option,
                        onClick = { sideChoice = option },
                        label = { Text(sideName(option)) },
                        modifier = Modifier.height(KraftSpacing.Spacing48),
                    )
                }
            }
            Spacer(Modifier.height(KraftSpacing.Spacing16))

            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        text = "Flip the board",
                        style = MaterialTheme.typography.bodyLarge,
                    )
                    Text(
                        text = "Black at the bottom from move one.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Switch(checked = flipped, onCheckedChange = { flipped = it })
            }
            Spacer(Modifier.height(KraftSpacing.Spacing32))

            Button(
                onClick = {
                    val side = when (sideChoice) {
                        SideChoice.WHITE -> Side.WHITE
                        SideChoice.BLACK -> Side.BLACK
                        SideChoice.RANDOM -> if (kotlin.random.Random.nextBoolean()) {
                            Side.WHITE
                        } else {
                            Side.BLACK
                        }
                    }
                    onPlay(difficulty, side, flipped)
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(KraftSpacing.Spacing48),
            ) {
                Text("Play")
            }
        }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

fun difficultyName(difficulty: Difficulty): String = when (difficulty) {
    Difficulty.RELAXED -> "Relaxed"
    Difficulty.CASUAL -> "Casual"
    Difficulty.SHARP -> "Sharp"
    Difficulty.TOUGH -> "Tough"
}

fun sideName(choice: SideChoice): String = when (choice) {
    SideChoice.WHITE -> "White"
    SideChoice.BLACK -> "Black"
    SideChoice.RANDOM -> "Random"
}
