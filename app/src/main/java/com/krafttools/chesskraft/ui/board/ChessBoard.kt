/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 Kedhar Sairam
 */
package com.krafttools.chesskraft.ui.board

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import com.kraft.ui.motion.rememberReduceMotion
import com.kraft.ui.tokens.KraftSpacing
import com.krafttools.chesskraft.domain.BoardGeometry
import com.krafttools.chesskraft.domain.PieceCode
import com.krafttools.chesskraft.domain.PieceType
import com.krafttools.chesskraft.domain.Position
import com.krafttools.chesskraft.domain.describeSquare
import com.krafttools.chesskraft.presentation.announceText
import com.krafttools.chesskraft.presentation.GameUiState
import com.krafttools.chesskraft.ui.theme.ChessKraftColors

/**
 * The board. One Compose [Canvas] — a single draw pass — plus 64
 * layout-only semantics nodes (no drawing, no touch handling) so TalkBack
 * can visit every square ("e4, white knight") while gestures stay on the
 * Canvas with 8dp miss tolerance.
 */
@Composable
fun ChessBoard(
    state: GameUiState,
    onTap: (Int) -> Unit,
    onDrop: (Int, Int) -> Unit,
    modifier: Modifier = Modifier,
    interactive: Boolean = true,
) {
    var boardPx by remember { mutableStateOf(0f) }
    var dragFrom by remember { mutableStateOf<Int?>(null) }
    var dragPos by remember { mutableStateOf<Offset?>(null) }
    val density = LocalDensity.current
    val slopPx = remember(density) {
        with(density) { KraftSpacing.Spacing8.toPx() }
    }
    val reduceMotion = rememberReduceMotion()
    val measurer = rememberTextMeasurer()
    val motion = rememberBoardMotion(reduceMotion)

    val snapshot = remember(state.pieces, state.sideToMove) {
        Position.fromSnapshot(state.pieces, state.sideToMove)
    }
    val errorColor = MaterialTheme.colorScheme.error
    val coordStyle = MaterialTheme.typography.labelMedium
    val announcement = remember(state.pieces, state.statusText, state.sans) {
        announceText(snapshot, state.sans.lastOrNull(), state.result) + " " + state.statusText
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .aspectRatio(1f)
            .graphicsLayer { translationX = motion.nudgePx }
            .semantics(mergeDescendants = false) {
                liveRegion = LiveRegionMode.Polite
                contentDescription = announcement
            }
            .onSizeChanged { boardPx = it.width.toFloat() },
    ) {
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(boardPx, state.flipped, interactive, slopPx) {
                    if (!interactive) return@pointerInput
                    detectTapGestures { offset ->
                        val sq = BoardGeometry.hitTest(
                            offset.x, offset.y, boardPx, state.flipped, slopPx,
                        )
                        if (sq != null) onTap(sq)
                    }
                }
                .pointerInput(boardPx, state.flipped, interactive, slopPx) {
                    if (!interactive) return@pointerInput
                    detectDragGestures(
                        onDragStart = { offset ->
                            val sq = BoardGeometry.hitTest(
                                offset.x, offset.y, boardPx, state.flipped, slopPx,
                            )
                            if (sq != null) {
                                dragFrom = sq
                                dragPos = offset
                            }
                        },
                        onDrag = { change, _ ->
                            change.consume()
                            dragPos = change.position
                        },
                        onDragEnd = {
                            val from = dragFrom
                            val end = dragPos
                            dragFrom = null
                            dragPos = null
                            if (from != null && end != null) {
                                val to = BoardGeometry.hitTest(
                                    end.x, end.y, boardPx, state.flipped, slopPx,
                                )
                                if (to != null && to != from) onDrop(from, to)
                            }
                        },
                        onDragCancel = {
                            dragFrom = null
                            dragPos = null
                        },
                    )
                },
        ) {
            if (size.width <= 0f) return@Canvas
            drawBoard(
                state = state,
                measurer = measurer,
                errorColor = errorColor,
                coordStyle = coordStyle,
                dragFrom = dragFrom,
                dragPos = dragPos,
                slideProgress = motion.slideProgress,
                captureAlpha = motion.captureAlpha,
            )
        }
        // 64 layout-only TalkBack nodes. Transparent, no pointer handling —
        // touch falls through to the Canvas; TalkBack gets a node per square.
        Column(Modifier.fillMaxSize()) {
            val rows = if (state.flipped) 0..7 else 7 downTo 0
            for (rank in rows) {
                Row(Modifier.fillMaxWidth().weight(1f)) {
                    val files = if (state.flipped) 7 downTo 0 else 0..7
                    for (file in files) {
                        val sq = file + rank * 8
                        val label = describeSquare(snapshot, sq)
                        Box(
                            Modifier
                                .weight(1f)
                                .fillMaxSize()
                                .semantics {
                                    contentDescription = label
                                    if (interactive) {
                                        onClick(label = "Play $label") {
                                            onTap(sq)
                                            true
                                        }
                                    }
                                },
                        )
                    }
                }
            }
        }
    }

    LaunchedEffect(state.nudgeToken) {
        if (state.nudgeToken > 0) motion.nudge()
    }
    LaunchedEffect(state.lastMoveTo, state.pieces) {
        motion.onPositionChanged(state.pieces, state.lastMoveFrom, state.lastMoveTo)
    }
}

