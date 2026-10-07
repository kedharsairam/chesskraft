/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 Kedhar Sairam
 *
 * ChessKraft's own piece set: six Staunton-inspired silhouettes drawn as
 * vector paths in a 0..100 box (y down). Original geometry — no font, no
 * asset, no borrowed outline — so the pieces render identically on every
 * device instead of inheriting whatever chess glyphs the OEM shipped.
 *
 * Every piece shares one pedestal (trapezoid + bar + collar ring); only the
 * head differs. White fills cream with a cocoa edge; Black fills espresso
 * with a cream edge. The edge is drawn, never a shadow.
 */
package com.krafttools.chesskraft.ui.board

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.drawscope.withTransform
import com.krafttools.chesskraft.domain.PieceCode
import com.krafttools.chesskraft.domain.PieceType
import com.krafttools.chesskraft.ui.theme.ChessKraftColors

/** Shared pedestal every piece stands on: trapezoid + bar + collar ring. */
private fun Path.pedestal() {
    moveTo(36f, 76f)
    lineTo(64f, 76f)
    lineTo(70f, 88f)
    lineTo(30f, 88f)
    close()
    addRect(Rect(26f, 88f, 74f, 94f))
    addOval(Rect(38f, 70f, 62f, 77f))
}

private fun Path.pawnHead() {
    addOval(Rect(38f, 40f, 62f, 64f))
    moveTo(44f, 62f)
    lineTo(56f, 62f)
    lineTo(58f, 72f)
    lineTo(42f, 72f)
    close()
}

private fun Path.knightHead() {
    // Horse head in left profile: muzzle, forehead, pricked ear, arched
    // crest, jaw, and a neck that runs into the collar. Stylized planes,
    // unmistakably a horse.
    moveTo(28f, 54f) // muzzle front
    lineTo(34f, 36f) // face up
    lineTo(38f, 24f) // ear front
    lineTo(43f, 31f) // ear back
    lineTo(52f, 30f) // poll
    quadraticTo(64f, 32f, 68f, 44f) // crest arch
    quadraticTo(70f, 52f, 64f, 56f) // jaw curve
    lineTo(60f, 72f) // neck back down
    lineTo(44f, 72f) // neck front
    quadraticTo(42f, 62f, 36f, 60f) // throatlatch
    close()
}

private fun Path.bishopHead() {
    // Mitre: pointed dome with a diagonal slit, orb on top.
    moveTo(36f, 62f)
    quadraticTo(36f, 40f, 50f, 30f)
    quadraticTo(64f, 40f, 64f, 62f)
    close()
    addOval(Rect(46f, 20f, 54f, 28f))
}

private fun Path.rookHead() {
    // Crenellated crown: three merlons, stepped outline, tapered body.
    moveTo(34f, 44f)
    lineTo(34f, 32f)
    lineTo(41f, 32f)
    lineTo(41f, 37f)
    lineTo(47f, 37f)
    lineTo(47f, 32f)
    lineTo(53f, 32f)
    lineTo(53f, 37f)
    lineTo(59f, 37f)
    lineTo(59f, 32f)
    lineTo(66f, 32f)
    lineTo(66f, 44f)
    lineTo(61f, 56f)
    lineTo(59f, 72f)
    lineTo(41f, 72f)
    lineTo(39f, 56f)
    close()
}

private fun Path.queenHead() {
    // Five-point coronet with orbs, band, tapered body.
    moveTo(32f, 52f)
    lineTo(30f, 34f)
    lineTo(38f, 44f)
    lineTo(42f, 30f)
    lineTo(47f, 43f)
    lineTo(50f, 28f)
    lineTo(53f, 43f)
    lineTo(58f, 30f)
    lineTo(62f, 44f)
    lineTo(70f, 34f)
    lineTo(68f, 52f)
    lineTo(63f, 56f)
    lineTo(60f, 72f)
    lineTo(40f, 72f)
    lineTo(37f, 56f)
    close()
    for (x in listOf(30f, 42f, 50f, 58f, 70f)) {
        addOval(Rect(x - 3f, if (x == 50f) 22f else 28f, x + 3f, if (x == 50f) 28f else 34f))
    }
}

private fun Path.kingHead() {
    // Three-point crown with a cross at the apex, band, tapered body.
    moveTo(34f, 52f)
    lineTo(32f, 34f)
    lineTo(42f, 44f)
    lineTo(46f, 38f)
    lineTo(54f, 38f)
    lineTo(58f, 44f)
    lineTo(68f, 34f)
    lineTo(66f, 52f)
    lineTo(61f, 56f)
    lineTo(59f, 72f)
    lineTo(41f, 72f)
    lineTo(39f, 56f)
    close()
    addRect(Rect(48f, 18f, 52f, 34f))
    addRect(Rect(43f, 23f, 57f, 27f))
}

/** Silhouette path for a piece type, pedestal included. */
fun piecePath(type: PieceType): Path = Path().apply {
    when (type) {
        PieceType.PAWN -> pawnHead()
        PieceType.KNIGHT -> knightHead()
        PieceType.BISHOP -> bishopHead()
        PieceType.ROOK -> rookHead()
        PieceType.QUEEN -> queenHead()
        PieceType.KING -> kingHead()
    }
    pedestal()
}

private fun DrawScope.drawPieceAt(code: Int, center: Offset, sidePx: Float) {
    val type = PieceCode.typeOf(code) ?: return
    val white = code > 0
    val s = sidePx * PieceScale / PieceBox
    translate(center.x - 50f * s, center.y - 50f * s) {
        withTransform({ scale(s, s, Offset.Zero) }) {
            val path = piecePath(type)
            drawPath(
                path = path,
                color = if (white) ChessKraftColors.PieceWhite else ChessKraftColors.PieceBlack,
            )
            drawPath(
                path = path,
                color = if (white) {
                    ChessKraftColors.PieceEdgeDark
                } else {
                    ChessKraftColors.PieceEdgeLight
                },
                style = Stroke(width = PieceEdgeWidth),
            )
            if (type == PieceType.KNIGHT) {
                // The eye: punched in the contrasting ink so the head reads at 40dp.
                drawCircle(
                    color = if (white) {
                        ChessKraftColors.PieceEdgeDark
                    } else {
                        ChessKraftColors.PieceWhite
                    },
                    radius = PieceEyeRadius,
                    center = Offset(43f, 42f),
                )
            }
            if (type == PieceType.BISHOP) {
                // The mitre slit.
                drawLine(
                    color = if (white) {
                        ChessKraftColors.PieceEdgeDark
                    } else {
                        ChessKraftColors.PieceWhite
                    },
                    start = Offset(56f, 40f),
                    end = Offset(46f, 54f),
                    strokeWidth = PieceEdgeWidth,
                )
            }
        }
    }
}

/**
 * One piece, anywhere a piece needs to be small: captured strips, promotion
 * options, headers. Vector, not a font glyph.
 */
@Composable
fun PieceMark(
    code: Int,
    modifier: Modifier = Modifier,
) {
    Canvas(modifier = modifier) {
        drawPieceAt(code, Offset(size.width / 2f, size.height / 2f), size.minDimension)
    }
}

/** Draw-scope entry used by the board. */
fun DrawScope.drawVectorPiece(code: Int, center: Offset, sidePx: Float) {
    drawPieceAt(code, center, sidePx)
}

private const val PieceBox = 100f
private const val PieceEdgeWidth = 2.2f
private const val PieceEyeRadius = 2.6f
/** Piece art fills 92% of the square — inset, never edge-to-edge. */
private const val PieceScale = 0.92f
