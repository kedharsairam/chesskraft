/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 Kedhar Sairam
 */
package com.krafttools.chesskraft.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import android.content.res.Configuration
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import com.krafttools.chesskraft.ui.board.PieceMark
import com.kraft.ui.tokens.KraftRadius
import com.kraft.ui.tokens.KraftSpacing
import com.kraft.ui.tokens.KraftTypeScale
import com.krafttools.chesskraft.domain.PieceCode
import com.krafttools.chesskraft.domain.PieceType
import com.krafttools.chesskraft.domain.Side
import com.krafttools.chesskraft.ui.theme.ChessKraftColors
import com.krafttools.chesskraft.domain.GameReviewResult
import com.krafttools.chesskraft.domain.pieceValue
import com.krafttools.chesskraft.domain.Bot
import com.krafttools.chesskraft.domain.Bots
import com.krafttools.chesskraft.engine.Difficulty
import com.krafttools.chesskraft.presentation.GameUiState
import com.krafttools.chesskraft.presentation.GameViewModel
import com.krafttools.chesskraft.presentation.SoundPlayer
import com.krafttools.chesskraft.ui.board.ChessBoard
import com.krafttools.chesskraft.ui.board.GameOverSheet
import com.krafttools.chesskraft.ui.board.PieceMark
import com.krafttools.chesskraft.ui.board.PromotionDialog
import androidx.compose.material3.Icon
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.BlurOff
import androidx.compose.material.icons.filled.East
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.outlined.Flag
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PanTool
import androidx.compose.material.icons.filled.VolumeOff
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.SwapVert
import androidx.compose.material.icons.filled.Undo
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.width
import com.kraft.ui.tokens.KraftIconSize
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.runtime.getValue
import kotlinx.coroutines.delay

/**
 * The Game screen: captured strip, board, plain-English status, and the
 * toolbar — Undo / Hint / New / Resign / Flip, plus Sound up top.
 */
