/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 Kedhar Sairam
 */
package com.krafttools.chesskraft.ui.screens

import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import com.kraft.ui.motion.rememberReduceMotion
import com.kraft.ui.tokens.KraftRadius
import com.kraft.ui.tokens.KraftSpacing
import com.krafttools.chesskraft.domain.BoardGeometry
import com.krafttools.chesskraft.domain.ChessMove
import com.krafttools.chesskraft.domain.Coach
import com.krafttools.chesskraft.domain.CoachLine
import com.krafttools.chesskraft.domain.GameReview
import com.krafttools.chesskraft.domain.GameReviewResult
import com.krafttools.chesskraft.domain.MoveVerdict
import com.krafttools.chesskraft.domain.Position
import com.krafttools.chesskraft.domain.ReviewedMove
import com.krafttools.chesskraft.domain.Side
import com.krafttools.chesskraft.domain.parseSquare
import com.krafttools.chesskraft.domain.squareName
import com.krafttools.chesskraft.presentation.GameUiState
import com.krafttools.chesskraft.ui.board.ChessBoard
import com.krafttools.chesskraft.ui.theme.ChessKraftColors
import kotlin.math.abs

/**
 * Post-game review: a board you can step through, not a list with a picture on
 * top of it.
 *
 * The screen is a scrubber over one pass of data. [review] already carries
 * every verdict, every loss in centipawns, and the evaluation each move left
 * behind ([ReviewedMove.evalAfterCp]); [fens] is the trail the game recorded,
 * `fens[0]` the starting position and `fens[i + 1]` the position after ply `i`.
 * Nothing here searches — the numbers are already in hand and the screen's job
 * is to make them legible one ply at a time.
 *
 * Three layers, top to bottom: the board showing the selected ply, the controls
 * and the plain-English reading of that ply, then the move list — which is a
 * scrubber rather than a report, since tapping a row moves the board to it.
 *
 * Selection starts on the LAST move, because that is what a player opens a
 * review to see, and they walk backwards from there. Index 0 is the starting
 * position, so the list and the controls can step one move further back than
 * the last grade.
 *
 * The board is drawn from the player's own side ([playerSide]), the way it was
 * when they played, and it is handed to [ChessBoard] non-interactive: a review
 * is for reading positions, and a board that accepted a drag would promise a
 * game that is already over.
 *
 * Colour is never alone. Every verdict keeps its word, the engine's better move
 * is named in text as well as ringed on the felt, and every meter is read out
 * for TalkBack.
 *
 * @param review the graded game, as built by `GameReview.build`.
 * @param fens the position trail: `fens[0]` first and one entry per move on top
 *   of that. A short or unreadable trail degrades to "no position here" rather
 *   than to a board showing the wrong squares.
 * @param playerSide who played. Sets both the accuracy tiles and the board's
 *   orientation.
 * @param onBack optional; null draws no back affordance at all, for a review
 *   that is the only thing in its stack.
 */
