/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 Kedhar Sairam
 */
package com.krafttools.chesskraft.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import com.kraft.ui.tokens.KraftIconSize
import com.kraft.ui.tokens.KraftRadius
import com.kraft.ui.tokens.KraftSpacing
import com.kraft.ui.tokens.KraftTypeScale
import com.krafttools.chesskraft.domain.Bot
import com.krafttools.chesskraft.domain.Bots
import com.krafttools.chesskraft.domain.FinishedGame
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
 * Home, as a hub rather than a form.
 *
 * The order of this screen is the argument it makes: first the opponent you
 * are about to face, then the settings that change how the game is set up, and
 * finally the one action. The four difficulty chips became four bot cards —
 * each one a person, named and described in [Bot]'s own words, with its level
 * as a single word — because a level on its own is a slider label, and a
 * slider is not a reason to play someone.
 *
 * Settings stay, and stay tappable, but they are deliberately the quietest
 * thing here: a flat panel with a "Settings" label, below the cards, in the
 * same order as the setup card had them. Play stays pinned at the bottom and
 * names the opponent it will start, so the button and the selection can never
 * disagree about who you are about to meet.
 *
 * [history] is here for the one fact Home can state without opening anything:
 * how the record so far reads. The list itself lives on `HistoryScreen`, and
 * reaching it is a navigation decision this screen has no callback for — see
 * the note on `HistoryScreen`. Nothing on Home pretends to be tappable but
 * isn't.
 */
@Composable
fun HomeScreen(
    onPlay: (Difficulty, Side, Boolean, Long?) -> Unit,
    modifier: Modifier = Modifier,
    saved: Boolean = false,
    onContinue: (() -> Unit)? = null,
    history: List<FinishedGame> = emptyList(),
    onOpenHistory: (() -> Unit)? = null,
    onOpenAbout: (() -> Unit)? = null,
) {
    var difficulty by remember { mutableStateOf(Difficulty.CASUAL) }
    var sideChoice by remember { mutableStateOf(SideChoice.WHITE) }
    var flipped by remember { mutableStateOf(false) }
    var timeChoice by remember { mutableStateOf(TimeChoice.TEN) }
    val bot = remember(difficulty) { Bots.forLevel(difficulty) }

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
            SectionLabel("Your opponent")
            Spacer(Modifier.height(KraftSpacing.Spacing8))
            BotCardList(selected = difficulty, onSelect = { difficulty = it })
            Spacer(Modifier.height(KraftSpacing.Spacing24))
            SettingsCard(
                sideChoice = sideChoice,
                onSide = { sideChoice = it },
                timeChoice = timeChoice,
                onTime = { timeChoice = it },
                flipped = flipped,
                onFlip = { flipped = !flipped },
            )
            if (onOpenAbout != null) {
                Spacer(Modifier.height(KraftSpacing.Spacing16))
                AboutRow(onOpenAbout)
            }
            Spacer(Modifier.height(KraftSpacing.Spacing24))
            // Always shown, not only once there is a record. The first-run
            // player is exactly the one who needs to know the app keeps a
            // record, and a hidden row is a row nobody discovers.
            RecordCard(
                history = history,
                onOpen = onOpenHistory,
                modifier = Modifier.padding(horizontal = KraftSpacing.ScreenEdge),
            )
            Spacer(Modifier.height(KraftSpacing.Spacing24))
        }

        Button(
            onClick = {
                onPlay(difficulty, sideFor(sideChoice), flipped, timeChoice.ms)
            },
            shape = RoundedCornerShape(KraftRadius.Medium),
            colors = ButtonDefaults.buttonColors(),
            modifier = Modifier
                .navigationBarsPadding()
                .padding(horizontal = KraftSpacing.ScreenEdge)
                .padding(bottom = KraftSpacing.Spacing8)
                .fillMaxWidth()
                .height(KraftSpacing.Spacing56)
                .semantics(mergeDescendants = true) {
                    contentDescription = "Play ${bot.name}, ${difficultyName(difficulty)}."
                },
        ) {
            Text(
                // Named, so the button and the selected card can never be read
                // as talking about two different people.
                text = "Play ${bot.name}",
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontSize = KraftTypeScale.Callout,
                    fontWeight = FontWeight.SemiBold,
                ),
                maxLines = 1,
            )
        }
    }
}