@Composable
fun GameScreen(
    viewModel: GameViewModel,
    onRematch: () -> Unit,
    onNewGame: () -> Unit,
    onOpenReview: (GameReviewResult) -> Unit,
    modifier: Modifier = Modifier,
    /**
     * Called once a game has actually ended, so the caller can drop the resume
     * card and re-read the history. Firing it on every recomposition would put
     * a file read in the composition path; it is therefore tied to the same
     * transition that shows the game-over screen.
     */
    onGameFinished: (() -> Unit)? = null,
) {
    val state by viewModel.state.collectAsState()
    val haptics = LocalHapticFeedback.current
    val context = LocalContext.current
    var confirmResign by remember { mutableStateOf(false) }
    var sheetOpen by remember { mutableStateOf(false) }
    var reviewJob by remember { mutableStateOf<(() -> Unit)?>(null) }
    var reviewing by remember { mutableStateOf(false) }

    // Clock cadence: the ViewModel owns the arithmetic, the composable owns
    // the heartbeat, so no coroutine loop lives in the ViewModel.
    LaunchedEffect(Unit) {
        if (state.clockWhiteMs != null) {
            while (true) {
                viewModel.markClockStart(System.currentTimeMillis())
                delay(ClockTickMs)
                val start = viewModel.clockStartedAt()
                if (start != null) {
                    viewModel.onElapsed(System.currentTimeMillis() - start)
                }
                if (viewModel.state.value.result != null) break
            }
        }
    }
    LaunchedEffect(Unit) {
        if (viewModel.soundPlayer == null) {
            viewModel.soundPlayer = SoundPlayer(context).also {
                it.enabled = viewModel.state.value.soundOn
            }
        }
    }
    LaunchedEffect(state.result) {
        if (state.result != null) sheetOpen = true
    }

    // Haptics: tick on pick-up, thock on a move, double-tick on check.
    // Illegal taps stay silent — the board shakes instead.
    var previous by remember { mutableStateOf(state) }
    LaunchedEffect(state) {
        if (state.pieces != previous.pieces) {
            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
        } else if (state.selected != null && previous.selected == null) {
            haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        }
        if (state.checkSquare != null && previous.checkSquare == null) {
            delay(CheckHapticGapMs)
            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
            delay(CheckHapticGapMs)
            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
        }
        previous = state
    }

    Column(modifier = modifier.fillMaxSize().navigationBarsPadding()) {
        // Compact bar: the opponent bar sits directly under it, so the title
        // band is one row tall instead of a wasted 200px. The foundation's
        // default-ink title rendered zero pixels on this screen (node present,
        // nothing drawn — reported to the foundation track), so ink is explicit.
        GameTopBar(
            soundOn = state.soundOn,
            onToggleSound = viewModel::toggleSound,
            thinking = state.aiThinking,
            showArrows = state.showArrows,
            onToggleArrows = viewModel::toggleArrows,
        )

        // The board is sized by the smaller incoming dimension: full width in
        // portrait, the height budget in landscape with the strips and status
        // reflowing into a side column.
        // No weight here: the board block wraps its content at full width, and
        // the move-list box below takes the remaining height. Two weighted
        // children split the space in half and the board lost half its size —
        // visible on the device, invisible in the source.
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            // Orientation from the configuration, never from this box's own
            // constraints: an earlier version compared maxWidth > maxHeight
            // here, and any layout change that shrank the board block flipped
            // the whole screen into the side-by-side branch mid-game.
            val landscape = LocalConfiguration.current.orientation ==
                Configuration.ORIENTATION_LANDSCAPE
            val boardSide = minOf(maxWidth * BoardLandscapeFraction, maxHeight)
            if (!landscape) {
                // No vertical centring: the board block hugs the top and all
                // spare pixels collect in ONE place — under the status, where
                // the move list lives. Centring split the slack in half and
                // left a void above and below.
                // wrapContentHeight, not fillMaxSize: this Column is the only
                // sized child of an unweighted Box, so fillMaxSize claimed all
                // the height it could reach and left the move-list box below
                // with none — the list rendered nothing at all, on a board that
                // looked perfect.
                Column(modifier = Modifier.wrapContentHeight()) {
                    BoardChrome(
                        state = state,
                        playerSide = viewModel.playerSide,
                        statusText = if (state.aiThinking) "Thinking…" else state.statusText,
                        onTap = viewModel::onTap,
                        onDrop = viewModel::onDrop,
                    )
                }
            } else {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    ChessBoard(
                        state = state,
                        onTap = viewModel::onTap,
                        onDrop = viewModel::onDrop,
                        modifier = Modifier
                            .size(boardSide)
                            .padding(start = KraftSpacing.Spacing16),
                    )
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .padding(horizontal = KraftSpacing.Spacing16),
                        verticalArrangement = Arrangement.Center,
                    ) {
                        SideChrome(
                            state = state,
                            playerSide = viewModel.playerSide,
                            statusText = if (state.aiThinking) "Thinking…" else state.statusText,
                        )
                    }
                }
            }
        }

        // The bottom of the screen belongs to the game story: the move list
        // sits in the slack under the board, where chess.com puts its panel.
        Box(Modifier.weight(1f)) {
            MoveStrip(sans = state.sans, modifier = Modifier.fillMaxSize())
        }

        ToolbarRow(
            canOfferDraw = state.sideToMove == viewModel.playerSide && state.result == null,
            drawPending = state.drawOfferPending,
            onOfferDraw = viewModel::offerDraw,
            canUndo = state.canUndo,
            onUndo = viewModel::undo,
            onHint = viewModel::hint,
            onNew = onNewGame,
            onResign = { confirmResign = true },
            onFlip = viewModel::flip,
            modifier = Modifier
                .navigationBarsPadding()
                .padding(bottom = KraftSpacing.Spacing8),
        )
    }

    if (state.pendingPromotion.isNotEmpty()) {
        PromotionDialog(
            options = state.pendingPromotion,
            side = state.sideToMove,
            onChoose = viewModel::onPromote,
            onDismiss = viewModel::dismissPromotion,
        )
    }

    if (confirmResign) {
        AlertDialog(
            onDismissRequest = { confirmResign = false },
            title = { Text("Resign this game?") },
            text = { Text("Your opponent takes the point. You can still review the game after.") },
            confirmButton = {
                Button(
                    onClick = {
                        confirmResign = false
                        viewModel.resign()
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error,
                        contentColor = MaterialTheme.colorScheme.onError,
                    ),
                ) {
                    Text("Resign")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { confirmResign = false },
                    colors = ButtonDefaults.textButtonColors(
                        contentColor = MaterialTheme.colorScheme.onSurface,
                    ),
                ) {
                    Text("Keep Playing")
                }
            },
        )
    }

    // The review grades every ply against the engine. Off the UI thread, and
    // cancelled if the sheet is dismissed before it finishes.
    LaunchedEffect(reviewJob) {
        val job = reviewJob ?: return@LaunchedEffect
        val built = viewModel.buildReview()
        if (built != null) {
            job()
            reviewJob = null
            onOpenReview(built)
        }
    }

    val result = state.result
    // Keyed on the result, not on composition: the save has just gained a
    // finished game and the resume card on Home is now a lie.
    LaunchedEffect(result) {
        if (result != null) onGameFinished?.invoke()
    }
    if (result != null && sheetOpen) {
        GameOverSheet(
            result = result,
            moveCount = state.sans.size,
            onRematch = onRematch,
            onNewGame = onNewGame,
            onReview = {
                reviewing = true
                reviewJob = { reviewing = false }
            },
            reviewRunning = reviewing,
            onDismiss = { sheetOpen = false },
        )
    }
}

