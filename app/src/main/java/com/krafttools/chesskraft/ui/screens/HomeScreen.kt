/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 Kedhar Sairam
 */
package com.krafttools.chesskraft.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import com.kraft.ui.tokens.KraftRadius
import com.kraft.ui.tokens.KraftSpacing
import com.kraft.ui.tokens.KraftTypeScale
import com.krafttools.chesskraft.domain.PieceCode
import com.krafttools.chesskraft.domain.PieceType
import com.krafttools.chesskraft.domain.Side
import com.krafttools.chesskraft.engine.Difficulty
import com.krafttools.chesskraft.ui.board.PieceMark
import com.krafttools.chesskraft.ui.components.ChessChoiceChip
import com.krafttools.chesskraft.ui.components.ChessChoiceRow

/** Which colour the player picks up. Random is flipped for a coin at kickoff. */
enum class SideChoice {
    WHITE,
    BLACK,
    RANDOM,
}

/** Clock presets. Off keeps the untimed game; anything else arms both banks. */
enum class TimeChoice(val label: String, val ms: Long?) {
    OFF("Off", null),
    FIVE("5 min", 5L * 60L * 1000L),
    TEN("10 min", 10L * 60L * 1000L),
    FIFTEEN("15 min", 15L * 60L * 1000L),
}

/**
 * Home / New Game. A header that says what the app is, one grouped setup
 * card (strength, colour, flip), and the single Play action pinned bottom.
 * No top bar: the header is the bar. No clocks, no ratings, no theme picker.
 */
@Composable
fun HomeScreen(
    onPlay: (Difficulty, Side, Boolean, Long?) -> Unit,
    modifier: Modifier = Modifier,
    saved: Boolean = false,
    onContinue: (() -> Unit)? = null,
) {
    var difficulty by remember { mutableStateOf(Difficulty.CASUAL) }
    var sideChoice by remember { mutableStateOf(SideChoice.WHITE) }
    var flipped by remember { mutableStateOf(false) }
    var timeChoice by remember { mutableStateOf(TimeChoice.TEN) }

    Column(modifier = modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState()),
        ) {
            HeaderBlock()
            if (saved && onContinue != null) {
                Spacer(Modifier.height(KraftSpacing.Spacing16))
                ContinueCard(onContinue = onContinue)
            }
            Spacer(Modifier.height(KraftSpacing.Spacing24))
            SetupCard(
                difficulty = difficulty,
                onDifficulty = { difficulty = it },
                sideChoice = sideChoice,
                onSide = { sideChoice = it },
                timeChoice = timeChoice,
                onTime = { timeChoice = it },
                flipped = flipped,
                onFlip = { flipped = !flipped },
            )
            Spacer(Modifier.height(KraftSpacing.Spacing24))
        }

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
                onPlay(difficulty, side, flipped, timeChoice.ms)
            },
            shape = RoundedCornerShape(KraftRadius.Medium),
            colors = ButtonDefaults.buttonColors(),
            modifier = Modifier
                .navigationBarsPadding()
                .padding(horizontal = KraftSpacing.ScreenEdge)
                .padding(bottom = KraftSpacing.Spacing8)
                .fillMaxWidth()
                .height(KraftSpacing.Spacing56),
        ) {
            Text(
                text = "Play",
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontSize = KraftTypeScale.Callout,
                    fontWeight = FontWeight.SemiBold,
                ),
            )
        }
    }
}

/**
 * The header is the top bar: kicker, title, one-line promise, and a giant
 * knight watermark that says "chess" before a word is read. The watermark is
 * decorative only — hidden from TalkBack, low alpha, never behind text.
 */