/**
 * The header, compressed to a bar.
 *
 * It used to carry a display title, a promise and a giant knight. The knight
 * stays, because the app is chess before it is anything else and a word is not
 * a picture of one; the title and the promise go, because the screen below
 * now has something better to say than what the app is.
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
                top = KraftSpacing.Spacing16,
            ),
    ) {
        PieceMark(
            code = PieceCode.of(Side.WHITE, PieceType.KNIGHT),
            modifier = Modifier
                .align(Alignment.TopEnd)
                .offset(y = KraftSpacing.Spacing24)
                .size(KraftSpacing.Spacing64)
                .alpha(KnightWatermarkAlpha),
        )
        Column {
            Text(
                text = "ChessKraft",
                style = MaterialTheme.typography.titleLarge.copy(
                    fontWeight = FontWeight.Bold,
                ),
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
            )
            Spacer(Modifier.height(KraftSpacing.Spacing2))
            Text(
                text = "Offline · Private · No accounts",
                style = MaterialTheme.typography.labelMedium.copy(
                    fontWeight = FontWeight.SemiBold,
                ),
                color = MaterialTheme.colorScheme.primary,
                maxLines = 1,
            )
        }
    }
}

/**
 * The unfinished game, offered first. A card rather than a button: it says what
 * is waiting, and it is the only thing on this screen that is not a choice
 * between new games.
 */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
private fun ContinueCard(onContinue: () -> Unit) {
    Card(
        onClick = onContinue,
        shape = RoundedCornerShape(KraftRadius.Large),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        ),
        border = BorderStroke(
            KraftSpacing.BorderWidth,
            MaterialTheme.colorScheme.outlineVariant,
        ),
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

/**
 * The four opponents, weakest first — the order [Bots.all] declares, so
 * position on this screen is itself the ladder.
 *
 * One card per level, no more and no fewer: the engine has exactly four levels
 * and a fifth card would be an opponent that does not exist.
 */
@Composable
private fun BotCardList(
    selected: Difficulty,
    onSelect: (Difficulty) -> Unit,
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(KraftSpacing.Spacing12),
        modifier = Modifier.padding(horizontal = KraftSpacing.ScreenEdge),
    ) {
        for (bot in Bots.all) {
            BotCard(
                bot = bot,
                selected = bot.level == selected,
                onClick = { onSelect(bot.level) },
            )
        }
    }
}

/**
 * One opponent, as a choice.
 *
 * Three facts and nothing else: who they are ([Bot.name]), what they promise
 * ([Bot.tagline], verbatim — the domain file's own sentence is the only copy on
 * this screen that can be trusted to be true of the search), and how strong
 * they are, as one word. The style clause stays behind: four sentences per card
 * is a wall, and the one line is the one a player reads before move one.
 *
 * Selected is loud in three ways at once, because one is not enough: the card
 * fills with the app's accent, its border thickens, and a tick appears in the
 * mark on the left. The word "Selected" is in the accessibility state, and the
 * name is set in the app's own accent as well, so the choice survives a screen
 * with no colour at all. [Modifier.selectable] carries the radio semantics, so
 * TalkBack says which of the four is chosen rather than making a player tap
 * each card to find out.
 */