/**
 * Compact bar: brass knight + title left, quiet sound action right. All ink
 * explicit — see the call-site note about the foundation bar.
 */
@Composable
private fun GameTopBar(
    soundOn: Boolean,
    onToggleSound: () -> Unit,
    thinking: Boolean,
    showArrows: Boolean,
    onToggleArrows: () -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(horizontal = KraftSpacing.ScreenEdge, vertical = KraftSpacing.Spacing8),
    ) {
        Text(
            text = "♞",
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.primary,
        )
        Spacer(Modifier.size(KraftSpacing.Spacing8))
        Text(
            text = "ChessKraft",
            style = MaterialTheme.typography.titleLarge.copy(
                fontWeight = FontWeight.SemiBold,
            ),
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            modifier = Modifier.weight(1f),
        )
        if (thinking) {
            ThinkingIndicator()
            Spacer(Modifier.size(KraftSpacing.Spacing8))
        }
        IconButton(
            onClick = onToggleArrows,
            modifier = Modifier
                .size(KraftSpacing.Spacing48)
                .semantics(mergeDescendants = true) {
                    contentDescription = if (showArrows) {
                        "Hide the move arrows."
                    } else {
                        "Show the move arrows."
                    }
                },
        ) {
            Icon(
                imageVector = if (showArrows) {
                    Icons.Filled.East
                } else {
                    Icons.Filled.BlurOff
                },
                contentDescription = null,
                tint = if (showArrows) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
                modifier = Modifier.size(KraftIconSize.Medium),
            )
        }
        // An icon, not a word: a toggle that spells out its state reads as a
        // status line and pushes the title around. The state is in the icon,
        // and the sentence is in the semantics for anyone who cannot see it.
        IconButton(
            onClick = onToggleSound,
            modifier = Modifier
                .size(KraftSpacing.Spacing48)
                .semantics(mergeDescendants = true) {
                    contentDescription = if (soundOn) "Mute move sounds." else "Unmute move sounds."
                },
        ) {
            Icon(
                imageVector = if (soundOn) Icons.Filled.VolumeUp else Icons.Filled.VolumeOff,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(KraftIconSize.Medium),
            )
        }
    }
}