@Composable
fun ReviewScreen(
    review: GameReviewResult,
    fens: List<String>,
    playerSide: Side,
    modifier: Modifier = Modifier,
    onBack: (() -> Unit)? = null,
) {
    // The last move is the last entry, so a fresh screen opens on the end of the
    // game rather than on 1.e4.
    var ply by rememberSaveable { mutableIntStateOf(review.moves.size) }
    val selected = ply.coerceIn(0, review.moves.size)

    // Read once per trail rather than once per recomposition: a few dozen FENs
    // cost nothing, and stepping back and forth must not re-read them per tap.
    val positions = remember(fens) { fens.map { Position.fromFen(it).getOrNull() } }

    Column(
        modifier = modifier
            .fillMaxSize()
            .statusBarsPadding(),
    ) {
        ReviewTopBar(onBack = onBack)
        ReviewTiles(review, playerSide)
        if (review.moves.isEmpty()) {
            EmptyReview()
            return@Column
        }

        val move = review.moves.getOrNull(selected - 1)
        val position = positions.getOrNull(selected)
        // The position the move was PLAYED FROM, which is not the position the
        // board is now showing. Both SAN strings below — the move that was made
        // and the move the engine wanted instead — only exist in the position
        // before it, so resolving them against `position` silently found
        // nothing and the board drew the new position with no marker on it.
        val fromPosition = positions.getOrNull(selected - 1)
        // A string that does not resolve resolves to nothing, never to the
        // wrong square.
        val played = remember(selected, fromPosition, move) {
            resolveSan(fromPosition, move?.san)
        }
        val engineMove = remember(selected, fromPosition, move) {
            engineAlternative(fromPosition, move)
        }
        // Who actually moved, read off the FEN rather than off the ply's
        // parity: the FEN knows who was to move even when a saved trail
        // disagrees with the move list.
        val mover = positions.getOrNull(selected - 1)?.sideToMove ?: playerSide
        val sansSoFar = remember(selected, review.moves) {
            review.moves.take(selected).map { it.san }
        }
        // Said once, and used twice: as the board's status line (which the board
        // reads out in its live region on every step) and as the caption's
        // third line. A ring needs naming out loud as well as on the felt.
        val note = engineNote(move, engineMove)

        ReviewLayout(
            boardState = boardStateOf(
                position = position,
                played = played,
                playerSide = playerSide,
                sans = sansSoFar,
                // Read out by the board on every step, in the board's own live
                // region: the rings need naming out loud as well as in the
                // caption.
                statusText = note.orEmpty(),
                evalCp = move?.evalAfterCp,
            ),
            engineMove = engineMove,
            review = review,
            playerSide = playerSide,
            selected = selected,
            mover = mover,
            move = move,
            engineNote = note,
            evalCp = move?.evalAfterCp,
            onSelect = { ply = it.coerceIn(0, review.moves.size) },
        )
    }
}

/**
 * The board and the scrubber, stacked or side by side.
 *
 * The board takes its size from the space rather than from the width alone: in
 * portrait it is the smaller of the inset width and a share of the height, so
 * the list below it always has room; in landscape the board moves into a column
 * beside the list and takes the smaller of that column and the height. Both
 * branches hand the same pieces to the same two composables, so there is one
 * board and one scrubber to reason about.
 */
