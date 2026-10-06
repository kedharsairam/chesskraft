/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 Kedhar Sairam
 *
 * 0x88 board: an IntArray(128) plus game state, with make/unmake kept
 * symmetric through fixed-size primitive stacks (no per-node allocation,
 * so perft and search can reuse one Position for the whole tree).
 *
 * makeMove always applies the move and pushes undo + key history; it
 * returns false when the move leaves the mover's own king in check, in
 * which case the caller must still call unmakeMove exactly once.
 */
package com.krafttools.chesskraft.engine

class Position {
    val board = IntArray(128)
    var side: Int = WHITE
    var castling: Int = 0
    var epSquare: Int = NO_SQUARE
    var halfmove: Int = 0
    var fullmove: Int = 1
    val kingSq = IntArray(2)
    var key: Long = 0L

    private val hMove = IntArray(MAX_HISTORY)
    private val hCastle = IntArray(MAX_HISTORY)
    private val hEp = IntArray(MAX_HISTORY)
    private val hHalf = IntArray(MAX_HISTORY)
    private val hKey = LongArray(MAX_HISTORY)
    private var hCount = 0

    private val keyHist = LongArray(MAX_HISTORY)
    private var keyCount = 0

    fun historySize(): Int = keyCount

    // ── FEN ──────────────────────────────────────────────────────────

    fun setFen(fen: String) {
        board.fill(EMPTY)
        castling = 0
        epSquare = NO_SQUARE
        halfmove = 0
        fullmove = 1
        hCount = 0
        keyCount = 0
        val parts = fen.trim().split(Regex("\\s+"))
        require(parts.size == 6) { "bad FEN: $fen" }
        var rank = 7
        var file = 0
        for (ch in parts[0]) {
            when {
                ch == '/' -> {
                    require(rank > 0 && file == 8) { "bad FEN ranks: $fen" }
                    rank--
                    file = 0
                }
                ch.isDigit() -> file += ch.code - '0'.code
                else -> {
                    val color = if (ch.isUpperCase()) WHITE else BLACK
                    val type = when (ch.lowercaseChar()) {
                        'p' -> PAWN
                        'n' -> KNIGHT
                        'b' -> BISHOP
                        'r' -> ROOK
                        'q' -> QUEEN
                        'k' -> KING
                        else -> throw IllegalArgumentException("bad FEN piece: $ch")
                    }
                    require(file < 8) { "bad FEN file: $fen" }
                    board[rank * 16 + file] = makePiece(type, color)
                    file++
                }
            }
        }
        require(rank == 0 && file == 8) { "bad FEN placement: $fen" }
        side = when (parts[1]) {
            "w" -> WHITE
            "b" -> BLACK
            else -> throw IllegalArgumentException("bad FEN side: $fen")
        }
        if (parts[2] != "-") {
            for (ch in parts[2]) {
                castling = castling or when (ch) {
                    'K' -> CASTLE_WK
                    'Q' -> CASTLE_WQ
                    'k' -> CASTLE_BK
                    'q' -> CASTLE_BQ
                    else -> throw IllegalArgumentException("bad FEN castling: $fen")
                }
            }
        }
        epSquare = if (parts[3] == "-") NO_SQUARE else {
            val sq = squareFromName(parts[3], 0)
            require(onBoard(sq)) { "bad FEN ep: $fen" }
            sq
        }
        halfmove = parts[4].toInt()
        fullmove = parts[5].toInt()
        for (sq in 0 until 128) {
            if (!onBoard(sq)) continue
            if (pieceType(board[sq]) == KING) {
                kingSq[pieceColor(board[sq])] = sq
            }
        }
        key = computeKey()
        keyHist[0] = key
        keyCount = 1
    }

    fun toFen(): String {
        val b = StringBuilder()
        for (rank in 7 downTo 0) {
            var empty = 0
            for (file in 0 until 8) {
                val p = board[rank * 16 + file]
                if (p == EMPTY) {
                    empty++
                } else {
                    if (empty > 0) {
                        b.append(('0'.code + empty).toChar())
                        empty = 0
                    }
                    var ch = when (pieceType(p)) {
                        PAWN -> 'p'
                        KNIGHT -> 'n'
                        BISHOP -> 'b'
                        ROOK -> 'r'
                        QUEEN -> 'q'
                        else -> 'k'
                    }
                    if (pieceColor(p) == WHITE) ch = ch.uppercaseChar()
                    b.append(ch)
                }
            }
            if (empty > 0) b.append(('0'.code + empty).toChar())
            if (rank > 0) b.append('/')
        }
        b.append(if (side == WHITE) " w " else " b ")
        if (castling == 0) {
            b.append('-')
        } else {
            if ((castling and CASTLE_WK) != 0) b.append('K')
            if ((castling and CASTLE_WQ) != 0) b.append('Q')
            if ((castling and CASTLE_BK) != 0) b.append('k')
            if ((castling and CASTLE_BQ) != 0) b.append('q')
        }
        b.append(' ')
        b.append(if (epSquare == NO_SQUARE) "-" else squareName(epSquare))
        b.append(' ')
        b.append(halfmove)
        b.append(' ')
        b.append(fullmove)
        return b.toString()
    }