@Composable
private fun BoardChrome(
    state: GameUiState,
    playerSide: Side,
    statusText: String,
    onTap: (Int) -> Unit,
    onDrop: (Int, Int) -> Unit,
) {
    PlayerStrip(
        name = opponentName(playerSide, state),
        pieces = if (playerSide == Side.WHITE) {
            state.capturedByBlack
        } else {
            state.capturedByWhite
        },
        victimSide = playerSide,
        active = state.sideToMove != playerSide,
        clockMs = if (playerSide == Side.WHITE) state.clockBlackMs else state.clockWhiteMs,
        label = capturedLabel(playerSide.opponent(), state.capturedByWhite, state.capturedByBlack),
    )
    // Full-bleed board: it runs edge to edge under the player bars, the way
    // chess.com draws it. Inset boards read as a widget; this reads as a game.
    ChessBoard(
        state = state,
        onTap = onTap,
        onDrop = onDrop,
        showArrows = state.showArrows,
    )
    PlayerStrip(
        name = "You",
        pieces = if (playerSide == Side.WHITE) {
            state.capturedByWhite
        } else {
            state.capturedByBlack
        },
        victimSide = playerSide.opponent(),
        active = state.sideToMove == playerSide,
        clockMs = if (playerSide == Side.WHITE) state.clockWhiteMs else state.clockBlackMs,
        label = capturedLabel(playerSide, state.capturedByWhite, state.capturedByBlack),
    )
    StatusRow(
        statusText = statusText,
        evalCp = state.evalCp,
        openingName = state.openingName,
        coachLine = state.coachLine,
    )
}

@Composable
private fun SideChrome(
    state: GameUiState,
    playerSide: Side,
    statusText: String,
) {
    PlayerStrip(
        name = opponentName(playerSide, state),
        pieces = if (playerSide == Side.WHITE) {
            state.capturedByBlack
        } else {
            state.capturedByWhite
        },
        victimSide = playerSide,
        active = state.sideToMove != playerSide,
        clockMs = if (playerSide == Side.WHITE) state.clockBlackMs else state.clockWhiteMs,
        label = capturedLabel(playerSide.opponent(), state.capturedByWhite, state.capturedByBlack),
    )
    PlayerStrip(
        name = "You",
        pieces = if (playerSide == Side.WHITE) {
            state.capturedByWhite
        } else {
            state.capturedByBlack
        },
        victimSide = playerSide.opponent(),
        active = state.sideToMove == playerSide,
        clockMs = if (playerSide == Side.WHITE) state.clockWhiteMs else state.clockBlackMs,
        label = capturedLabel(playerSide, state.capturedByWhite, state.capturedByBlack),
    )
    StatusRow(
        statusText = statusText,
        evalCp = state.evalCp,
        openingName = state.openingName,
        coachLine = state.coachLine,
    )
}

/**
 * The game story in one line: numbered move pairs, latest highlighted,
 * auto-following. Chess.com never hides the moves; neither do we. Instant
 * snap (never animated scroll) so reduce-motion has nothing to gate.
 */
@Composable
private fun MoveStrip(sans: List<String>, modifier: Modifier = Modifier) {
    if (sans.isEmpty()) return
    val rows = remember(sans) {
        buildList {
            var i = 0
            while (i < sans.size) {
                add(Triple(i / 2 + 1, sans[i], sans.getOrNull(i + 1)))
                i += 2
            }
        }
    }
    val listState = rememberLazyListState()
    LaunchedEffect(rows.size) {
        if (rows.isNotEmpty()) listState.scrollToItem(rows.size - 1)
    }
    // Two columns of numbered move pairs, the way chess.com prints them, so the
    // game reads as a page of moves rather than a ticker. Latest move is the one
    // thing in the app's green.
    LazyColumn(
        state = listState,
        modifier = modifier
            .padding(horizontal = KraftSpacing.ScreenEdge, vertical = KraftSpacing.Spacing4)
            .semantics(mergeDescendants = true) {
                contentDescription = "Moves so far. ${rows.takeLast(3).joinToString(", ")}."
            },
    ) {
        items(rows.size) { index ->
            val (number, white, black) = rows[index]
            val current = index == rows.size - 1
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(KraftSpacing.Spacing8),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    text = "$number.",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    modifier = Modifier.width(MoveNumberWidth),
                )
                Text(
                    text = white,
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontWeight = if (current && black == null) {
                            FontWeight.SemiBold
                        } else {
                            FontWeight.Normal
                        },
                    ),
                    color = if (current && black == null) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurface
                    },
                    maxLines = 1,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    text = black.orEmpty(),
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontWeight = if (current && black != null) FontWeight.SemiBold else FontWeight.Normal,
                    ),
                    color = if (current && black != null) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurface
                    },
                    maxLines = 1,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