@Composable
private fun HeaderBlock() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(
                start = KraftSpacing.ScreenEdge,
                end = KraftSpacing.ScreenEdge,
                top = KraftSpacing.Spacing24,
            ),
    ) {
        PieceMark(
            code = PieceCode.of(Side.WHITE, PieceType.KNIGHT),
            modifier = Modifier
                .align(Alignment.TopEnd)
                .offset(y = KraftSpacing.Spacing32)
                .size(KraftSpacing.Spacing64 * 2)
                .alpha(KnightWatermarkAlpha),
        )
        Column {
            Text(
                text = "Offline · Private · No clock",
                style = MaterialTheme.typography.labelMedium.copy(
                    fontWeight = FontWeight.SemiBold,
                ),
                color = MaterialTheme.colorScheme.primary,
                maxLines = 1,
            )
            Spacer(Modifier.height(KraftSpacing.Spacing4))
            Text(
                text = "ChessKraft",
                style = MaterialTheme.typography.displayLarge.copy(
                    fontWeight = FontWeight.Bold,
                ),
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
            )
            Spacer(Modifier.height(KraftSpacing.Spacing4))
            Text(
                text = "A quiet game of chess. Just you and the board.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/**
 * One inset grouped card: the whole setup lives in a single tappable-feeling
 * surface with hairline dividers, not three loose sections on black.
 */
/**
 * The unfinished game, offered first. A card rather than a button: it says what
 * is waiting, and it is the only thing on this screen that is not a choice
 * between new games.
 */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
private fun ContinueCard(onContinue: () -> Unit) {
    val shape = RoundedCornerShape(KraftRadius.Large)
    val outline = BorderStroke(
        KraftSpacing.BorderWidth,
        MaterialTheme.colorScheme.primary,
    )
    Card(
        onClick = onContinue,
        shape = shape,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        ),
        border = outline,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = KraftSpacing.ScreenEdge)
            .heightIn(min = KraftSpacing.Spacing56)
            .semantics(mergeDescendants = true) {
                contentDescription = "Continue your unfinished game."
            },
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(
                horizontal = KraftSpacing.Spacing16,
                vertical = KraftSpacing.Spacing12,
            ),
        ) {
            PieceMark(
                code = PieceCode.of(Side.WHITE, PieceType.KING),
                modifier = Modifier.size(KraftSpacing.Spacing32),
            )
            Spacer(Modifier.size(KraftSpacing.Spacing12))
            Column(Modifier.weight(1f)) {
                Text(
                    text = "Unfinished game",
                    style = MaterialTheme.typography.bodyLarge.copy(
                        fontWeight = FontWeight.SemiBold,
                    ),
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                )
                Text(
                    text = "Pick up where you left off.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                )
            }
        }
    }
}

@Composable
private fun SetupCard(
    difficulty: Difficulty,
    onDifficulty: (Difficulty) -> Unit,
    sideChoice: SideChoice,
    onSide: (SideChoice) -> Unit,
    timeChoice: TimeChoice,
    onTime: (TimeChoice) -> Unit,
    flipped: Boolean,
    onFlip: () -> Unit,
) {
    Card(
        shape = RoundedCornerShape(KraftRadius.Large),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        ),
        border = BorderStroke(
            KraftSpacing.BorderWidth,
            MaterialTheme.colorScheme.outlineVariant,
        ),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = KraftSpacing.ScreenEdge),
    ) {
        Column(modifier = Modifier.padding(vertical = KraftSpacing.Spacing16)) {
            SectionLabel("Strength")
            Spacer(Modifier.height(KraftSpacing.Spacing8))
            DifficultyRow(difficulty, onDifficulty)
            Spacer(Modifier.height(KraftSpacing.Spacing16))
            CardDivider()
            Spacer(Modifier.height(KraftSpacing.Spacing16))
            SectionLabel("You play")
            Spacer(Modifier.height(KraftSpacing.Spacing8))
            SideRow(sideChoice, onSide)
            Spacer(Modifier.height(KraftSpacing.Spacing16))
            CardDivider()
            Spacer(Modifier.height(KraftSpacing.Spacing16))
            SectionLabel("Clock")
            Spacer(Modifier.height(KraftSpacing.Spacing8))
            TimeRow(timeChoice, onTime)
            Spacer(Modifier.height(KraftSpacing.Spacing8))
            CardDivider()
            FlipRow(flipped = flipped, onFlip = onFlip)
        }
    }
}

@Composable
private fun CardDivider() {
    HorizontalDivider(
        thickness = KraftSpacing.BorderWidth,
        color = MaterialTheme.colorScheme.outlineVariant,
        modifier = Modifier.padding(horizontal = KraftSpacing.Spacing16),
    )
}

@Composable
private fun DifficultyRow(
    selected: Difficulty,
    onSelect: (Difficulty) -> Unit,
) {
    ChessChoiceRow {
        for (option in Difficulty.entries) {
            ChessChoiceChip(
                selected = selected == option,
                label = difficultyName(option),
                onClick = { onSelect(option) },
            )
        }
    }
}

@Composable
private fun SideRow(
    selected: SideChoice,
    onSelect: (SideChoice) -> Unit,
) {
    ChessChoiceRow {
        for (option in SideChoice.entries) {
            ChessChoiceChip(
                selected = selected == option,
                label = sideName(option),
                onClick = { onSelect(option) },
            )
        }
    }
}

/**
 * Whole-row toggle: text block on the left, the switch pinned right and
 * vertically centered. The Row owns the tap; the Switch itself is stateless.
 */
@Composable
private fun FlipRow(flipped: Boolean, onFlip: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .toggleable(
                value = flipped,
                role = Role.Switch,
                onValueChange = { onFlip() },
            )
            .fillMaxWidth()
            .heightIn(min = KraftSpacing.Spacing48)
            .padding(horizontal = KraftSpacing.Spacing16),
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                text = "Flip the board",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = "Black at the bottom from move one.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Switch(
            checked = flipped,
            onCheckedChange = null,
            colors = SwitchDefaults.colors(
                checkedTrackColor = MaterialTheme.colorScheme.primary,
                checkedThumbColor = MaterialTheme.colorScheme.onPrimary,
            ),
        )
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text = text.uppercase(),
        style = MaterialTheme.typography.labelMedium.copy(
            fontSize = KraftTypeScale.Footnote,
            letterSpacing = KraftTypeScale.LabelSpacing,
            fontWeight = FontWeight.SemiBold,
        ),
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        maxLines = 1,
        modifier = Modifier.padding(horizontal = KraftSpacing.Spacing16),
    )
}

fun difficultyName(difficulty: Difficulty): String = when (difficulty) {
    Difficulty.RELAXED -> "Relaxed"
    Difficulty.CASUAL -> "Casual"
    Difficulty.SHARP -> "Sharp"
    Difficulty.TOUGH -> "Tough"
}

@Composable
private fun TimeRow(
    selected: TimeChoice,
    onSelect: (TimeChoice) -> Unit,
) {
    ChessChoiceRow {
        for (option in TimeChoice.entries) {
            ChessChoiceChip(
                selected = selected == option,
                label = option.label,
                onClick = { onSelect(option) },
            )
        }
    }
}

fun sideName(choice: SideChoice): String = when (choice) {
    SideChoice.WHITE -> "White"
    SideChoice.BLACK -> "Black"
    SideChoice.RANDOM -> "Random"
}

/** Knight watermark strength: present, never competing with the title. */
private const val KnightWatermarkAlpha = 0.16f