@Composable
private fun ReviewLayout(
    boardState: GameUiState?,
    engineMove: ChessMove?,
    review: GameReviewResult,
    playerSide: Side,
    selected: Int,
    mover: Side,
    move: ReviewedMove?,
    engineNote: String?,
    evalCp: Int?,
    onSelect: (Int) -> Unit,
) {
    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        // Read into locals before either branch: inside a Row or Column these
        // names resolve to *that* scope's constraints, not to this box's.
        val available = maxHeight
        val full = maxWidth
        if (available >= full) {
            val side = minOf(full - ScreenEdge * 2, available * BoardHeightShare)
            Column(modifier = Modifier.fillMaxSize()) {
                ReviewBoard(
                    boardState = boardState,
                    engineMove = engineMove,
                    flipped = playerSide == Side.BLACK,
                    side = side,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = ScreenEdge)
                        .height(side),
                )
                ReviewScrubber(
                    review = review,
                    playerSide = playerSide,
                    selected = selected,
                    mover = mover,
                    move = move,
                    engineNote = engineNote,
                    evalCp = evalCp,
                    onSelect = onSelect,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        } else {
            val column = full * BoardColumnShare
            Row(modifier = Modifier.fillMaxSize()) {
                ReviewBoard(
                    boardState = boardState,
                    engineMove = engineMove,
                    flipped = playerSide == Side.BLACK,
                    side = minOf(column - ScreenEdge * 2, available),
                    modifier = Modifier
                        .width(column)
                        .padding(horizontal = ScreenEdge),
                )
                ReviewScrubber(
                    review = review,
                    playerSide = playerSide,
                    selected = selected,
                    mover = mover,
                    move = move,
                    engineNote = engineNote,
                    evalCp = evalCp,
                    onSelect = onSelect,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

/**
 * The real board, at the size the layout decided on.
 *
 * [ChessBoard] is the same composable the game screen plays on, handed a state
 * built from the FEN and asked for no interaction at all. The played move is
 * `lastMoveFrom`/`lastMoveTo`, so the board's own arrow draws it; nothing here
 * redraws a move the board already knows how to draw.
 *
 * The engine's better move is drawn on top by [EngineMoveRings], because a
 * position the reader has to imagine is a position they will not learn from, and
 * the board has no vocabulary for "the move that was not played".
 */
@Composable
private fun ReviewBoard(
    boardState: GameUiState?,
    engineMove: ChessMove?,
    flipped: Boolean,
    side: Dp,
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        if (boardState == null) {
            MissingPosition()
        } else {
            ChessBoard(
                state = boardState,
                // Unreachable: the board is asked for no interaction at all.
                onTap = {},
                onDrop = { _, _ -> },
                modifier = Modifier.size(side),
                interactive = false,
            )
            EngineMoveRings(engineMove = engineMove, flipped = flipped, side = side)
        }
    }
}

/**
 * The engine's move, ringed on the felt.
 *
 * A ring and a whisper of a wash, never a second arrow: the played move is
 * already gold on this board, and two arrows would be a puzzle rather than a
 * lesson. The ring is inset from the square's edge so a piece standing on it
 * still reads, and every number here is a fraction of the square for the same
 * reason every marker on [ChessBoard] is — a mark has to look the same on a
 * phone board and a tablet one.
 *
 * The squares come from the same geometry the board itself draws with, and the
 * canvas is given the board's exact size, so a ring cannot drift onto a
 * neighbouring square at another density or another orientation.
 */
@Composable
private fun EngineMoveRings(engineMove: ChessMove?, flipped: Boolean, side: Dp) {
    if (engineMove == null) return
    val gold = ChessKraftColors.FeltGold
    Canvas(modifier = Modifier.size(side)) {
        val sq = BoardGeometry.squareSize(size.width)
        val inset = sq * EngineRingInset
        for (square in listOf(engineMove.from, engineMove.to)) {
            val (col, row) = BoardGeometry.displayCell(square, flipped)
            val topLeft = Offset(col * sq, row * sq)
            drawRect(
                color = gold.copy(alpha = EngineRingFillAlpha),
                topLeft = topLeft,
                size = Size(sq, sq),
            )
            drawRect(
                color = gold.copy(alpha = EngineRingStrokeAlpha),
                topLeft = Offset(topLeft.x + inset, topLeft.y + inset),
                size = Size(sq - inset * 2, sq - inset * 2),
                style = Stroke(width = sq * EngineRingWidth),
            )
        }
    }
}

/**
 * Everything under the board: what this ply is, what the coach says about it,
 * the controls that move it, and the list that scrubs it.
 */
@Composable
private fun ReviewScrubber(
    review: GameReviewResult,
    playerSide: Side,
    selected: Int,
    mover: Side,
    move: ReviewedMove?,
    engineNote: String?,
    evalCp: Int?,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier) {
        BoardCaption(
            selected = selected,
            total = review.moves.size,
            move = move,
            mover = mover,
            evalCp = evalCp,
            engineNote = engineNote,
        )
        Stepper(selected = selected, total = review.moves.size, onSelect = onSelect)
        MoveList(
            review = review,
            playerSide = playerSide,
            selected = selected,
            onSelect = onSelect,
            modifier = Modifier.weight(1f),
        )
    }
}

/**
 * What the board is showing, in words: which ply, what was played, what the
 * coach says about it, what the engine would have played and where it left the
 * game.
 *
 * The evaluation arrives as a wide meter plus its sentence, because a meter with
 * no number is a shape and a number with no meter is trivia. Here they are the
 * same fact twice, which is the convention the in-game eval bar already set on
 * this app.
 */
@Composable
private fun BoardCaption(
    selected: Int,
    total: Int,
    move: ReviewedMove?,
    mover: Side,
    evalCp: Int?,
    engineNote: String?,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.padding(horizontal = ScreenEdge),
        verticalArrangement = Arrangement.spacedBy(KraftSpacing.Spacing4),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = if (move == null) plyLabel(selected, total) else {
                    "${plyLabel(selected, total)} · ${move.san}"
                },
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                modifier = Modifier.weight(1f, fill = false),
            )
            if (move != null) {
                Spacer(Modifier.width(KraftSpacing.Spacing8))
                // The word is the whole of it here: no rows of dots to sit
                // beside, and no verdict without its name.
                Text(
                    text = move.verdict.label(),
                    style = MaterialTheme.typography.titleLarge,
                    color = verdictColor(move.verdict),
                    maxLines = 1,
                )
            }
        }
        val sentence = move?.let { moveSentence(it) }
        if (sentence != null) {
            Text(
                text = sentence,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = CoachMaxLines,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (evalCp != null) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                EvalMeter(
                    evalCp = evalCp,
                    mover = mover,
                    modifier = Modifier.weight(1f),
                )
                Spacer(Modifier.width(KraftSpacing.Spacing8))
                Text(
                    text = evalSentence(evalCp),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                )
            }
        }
        if (engineNote != null) {
            Text(
                text = engineNote,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = EngineNoteMaxLines,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/**
 * Previous / next, one ply at a time.
 *
 * Two full-width buttons rather than chevrons in the corners: stepping is the
 * primary gesture here, so it gets the primary touch target. The ends of the
 * game disable rather than wrap, because "next" on the last move should not
 * silently teleport back to the first.
 */
@Composable
private fun Stepper(selected: Int, total: Int, onSelect: (Int) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = ScreenEdge, vertical = KraftSpacing.Spacing8),
        horizontalArrangement = Arrangement.spacedBy(KraftSpacing.Spacing8),
    ) {
        StepButton(
            text = "Previous",
            description = if (selected == 0) "At the start of the game" else "Previous move",
            enabled = selected > 0,
            onClick = { onSelect(selected - 1) },
            modifier = Modifier.weight(1f),
        )
        StepButton(
            text = "Next",
            description = if (selected >= total) "At the end of the game" else "Next move",
            enabled = selected < total,
            onClick = { onSelect(selected + 1) },
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun StepButton(
    text: String,
    description: String,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        shape = RoundedCornerShape(KraftRadius.Standard),
        colors = ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            contentColor = MaterialTheme.colorScheme.onSurface,
        ),
        modifier = modifier
            .heightIn(min = KraftSpacing.TouchTarget)
            .semantics { contentDescription = description },
    ) {
        Text(text = text, style = MaterialTheme.typography.bodyMedium, maxLines = 1)
    }
}

/**
 * The move list, as a scrubber.
 *
 * Every row carries the move, its verdict in words, the coach's one sentence
 * and its own evaluation meter; tapping a row moves the board there. The list
 * follows the *selection* — not the scroll — so a thumb reading down the list is
 * never fighting the screen for the scroll position, and it snaps rather than
 * animates, which is both the in-game precedent (MoveStrip) and one less thing
 * for a reader who has asked for less motion.
 */
@Composable
private fun MoveList(
    review: GameReviewResult,
    playerSide: Side,
    selected: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val listState = rememberLazyListState()
    LaunchedEffect(selected, review.moves.size) {
        if (selected <= 0) return@LaunchedEffect
        // Item 0 is the legend, so move i sits at i + 1.
        val last = (listState.layoutInfo.totalItemsCount - 1).coerceAtLeast(0)
        listState.scrollToItem((selected + LegendItemCount).coerceAtMost(last))
    }
    LazyColumn(
        state = listState,
        modifier = modifier,
        contentPadding = PaddingValues(
            horizontal = ScreenEdge,
            vertical = KraftSpacing.Spacing4,
        ),
    ) {
        item(key = LegendKey) {
            VerdictLegend()
            Spacer(Modifier.height(KraftSpacing.Spacing8))
        }
        itemsIndexed(
            items = review.moves,
            key = { _, move -> move.ply },
        ) { _, move ->
            ReviewRow(
                move = move,
                isPlayerMove = move.isPlayerMove(playerSide),
                isSelected = move.ply == selected - 1,
                onClick = { onSelect(move.ply + 1) },
            )
        }
    }
}

/**
 * One move: the number, the move, the coach's sentence, the evaluation it left
 * behind, and the verdict.
 *
 * The meter reads from the mover's side of the board rather than White's, so a
 * move of yours never looks bad for being good: Black's rows grow away from the
 * centre when Black is winning. The number behind it is untouched — the same
 * evaluation, read from the other end — and the sentence in the row's
 * description names the side, so the two can never read as disagreeing.
 */
@Composable
private fun ReviewRow(
    move: ReviewedMove,
    isPlayerMove: Boolean,
    isSelected: Boolean,
    onClick: () -> Unit,
) {
    val shape = RoundedCornerShape(KraftRadius.Small)
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = RowMinHeight)
            .clip(shape)
            .background(
                if (isSelected) {
                    MaterialTheme.colorScheme.surfaceContainerHigh
                } else {
                    Color.Transparent
                },
            )
            .selectable(selected = isSelected, onClick = onClick)
            .padding(horizontal = KraftSpacing.Spacing8, vertical = KraftSpacing.Spacing6)
            .semantics(mergeDescendants = true) {
                contentDescription = rowDescription(move, isPlayerMove, isSelected)
            },
    ) {
        Text(
            text = moveNumber(move),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            modifier = Modifier.width(VerdictNumberWidth),
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = move.san,
                style = MaterialTheme.typography.bodyLarge.copy(
                    fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                ),
                color = if (isSelected) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurface
                },
                maxLines = 1,
            )
            val sentence = moveSentence(move)
            if (sentence != null) {
                Text(
                    text = sentence,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = CoachMaxLines,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        EvalMeter(evalCp = move.evalAfterCp, mover = moverOf(move))
        Spacer(Modifier.width(KraftSpacing.Spacing8))
        VerdictDot(move.verdict)
        Spacer(Modifier.width(KraftSpacing.Spacing4))
        Text(
            text = move.verdict.label(),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.width(VerdictWordWidth),
        )
    }
}

/**
 * The per-move evaluation meter: a thin bar filling from the centre towards
 * whoever is better, with a notch at zero.
 *
 * The scale is the in-game eval bar's scale — the same cap at three pawns, the
 * same track, the same notch, the same two fills — so a number read off one bar
 * means the same thing on the other and the two can never disagree about how
 * good a position is. The drawing is duplicated rather than shared because the
 * game screen owns that file; [ReviewEvalScaleCp] is the one number that has to
 * move with it.
 *
 * No content description of its own: the row's description already reads the
 * evaluation out, and a bare meter is a node TalkBack would stop on with
 * nothing to say.
 */
@Composable
private fun EvalMeter(evalCp: Int?, mover: Side, modifier: Modifier = Modifier) {
    if (evalCp == null) {
        // The row keeps its shape: a move the analyser never saw has no
        // evaluation, and an empty slot is honest where a zeroed bar is not.
        Spacer(modifier.height(KraftSpacing.Spacing4))
        return
    }
    // White's point of view, turned round for a Black mover so the bar reads as
    // *this move's* evaluation.
    val relative = if (mover == Side.WHITE) evalCp else -evalCp
    val fraction = relative.coerceIn(-ReviewEvalScaleCp, ReviewEvalScaleCp).toFloat() /
        ReviewEvalScaleCp
    val track = MaterialTheme.colorScheme.outlineVariant
    val notch = MaterialTheme.colorScheme.onSurfaceVariant
    val fill = if (relative >= 0) ChessKraftColors.Accent else notch
    Canvas(
        modifier = modifier
            .height(KraftSpacing.Spacing4)
            .width(KraftSpacing.Spacing48),
    ) {
        val w = size.width
        val h = size.height
        val r = h / 2f
        drawRoundRect(color = track, size = Size(w, h), cornerRadius = CornerRadius(r, r))
        val half = w / 2f
        val reach = abs(fraction) * half
        val left = if (fraction >= 0f) half else half - reach
        drawRoundRect(
            color = fill,
            topLeft = Offset(left, 0f),
            size = Size(reach.coerceAtLeast(h), h),
            cornerRadius = CornerRadius(r, r),
        )
        // The zero line, so "level" is legible at a glance and not only as an
        // empty bar.
        drawLine(
            color = notch,
            start = Offset(half, 0f),
            end = Offset(half, h),
            strokeWidth = MeterNotchWidth.toFloat(),
        )
    }
}

/** Back, only when there is somewhere to go back to. */
@Composable
private fun ReviewTopBar(onBack: (() -> Unit)?) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .padding(end = ScreenEdge, bottom = KraftSpacing.Spacing8),
    ) {
        if (onBack != null) {
            Button(
                onClick = onBack,
                shape = RoundedCornerShape(KraftRadius.Standard),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                    contentColor = MaterialTheme.colorScheme.onSurface,
                ),
                modifier = Modifier
                    .heightIn(min = KraftSpacing.TouchTarget)
                    .semantics { contentDescription = "Back to the game" },
            ) {
                Text(text = "‹ Back", style = MaterialTheme.typography.bodyMedium, maxLines = 1)
            }
            Spacer(Modifier.width(KraftSpacing.Spacing12))
        }
        Text(
            text = "Game review",
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
        )
    }
}