private fun StatusRow(
    statusText: String,
    evalCp: Int?,
    openingName: String? = null,
    coachLine: String? = null,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = KraftSpacing.ScreenEdge, vertical = KraftSpacing.Spacing8),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = statusText,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
            modifier = Modifier.semantics(mergeDescendants = true) {
                contentDescription = "Status. $statusText"
            },
        )
        if (openingName != null) {
            Text(
                text = openingName,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
                maxLines = 1,
                modifier = Modifier.semantics(mergeDescendants = true) {
                    contentDescription = "Opening: $openingName."
                },
            )
        }
        EvalBar(evalCp = evalCp)
    }
}

/**
 * The eval bar, horizontal: who is better, from the engine. White's edge
 * fills from the centre toward Black, capped at +/-3 pawns so a winning
 * position does not pin the marker to the end and stop moving. Word + bar,
 * never the bar alone.
 */
@Composable
private fun EvalBar(evalCp: Int?) {
    if (evalCp == null) return
    val fraction = (evalCp.coerceIn(-EvalBarCp, EvalBarCp).toFloat() / EvalBarCp)
    // A meter, not a progress bar: white's edge is the app's green, black's is
    // the quiet outline, and the track sits well back. At equal the bar reads
    // empty because that is the truth, but the notch says "level", not "loading".
    val track = MaterialTheme.colorScheme.outlineVariant
    val notch = MaterialTheme.colorScheme.onSurfaceVariant
    val fill = if (evalCp >= 0) {
        ChessKraftColors.Accent
    } else {
        notch
    }
    Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = KraftSpacing.Spacing6)
            .height(EvalBarThickness.dp)
            .semantics(mergeDescendants = true) {
                contentDescription = if (fraction >= 0f) {
                    "White is better by about ${pawns(evalCp)}."
                } else {
                    "Black is better by about ${pawns(-evalCp)}."
                }
            },
    ) {
        val w = size.width
        val h = size.height
        val r = h / 2f
        drawRoundRect(
            color = track,
            size = androidx.compose.ui.geometry.Size(w, h),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(r, r),
        )
        val half = w / 2f
        val reach = kotlin.math.abs(fraction) * half
        val left = if (fraction >= 0f) half else half - reach
        drawRoundRect(
            color = fill,
            topLeft = androidx.compose.ui.geometry.Offset(left, 0f),
            size = androidx.compose.ui.geometry.Size(reach.coerceAtLeast(h), h),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(r, r),
        )
        // Centre notch: the zero line, so "even" is legible.
        drawLine(
            color = notch,
            start = androidx.compose.ui.geometry.Offset(half, 0f),
            end = androidx.compose.ui.geometry.Offset(half, h),
            strokeWidth = EvalBarNotchWidth.toFloat(),
        )
    }
}

private fun pawns(cp: Int): String {
    val p = cp / 100.0
    return if (p >= 0.95) String.format("%.1f pawns", p) else "${cp} centipawns"
}

private val EvalBarThickness = 8
private val EvalBarNotchWidth = 2
/** +/- three pawns is the end of the scale; beyond that the bar stops moving. */
private const val EvalBarCp = 300

/**
 * The engine's one sentence about your last move. Quieter than the status
 * line and never louder than the board: a line of coaching, not an alarm.
 */
@Composable
private fun CoachLineText(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
        maxLines = 2,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = KraftSpacing.Spacing8)
            .semantics(mergeDescendants = true) { contentDescription = "Coach: $text" },
    )
}

/**
 * Three dots that cycle while the engine thinks — the waiting state said out
 * loud. No spinner dependency, no infinite rotation: a phase change is a
 * colour change, which is free.
 */
@Composable
private fun ThinkingIndicator() {
    val transition = rememberInfiniteTransition(label = "thinking")
    val phase by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = ThinkingCycleMs, easing = LinearEasing),
        ),
        label = "phase",
    )
    Row(
        horizontalArrangement = Arrangement.spacedBy(ThinkingDotGap.dp),
        modifier = Modifier.semantics(mergeDescendants = true) {
            contentDescription = "The computer is thinking."
        },
    ) {
        for (i in 0 until ThinkingDots) {
            val lit = (phase * ThinkingDots).toInt() == i
            Box(
                modifier = Modifier
                    .size(ThinkingDotSize.dp)
                    .clip(RoundedCornerShape(KraftRadius.Pill))
                    .background(
                        if (lit) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.outline
                        },
                    ),
            )
        }
    }
}

