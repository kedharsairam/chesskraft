/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 Kedhar Sairam
 *
 * Pseudo-legal move generation over the 0x88 board, written per piece.
 * Every move the generator emits is verified by make/unmake plus a king
 * safety check before it counts as legal, which is also what makes the
 * en-passant pin fall out with no special case: the pin is discovered
 * by trying the capture and seeing the king attacked.
 *
 * Castling needs explicit work: rights bits, empty transit squares, and
 * the king's path (including its starting square) unattacked.
 */
package com.krafttools.chesskraft.engine

object MoveGen {
    const val BUFFER_SIZE = 256

    fun generateAll(pos: Position, buf: IntArray, start: Int): Int {
        var n = start
        val side = pos.side
        for (sq in 0 until 128) {
            if (!onBoard(sq)) continue
            val p = pos.board[sq]
            if (p == EMPTY || pieceColor(p) != side) continue
            n = when (pieceType(p)) {
                PAWN -> genPawn(pos, sq, p, buf, n, false)
                KNIGHT -> genSteps(pos, sq, p, Position.KNIGHT_STEPS, buf, n)
                BISHOP -> genSlides(pos, sq, p, Position.DIAG, buf, n)
                ROOK -> genSlides(pos, sq, p, Position.ORTHO, buf, n)
                QUEEN -> {
                    var k = genSlides(pos, sq, p, Position.ORTHO, buf, n)
                    k = genSlides(pos, sq, p, Position.DIAG, buf, k)
                    k
                }
                else -> genSteps(pos, sq, p, Position.KING_STEPS, buf, n)
            }
        }
        n = genCastles(pos, buf, n)
        return n
    }

    fun generateCaptures(pos: Position, buf: IntArray, start: Int): Int {
        var n = start
        val side = pos.side
        for (sq in 0 until 128) {
            if (!onBoard(sq)) continue
            val p = pos.board[sq]
            if (p == EMPTY || pieceColor(p) != side) continue
            n = when (pieceType(p)) {
                PAWN -> genPawn(pos, sq, p, buf, n, true)
                KNIGHT -> genCaptureSteps(pos, sq, p, Position.KNIGHT_STEPS, buf, n)
                BISHOP -> genCaptureSlides(pos, sq, p, Position.DIAG, buf, n)
                ROOK -> genCaptureSlides(pos, sq, p, Position.ORTHO, buf, n)
                QUEEN -> {
                    var k = genCaptureSlides(pos, sq, p, Position.ORTHO, buf, n)
                    k = genCaptureSlides(pos, sq, p, Position.DIAG, buf, k)
                    k
                }
                else -> genCaptureSteps(pos, sq, p, Position.KING_STEPS, buf, n)
            }
        }
        return n
    }

    private fun genPawn(pos: Position, sq: Int, p: Int, buf: IntArray, start: Int, capturesOnly: Boolean): Int {
        var n = start
        val white = pieceColor(p) == WHITE
        val foe = if (white) BLACK else WHITE
        val up = if (white) 16 else -16
        val homeRank = if (white) 1 else 6
        val promoRank = if (white) 7 else 0
        val one = sq + up
        if (!capturesOnly && onBoard(one) && pos.board[one] == EMPTY) {
            if (rankOf(one) == promoRank) {
                n = addPromos(buf, n, sq, one, p, NO_PIECE)
            } else {
                buf[n++] = makeMove(sq, one, p)
                val two = sq + 2 * up
                if (rankOf(sq) == homeRank && pos.board[two] == EMPTY) {
                    buf[n++] = withDoublePush(makeMove(sq, two, p))
                }
            }
        }
        val left = sq + up - 1
        val right = sq + up + 1
        for (to in intArrayOf(left, right)) {
            if (!onBoard(to)) continue
            val target = pos.board[to]
            if (target != EMPTY && pieceColor(target) == foe) {
                if (rankOf(to) == promoRank) {
                    n = addPromos(buf, n, sq, to, p, target)
                } else {
                    buf[n++] = makeMove(sq, to, p, target)
                }
            } else if (target == EMPTY && to == pos.epSquare) {
                val foePawn = makePiece(PAWN, foe)
                if (rankOf(to) == promoRank) {
                    n = addPromos(buf, n, sq, to, p, foePawn)
                } else {
                    buf[n++] = withEpCapture(makeMove(sq, to, p, foePawn))
                }
            }
        }
        return n
    }

    private fun addPromos(buf: IntArray, start: Int, from: Int, to: Int, pawn: Int, captured: Int): Int {
        var n = start
        val color = pieceColor(pawn)
        for (t in intArrayOf(KNIGHT, BISHOP, ROOK, QUEEN)) {
            buf[n++] = makeMove(from, to, pawn, captured, makePiece(t, color))
        }
        return n
    }