private fun DrawScope.drawBoard(
    state: GameUiState,
    measurer: TextMeasurer,
    errorColor: androidx.compose.ui.graphics.Color,
    coordStyle: TextStyle,
    dragFrom: Int?,
    dragPos: Offset?,
    slideProgress: Float,
    captureAlpha: Float,
) {
    val boardPx = size.width
    val sq = BoardGeometry.squareSize(boardPx)
    val light = ChessKraftColors.LightSquare
    val dark = ChessKraftColors.DarkSquare
    val accent = ChessKraftColors.Accent
    val error = errorColor

    // 1. Squares.
    for (square in 0..63) {
        val (col, row) = BoardGeometry.displayCell(square, state.flipped)
        drawRect(
            color = if ((col + row) % 2 == 0) light else dark,
            topLeft = Offset(col * sq, row * sq),
            size = Size(sq, sq),
        )
    }

    // 2. Last-move wash (brass, translucent — never colour-only: the moved
    // piece visibly changed squares too).
    val lastFrom = state.lastMoveFrom
    val lastTo = state.lastMoveTo
    if (lastFrom != null && lastTo != null) {
        for (moveSq in listOf(lastFrom, lastTo)) {
            val (col, row) = BoardGeometry.displayCell(moveSq, state.flipped)
            drawRect(
                color = accent.copy(alpha = LastMoveAlpha),
                topLeft = Offset(col * sq, row * sq),
                size = Size(sq, sq),
            )
        }
    }

    // 3. Hint wash.
    val hint = state.hintMove
    if (hint != null) {
        for (hintSq in listOf(hint.from, hint.to)) {
            val (col, row) = BoardGeometry.displayCell(hintSq, state.flipped)
            drawRect(
                color = accent.copy(alpha = HintAlpha),
                topLeft = Offset(col * sq, row * sq),
                size = Size(sq, sq),
            )
        }
    }

    // 4. Check: red wash plus a ring — wash and ring together, never colour only.
    val check = state.checkSquare
    if (check != null) {
        val center = cellCenter(check, boardPx, state.flipped)
        drawRect(
            color = error.copy(alpha = CheckWashAlpha),
            topLeft = Offset(center.x - sq / 2f, center.y - sq / 2f),
            size = Size(sq, sq),
        )
        drawCircle(
            color = error,
            radius = sq * CheckRingRadius,
            center = center,
            style = Stroke(width = sq * RingWidth),
        )
    }

    // 5. Selection ring + legal targets: filled dots vs hollow capture
    // rings, so the two states differ by shape in grayscale too.
    val selected = state.selected
    if (selected != null) {
        val center = cellCenter(selected, boardPx, state.flipped)
        drawCircle(
            color = accent,
            radius = sq * SelectedRingRadius,
            center = center,
            style = Stroke(width = sq * RingWidth),
        )
        for ((target, isCapture) in state.targets) {
            val targetCenter = cellCenter(target, boardPx, state.flipped)
            if (isCapture) {
                drawCircle(
                    color = accent,
                    radius = sq * CaptureRingRadius,
                    center = targetCenter,
                    style = Stroke(width = sq * RingWidth),
                )
            } else {
                drawCircle(
                    color = accent.copy(alpha = DotAlpha),
                    radius = sq * DotRadius,
                    center = targetCenter,
                )
            }
        }
    }

    // 6. Capture flash: quick fade on the landing square.
    if (captureAlpha > 0f && lastTo != null) {
        val center = cellCenter(lastTo, boardPx, state.flipped)
        drawCircle(
            color = accent.copy(alpha = captureAlpha * CaptureFlashMax),
            radius = sq * CaptureRingRadius,
            center = center,
            style = Stroke(width = sq * RingWidth),
        )
    }

    // 7. Coordinates inside the corners, in the opposite square colour so
    // they read on light and dark wood alike.
    for (label in BoardGeometry.coordinateLabels(state.flipped)) {
        val (col, row) = BoardGeometry.displayCell(label.square, state.flipped)
        val squareIsLight = (col + row) % 2 == 0
        val color = if (squareIsLight) dark else light
        val layout = measurer.measure(label.text, style = coordStyle.copy(color = color))
        val pad = sq * CoordPad
        val topLeft = if (label.isFile) {
            Offset((col + 1) * sq - layout.size.width - pad, (row + 1) * sq - layout.size.height - pad)
        } else {
            Offset(col * sq + pad, row * sq + pad)
        }
        drawText(layout, topLeft = topLeft)
    }

    // 8. Pieces. The sliding piece is drawn at its lerped position; its
    // landing square is skipped until the slide lands.
    val slidingPiece = if (slideProgress < 1f && lastFrom != null && lastTo != null) {
        state.pieces[lastTo]
    } else {
        0
    }
    for (square in 0..63) {
        if (square == lastTo && slidingPiece != 0) continue
        if (square == dragFrom && dragPos != null) continue
        val code = state.pieces[square]
        if (code == 0) continue
        drawPiece(measurer, code, cellCenter(square, boardPx, state.flipped), sq)
    }
    if (slidingPiece != 0 && lastFrom != null && lastTo != null) {
        val fromCenter = cellCenter(lastFrom, boardPx, state.flipped)
        val toCenter = cellCenter(lastTo, boardPx, state.flipped)
        val at = Offset(
            fromCenter.x + (toCenter.x - fromCenter.x) * slideProgress,
            fromCenter.y + (toCenter.y - fromCenter.y) * slideProgress,
        )
        drawPiece(measurer, slidingPiece, at, sq)
    }
    // Dragged piece follows the finger, drawn last so it floats above.
    if (dragFrom != null && dragPos != null) {
        val code = state.pieces[dragFrom]
        if (code != 0) drawPiece(measurer, code, dragPos, sq)
    }
}