/**
 * A player card: brass turn dot (shape + word, never color-only — the status
 * line carries the same fact in words), name, captured pieces, material edge.
 */
@Composable
private fun PlayerStrip(
    name: String,
    pieces: List<PieceType>,
    victimSide: Side,
    active: Boolean,
    clockMs: Long?,
    label: String,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = KraftSpacing.Spacing16, vertical = KraftSpacing.Spacing4)
            .semantics(mergeDescendants = true) { contentDescription = label },
    ) {
        TurnDot(active = active, description = if (active) "$name to move." else "$name waiting.")
        Spacer(Modifier.size(KraftSpacing.Spacing8))
        PlayerAvatar(victimSide = victimSide, label = name)
        Spacer(Modifier.size(KraftSpacing.Spacing8))
        Text(
            text = name,
            style = MaterialTheme.typography.labelMedium.copy(
                fontWeight = if (active) FontWeight.SemiBold else FontWeight.Normal,
            ),
            color = if (active) {
                MaterialTheme.colorScheme.onSurface
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
            maxLines = 1,
        )
        Spacer(Modifier.size(KraftSpacing.Spacing8))
        Row(
            horizontalArrangement = Arrangement.spacedBy(KraftSpacing.Spacing2),
            modifier = Modifier.weight(1f),
        ) {
            for (type in pieces) {
                PieceMark(
                    code = PieceCode.of(victimSide, type),
                    modifier = Modifier.size(KraftSpacing.Spacing24),
                )
            }
        }
        val material = pieces.sumOf { pieceValue(it) } / 100
        if (material > 0) {
            Text(
                text = "+$material",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
            )
            Spacer(Modifier.size(KraftSpacing.Spacing8))
        }
        if (clockMs != null) {
            ClockPill(clockMs = clockMs, running = active)
        }
    }
}

/**
 * The clock pill: mm:ss, tabular by construction (two digits every field),
 * red under twenty seconds with a semibold promotion. Null clock (untimed
 * game) renders nothing — no pill, no placeholder, no explanation owed.
 */
@Composable
private fun ClockPill(clockMs: Long, running: Boolean) {
    val totalSeconds = (clockMs / 1000L).coerceAtLeast(0L)
    val text = "%d:%02d".format(totalSeconds / 60L, totalSeconds % 60L)
    val low = clockMs < ClockLowMs
    Card(
        shape = RoundedCornerShape(KraftRadius.Small),
        colors = CardDefaults.cardColors(
            containerColor = if (low) {
                MaterialTheme.colorScheme.error
            } else {
                MaterialTheme.colorScheme.surfaceContainerHighest
            },
        ),
        modifier = Modifier.semantics(mergeDescendants = true) {
            contentDescription = "Clock. $text remaining."
        },
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelMedium.copy(
                fontWeight = FontWeight.SemiBold,
            ),
            color = if (low) {
                MaterialTheme.colorScheme.onError
            } else {
                MaterialTheme.colorScheme.onSurface
            },
            maxLines = 1,
            modifier = Modifier.padding(
                horizontal = KraftSpacing.Spacing8,
                vertical = KraftSpacing.Spacing4,
            ),
        )
    }
}

@Composable
private fun PlayerAvatar(victimSide: Side, label: String) {
    // Your army's king as your face; the computer's king as its face.
    // Chess.com puts a face next to every name; ours is the army itself.
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(KraftSpacing.Spacing32)
            .clip(RoundedCornerShape(KraftRadius.Pill))
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .semantics { contentDescription = "Avatar for $label." },
    ) {
        PieceMark(
            code = PieceCode.of(victimSide.opponent(), PieceType.KING),
            modifier = Modifier.size(KraftSpacing.Spacing24),
        )
    }
}

@Composable
private fun TurnDot(active: Boolean, description: String) {
    val color = if (active) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.outlineVariant
    }
    Box(
        modifier = Modifier
            .size(KraftSpacing.Spacing8)
            .semantics { contentDescription = description },
    ) {
        androidx.compose.foundation.Canvas(modifier = Modifier.fillMaxSize()) {
            drawCircle(color = color)
        }
    }
}