    private fun genSteps(
        pos: Position, sq: Int, p: Int, steps: IntArray, buf: IntArray, start: Int,
    ): Int {
        var n = start
        for (o in steps) {
            val to = sq + o
            if (!onBoard(to)) continue
            val target = pos.board[to]
            if (target == EMPTY) {
                buf[n++] = makeMove(sq, to, p)
            } else if (pieceColor(target) != pos.side) {
                buf[n++] = makeMove(sq, to, p, target)
            }
        }
        return n
    }

    private fun genCaptureSteps(
        pos: Position, sq: Int, p: Int, steps: IntArray, buf: IntArray, start: Int,
    ): Int {
        var n = start
        for (o in steps) {
            val to = sq + o
            if (!onBoard(to)) continue
            val target = pos.board[to]
            if (target != EMPTY && pieceColor(target) != pos.side) {
                buf[n++] = makeMove(sq, to, p, target)
            }
        }
        return n
    }

    private fun genSlides(
        pos: Position, sq: Int, p: Int, dirs: IntArray, buf: IntArray, start: Int,
    ): Int {
        var n = start
        for (d in dirs) {
            var to = sq + d
            while (onBoard(to)) {
                val target = pos.board[to]
                if (target == EMPTY) {
                    buf[n++] = makeMove(sq, to, p)
                } else {
                    if (pieceColor(target) != pos.side) {
                        buf[n++] = makeMove(sq, to, p, target)
                    }
                    break
                }
                to += d
            }
        }
        return n
    }

    private fun genCaptureSlides(
        pos: Position, sq: Int, p: Int, dirs: IntArray, buf: IntArray, start: Int,
    ): Int {
        var n = start
        for (d in dirs) {
            var to = sq + d
            while (onBoard(to)) {
                val target = pos.board[to]
                if (target != EMPTY) {
                    if (pieceColor(target) != pos.side) {
                        buf[n++] = makeMove(sq, to, p, target)
                    }
                    break
                }
                to += d
            }
        }
        return n
    }

    private fun genCastles(pos: Position, buf: IntArray, start: Int): Int {
        var n = start
        if (pos.side == WHITE) {
            val foe = BLACK
            if ((pos.castling and CASTLE_WK) != 0 &&
                pos.board[makeSquare(5, 0)] == EMPTY &&
                pos.board[makeSquare(6, 0)] == EMPTY &&
                pos.board[makeSquare(4, 0)] == WK &&
                pos.board[makeSquare(7, 0)] == WR &&
                !pos.isSquareAttacked(makeSquare(4, 0), foe) &&
                !pos.isSquareAttacked(makeSquare(5, 0), foe) &&
                !pos.isSquareAttacked(makeSquare(6, 0), foe)
            ) {
                buf[n++] = withCastle(makeMove(makeSquare(4, 0), makeSquare(6, 0), WK))
            }
            if ((pos.castling and CASTLE_WQ) != 0 &&
                pos.board[makeSquare(3, 0)] == EMPTY &&
                pos.board[makeSquare(2, 0)] == EMPTY &&
                pos.board[makeSquare(1, 0)] == EMPTY &&
                pos.board[makeSquare(4, 0)] == WK &&
                pos.board[makeSquare(0, 0)] == WR &&
                !pos.isSquareAttacked(makeSquare(4, 0), foe) &&
                !pos.isSquareAttacked(makeSquare(3, 0), foe) &&
                !pos.isSquareAttacked(makeSquare(2, 0), foe)
            ) {
                buf[n++] = withCastle(makeMove(makeSquare(4, 0), makeSquare(2, 0), WK))
            }
        } else {
            val foe = WHITE
            if ((pos.castling and CASTLE_BK) != 0 &&
                pos.board[makeSquare(5, 7)] == EMPTY &&
                pos.board[makeSquare(6, 7)] == EMPTY &&
                pos.board[makeSquare(4, 7)] == BK &&
                pos.board[makeSquare(7, 7)] == BR &&
                !pos.isSquareAttacked(makeSquare(4, 7), foe) &&
                !pos.isSquareAttacked(makeSquare(5, 7), foe) &&
                !pos.isSquareAttacked(makeSquare(6, 7), foe)
            ) {
                buf[n++] = withCastle(makeMove(makeSquare(4, 7), makeSquare(6, 7), BK))
            }
            if ((pos.castling and CASTLE_BQ) != 0 &&
                pos.board[makeSquare(3, 7)] == EMPTY &&
                pos.board[makeSquare(2, 7)] == EMPTY &&
                pos.board[makeSquare(1, 7)] == EMPTY &&
                pos.board[makeSquare(4, 7)] == BK &&
                pos.board[makeSquare(0, 7)] == BR &&
                !pos.isSquareAttacked(makeSquare(4, 7), foe) &&
                !pos.isSquareAttacked(makeSquare(3, 7), foe) &&
                !pos.isSquareAttacked(makeSquare(2, 7), foe)
            ) {
                buf[n++] = withCastle(makeMove(makeSquare(4, 7), makeSquare(2, 7), BK))
            }
        }
        return n
    }