    // ── Attacks ──────────────────────────────────────────────────────

    fun isSquareAttacked(sq: Int, by: Int): Boolean {
        if (by == WHITE) {
            var t = sq - 15
            if (onBoard(t) && board[t] == WP) return true
            t = sq - 17
            if (onBoard(t) && board[t] == WP) return true
        } else {
            var t = sq + 15
            if (onBoard(t) && board[t] == BP) return true
            t = sq + 17
            if (onBoard(t) && board[t] == BP) return true
        }
        val enemyKnight = if (by == WHITE) WN else BN
        val enemyKing = if (by == WHITE) WK else BK
        for (o in KNIGHT_STEPS) {
            val t = sq + o
            if (onBoard(t) && board[t] == enemyKnight) return true
        }
        for (o in KING_STEPS) {
            val t = sq + o
            if (onBoard(t) && board[t] == enemyKing) return true
        }
        val enemyRook = if (by == WHITE) WR else BR
        val enemyQueen = if (by == WHITE) WQ else BQ
        for (d in ORTHO) {
            var t = sq + d
            while (onBoard(t)) {
                val p = board[t]
                if (p != EMPTY) {
                    if (p == enemyRook || p == enemyQueen) return true
                    break
                }
                t += d
            }
        }
        val enemyBishop = if (by == WHITE) WB else BB
        for (d in DIAG) {
            var t = sq + d
            while (onBoard(t)) {
                val p = board[t]
                if (p != EMPTY) {
                    if (p == enemyBishop || p == enemyQueen) return true
                    break
                }
                t += d
            }
        }
        return false
    }

    fun isInCheck(color: Int): Boolean = isSquareAttacked(kingSq[color], opposite(color))

    // ── Make / unmake ────────────────────────────────────────────────

    fun makeMove(m: Int): Boolean {
        val from = moveFrom(m)
        val to = moveTo(m)
        val piece = movePiece(m)
        val captured = moveCaptured(m)
        val promo = movePromo(m)
        val mover = side
        val foe = opposite(mover)

        hMove[hCount] = m
        hCastle[hCount] = castling
        hEp[hCount] = epSquare
        hHalf[hCount] = halfmove
        hKey[hCount] = key
        hCount++

        key = key xor Zobrist.sideKey
        key = key xor Zobrist.castleKeys[castling]
        if (epSquare != NO_SQUARE) key = key xor Zobrist.epKeys[fileOf(epSquare)]

        board[from] = EMPTY
        key = key xor Zobrist.pieceKeys[piece][from]
        if (isEpCapture(m)) {
            val capSq = if (mover == WHITE) to - 16 else to + 16
            val capPiece = board[capSq]
            board[capSq] = EMPTY
            key = key xor Zobrist.pieceKeys[capPiece][capSq]
        } else if (captured != NO_PIECE) {
            key = key xor Zobrist.pieceKeys[captured][to]
        }
        val placed = if (promo != NO_PIECE) promo else piece
        board[to] = placed
        key = key xor Zobrist.pieceKeys[placed][to]

        if (isCastle(m)) {
            if (to > from) {
                val rf = from + 3
                val rt = from + 1
                val rook = board[rf]
                board[rf] = EMPTY
                board[rt] = rook
                key = key xor Zobrist.pieceKeys[rook][rf] xor Zobrist.pieceKeys[rook][rt]
            } else {
                val rf = from - 4
                val rt = from - 1
                val rook = board[rf]
                board[rf] = EMPTY
                board[rt] = rook
                key = key xor Zobrist.pieceKeys[rook][rf] xor Zobrist.pieceKeys[rook][rt]
            }
        }

        if (pieceType(piece) == KING) {
            kingSq[mover] = to
        }

        castling = castling and CASTLE_MASK[from] and CASTLE_MASK[to]
        key = key xor Zobrist.castleKeys[castling]

        epSquare = NO_SQUARE
        if (isDoublePush(m)) {
            epSquare = if (mover == WHITE) from + 16 else from - 16
            key = key xor Zobrist.epKeys[fileOf(epSquare)]
        }

        halfmove = if (pieceType(piece) == PAWN || captured != NO_PIECE || isEpCapture(m)) 0 else halfmove + 1
        if (mover == BLACK) fullmove++

        side = foe
        keyHist[keyCount] = key
        keyCount++

        return !isSquareAttacked(kingSq[mover], foe)
    }