/**
 * The opponent is a name, not a dial. "Computer · Casual" made the thing you
 * are playing feel like a settings value; Bertie does not.
 */
private fun opponentName(playerSide: Side, state: GameUiState): String =
    Bots.forLevel(state.difficulty).name

private fun capturedLabel(
    forSide: Side,
    capturedByWhite: List<PieceType>,
    capturedByBlack: List<PieceType>,
): String {
    val pieces = if (forSide == Side.WHITE) capturedByWhite else capturedByBlack
    val who = if (forSide == Side.WHITE) "White" else "Black"
    if (pieces.isEmpty()) return "$who has captured nothing yet."
    return "$who captured: ${pieces.joinToString(", ") { it.name.lowercase() }}."
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ToolbarRow(
    canOfferDraw: Boolean,
    drawPending: Boolean,
    onOfferDraw: () -> Unit,
    canUndo: Boolean,
    onUndo: () -> Unit,
    onHint: () -> Unit,
    onNew: () -> Unit,
    onResign: () -> Unit,
    onFlip: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // One segmented bar, not five floating labels: a hairline container with
    // dividers reads as a single instrument strip. Wraps at font-scale 2.0
    // via FlowRow instead of clipping.
    Card(
        shape = RoundedCornerShape(KraftRadius.Large),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        ),
        border = BorderStroke(
            KraftSpacing.BorderWidth,
            MaterialTheme.colorScheme.outlineVariant,
        ),
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = KraftSpacing.ScreenEdge),
    ) {
        FlowRow(
            horizontalArrangement = Arrangement.SpaceEvenly,
            modifier = Modifier.fillMaxWidth(),
        ) {
            ToolbarIcon(Icons.Filled.Undo, "Undo", "Take back your last move", canUndo, onUndo)
            ToolbarIcon(Icons.Filled.Lightbulb, "Hint", "Show a suggested move", true, onHint)
            ToolbarIcon(Icons.Filled.Add, "New", "Start a new game", true, onNew)
            ToolbarIcon(
                Icons.Outlined.Flag,
                "Resign",
                "Give up this game",
                true,
                onResign,
                destructive = true,
            )
            ToolbarIcon(Icons.Filled.SwapVert, "Flip", "Turn the board around", true, onFlip)
            ToolbarIcon(
                Icons.Filled.PanTool,
                "Offer draw",
                "Ask the computer for a draw.",
                canOfferDraw && !drawPending,
                onOfferDraw,
            )
        }
    }
}

@Composable
private fun ToolbarIcon(
    icon: ImageVector,
    label: String,
    description: String,
    enabled: Boolean,
    onClick: () -> Unit,
    destructive: Boolean = false,
) {
    // Icon-only, so the icon is invisible to TalkBack and the name + action
    // do the talking. Never color-alone: Resign is the only red thing here.
    IconButton(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier
            .size(KraftSpacing.Spacing48)
            .semantics(mergeDescendants = true) {
                contentDescription = "$label. $description."
            },
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = when {
                !enabled -> MaterialTheme.colorScheme.onSurfaceVariant
                destructive -> MaterialTheme.colorScheme.error
                else -> MaterialTheme.colorScheme.onSurface
            },
            modifier = Modifier.size(KraftIconSize.Medium),
        )
    }
}

/** Board share of the width budget in landscape; the rest is strips + status. */
private const val BoardLandscapeFraction = 0.62f
private const val CheckHapticGapMs = 90L
/** Number column in the move panel — wide enough for three digits. */
private val MoveNumberWidth = KraftSpacing.Spacing24
/** Under twenty seconds the pill goes red. Twenty, not ten: at ten the game is already panic. */
private const val ClockLowMs = 20_000L
/** Four beats a second: a second-resolution clock does not need more, and 250ms keeps the pill from stuttering. */
private const val ClockTickMs = 250L
private const val ThinkingDots = 3
private const val ThinkingDotSize = 6
private const val ThinkingDotGap = 3
private const val ThinkingCycleMs = 900
