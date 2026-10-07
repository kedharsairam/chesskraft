/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 Kedhar Sairam
 */
package com.krafttools.chesskraft.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import com.kraft.ui.tokens.KraftIconSize
import com.kraft.ui.tokens.KraftRadius
import com.kraft.ui.tokens.KraftSpacing
import com.kraft.ui.tokens.KraftTypeScale
import com.krafttools.chesskraft.domain.Bots
import com.krafttools.chesskraft.domain.FinishedGame
import com.krafttools.chesskraft.domain.PieceCode
import com.krafttools.chesskraft.domain.PieceType
import com.krafttools.chesskraft.domain.Side
import com.krafttools.chesskraft.ui.board.PieceMark
import java.time.DateTimeException
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit

/**
 * Finished games, newest first.
 *
 * The save file has been writing one [FinishedGame] per game since v1 and
 * nothing has ever read it back, so this screen is the first thing in the app
 * that shows a player their own results. Four facts per game and no more: the
 * result in the plain words the game-over sheet used, how long it ran, which
 * opponent, and which colour the player had. There is no board, because the
 * record does not carry one — a row that implied a replay would be a promise
 * the file cannot keep.
 *
 * The summary line counts the records. It never guesses: a stored result string
 * this build does not recognise is counted as neither a win, a draw nor a loss,
 * and when that happens the line says so instead of quietly adding the
 * remainder to whichever column looks tidier. See [tallyOf].
 *
 * Navigation is the host's job — [onBack] and nothing else. This screen does
 * not know about `Route`, and a history list that could also start a game or
 * delete a record would be a different screen with a different name.
 */
@Composable
fun HistoryScreen(
    history: List<FinishedGame>,
    modifier: Modifier = Modifier,
    onBack: () -> Unit,
) {
    // Sorted by the recorded end time rather than trusted in place. The store
    // appends newest-first, so this agrees with it in every normal case; it
    // only differs if a save is hand-edited or restored out of order, and then
    // "newest first" is the contract of this screen, not of the file.
    val ordered = remember(history) { newestFirst(history) }
    val tally = remember(history) { tallyOf(history) }

    Column(modifier = modifier.fillMaxSize()) {
        HistoryTopBar(onBack = onBack)
        if (ordered.isEmpty()) {
            EmptyHistory()
        } else {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState()),
            ) {
                SummaryCard(tally = tally)
                Spacer(Modifier.height(KraftSpacing.Spacing12))
                GamePanel(ordered)
                Spacer(Modifier.height(KraftSpacing.Spacing24))
            }
        }
    }
}

/**
 * The bar: a back action and the name of the thing being looked at.
 *
 * A 48dp target and a spoken label, because this is the one control a player
 * who came here by mistake needs to find first.
 */
@Composable
private fun HistoryTopBar(onBack: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(horizontal = KraftSpacing.ScreenEdge, vertical = KraftSpacing.Spacing8),
    ) {
        IconButton(
            onClick = onBack,
            modifier = Modifier
                .size(KraftSpacing.Spacing48)
                .semantics(mergeDescendants = true) {
                    contentDescription = "Back to the home screen."
                },
        ) {
            Icon(
                imageVector = Icons.Filled.ArrowBack,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.size(KraftIconSize.Medium),
            )
        }
        Spacer(Modifier.size(KraftSpacing.Spacing8))
        Text(
            text = "History",
            style = MaterialTheme.typography.titleLarge.copy(
                fontWeight = FontWeight.SemiBold,
            ),
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
    }
}

/**
 * The counts, and the caveat when the counts do not add up.
 *
 * Deliberately not a card: this is a sentence about the list directly beneath
 * it, and a panel around it would be a panel around nothing.
 */
@Composable
private fun SummaryCard(tally: HistoryTally) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = KraftSpacing.ScreenEdge, vertical = KraftSpacing.Spacing12),
    ) {
        Text(
            text = tally.line(),
            style = MaterialTheme.typography.bodyLarge.copy(
                fontSize = KraftTypeScale.Callout,
                fontWeight = FontWeight.SemiBold,
            ),
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.semantics(mergeDescendants = true) {
                contentDescription = tally.spoken()
            },
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
    }
}

/**
 * One panel holding the records, hairline between rows — a table of games
 * rather than a stack of unrelated cards, because these rows are a list of
 * facts about the same thing.
 */
@Composable
private fun GamePanel(ordered: List<FinishedGame>) {
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
        // A plain Column, not a LazyColumn: the rows live inside the page's
        // verticalScroll, and a lazy list inside a scrollable column is
        // measured with an infinite height, which Compose refuses. The save
        // caps history at a hundred rows, so nothing here needs laziness.
        Column(modifier = Modifier.fillMaxWidth()) {
            ordered.forEachIndexed { index, game ->
                GameRow(game = game, last = index == ordered.lastIndex)
            }
        }
    }
}