    // ── Legal-move helpers ───────────────────────────────────────────

    /** Counts legal moves without allocating; stops after [limit]. */
    fun countLegal(pos: Position, limit: Int = Int.MAX_VALUE): Int {
        val buf = IntArray(BUFFER_SIZE)
        val n = generateAll(pos, buf, 0)
        var count = 0
        for (i in 0 until n) {
            if (pos.makeMove(buf[i])) count++
            pos.unmakeMove()
            if (count >= limit) break
        }
        return count
    }

    fun hasLegalMove(pos: Position): Boolean = countLegal(pos, 1) > 0

    fun isCheckmate(pos: Position): Boolean = pos.isInCheck(pos.side) && !hasLegalMove(pos)

    fun isStalemate(pos: Position): Boolean = !pos.isInCheck(pos.side) && !hasLegalMove(pos)

    /** Collects legal moves into [out], returning the count. */
    fun legalMoves(pos: Position, out: IntArray, start: Int = 0): Int {
        val buf = IntArray(BUFFER_SIZE)
        val n = generateAll(pos, buf, 0)
        var count = start
        for (i in 0 until n) {
            if (pos.makeMove(buf[i])) {
                out[count++] = buf[i]
            }
            pos.unmakeMove()
        }
        return count - start
    }

    // ── Perft ────────────────────────────────────────────────────────

    fun perft(pos: Position, depth: Int): Long {
        if (depth == 0) return 1L
        val buf = IntArray(BUFFER_SIZE)
        val n = generateAll(pos, buf, 0)
        var nodes = 0L
        for (i in 0 until n) {
            if (pos.makeMove(buf[i])) {
                nodes += perft(pos, depth - 1)
            }
            pos.unmakeMove()
        }
        return nodes
    }

    /** Per-move node counts from the root, for divide output and debugging. */
    fun divide(pos: Position, depth: Int): List<Pair<String, Long>> {
        val buf = IntArray(BUFFER_SIZE)
        val n = generateAll(pos, buf, 0)
        val out = ArrayList<Pair<String, Long>>(n)
        for (i in 0 until n) {
            if (pos.makeMove(buf[i])) {
                out.add(Pair(moveToUci(buf[i]), perft(pos, depth - 1)))
            }
            pos.unmakeMove()
        }
        return out
    }

    // ── UCI ──────────────────────────────────────────────────────────

    fun moveToUci(m: Int): String {
        val b = StringBuilder()
        b.append(squareName(moveFrom(m)))
        b.append(squareName(moveTo(m)))
        val promo = movePromo(m)
        if (promo != NO_PIECE) {
            b.append(
                when (pieceType(promo)) {
                    KNIGHT -> 'n'
                    BISHOP -> 'b'
                    ROOK -> 'r'
                    else -> 'q'
                },
            )
        }
        return b.toString()
    }

    /** Finds the legal move matching [uci], or NO_MOVE. */
    fun parseUci(pos: Position, uci: String): Int {
        if (uci.length < 4) return NO_MOVE
        val from = squareFromName(uci, 0)
        val to = squareFromName(uci, 2)
        if (!onBoard(from) || !onBoard(to)) return NO_MOVE
        val promoChar = if (uci.length > 4) uci[4] else 0.toChar()
        val buf = IntArray(BUFFER_SIZE)
        val n = generateAll(pos, buf, 0)
        for (i in 0 until n) {
            val m = buf[i]
            if (moveFrom(m) != from || moveTo(m) != to) continue
            if (!isPromotion(m) && promoChar == 0.toChar()) {
                if (pos.makeMove(m)) {
                    pos.unmakeMove()
                    return m
                }
                pos.unmakeMove()
            } else if (isPromotion(m) && promoChar != 0.toChar()) {
                val want = when (promoChar) {
                    'n' -> KNIGHT
                    'b' -> BISHOP
                    'r' -> ROOK
                    'q' -> QUEEN
                    else -> -1
                }
                if (pieceType(movePromo(m)) != want) continue
                if (pos.makeMove(m)) {
                    pos.unmakeMove()
                    return m
                }
                pos.unmakeMove()
            }
        }
        return NO_MOVE
    }
}
