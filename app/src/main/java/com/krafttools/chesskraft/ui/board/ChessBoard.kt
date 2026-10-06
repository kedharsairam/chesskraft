/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 Kedhar Sairam
 */
package com.krafttools.chesskraft.ui.board

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
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
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.TextUnit
import com.kraft.ui.motion.rememberReduceMotion
import com.kraft.ui.tokens.KraftSpacing
import com.krafttools.chesskraft.domain.BoardGeometry
import com.krafttools.chesskraft.domain.CoordLabel
import com.krafttools.chesskraft.domain.PieceCode
import com.krafttools.chesskraft.domain.PieceType
import com.krafttools.chesskraft.domain.Position
import com.krafttools.chesskraft.domain.describeSquare
import com.krafttools.chesskraft.presentation.announceText
import com.krafttools.chesskraft.presentation.GameUiState
import com.krafttools.chesskraft.ui.theme.ChessKraftColors

/**
 * Piece glyph layouts, cached and keyed on the square size in pixels: a text
 * layout only depends on (glyph, font size), so after the first frame the draw
 * pass measures nothing and allocates nothing.
 */
private class PieceLayoutCache {
    var bucket: Int = Int.MIN_VALUE
    val edge: HashMap<Int, TextLayoutResult> = HashMap(12)
    val face: HashMap<Int, TextLayoutResult> = HashMap(12)
}