private fun cellCenter(square: Int, boardPx: Float, flipped: Boolean): Offset {
    val point = BoardGeometry.squareCenter(square, boardPx, flipped)
    return Offset(point.x, point.y)
}

/** Cburnett-style glyphs drawn as text: outline forms for White, solid for Black. */
fun glyphFor(code: Int): String {
    val type = PieceCode.typeOf(code) ?: return ""
    val white = code > 0
    return when (type) {
        PieceType.KING -> if (white) "\u2654" else "\u265A"
        PieceType.QUEEN -> if (white) "\u2655" else "\u265B"
        PieceType.ROOK -> if (white) "\u2656" else "\u265C"
        PieceType.BISHOP -> if (white) "\u2657" else "\u265D"
        PieceType.KNIGHT -> if (white) "\u2658" else "\u265E"
        PieceType.PAWN -> if (white) "\u2659" else "\u265F"
    }
}

private fun DrawScope.drawPiece(measurer: TextMeasurer, code: Int, center: Offset, sq: Float) {
    val glyph = glyphFor(code)
    if (glyph.isEmpty()) return
    val fontSize = (sq * PieceScale).toSp()
    if (code > 0) {
        // White: light face with a dark edge so it reads on light wood.
        // An edge, not a shadow — same centre, one pixel out.
        val edge = measurer.measure(
            glyph,
            style = TextStyle(color = ChessKraftColors.PieceBlack, fontSize = fontSize),
        )
        val face = measurer.measure(
            glyph,
            style = TextStyle(color = ChessKraftColors.PieceWhite, fontSize = fontSize),
        )
        val edgeStep = (sq * EdgeStep).coerceAtLeast(1f)
        for ((dx, dy) in EDGE_OFFSETS) {
            drawText(
                edge,
                topLeft = Offset(
                    center.x - edge.size.width / 2f + dx * edgeStep,
                    center.y - edge.size.height / 2f + dy * edgeStep,
                ),
            )
        }
        drawText(
            face,
            topLeft = Offset(center.x - face.size.width / 2f, center.y - face.size.height / 2f),
        )
    } else {
        val layout = measurer.measure(
            glyph,
            style = TextStyle(color = ChessKraftColors.PieceBlack, fontSize = fontSize),
        )
        drawText(
            layout,
            topLeft = Offset(center.x - layout.size.width / 2f, center.y - layout.size.height / 2f),
        )
    }
}

private val EDGE_OFFSETS = listOf(
    1f to 0f, -1f to 0f, 0f to 1f, 0f to -1f,
)

private const val PieceScale = 0.72f
private const val EdgeStep = 0.02f
private const val DotRadius = 0.14f
private const val DotAlpha = 0.85f
private const val CaptureRingRadius = 0.42f
private const val SelectedRingRadius = 0.44f
private const val CheckRingRadius = 0.44f
private const val RingWidth = 0.055f
private const val LastMoveAlpha = 0.32f
private const val HintAlpha = 0.22f
private const val CheckWashAlpha = 0.45f
private const val CoordPad = 0.06f
private const val CaptureFlashMax = 0.9f