/**
 * One game: the result in words, then the three facts that make it a record —
 * which colour, which opponent, how long. The date sits right because that is
 * the only field a player uses to decide whether to care about a row.
 *
 * The whole row is one TalkBack node reading the same facts in the same order
 * as it reads on screen. The pieces of the sentence are split for the eye and
 * joined for the ear, which is the whole point of merging here.
 */
@Composable
private fun GameRow(game: FinishedGame, last: Boolean) {
    val bot = Bots.forLevel(game.difficulty)
    val side = if (game.playerSide == Side.WHITE) "White" else "Black"
    val level = difficultyName(game.difficulty)
    val day = dayLabel(game.endedAtMs)
    val spoken = buildString {
        append(game.result.ifBlank { UnreadableResult }.removeSuffix("."))
        append(". ")
        append(level)
        append(" level, ")
        append(bot.name)
        append(". ")
        append(movesPhrase(game.moveCount))
        append(". You played ")
        append(side)
        if (day != null) {
            append(". ")
            append(day)
        }
        append(".")
    }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = KraftSpacing.Spacing56)
            .padding(horizontal = KraftSpacing.Spacing16, vertical = KraftSpacing.Spacing12)
            .semantics(mergeDescendants = true) { contentDescription = spoken },
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = game.result.ifBlank { UnreadableResult },
                style = MaterialTheme.typography.bodyLarge.copy(
                    fontWeight = FontWeight.SemiBold,
                ),
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            if (day != null) {
                Spacer(Modifier.size(KraftSpacing.Spacing8))
                Text(
                    text = day,
                    style = MaterialTheme.typography.labelMedium.copy(
                        fontSize = KraftTypeScale.Caption1,
                    ),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                )
            }
        }
        Spacer(Modifier.height(KraftSpacing.Spacing2))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = listOf(side, level, movesPhrase(game.moveCount)).joinToString(" · "),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            Spacer(Modifier.size(KraftSpacing.Spacing8))
            Text(
                // The opponent's name beside its level: one bot per level, so
                // the word is the same fact said the way the player chose it.
                text = bot.name,
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontWeight = FontWeight.SemiBold,
                ),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
            )
        }
    }
    if (!last) {
        HorizontalDivider(
            thickness = KraftSpacing.BorderWidth,
            color = MaterialTheme.colorScheme.outlineVariant,
            modifier = Modifier.padding(start = KraftSpacing.Spacing16),
        )
    }
}

/**
 * Nothing recorded yet — and what will be, in one sentence.
 *
 * This is the state every first run lands in, so a blank screen here would be
 * the first thing the app ever shows a new player. The sentence is the reason
 * to come back: finishing any game puts a row above this text.
 */