/**
 * The board. One Compose [Canvas] — a single draw pass — plus 64
 * layout-only semantics nodes (no drawing, no touch handling) so TalkBack
 * can visit every square ("e4, white knight") while gestures stay on the
 * Canvas with 8dp miss tolerance.
 *
 * The board is sized by the smaller of its incoming constraints, so in
 * landscape it fits the height instead of overflowing it. Drag state
 * ([dragFrom]/[dragPos]) lives here, split from the board snapshot, so a
 * finger never mutates what the draw pass reads.
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
    // Coordinates: inside the square corners, Caption1 (12sp — inside the
    // 9–13sp band), per-square contrasting tone, no chips or backplates.
    val coordStyle = MaterialTheme.typography.labelMedium
    val coordOnLight = remember(coordStyle) {
        coordStyle.copy(color = ChessKraftColors.DarkSquare)
    }
    val coordOnDark = remember(coordStyle) {
        coordStyle.copy(color = ChessKraftColors.LightSquare)
    }
    val announcement = remember(state.pieces, state.statusText, state.sans) {
        announceText(snapshot, state.sans.lastOrNull(), state.result) + " " + state.statusText
    }

    // Derived snapshots, rebuilt only when their inputs change — the draw
    // pass below then iterates plain arrays and allocates nothing.
    val coordLabels = remember(state.flipped) { BoardGeometry.coordinateLabels(state.flipped) }
    val fontScale = density.fontScale
    val coordLayouts = remember(coordLabels, coordOnLight, coordOnDark, fontScale) {
        arrayOfNulls<TextLayoutResult>(coordLabels.size)
    }
    val pieceCache = remember(fontScale) { PieceLayoutCache() }
    val targetSquares = remember(state.targets) { state.targets.keys.toIntArray() }
    val targetCaptures = remember(state.targets, targetSquares) {
        BooleanArray(targetSquares.size) { i -> state.targets[targetSquares[i]] == true }
    }

    BoxWithConstraints(
        modifier = modifier.fillMaxWidth(),
        contentAlignment = Alignment.Center,
    ) {
        val side = minOf(maxWidth, maxHeight)
        Box(
            modifier = Modifier
                .size(side)
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
                    coordLabels = coordLabels,
                    coordLayouts = coordLayouts,
                    coordOnLight = coordOnLight,
                    coordOnDark = coordOnDark,
                    pieceCache = pieceCache,
                    targetSquares = targetSquares,
                    targetCaptures = targetCaptures,
                    dragFrom = dragFrom,
                    dragPos = dragPos,
                    slideProgress = motion.slideProgress,
                    captureAlpha = motion.captureAlpha,
                    selectPulse = motion.selectPulse,
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
    }

    LaunchedEffect(state.nudgeToken) {
        if (state.nudgeToken > 0) motion.nudge()
    }
    LaunchedEffect(state.lastMoveTo, state.pieces) {
        motion.onPositionChanged(state.pieces, state.lastMoveFrom, state.lastMoveTo)
    }
    LaunchedEffect(state.selected) {
        if (state.selected != null) motion.pulse()
    }
}

private fun DrawScope.drawBoard(
    state: GameUiState,
    measurer: TextMeasurer,
    errorColor: androidx.compose.ui.graphics.Color,
    coordLabels: List<CoordLabel>,
    coordLayouts: Array<TextLayoutResult?>,
    coordOnLight: TextStyle,
    coordOnDark: TextStyle,
    pieceCache: PieceLayoutCache,
    targetSquares: IntArray,
    targetCaptures: BooleanArray,
    dragFrom: Int?,
    dragPos: Offset?,
    slideProgress: Float,
    captureAlpha: Float,
    selectPulse: Float,
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

    // 2. Last-move warm wash. The moved piece visibly changed squares too,
    // so the state is never colour-only.
    val lastFrom = state.lastMoveFrom
    val lastTo = state.lastMoveTo
    if (lastFrom != null && lastTo != null) {
        val fromCell = BoardGeometry.displayCell(lastFrom, state.flipped)
        drawRect(
            color = accent.copy(alpha = LastMoveAlpha),
            topLeft = Offset(fromCell.first * sq, fromCell.second * sq),
            size = Size(sq, sq),
        )
        val toCell = BoardGeometry.displayCell(lastTo, state.flipped)
        drawRect(
            color = accent.copy(alpha = LastMoveAlpha),
            topLeft = Offset(toCell.first * sq, toCell.second * sq),
            size = Size(sq, sq),
        )
    }

    // 3. Hint wash.
    val hint = state.hintMove
    if (hint != null) {
        val hintFrom = BoardGeometry.displayCell(hint.from, state.flipped)
        drawRect(
            color = accent.copy(alpha = HintAlpha),
            topLeft = Offset(hintFrom.first * sq, hintFrom.second * sq),
            size = Size(sq, sq),
        )
        val hintTo = BoardGeometry.displayCell(hint.to, state.flipped)
        drawRect(
            color = accent.copy(alpha = HintAlpha),
            topLeft = Offset(hintTo.first * sq, hintTo.second * sq),
            size = Size(sq, sq),
        )
    }

    // 4. Check: a red radial burst — filled core, ring, and four diagonal
    // rays. Round where every other marker is square, so it reads in
    // grayscale too. Red is reserved for check; nothing else uses it.
    val check = state.checkSquare
    if (check != null) {
        val center = cellCenter(check, boardPx, state.flipped)
        drawCircle(
            color = error.copy(alpha = CheckCoreAlpha),
            radius = sq * CheckCoreRadius,
            center = center,
        )
        drawCircle(
            color = error,
            radius = sq * CheckRingRadius,
            center = center,
            style = Stroke(width = sq * RingWidth),
        )
        val rayInner = sq * CheckRayInner
        val rayOuter = sq * CheckRayOuter
        val dIn = rayInner * Diagonal
        val dOut = rayOuter * Diagonal
        drawLine(error, Offset(center.x - dOut, center.y - dOut), Offset(center.x - dIn, center.y - dIn), strokeWidth = sq * RingWidth)
        drawLine(error, Offset(center.x + dIn, center.y - dIn), Offset(center.x + dOut, center.y - dOut), strokeWidth = sq * RingWidth)
        drawLine(error, Offset(center.x - dOut, center.y + dOut), Offset(center.x - dIn, center.y + dIn), strokeWidth = sq * RingWidth)
        drawLine(error, Offset(center.x + dIn, center.y + dIn), Offset(center.x + dOut, center.y + dOut), strokeWidth = sq * RingWidth)
    }

    // 5. Selection: deep wash plus a ring (wash and shape together), legal
    // targets as filled dots vs hollow capture rings — the two differ by
    // shape in grayscale too. The one-shot pulse draws a single expanding
    // ring; there are no looping animations.
    val selected = state.selected
    if (selected != null) {
        val selectedCell = BoardGeometry.displayCell(selected, state.flipped)
        drawRect(
            color = accent.copy(alpha = SelectedWashAlpha),
            topLeft = Offset(selectedCell.first * sq, selectedCell.second * sq),
            size = Size(sq, sq),
        )
        val center = cellCenter(selected, boardPx, state.flipped)
        drawCircle(
            color = accent,
            radius = sq * SelectedRingRadius,
            center = center,
            style = Stroke(width = sq * RingWidth),
        )
        if (selectPulse > 0f) {
            drawCircle(
                color = accent.copy(alpha = selectPulse * PulseMaxAlpha),
                radius = sq * (SelectedRingRadius + selectPulse * PulseSpread),
                center = center,
                style = Stroke(width = sq * RingWidth),
            )
        }
        for (i in targetSquares.indices) {
            val target = targetSquares[i]
            val targetCenter = cellCenter(target, boardPx, state.flipped)
            if (targetCaptures[i]) {
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
    // they read on light and dark wood alike. Layouts are cached per label;
    // steady state measures nothing.
    for (i in coordLabels.indices) {
        val label = coordLabels[i]
        val (col, row) = BoardGeometry.displayCell(label.square, state.flipped)
        val squareIsLight = (col + row) % 2 == 0
        var layout = coordLayouts[i]
        if (layout == null) {
            layout = measurer.measure(
                label.text,
                style = if (squareIsLight) coordOnLight else coordOnDark,
            )
            coordLayouts[i] = layout
        }
        val pad = sq * CoordPad
        val topLeft = if (label.isFile) {
            Offset((col + 1) * sq - layout.size.width - pad, (row + 1) * sq - layout.size.height - pad)
        } else {
            Offset(col * sq + pad, row * sq + pad)
        }
        drawText(layout, topLeft = topLeft)
    }

    // 8. Pieces, cached per (glyph, square size). The sliding piece is drawn
    // at its lerped position; its landing square is skipped until it lands.
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
        drawPiece(measurer, pieceCache, code, cellCenter(square, boardPx, state.flipped), sq)
    }
    if (slidingPiece != 0 && lastFrom != null && lastTo != null) {
        val fromCenter = cellCenter(lastFrom, boardPx, state.flipped)
        val toCenter = cellCenter(lastTo, boardPx, state.flipped)
        val at = Offset(
            fromCenter.x + (toCenter.x - fromCenter.x) * slideProgress,
            fromCenter.y + (toCenter.y - fromCenter.y) * slideProgress,
        )
        drawPiece(measurer, pieceCache, slidingPiece, at, sq)
    }
    // Dragged piece follows the finger, drawn last so it floats above.
    if (dragFrom != null && dragPos != null) {
        val code = state.pieces[dragFrom]
        if (code != 0) drawPiece(measurer, pieceCache, code, dragPos, sq)
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

private fun DrawScope.drawPiece(
    measurer: TextMeasurer,
    cache: PieceLayoutCache,
    code: Int,
    center: Offset,
    sq: Float,
) {
    val glyph = glyphFor(code)
    if (glyph.isEmpty()) return
    val bucket = (sq * BucketScale).toInt()
    if (cache.bucket != bucket) {
        cache.bucket = bucket
        cache.edge.clear()
        cache.face.clear()
    }
    // One TextStyle per size bucket exists only on a cache miss; steady state
    // reuses the measured layouts and allocates nothing.
    val fontSize: TextUnit = (sq * PieceScale).toSp()
    if (code > 0) {
        // White: light face with a thick dark edge so it reads on light wood.
        // An edge, not a shadow — same centre, one step out, four ways.
        var edge = cache.edge[code]
        if (edge == null) {
            edge = measurer.measure(
                glyph,
                style = TextStyle(color = ChessKraftColors.PieceBlack, fontSize = fontSize),
            )
            cache.edge[code] = edge
        }
        var face = cache.face[code]
        if (face == null) {
            face = measurer.measure(
                glyph,
                style = TextStyle(color = ChessKraftColors.PieceWhite, fontSize = fontSize),
            )
            cache.face[code] = face
        }
        val edgeStep = (sq * EdgeStep).coerceAtLeast(1f)
        val baseX = center.x - edge.size.width / 2f
        val baseY = center.y - edge.size.height / 2f
        drawText(edge, topLeft = Offset(baseX + edgeStep, baseY))
        drawText(edge, topLeft = Offset(baseX - edgeStep, baseY))
        drawText(edge, topLeft = Offset(baseX, baseY + edgeStep))
        drawText(edge, topLeft = Offset(baseX, baseY - edgeStep))
        drawText(
            face,
            topLeft = Offset(center.x - face.size.width / 2f, center.y - face.size.height / 2f),
        )
    } else {
        var layout = cache.face[code]
        if (layout == null) {
            layout = measurer.measure(
                glyph,
                style = TextStyle(color = ChessKraftColors.PieceBlack, fontSize = fontSize),
            )
            cache.face[code] = layout
        }
        drawText(
            layout,
            topLeft = Offset(center.x - layout.size.width / 2f, center.y - layout.size.height / 2f),
        )
    }
}

private const val PieceScale = 0.78f
private const val BucketScale = 16f
private const val EdgeStep = 0.02f
private const val DotRadius = 0.19f
private const val DotAlpha = 0.85f
private const val CaptureRingRadius = 0.42f
private const val SelectedRingRadius = 0.44f
private const val CheckRingRadius = 0.44f
private const val CheckCoreRadius = 0.30f
private const val CheckCoreAlpha = 0.55f
private const val CheckRayInner = 0.36f
private const val CheckRayOuter = 0.47f
private const val Diagonal = 0.7071f
private const val RingWidth = 0.055f
private const val LastMoveAlpha = 0.41f
private const val SelectedWashAlpha = 0.50f
private const val PulseSpread = 0.10f
private const val PulseMaxAlpha = 0.7f
private const val HintAlpha = 0.22f
private const val CoordPad = 0.06f
private const val CaptureFlashMax = 0.9f