    fun unmakeMove() {
        keyCount--
        hCount--
        val m = hMove[hCount]
        val from = moveFrom(m)
        val to = moveTo(m)
        val piece = movePiece(m)
        val captured = moveCaptured(m)
        side = opposite(side)
        val mover = side
        if (mover == BLACK) fullmove--

        board[from] = piece
        if (isEpCapture(m)) {
            board[to] = EMPTY
            val capSq = if (mover == WHITE) to - 16 else to + 16
            board[capSq] = makePiece(PAWN, opposite(mover))
        } else {
            board[to] = captured
        }
        if (isCastle(m)) {
            if (to > from) {
                board[from + 3] = board[from + 1]
                board[from + 1] = EMPTY
            } else {
                board[from - 4] = board[from - 1]
                board[from - 1] = EMPTY
            }
        }
        if (pieceType(piece) == KING) {
            kingSq[mover] = from
        }
        castling = hCastle[hCount]
        epSquare = hEp[hCount]
        halfmove = hHalf[hCount]
        key = hKey[hCount]
    }

    // ── Draws ────────────────────────────────────────────────────────

    fun repetitions(): Int {
        var n = 0
        for (i in 0 until keyCount) {
            if (keyHist[i] == key) n++
        }
        return n
    }

    fun isThreefold(): Boolean = repetitions() >= 3

    fun isFiftyMove(): Boolean = halfmove >= 100

    fun hasInsufficientMaterial(): Boolean {
        var minors = 0
        var bishopSquareColor = -1
        var mixedBishops = false
        for (sq in 0 until 128) {
            if (!onBoard(sq)) continue
            val p = board[sq]
            if (p == EMPTY) continue
            when (pieceType(p)) {
                PAWN, ROOK, QUEEN -> return false
                BISHOP, KNIGHT -> {
                    minors++
                    if (pieceType(p) == BISHOP) {
                        val c = (fileOf(sq) + rankOf(sq)) and 1
                        if (bishopSquareColor == -1) bishopSquareColor = c
                        else if (bishopSquareColor != c) mixedBishops = true
                    }
                }
            }
        }
        if (minors <= 1) return true
        var onlyBishops = true
        for (sq in 0 until 128) {
            if (!onBoard(sq)) continue
            val p = board[sq]
            if (p != EMPTY && pieceType(p) == KNIGHT) {
                onlyBishops = false
                break
            }
        }
        return onlyBishops && !mixedBishops
    }

    fun computeKey(): Long {
        var k = 0L
        for (sq in 0 until 128) {
            if (!onBoard(sq)) continue
            val p = board[sq]
            if (p != EMPTY) k = k xor Zobrist.pieceKeys[p][sq]
        }
        if (side == BLACK) k = k xor Zobrist.sideKey
        k = k xor Zobrist.castleKeys[castling]
        if (epSquare != NO_SQUARE) k = k xor Zobrist.epKeys[fileOf(epSquare)]
        return k
    }

    companion object {
        const val MAX_HISTORY = 8192

        val KNIGHT_STEPS = intArrayOf(14, -14, 18, -18, 31, -31, 33, -33)
        val KING_STEPS = intArrayOf(1, -1, 15, -15, 16, -16, 17, -17)
        val ORTHO = intArrayOf(1, -1, 16, -16)
        val DIAG = intArrayOf(15, -15, 17, -17)

        /** Castling bits surviving when a piece leaves/lands on each square. */
        val CASTLE_MASK: IntArray = IntArray(128) { 15 }.apply {
            this[makeSquare(4, 0)] = 15 xor (CASTLE_WK or CASTLE_WQ)
            this[makeSquare(7, 0)] = 15 xor CASTLE_WK
            this[makeSquare(0, 0)] = 15 xor CASTLE_WQ
            this[makeSquare(4, 7)] = 15 xor (CASTLE_BK or CASTLE_BQ)
            this[makeSquare(7, 7)] = 15 xor CASTLE_BK
            this[makeSquare(0, 7)] = 15 xor CASTLE_BQ
        }

        fun fromFen(fen: String): Position {
            val p = Position()
            p.setFen(fen)
            return p
        }

        const val STARTPOS = "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1"
    }
}