@Composable
private fun ColumnScope.EmptyHistory() {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = Modifier
            .fillMaxWidth()
            .weight(1f)
            .padding(horizontal = KraftSpacing.Spacing32)
            .semantics(mergeDescendants = true) {
                contentDescription = "No games yet. Every game you finish is listed here: " +
                    "the result, how long it ran, and who you played."
            },
    ) {
        PieceMark(
            code = PieceCode.of(Side.WHITE, PieceType.KNIGHT),
            modifier = Modifier
                .size(KraftSpacing.Spacing64)
                .alpha(EmptyMarkAlpha),
        )
        Spacer(Modifier.height(KraftSpacing.Spacing16))
        Text(
            text = "No games yet",
            style = MaterialTheme.typography.titleMedium.copy(
                fontWeight = FontWeight.SemiBold,
            ),
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(KraftSpacing.Spacing4))
        Text(
            text = "Every game you finish is listed here — the result, how long " +
                "it ran, and who you played.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}

// -- The record, counted ---------------------------------------------------

/** Newest first. Stable, so two games ending in the same millisecond keep the order they were saved in. */
internal fun newestFirst(history: List<FinishedGame>): List<FinishedGame> =
    history.sortedByDescending { it.endedAtMs }

/**
 * What one stored [FinishedGame.result] counts as.
 *
 * [OTHER] is the important one: the result is a sentence this app wrote, and a
 * sentence can change. Anything the rules below do not cover stays [OTHER] and
 * is reported, never rounded to the nearest plausible verdict.
 */
private enum class Outcome { WIN, DRAW, LOSS, OTHER }

/**
 * The counts behind the summary line.
 *
 * [games] is the number of records; [wins] + [draws] + [losses] is how many of
 * them were readable. When the two disagree, [other] is the size of the gap.
 */
internal data class HistoryTally(
    val games: Int,
    val wins: Int,
    val draws: Int,
    val losses: Int,
    val other: Int,
) {
    /** "4 games · 2 wins · 1 draw · 1 loss". Zeros are kept: a missing zero is a claim. */
    fun line(): String = listOf(
        count(games, "game", "games"),
        count(wins, "win", "wins"),
        count(draws, "draw", "draws"),
        count(losses, "loss", "losses"),
    ).joinToString(" · ")

    /**
     * The same numbers, for an ear rather than an eye.
     *
     * "1-0" and "0-1" read as nonsense aloud, so this spells the record out:
     * "You won 2 of 4 games. 1 draw. 1 loss." It leads with "You won", because
     * "0 of 5 games won" is a sentence about a fraction, not about a person.
     */
    fun spoken(): String = buildString {
        append("You won $wins of $games games. ")
        append("${count(draws, "draw", "draws")}. ")
        append("${count(losses, "loss", "losses")}.")
        if (other > 0) append(" ${
            count(other, "result", "results")
        } this screen could not read.")
    }

    /** Null when everything was readable, so the caller can leave the caveat out. */
    fun unreadableNote(): String? = if (other == 0) {
        null
    } else {
        "${count(other, "result", "results")} is in a form this build does not " +
            "recognise, so it is counted in none of those."
    }
}

/** Counts [history]. Pure, so the numbers on screen can only come from the records. */
internal fun tallyOf(history: List<FinishedGame>): HistoryTally {
    var wins = 0
    var draws = 0
    var losses = 0
    var other = 0
    for (game in history) {
        when (outcomeOf(game.result)) {
            Outcome.WIN -> wins += 1
            Outcome.DRAW -> draws += 1
            Outcome.LOSS -> losses += 1
            Outcome.OTHER -> other += 1
        }
    }
    return HistoryTally(
        games = history.size,
        wins = wins,
        draws = draws,
        losses = losses,
        other = other,
    )
}

/**
 * Reads a stored result as one of the four buckets.
 *
 * The endings `GameResult` can produce are known and small — "You win",
 * "You lose", "You win on time", "You lost on time", "They resigned",
 * "You resigned", "Draw", "Draw agreed" — so this matches those prefixes and
 * leaves everything else unclassified. Two details matter: the prefix is "you
 * los" rather than "you lose", because the flag result is past tense ("You lost
 * on time"), and "they resign" is a win for the player even though the words
 * are not the player's.
 */
private fun outcomeOf(result: String): Outcome {
    val text = result.trim().lowercase()
    return when {
        text.startsWith("you win") -> Outcome.WIN
        text.startsWith("they resign") -> Outcome.WIN
        text.startsWith("you los") -> Outcome.LOSS
        text.startsWith("you resign") -> Outcome.LOSS
        text.startsWith("draw") -> Outcome.DRAW
        else -> Outcome.OTHER
    }
}

private fun count(n: Int, one: String, many: String): String =
    if (n == 1) "1 $one" else "$n $many"

/** The same wording the game-over sheet uses, so a record and a result agree. */
internal fun movesPhrase(plyCount: Int): String =
    fullMoves(plyCount).let { moves ->
        when (moves) {
            0 -> "no moves"
            1 -> "1 move"
            else -> "$moves moves"
        }
    }

/**
 * Plies to moves, the way a score sheet counts them.
 *
 * The save stores plies, which is the right thing to store, but `1.e4 Nc6` is
 * one move and not two. Printing the ply count called it two, and on a screen
 * whose job is to teach chess that is a visible error.
 */
internal fun fullMoves(plyCount: Int): Int = (plyCount + 1) / 2

/**
 * "Today", "Yesterday", "8 Oct", or "8 Oct 2025" for an older year.
 *
 * Null when the record carries no usable time. The field is a plain millisecond
 * count in a hand-editable file, so an absurd value has to render as nothing
 * rather than crash a list the player came back to read.
 */
private fun dayLabel(endedAtMs: Long): String? {
    if (endedAtMs <= 0L) return null
    val date = try {
        Instant.ofEpochMilli(endedAtMs).atZone(ZoneId.systemDefault()).toLocalDate()
    } catch (e: DateTimeException) {
        return null
    }
    val today = LocalDate.now()
    val days = ChronoUnit.DAYS.between(date, today)
    return when {
        days == 0L -> "Today"
        days == 1L -> "Yesterday"
        else -> DayFormat.format(date) +
            if (date.year == today.year) "" else " ${date.year}"
    }
}

/** Locale short day + month. Built once: the list can hold twenty rows. */
private val DayFormat: DateTimeFormatter = DateTimeFormatter.ofPattern("d MMM")

/** Shown where a row's result text is missing or blank, instead of an empty line. */
private const val UnreadableResult = "Result not recorded"

/** The empty-state mark sits back so it never competes with the sentence under it. */
private const val EmptyMarkAlpha = 0.5f