/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 Kedhar Sairam
 *
 * Piece art: the Cburnett set (Colin M.L. Burnett), bundled as 500px PNGs
 * under res/drawable-nodpi. License: 3-clause BSD (dual-licensed by the
 * author; BSD selected). Copyright retained in NOTICE at the repo root;
 * credit in README. Our code draws them; the art is his.
 *
 * Why bundled art instead of our own vectors: our first hand-drawn set read
 * as blobby at 41dp. Cburnett is the Wikipedia/Lichess standard — thick
 * outlines, flat fills, proven at small sizes. 136KB for twelve pieces.
 */
package com.krafttools.chesskraft.ui.board

import android.content.Context
import android.graphics.BitmapFactory
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.platform.LocalContext
import com.krafttools.chesskraft.R
import kotlin.math.roundToInt
import com.krafttools.chesskraft.domain.PieceCode
import com.krafttools.chesskraft.domain.PieceType

/** Decoded once per process, shared by the board and every small mark. */
object PieceArt {
    @Volatile
    private var cache: Map<Int, ImageBitmap>? = null

    fun of(context: Context): Map<Int, ImageBitmap> =
        cache ?: synchronized(this) {
            cache ?: build(context).also { cache = it }
        }

    private fun build(context: Context): Map<Int, ImageBitmap> {
        val out = HashMap<Int, ImageBitmap>(12)
        for (type in PieceType.entries) {
            for (white in listOf(true, false)) {
                val code = PieceCode.of(if (white) com.krafttools.chesskraft.domain.Side.WHITE else com.krafttools.chesskraft.domain.Side.BLACK, type)
                val bmp = BitmapFactory.decodeResource(context.resources, resFor(code))
                if (bmp != null) out[code] = bmp.asImageBitmap()
            }
        }
        return out
    }

    fun resFor(code: Int): Int {
        val type = PieceCode.typeOf(code) ?: return 0
        val white = code > 0
        return when (type) {
            PieceType.KING -> if (white) R.drawable.piece_klt else R.drawable.piece_kdt
            PieceType.QUEEN -> if (white) R.drawable.piece_qlt else R.drawable.piece_qdt
            PieceType.ROOK -> if (white) R.drawable.piece_rlt else R.drawable.piece_rdt
            PieceType.BISHOP -> if (white) R.drawable.piece_blt else R.drawable.piece_bdt
            PieceType.KNIGHT -> if (white) R.drawable.piece_nlt else R.drawable.piece_ndt
            PieceType.PAWN -> if (white) R.drawable.piece_plt else R.drawable.piece_pdt
        }
    }
}

/**
 * One piece, anywhere a piece needs to be small: captured strips, promotion
 * options, headers, avatars.
 */
@Composable
fun PieceMark(
    code: Int,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val art = remember { PieceArt.of(context) }
    val bitmap = art[code]
    if (bitmap != null) {
        Canvas(modifier = modifier) {
            val side = size.minDimension
            drawImage(
                image = bitmap,
                dstOffset = intOffsetOf(size, side),
                dstSize = intSizeOf(side),
            )
        }
    }
}

private fun intOffsetOf(size: Size, side: Float) = IntOffset(
    ((size.width - side) / 2f).roundToInt(),
    ((size.height - side) / 2f).roundToInt(),
)

private fun intSizeOf(side: Float) = IntSize(side.roundToInt(), side.roundToInt())

/** Board entry: draws the art bitmap centered on [center]. */
fun DrawScope.drawArtPiece(art: Map<Int, ImageBitmap>, code: Int, center: Offset, sidePx: Float) {
    val bitmap = art[code] ?: return
    // Cburnett's 45-box carries its own margins; fill the square.
    val side = sidePx * PieceArtScale
    drawImage(
        image = bitmap,
        dstOffset = IntOffset(
            (center.x - side / 2f).roundToInt(),
            (center.y - side / 2f).roundToInt(),
        ),
        dstSize = IntSize(side.roundToInt(), side.roundToInt()),
    )
}

private const val PieceArtScale = 0.98f