@Composable
private fun BotCard(
    bot: Bot,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val shape = RoundedCornerShape(if (selected) KraftRadius.Large else KraftRadius.Medium)
    Card(
        shape = shape,
        colors = CardDefaults.cardColors(
            containerColor = if (selected) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.surfaceContainerLow
            },
        ),
        border = BorderStroke(
            if (selected) KraftSpacing.Spacing2 else KraftSpacing.BorderWidth,
            if (selected) {
                MaterialTheme.colorScheme.onPrimary
            } else {
                MaterialTheme.colorScheme.outlineVariant
            },
        ),
        modifier = Modifier
            .fillMaxWidth()
            .selectable(
                selected = selected,
                role = Role.RadioButton,
                onClick = onClick,
            )
            .semantics(mergeDescendants = true) {
                contentDescription = "${bot.name}, ${difficultyName(bot.level)}. ${bot.tagline}"
            },
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(
                horizontal = KraftSpacing.Spacing16,
                vertical = KraftSpacing.Spacing16,
            ),
        ) {
            SelectionMark(selected = selected)
            Spacer(Modifier.size(KraftSpacing.Spacing12))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = bot.name,
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.SemiBold,
                        ),
                        color = if (selected) {
                            MaterialTheme.colorScheme.onPrimary
                        } else {
                            MaterialTheme.colorScheme.onSurface
                        },
                        maxLines = 1,
                    )
                    Spacer(Modifier.size(KraftSpacing.Spacing8))
                    LevelWord(level = bot.level, selected = selected)
                }
                Spacer(Modifier.height(KraftSpacing.Spacing4))
                Text(
                    text = bot.tagline,
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (selected) {
                        MaterialTheme.colorScheme.onPrimary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

/**
 * The tick, or the empty ring that means "not this one".
 *
 * Shape does the work: a filled disc with a tick against a hollow circle is
 * legible with no colour and no text, at the smallest size this screen has.
 */
@Composable
private fun SelectionMark(selected: Boolean) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(KraftSpacing.Spacing24)
            .clip(RoundedCornerShape(KraftRadius.Pill))
            .background(
                if (selected) {
                    MaterialTheme.colorScheme.onPrimary
                } else {
                    MaterialTheme.colorScheme.surfaceContainerLowest
                },
            )
            .border(
                width = if (selected) {
                    KraftSpacing.Spacing2
                } else {
                    KraftSpacing.BorderWidth
                },
                // The unselected ring is drawn in dim ink rather than `outline`:
                // `outline` sits within one step of the raised tile colour, so
                // on the device it read as a dark hole with no visible edge at
                // all, and the row lost its "nothing chosen here" mark.
                color = if (selected) {
                    MaterialTheme.colorScheme.onPrimary
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
                shape = RoundedCornerShape(KraftRadius.Pill),
            ),
    ) {
        if (selected) {
            Icon(
                imageVector = Icons.Filled.Check,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(KraftIconSize.Small),
            )
        }
    }
}

/**
 * The level as one word: "Relaxed", "Casual", "Sharp", "Tough".
 *
 * A pill so it reads as a badge and not as part of the tagline, and so the eye
 * can find the four levels without reading a single name.
 */
@Composable
private fun LevelWord(level: Difficulty, selected: Boolean) {
    Card(
        shape = RoundedCornerShape(KraftRadius.Pill),
        colors = CardDefaults.cardColors(
            containerColor = if (selected) {
                MaterialTheme.colorScheme.onPrimary.copy(alpha = LevelWordWashAlpha)
            } else {
                MaterialTheme.colorScheme.surfaceContainerHighest
            },
        ),
        modifier = Modifier
            .semantics { contentDescription = "" },
    ) {
        Text(
            text = difficultyName(level),
            style = MaterialTheme.typography.labelMedium.copy(
                fontSize = KraftTypeScale.Caption1,
                letterSpacing = KraftTypeScale.LabelSpacing,
                fontWeight = FontWeight.SemiBold,
            ),
            color = if (selected) {
                MaterialTheme.colorScheme.onPrimary
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
            maxLines = 1,
            modifier = Modifier.padding(
                horizontal = KraftSpacing.Spacing8,
                vertical = KraftSpacing.Spacing2,
            ),
        )
    }
}

/**
 * The settings, and nothing else: colour, clock, orientation.
 *
 * Flat on the page colour with a hairline and a "Settings" label above it, so
 * it reads as an options panel instead of competing with the opponent cards.
 * Same three rows, same order, same behaviour as before — this pass moved the
 * emphasis, not the controls.
 */
@Composable
private fun SettingsCard(
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
            containerColor = MaterialTheme.colorScheme.surfaceContainerLowest,
        ),
        border = BorderStroke(
            KraftSpacing.BorderWidth,
            MaterialTheme.colorScheme.outlineVariant,
        ),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = KraftSpacing.ScreenEdge),
    ) {
        Column(modifier = Modifier.padding(vertical = KraftSpacing.Spacing12)) {
            SectionLabel("Settings")
            Spacer(Modifier.height(KraftSpacing.Spacing12))
            CardDivider()
            Spacer(Modifier.height(KraftSpacing.Spacing12))
            SettingsLabel("You play")
            Spacer(Modifier.height(KraftSpacing.Spacing8))
            SideRow(sideChoice, onSide)
            Spacer(Modifier.height(KraftSpacing.Spacing12))
            CardDivider()
            Spacer(Modifier.height(KraftSpacing.Spacing12))
            SettingsLabel("Clock")
            Spacer(Modifier.height(KraftSpacing.Spacing8))
            TimeRow(timeChoice, onTime)
            Spacer(Modifier.height(KraftSpacing.Spacing8))
            CardDivider()
            FlipRow(flipped = flipped, onFlip = onFlip)
        }
    }
}

/**
 * The finished games so far, in one card, read-only.
 *
 * Home states the record; it does not open it — this screen has no navigation
 * callback, and a row that looks tappable and is not would be worse than no
 * row at all. Nothing renders for a first run, which has no record to state.
 * The counts come from [tallyOf], so the line cannot say something the records
 * do not.
 */
