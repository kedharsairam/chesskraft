/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 Kedhar Sairam
 */
package com.krafttools.chesskraft.ui.board

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.awaitTouchSlopOrCancellation
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
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
import com.kraft.ui.tokens.KraftRadius
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
import kotlin.math.hypot

/**
 * Piece glyph layouts, cached and keyed on the square size in pixels: a text
 * layout only depends on (glyph, font size), so after the first frame the draw
 * pass measures nothing and allocates nothing.
 */
/**
 * No piece cache: the six vector silhouettes are tiny (under two dozen verbs
 * each) and drawPath tessellation at 32 pieces holds 60fps on budget phones.
 * Caching would buy nothing and would key on glyph+size buckets forever.
 */

/**
 * The board. One Compose [Canvas] — a single draw pass — plus 64
 * layout-only semantics nodes (no drawing, no touch handling) so TalkBack
 * can visit every square ("e4, white knight") while gestures stay on the
 * Canvas with 8dp miss tolerance.
 *
 * The board is sized by the smaller of its incoming constraints, so in
 * landscape it fits the height instead of overflowing it. Drag state
 * ([dragFrom]/[dragPos]) lives here, split from the board snapshot, so a
 * finger never mutates what the draw pass reads — and it is read *only* there,
 * inside the Canvas lambda, which invalidates the draw and not the composition.
 * One state write per drop ([dropToken]) is the cost of learning whether a drop
 * was legal; the finger position costs none.
 */