/** The two accuracy numbers, side by side, each labelled by whose it is. */
@Composable
private fun ReviewTiles(review: GameReviewResult, playerSide: Side) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(KraftSpacing.Spacing8),
            modifier = Modifier.padding(horizontal = ScreenEdge),
        ) {
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
        Spacer(Modifier.height(KraftSpacing.Spacing8))
    }
}

@Composable
private fun AccuracyTile(label: String, accuracy: Int, tone: Color) {
    Column(
        modifier = Modifier
            .clip(RoundedCornerShape(KraftRadius.Standard))
            .background(MaterialTheme.colorScheme.surfaceContainerLow)
            .border(
                KraftSpacing.BorderWidth,
                MaterialTheme.colorScheme.outlineVariant,
                RoundedCornerShape(KraftRadius.Standard),
            )
            .padding(horizontal = KraftSpacing.Spacing16, vertical = KraftSpacing.Spacing8)
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

/**
 * Every verdict, spelled out, once — so the colours need no decoder and no
 * reader has to guess what "the yellow one" means. It sits at the top of the
 * scrubber and scrolls away with it.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun VerdictLegend() {
    Column {
        Text(
            text = "How each move was graded",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
        )
        Spacer(Modifier.height(KraftSpacing.Spacing4))
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(KraftSpacing.Spacing8),
            verticalArrangement = Arrangement.spacedBy(KraftSpacing.Spacing4),
        ) {
            for (verdict in MoveVerdict.entries) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    VerdictDot(verdict)
                    Spacer(Modifier.width(KraftSpacing.Spacing4))
                    Text(
                        text = verdict.label(),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                    )
                }
            }
        }
    }
}

/** Dot + word, never dot alone. */
@Composable
private fun VerdictDot(verdict: MoveVerdict) {
    Box(
        modifier = Modifier
            .size(KraftSpacing.Spacing8)
            .clip(RoundedCornerShape(KraftRadius.Pill))
            .background(verdictColor(verdict)),
    )
}

/** A trail too short, or too broken, to draw the ply the reader asked for. */
@Composable
private fun MissingPosition() {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .fillMaxSize()
            .clip(RoundedCornerShape(KraftRadius.Medium))
            .background(MaterialTheme.colorScheme.surfaceContainerLow),
    ) {
        Text(
            text = "That position is not in this game's trail.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(KraftSpacing.Spacing16),
        )
    }
}

@Composable
private fun EmptyReview() {
    Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
        Text(
            text = "No moves to review.",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/**
 * The board state for one ply.
 *
 * Pieces, side to move and the check burst come straight off the FEN; the last
 * move is the one that led here, which is what puts the board's own arrow on
 * it. Selection, targets and drop handling are left at their defaults because
 * the board is handed `interactive = false` and never reads them.
 */
private fun boardStateOf(
    position: Position?,
    played: ChessMove?,
    playerSide: Side,
    sans: List<String>,
    statusText: String,
    evalCp: Int?,
): GameUiState? {
    if (position == null) return null
    return GameUiState(
        pieces = position.board.toList(),
        sideToMove = position.sideToMove,
        playerSide = playerSide,
        lastMoveFrom = played?.from,
        lastMoveTo = played?.to,
        checkSquare = if (position.isInCheck()) {
            position.kingSquare(position.sideToMove)
        } else {
            null
        },
        // The player's own side of the board, the way they sat in front of it.
        flipped = playerSide == Side.BLACK,
        statusText = statusText,
        sans = sans,
        evalCp = evalCp,
    )
}

/**
 * The squares a SAN string names in a position, or null when it names none.
 *
 * Matching is by SAN and not by shape: the domain's own `toSan` is the only
 * authority here, so castling, en passant and a disambiguated knight all resolve
 * exactly as the move list printed them, and anything the domain would not
 * produce resolves to nothing rather than to a guess.
 *
 * Candidates are narrowed on the destination square before their SAN is built,
 * because building a SAN means making the move and asking whether it mates. That
 * turns a scan of every legal move into a handful.
 */
internal fun resolveSan(position: Position?, san: String?): ChessMove? {
    if (position == null || san.isNullOrBlank()) return null
    val destination = sanDestination(san, position.sideToMove)
    val candidates = position.generateLegalMoves().filter {
        destination == null || it.to == destination
    }
    return candidates.firstOrNull { position.toSan(it) == san }
}

/**
 * The engine's move in the same position, as squares — but only when the move
 * was not the best one, because then the board is already showing it and a
 * second ring on the same two squares says nothing twice.
 */
internal fun engineAlternative(position: Position?, move: ReviewedMove?): ChessMove? {
    if (position == null || move == null) return null
    if (move.verdict == MoveVerdict.BEST) return null
    val best = move.bestSan ?: return null
    if (best == move.san) return null
    return resolveSan(position, best)
}

/**
 * The destination square a SAN string names, used only to narrow the search.
 *
 * Null whenever the shape is not one it recognises, which costs a slower search
 * and never a wrong answer.
 */
private fun sanDestination(san: String, mover: Side): Int? {
    val body = san.trim().trimEnd('+', '#')
    if (body == "O-O" || body == "O-O-O") {
        val file = if (body.length > 3) 'c' else 'g'
        return parseSquare("$file${'1' + mover.homeRank()}")
    }
    // "e8=Q" ends in the promotion, not in the square.
    val trimmed = if (body.length > 3 && body[body.length - 2] == '=') {
        body.substring(0, body.length - 3)
    } else {
        body
    }
    if (trimmed.length < 2) return null
    return parseSquare(trimmed.substring(trimmed.length - 2))
}

/**
 * The one sentence about a move.
 *
 * [Coach] decides whether there is anything worth saying, and it is deliberately
 * silent far more often than it speaks. The fallback is not a second opinion but
 * the fact itself: naming the move the engine wanted is still useful on a move
 * the coach chose to pass over.
 */
internal fun moveSentence(move: ReviewedMove): String? {
    val line = Coach.lineFor(move.verdict, move.cpLoss, move.bestSan, move.san)
    if (line != null) {
        // One sentence whichever case the coach chose: the words live on each
        // shape rather than on the sealed type, so this is the join.
        return when (line) {
            is CoachLine.GaveAway -> line.what
            is CoachLine.MissedWin -> line.what
            is CoachLine.Accurate -> line.what
        }
    }
    return move.bestSan?.takeIf { it != move.san }?.let { "Better: $it" }
}

/**
 * The evaluation in the words the in-game eval bar uses for the same number, so
 * one position never gets two different sentences.
 */
internal fun evalSentence(evalCp: Int): String {
    val better = if (evalCp >= 0) "White" else "Black"
    val cp = abs(evalCp)
    // The search's mate sentinel is not a pawn count, and printing it as one
    // would misreport how the game ended.
    if (cp >= GameReview.MATE_THRESHOLD) return "$better has mate."
    val amount = pawnsWord(cp)
    return "$better is better by about $amount."
}

/** "Move 12 of 24", and the start is not a move. */
internal fun plyLabel(selected: Int, total: Int): String =
    if (selected <= 0) "Starting position" else "Move $selected of $total"

/** What one row says to TalkBack, in one pass. */
private fun rowDescription(
    move: ReviewedMove,
    isPlayerMove: Boolean,
    isSelected: Boolean,
): String = buildString {
    append("Move ${move.number} ${moveNumber(move)} ${move.san}, ${move.verdict.label()}. ")
    append(if (isPlayerMove) "Yours. " else "Computer's. ")
    append(lossPhrase(move.cpLoss))
    moveSentence(move)?.let { append(" $it") }
    val cp = move.evalAfterCp
    if (cp != null) append(" ${evalSentence(cp)}")
    if (isSelected) append(" Shown on the board.")
}.trim()

/** The loss in words, since centipawns are not one. */
private fun lossPhrase(cpLoss: Int): String = when {
    cpLoss <= 0 -> "Nothing lost."
    else -> "Cost about ${pawnsWord(cpLoss)}."
}

/** A centipawn count as the app says it out loud: pawns when it is big enough. */
private fun pawnsWord(cp: Int): String {
    val pawns = cp / 100.0
    return if (pawns >= 0.95) {
        String.format("%.1f pawns", pawns)
    } else {
        "$cp centipawns"
    }
}

/**
 * The sentence naming the engine's better move and the squares it is ringed on.
 *
 * Written under the board and read out by it, because a ring is a shape and the
 * reader deserves to be told which move it stands for.
 */
private fun engineNote(move: ReviewedMove?, engineMove: ChessMove?): String? {
    if (move == null || engineMove == null) return null
    val best = move.bestSan ?: return null
    return "Ringed: the engine's $best, ${squareName(engineMove.from)} to " +
        "${squareName(engineMove.to)}."
}

/** Who made a move: White moves on even plies. */
/**
 * Move numbering as a club score reads it: `1.` for White, `1…` for Black.
 *
 * Every row printing `1.` made a two-line game look like one move played
 * twice, which is the single most confusing thing a move list can do.
 */
private fun moveNumber(move: ReviewedMove): String =
    if (moverOf(move) == Side.WHITE) "${move.number}." else "${move.number}\u2026"

private fun moverOf(move: ReviewedMove): Side =
    if (move.ply % 2 == 0) Side.WHITE else Side.BLACK

private fun ReviewedMove.isPlayerMove(playerSide: Side): Boolean =
    (ply % 2 == 0) == (playerSide == Side.WHITE)

private val ScreenEdge = KraftSpacing.ScreenEdge
private val VerdictNumberWidth = KraftSpacing.Spacing40
private val VerdictWordWidth = KraftSpacing.Spacing64
private val RowMinHeight = KraftSpacing.TouchTarget

/**
 * The centre notch, in the same weight on every meter in the app so the review's
 * bars and the in-game one read as a single instrument. A canvas dimension
 * rather than spacing, and the one number here that is not a token or a fraction.
 */
private val MeterNotchWidth = 2

/**
 * +/- three pawns is the end of the scale, exactly as in the game screen's eval
 * bar. Keep the two in step: a review that pinned a mate at the same width as a
 * 1.5-pawn edge would be disagreeing with the bar the player just played against.
 */
private const val ReviewEvalScaleCp = 300

/**
 * How much of the height the board takes in portrait; the rest goes to the
 * caption, the stepper and the scrubber. A share rather than a dp, because the
 * budget is a share of whatever screen this is on.
 */
private const val BoardHeightShare = 0.44f

/** How much of the width the board takes in landscape, beside the list. */
private const val BoardColumnShare = 0.44f

/** Ring proportions, as fractions of a square — the board's own convention. */
private const val EngineRingInset = 0.09f
private const val EngineRingWidth = 0.05f
private const val EngineRingFillAlpha = 0.20f
private const val EngineRingStrokeAlpha = 0.85f

/** The coach writes one sentence; a row shows two lines of it before it elides. */
private const val CoachMaxLines = 2
private const val EngineNoteMaxLines = 2

/** One item of legend sits above the moves, so move i is item i + 1. */
private const val LegendItemCount = 1
private const val LegendKey = "verdict-legend"

/** How long the list takes to follow a step. Snapped under reduce-motion. */
private const val ScrollMs = 220

internal fun MoveVerdict.label(): String = when (this) {
    MoveVerdict.BEST -> "best"
    MoveVerdict.GOOD -> "good"
    MoveVerdict.INACCURACY -> "inaccuracy"
    MoveVerdict.MISTAKE -> "mistake"
    MoveVerdict.BLUNDER -> "blunder"
}

/** Green for praise, warm neutral for nitpicks, red for real damage. */
@Composable
internal fun verdictColor(verdict: MoveVerdict): Color = when (verdict) {
    MoveVerdict.BEST -> ChessKraftColors.VerdictBest
    MoveVerdict.GOOD -> ChessKraftColors.VerdictGood
    MoveVerdict.INACCURACY -> ChessKraftColors.VerdictInaccuracy
    MoveVerdict.MISTAKE -> ChessKraftColors.VerdictMistake
    MoveVerdict.BLUNDER -> MaterialTheme.colorScheme.error
}

internal fun GameReviewResult.accuracyFor(side: Side): Int =
    if (side == Side.WHITE) accuracyWhite else accuracyBlack