@Composable
private fun RecordCard(
    history: List<FinishedGame>,
    onOpen: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    val tally = remember(history) { tallyOf(history) }
    val latest = remember(history) { newestFirst(history).firstOrNull() }
    Column(modifier = modifier) {
        SectionLabel("Your games")
        Spacer(Modifier.height(KraftSpacing.Spacing8))
        Card(
            onClick = { onOpen?.invoke() },
            enabled = onOpen != null,
            shape = RoundedCornerShape(KraftRadius.Large),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerLowest,
            ),
            border = BorderStroke(
                KraftSpacing.BorderWidth,
                MaterialTheme.colorScheme.outlineVariant,
            ),
            modifier = Modifier
                .fillMaxWidth()
                .semantics(mergeDescendants = true) {
                    contentDescription = buildString {
                        append("Your games.")
                        if (latest == null) {
                            append(" No games finished yet.")
                            append(" Every game you finish is kept on this device.")
                        } else {
                            // spoken() already ends in a full stop, and
                            // latestLine() already says "Latest:" — joining
                            // them naively said "5 losses.. Latest: Latest:".
                            append(" ${tally.spoken()}")
                            tally.unreadableNote()?.let { append(" $it.") }
                            append(" ${latestLine(latest)}")
                            append(" Opens the full list.")
                        }
                    }
                },
        ) {
            Row(
                modifier = Modifier.padding(
                    horizontal = KraftSpacing.Spacing16,
                    vertical = KraftSpacing.Spacing16,
                ),
            ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = if (latest == null) "No games finished yet" else tally.line(),
                    style = MaterialTheme.typography.bodyLarge.copy(
                        fontWeight = FontWeight.SemiBold,
                    ),
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                val note = tally.unreadableNote()
                if (note != null) {
                    Spacer(Modifier.height(KraftSpacing.Spacing4))
                    Text(
                        text = note,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Spacer(Modifier.height(KraftSpacing.Spacing8))
                Text(
                    text = latest?.let { latestLine(it) }
                        ?: "Every game you finish is kept on this device. Nothing leaves it.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (onOpen != null) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .padding(start = KraftSpacing.Spacing8)
                        .size(KraftIconSize.Medium),
                )
            }
            }
        }
    }
}

/**
 * "Latest: You win against Wren, 40 moves." — the newest record, said once.
 *
 * A record whose result text is blank gets its own sentence rather than an
 * empty slot in someone else's, because the honest thing about a save we
 * cannot read is to say so.
 */
private fun latestLine(latest: FinishedGame): String {
    val bot = Bots.forLevel(latest.difficulty).name
    val moves = movesPhrase(latest.moveCount)
    val result = latest.result.trim()
    return if (result.isEmpty()) {
        "Latest: a game against $bot that has no result saved, $moves."
    } else {
        "Latest: $result against $bot, $moves."
    }
}

/**
 * One quiet line, below everything that matters on this screen.
 *
 * It is here because the piece art has a licence that says the credit travels
 * with the binary, and the binary is on a phone where a NOTICE file in the
 * source tree is not.
 */
@Composable
private fun AboutRow(onOpen: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(KraftRadius.Medium))
            .clickable(onClick = onOpen)
            .heightIn(min = KraftSpacing.Spacing48)
            .padding(horizontal = KraftSpacing.ScreenEdge, vertical = KraftSpacing.Spacing12)
            .semantics(mergeDescendants = true) {
                contentDescription = "About ChessKraft. Version and licences."
            },
    ) {
        Text(
            text = "About ChessKraft",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
        )
        Icon(
            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(KraftIconSize.Small),
        )
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

/** The group heading above a block of rows: caps, tracked out, quiet. */
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

/** The row name inside the settings panel — a label, so it is not a fourth choice. */
@Composable
private fun SettingsLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        maxLines = 1,
        modifier = Modifier.padding(horizontal = KraftSpacing.Spacing16),
    )
}

@Composable
private fun CardDivider() {
    HorizontalDivider(
        thickness = KraftSpacing.BorderWidth,
        color = MaterialTheme.colorScheme.outlineVariant,
        modifier = Modifier.padding(horizontal = KraftSpacing.Spacing16),
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

/**
 * The colour the player takes, with the coin already flipped.
 *
 * Here rather than at the call site so there is exactly one Random in the app
 * and the board cannot disagree with the sheet about which king is yours.
 */
private fun sideFor(choice: SideChoice): Side = when (choice) {
    SideChoice.WHITE -> Side.WHITE
    SideChoice.BLACK -> Side.BLACK
    SideChoice.RANDOM -> if (kotlin.random.Random.nextBoolean()) {
        Side.WHITE
    } else {
        Side.BLACK
    }
}

/** Knight watermark strength: present, never competing with the cards. */
private const val KnightWatermarkAlpha = 0.16f

/** Wash behind the level word on the selected card, so the pill reads on green. */
private const val LevelWordWashAlpha = 0.18f