@Composable
fun ChessBoard(
    state: GameUiState,
    onTap: (Int) -> Unit,
    onDrop: (Int, Int) -> Unit,
    modifier: Modifier = Modifier,
    interactive: Boolean = true,
    /**
     * Whether the last move is drawn as an arrow as well as the two-square
     * wash. Defaults on, so every existing caller is unchanged; pass false to
     * clear the felt for reading a position. Deliberately a plain parameter
     * rather than a field on the state this board does not own: whoever owns
     * `GameUiState` can wire a top-bar toggle through here without the board
     * growing a dependency on a settings holder.
     */
    showArrows: Boolean = true,
) {
    var boardPx by remember { mutableStateOf(0f) }
    var dragFrom by remember { mutableStateOf<Int?>(null) }
    var dragPos by remember { mutableStateOf<Offset?>(null) }
    // Bumped once per drop. The only drag state the composition observes, and
    // only as a LaunchedEffect key — never per finger pixel.
    var dropToken by remember { mutableIntStateOf(0) }
    val density = LocalDensity.current
    val slopPx = remember(density) {
        with(density) { KraftSpacing.Spacing8.toPx() }
    }
    val reduceMotion = rememberReduceMotion()
    val measurer = rememberTextMeasurer()
    val context = LocalContext.current
    val art = remember { PieceArt.of(context) }
    val motion = rememberBoardMotion(reduceMotion)

    val snapshot = remember(state.pieces, state.sideToMove) {
        Position.fromSnapshot(state.pieces, state.sideToMove)
    }
    val errorColor = MaterialTheme.colorScheme.error
    // Coordinates: inside the square corners, Caption1 (12sp — inside the
    // 9–13sp band), per-square contrasting tone, no chips or backplates.
    val coordStyle = MaterialTheme.typography.labelMedium
    val coordOnLight = remember(coordStyle) {
        coordStyle.copy(color = ChessKraftColors.CoordOnLight)
    }
    val coordOnDark = remember(coordStyle) {
        coordStyle.copy(color = ChessKraftColors.CoordOnDark)
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
                // Tournament frame: rounded felt with a brass hairline, floating
                // on the black chrome. The frame is drawn, not a shadow — depth
                // from outline on true black, per the sibling lesson.
                .clip(RoundedCornerShape(KraftRadius.Medium))
                .border(
                    // A hairline, not a gold frame: at 2dp the border became the
                    // loudest thing on screen and the felt stopped being the
                    // subject. The board is defined by its own edge now.
                    KraftSpacing.BorderWidth,
                    ChessKraftColors.HairlineSoft,
                    RoundedCornerShape(KraftRadius.Medium),
                )
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
                        // One gesture handler, not two. detectTapGestures and
                        // detectDragGestures on the same surface compete for the
                        // same stream, and the drag start was resolving a
                        // different square than the tap did — a drag from e2
                        // picked up e3, an empty one, so the piece vanished from
                        // the board and never followed the finger. Verified on
                        // the device with a logged square index, not by reading.
                        awaitEachGesture {
                            val down = awaitFirstDown(requireUnconsumed = false)
                            // The pickup square comes from the DOWN event, never
                            // from the position where the drag was recognised:
                            // those differ, and using the later one made a drag
                            // pick up the square below the finger's origin.
                            val dragStart = BoardGeometry.hitTest(
                                down.position.x,
                                down.position.y,
                                boardPx,
                                state.flipped,
                                slopPx,
                            )
                            val slop = awaitTouchSlopOrCancellation(down.id) { change, _ ->
                                motion.endReturn()
                                dragFrom = dragStart
                                dragPos = change.position
                            }
                            if (slop != null) {
                                // Keep the piece under the finger until lift.
                                while (true) {
                                    val event = awaitPointerEvent()
                                    val change = event.changes.firstOrNull { it.id == down.id }
                                    if (change == null || !change.pressed) break
                                    dragPos = change.position
                                    change.consume()
                                }
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
                                if (from != null) {
                                    // The piece stays where the finger left it
                                    // either way; whether it stays or flies home
                                    // is the position's call, not ours.
                                    motion.armReturn(
                                        from,
                                        end?.x ?: 0f,
                                        end?.y ?: 0f,
                                    )
                                }
                                dropToken++
                            } else {
                                // No slop crossed: a tap, on the square under the
                                // finger at press time.
                                if (dragStart != null) onTap(dragStart)
                            }
                        }
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
                    art = art,
                    targetSquares = targetSquares,
                    targetCaptures = targetCaptures,
                    slopPx = slopPx,
                    dragFrom = dragFrom,
                    dragPos = dragPos,
                    motion = motion,
                    showArrows = showArrows,
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

    // The drop verdict. The board does not own legality and does not guess at
    // it: the domain decides inside the ViewModel, and the only thing visible
    // from here is whether the origin square still holds its piece afterwards.
    // Empty means the move landed and the board owns the piece; still occupied
    // means the drop was refused and the piece flies home.
    //
    // Keyed on the position as well as the drop, so a landed move cancels the
    // flight on the first frame the board change reaches the composition.
    LaunchedEffect(dropToken, state.pieces) {
        val square = motion.returnSquare ?: return@LaunchedEffect
        // One frame of grace. The drop's state write lands *after* this
        // pointer callback returns, so the position read here can still be the
        // pre-drop one — and a legal drop must not flash a piece flying home.
        withFrameNanos { }
        if (state.pieces[square] != 0) motion.snapBack()
        motion.endReturn()
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
    art: Map<Int, ImageBitmap>,
    targetSquares: IntArray,
    targetCaptures: BooleanArray,
    slopPx: Float,
    dragFrom: Int?,
    dragPos: Offset?,
    motion: BoardMotion,
    showArrows: Boolean,
) {
    val boardPx = size.width
    val sq = BoardGeometry.squareSize(boardPx)
    val light = ChessKraftColors.LightSquare
    val dark = ChessKraftColors.DarkSquare
    val accent = ChessKraftColors.FeltGold
    val error = errorColor
    // Motion is read here, inside the Canvas lambda, so every animated value
    // invalidates the draw and none of them recomposes this screen. Flattened
    // into locals so the drawing below reads as plain numbers.
    val slideProgress = motion.slideProgress
    val captureAlpha = motion.captureAlpha
    val selectPulse = motion.selectPulse
    val returnSquare = motion.returnSquare
    val returnProgress = motion.returnProgress
    // A refused drop: the piece is drawn by the flight in step 11, not here.
    val flying = returnProgress < 1f && returnSquare != null

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

    // 7. Drag target: the square under the finger, outlined and faintly
    // washed, so the board says where the piece would land before you let go.
    // Resolved with the same hitTest and the same slop the pointer input drops
    // with — the outline never promises a square the drop would refuse, and it
    // clears itself the moment the drag does, because dragPos goes with it.
    // Gold, like every other marker on the felt: the app's green accent is the
    // dark square's own colour and would vanish into it.
    val dragOver = dragPos?.let {
        BoardGeometry.hitTest(it.x, it.y, boardPx, state.flipped, slopPx)
    }
    if (dragOver != null) {
        val (col, row) = BoardGeometry.displayCell(dragOver, state.flipped)
        val topLeft = Offset(col * sq, row * sq)
        val square = Size(sq, sq)
        drawRect(color = accent.copy(alpha = DragTargetWashAlpha), topLeft = topLeft, size = square)
        drawRect(
            color = accent,
            topLeft = topLeft,
            size = square,
            style = Stroke(width = DragTargetStroke.toPx()),
        )
    }

    // 8. Last-move arrow. A thin tapered shaft with a small head at the
    // destination, both ends trimmed to stay inside the two squares the move
    // touched — the head stops at the edge of the piece's own footprint rather
    // than running under it, because a head hidden by the piece it points at is
    // not an arrow. Drawn here, between the markers and the pieces: over the
    // squares and washes, under every piece. Gold for the reason the drag target
    // is gold — the app's green is the dark square's own colour.
    val arrowAlpha = motion.arrowAlpha
    if (showArrows && arrowAlpha > 0f && lastFrom != null && lastTo != null) {
        drawLastMoveArrow(
            from = cellCenter(lastFrom, boardPx, state.flipped),
            to = cellCenter(lastTo, boardPx, state.flipped),
            sq = sq,
            alpha = arrowAlpha,
            color = accent,
        )
    }

    // 9. Coordinates inside the corners, in the opposite square colour so
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

    // 10. Pieces, cached per (glyph, square size). The sliding piece is drawn
    // at its lerped position; its landing square is skipped until it lands.
    val slidingPiece = if (slideProgress < 1f && lastFrom != null && lastTo != null) {
        state.pieces[lastTo]
    } else {
        0
    }
    for (square in 0..63) {
        if (square == lastTo && slidingPiece != 0) continue
        if (square == dragFrom && dragPos != null) continue
        // The one piece in flight owns itself until it lands.
        if (flying && square == returnSquare) continue
        val code = state.pieces[square]
        if (code == 0) continue
        drawArtPiece(art, code, cellCenter(square, boardPx, state.flipped), sq)
    }
    if (slidingPiece != 0 && lastFrom != null && lastTo != null) {
        val fromCenter = cellCenter(lastFrom, boardPx, state.flipped)
        val toCenter = cellCenter(lastTo, boardPx, state.flipped)
        val at = Offset(
            fromCenter.x + (toCenter.x - fromCenter.x) * slideProgress,
            fromCenter.y + (toCenter.y - fromCenter.y) * slideProgress,
        )
        drawArtPiece(art, slidingPiece, at, sq)
    }

    // 11. The piece under the finger. Drawn last so it floats above everything,
    // and centred half a square up and left of the touch point: a thumb covers
    // the origin it drags from, so the piece has to sit clear of it. Scaled and
    // outlined so it reads as picked up rather than painted on — a lift, not a
    // drop shadow: no blur, no second pass, nothing a budget phone has to pay for.
    if (dragFrom != null && dragPos != null) {
        val code = state.pieces[dragFrom]

        if (code != 0) {
            drawLiftedPiece(
                art = art,
                code = code,
                center = Offset(dragPos.x - sq / 2f, dragPos.y - sq / 2f),
                box = sq * DragLiftScale,
            )
        }
    }

    // 12. A refused drop: the piece carries itself home over 150ms instead of
    // blinking out where the finger left it. Starts from the same offset the
    // drag ended at, so the handover is invisible, and settles as it goes —
    // the lift unwinds into the square it came from.
    if (flying && returnSquare != null) {
        val code = state.pieces[returnSquare]
        if (code != 0) {
            val home = cellCenter(returnSquare, boardPx, state.flipped)
            val start = Offset(motion.returnX - sq / 2f, motion.returnY - sq / 2f)
            drawLiftedPiece(
                art = art,
                code = code,
                center = Offset(
                    start.x + (home.x - start.x) * returnProgress,
                    start.y + (home.y - start.y) * returnProgress,
                ),
                box = sq * (1f + (DragLiftScale - 1f) * (1f - returnProgress)),
            )
        }
    }
}

/**
 * The last move, as an arrow: a tapered shaft from the centre of [from] to the
 * centre of [to], with a small head at the destination.
 *
 * Both ends are pulled back along the direction of travel ([ArrowStartInset]
 * and [ArrowTipInset] of a square) so nothing runs past the origin or the
 * destination, and the head length is capped against the shaft that is left, so
 * a one-square move gets a short stub of shaft and a proportionally smaller head
 * rather than a head with no shaft under it. Widths are fractions of [sq] like
 * every other marker here, so an arrow on a tablet board is the same drawing as
 * one on a phone board.
 *
 * One inline [Path] per draw: the shape is seven points, so caching it against a
 * size bucket would cost more bookkeeping than rebuilding it. The caller draws
 * this under the pieces, which is what keeps a piece sitting on top of its own
 * arrow.
 */
private fun DrawScope.drawLastMoveArrow(
    from: Offset,
    to: Offset,
    sq: Float,
    alpha: Float,
    color: androidx.compose.ui.graphics.Color,
) {
    val dx = to.x - from.x
    val dy = to.y - from.y
    val length = hypot(dx, dy)
    // hypot is non-negative by contract, so test the components: this is the
    // divide-by-zero guard for a null move (from == to) reaching here.
    if (dx == 0f && dy == 0f) return
    val ux = dx / length
    val uy = dy / length
    // Perpendicular, for the taper: half a turn off the direction of travel.
    val px = -uy
    val py = ux

    val startInset = minOf(sq * ArrowStartInset, length * ArrowInsetCap)
    val tipInset = sq * ArrowTipInset
    val tip = Offset(to.x - ux * tipInset, to.y - uy * tipInset)
    val shaftSpan = (length - startInset - tipInset).coerceAtLeast(0f)
    // Head length, whole (flare plus triangle), at most a bit over half of what
    // is left: a one-square move must keep a visible stub of shaft, or the head
    // is all that is drawn and it reads as a blob on the destination square.
    val head = minOf(sq * ArrowHeadLength, shaftSpan * ArrowHeadSpanCap)
    val flare = head * ArrowHeadFlareShare
    val baseHalf = sq * ArrowHeadHalf
    val neckHalf = sq * ArrowNeckHalf
    // Base of the head, then the neck just behind it: the flare from neck width
    // to head width is what makes the head read as a head rather than a point.
    val base = Offset(tip.x - ux * head, tip.y - uy * head)
    val neck = Offset(tip.x - ux * (head + flare), tip.y - uy * (head + flare))
    val tailHalf = sq * ArrowTailHalf
    val tail = Offset(
        from.x + ux * startInset,
        from.y + uy * startInset,
    )
    val paint = color.copy(alpha = alpha * ArrowAlpha)

    // Seven points, one closed path: a shaft fat at the tail narrowing to the
    // neck, flaring out to the head's base, and a triangle to the tip. Taper and
    // head together are what separate an arrow from a line; a plain stroke at one
    // width reads as a scratch on the felt.
    val path = Path().apply {
        moveTo(tail.x + px * tailHalf, tail.y + py * tailHalf)
        lineTo(neck.x + px * neckHalf, neck.y + py * neckHalf)
        lineTo(base.x + px * baseHalf, base.y + py * baseHalf)
        lineTo(tip.x, tip.y)
        lineTo(base.x - px * baseHalf, base.y - py * baseHalf)
        lineTo(neck.x - px * neckHalf, neck.y - py * neckHalf)
        lineTo(tail.x - px * tailHalf, tail.y - py * tailHalf)
        close()
    }
    drawPath(path, paint)
}

/**
 * A piece in hand: the art plus a hairline gold frame, both sized to [box].
 * The outline sits a hair outside the Cburnett art rather than on its edge —
 * an outline *on* the edge would bite into the fill.
 */
private fun DrawScope.drawLiftedPiece(
    art: Map<Int, ImageBitmap>,
    code: Int,
    center: Offset,
    box: Float,
) {
    drawArtPiece(art, code, center, box)
    val half = box / 2f
    drawRect(
        color = ChessKraftColors.FeltGold,
        topLeft = Offset(center.x - half, center.y - half),
        size = Size(box, box),
        style = Stroke(width = box * PieceOutlineWidth),
    )
}

private fun cellCenter(square: Int, boardPx: Float, flipped: Boolean): Offset {
    val point = BoardGeometry.squareCenter(square, boardPx, flipped)
    return Offset(point.x, point.y)
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
private const val LastMoveAlpha = 0.30f
private const val SelectedWashAlpha = 0.50f
private const val PulseSpread = 0.10f
private const val PulseMaxAlpha = 0.7f
private const val HintAlpha = 0.18f
private const val CoordPad = 0.07f
private const val CaptureFlashMax = 0.9f

// Last-move arrow. Every number is a fraction of the square, the same convention
// as CoordPad and RingWidth above: the arrow is a mark on the felt, so it should
// scale with the felt and not with the device's density.
private const val ArrowAlpha = 0.85f
/**
 * Tail pull-back from the origin centre, and head pull-back from the
 * destination centre. Both keep the arrow inside the two squares it spans: the
 * origin piece's own footprint is 0.44 of a square, so stopping short of it
 * leaves a readable gap instead of a stub under a piece.
 */
private const val ArrowStartInset = 0.30f
private const val ArrowTipInset = 0.34f

/**
 * Ceiling on [ArrowStartInset] for very short moves (the two centres can be
 * closer than the inset on a rotated board or a tight layout): at most a third
 * of the span, so a short arrow is short rather than inverted.
 */
private const val ArrowInsetCap = 0.34f

/**
 * Head length along the direction of travel (base to tip) and the share of the
 * remaining shaft it may take on a short move. 0.26 is what stays clear of a
 * knight's move: the head has to be long enough to see at all at 135px squares.
 */
private const val ArrowHeadLength = 0.26f
private const val ArrowHeadSpanCap = 0.55f

/** How much of the head sits behind the base as the flare out of the neck. */
private const val ArrowHeadFlareShare = 0.28f

/**
 * Half-widths. The head is deliberately three times the neck: the taper into a
 * thin neck and out again into a wide base is the whole visual difference
 * between an arrow and a tapered line, and at these sizes a head as narrow as the
 * shaft is invisible.
 */
private const val ArrowHeadHalf = 0.075f
private const val ArrowNeckHalf = 0.025f
private const val ArrowTailHalf = 0.042f

// Drag feel. Ratios of the square, not dp: a lift is a component metric (how
// far the piece comes off the felt), and a ratio keeps it identical on a phone
// board and a tablet one.
private const val DragLiftScale = 1.08f
/** Hairline around a lifted piece, as a fraction of its box. */
private const val PieceOutlineWidth = 0.035f
private const val DragTargetWashAlpha = 0.15f
/** 2dp — the drag target outline, matching the weight of the target rings. */
private val DragTargetStroke = KraftSpacing.Spacing2
