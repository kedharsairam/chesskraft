/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 Kedhar Sairam
 */
package com.krafttools.chesskraft.domain

import com.kraft.core.AppError
import com.kraft.core.KraftResult
import com.kraft.core.asFailure
import com.kraft.core.asSuccess

/**
 * A chess position. Pure Kotlin — no Android, no Compose.
 *
 * Mailbox 64 board, copy-on-write: [makeMove] returns a new position, the
 * caller (GameTree) owns history. The board UI never touches this directly;
 * it renders snapshots the ViewModel hands it.
 */
class Position private constructor(
    val board: IntArray,
    val sideToMove: Side,
    /** Bitmask: 1 = White kingside, 2 = White queenside, 4 = Black kingside, 8 = Black queenside. */
    val castling: Int,
    /** En-passant target square, or -1. */
    val epSquare: Int,
    val halfmove: Int,
    val fullmove: Int,
) {
    // -- Construction -----------------------------------------------------

    companion object {
        const val START_FEN = "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1"

        fun start(): Position = fromFen(START_FEN).getOrNull() ?: error("bad start FEN")

        /**
         * Read-only snapshot for rendering and announcements: the board owns
         * castling rights and ep state, so a snapshot carries none. Attacks,
         * check and square descriptions only need the pieces.
         */
        fun fromSnapshot(codes: List<Int>, sideToMove: Side): Position {
            val board = IntArray(64) { codes.getOrElse(it) { 0 } }
            return Position(board, sideToMove, 0, -1, 0, 1)
        }

        fun fromFen(fen: String): KraftResult<Position> {
            val parts = fen.trim().split(Regex("\\s+"))
            if (parts.size != 6) {
                return AppError.DataError.Validation("bad FEN: six fields").asFailure()
            }
            val board = IntArray(64)
            val rows = parts[0].split("/")
            if (rows.size != 8) return AppError.DataError.Validation("bad FEN: board").asFailure()
            for (row in rows.indices) {
                val rank = 7 - row
                var file = 0
                for (ch in rows[row]) {
                    if (ch.isDigit()) {
                        file += ch.digitToInt()
                    } else {
                        val side = if (ch.isUpperCase()) Side.WHITE else Side.BLACK
                        val type = when (ch.uppercaseChar()) {
                            'P' -> PieceType.PAWN
                            'N' -> PieceType.KNIGHT
                            'B' -> PieceType.BISHOP
                            'R' -> PieceType.ROOK
                            'Q' -> PieceType.QUEEN
                            'K' -> PieceType.KING
                            else -> return AppError.DataError.Validation("bad FEN: piece $ch").asFailure()
                        }
                        if (file > 7) return AppError.DataError.Validation("bad FEN: row").asFailure()
                        board[squareAt(file, rank)] = PieceCode.of(side, type)
                        file++
                    }
                }
                if (file != 8) return AppError.DataError.Validation("bad FEN: row width").asFailure()
            }
            val side = when (parts[1]) {
                "w" -> Side.WHITE
                "b" -> Side.BLACK
                else -> return AppError.DataError.Validation("bad FEN: side").asFailure()
            }
            var castling = 0
            if (parts[2] != "-") {
                for (ch in parts[2]) {
                    castling = castling or when (ch) {
                        'K' -> 1
                        'Q' -> 2
                        'k' -> 4
                        'q' -> 8
                        else -> return AppError.DataError.Validation("bad FEN: castling").asFailure()
                    }
                }
            }
            val ep = if (parts[3] == "-") -1 else parseSquare(parts[3])
                ?: return AppError.DataError.Validation("bad FEN: ep").asFailure()
            val half = parts[4].toIntOrNull()
                ?: return AppError.DataError.Validation("bad FEN: halfmove").asFailure()
            val full = parts[5].toIntOrNull()
                ?: return AppError.DataError.Validation("bad FEN: fullmove").asFailure()
            return Position(board, side, castling, ep, half, full).asSuccess()
        }
    }

    fun toFen(): String {
        val sb = StringBuilder()
        for (row in 7 downTo 0) {
            var empty = 0
            for (file in 0..7) {
                val code = board[squareAt(file, row)]
                if (code == 0) {
                    empty++
                } else {
                    if (empty > 0) {
                        sb.append(empty)
                        empty = 0
                    }
                    val letter = when (PieceCode.typeOf(code)) {
                        PieceType.PAWN -> "p"
                        PieceType.KNIGHT -> "n"
                        PieceType.BISHOP -> "b"
                        PieceType.ROOK -> "r"
                        PieceType.QUEEN -> "q"
                        PieceType.KING -> "k"
                        null -> "?"
                    }
                    sb.append(if (code > 0) letter.uppercase() else letter)
                }
            }
            if (empty > 0) sb.append(empty)
            if (row > 0) sb.append('/')
        }
        sb.append(if (sideToMove == Side.WHITE) " w " else " b ")
        if (castling == 0) {
            sb.append('-')
        } else {
            if (castling and 1 != 0) sb.append('K')
            if (castling and 2 != 0) sb.append('Q')
            if (castling and 4 != 0) sb.append('k')
            if (castling and 8 != 0) sb.append('q')
        }
        sb.append(' ')
        sb.append(if (epSquare < 0) "-" else squareName(epSquare))
        sb.append(' ').append(halfmove).append(' ').append(fullmove)
        return sb.toString()
    }

    // -- Queries ----------------------------------------------------------

    fun kingSquare(side: Side): Int {
        val want = PieceCode.of(side, PieceType.KING)
        for (sq in 0..63) if (board[sq] == want) return sq
        return -1
    }

    fun isInCheck(side: Side = sideToMove): Boolean {
        val king = kingSquare(side)
        return king >= 0 && isAttacked(king, side.opponent())
    }

    fun isAttacked(sq: Int, by: Side): Boolean {
        val file = fileOf(sq)
        val rank = rankOf(sq)
        // Pawns.
        val pawnRank = rank + if (by == Side.WHITE) -1 else 1
        if (pawnRank in 0..7) {
            for (df in intArrayOf(-1, 1)) {
                val pf = file + df
                if (pf in 0..7 && board[squareAt(pf, pawnRank)] == PieceCode.of(by, PieceType.PAWN)) {
                    return true
                }
            }
        }
        // Knights.
        for ((df, dr) in KNIGHT_STEPS) {
            val f = file + df
            val r = rank + dr
            if (isOnBoard(f, r) && board[squareAt(f, r)] == PieceCode.of(by, PieceType.KNIGHT)) {
                return true
            }
        }
        // King.
        for (df in -1..1) {
            for (dr in -1..1) {
                if (df == 0 && dr == 0) continue
                val f = file + df
                val r = rank + dr
                if (isOnBoard(f, r) && board[squareAt(f, r)] == PieceCode.of(by, PieceType.KING)) {
                    return true
                }
            }
        }
        // Sliders.
        for ((pieceKinds, dirs) in SLIDERS) {
            for ((df, dr) in dirs) {
                var f = file + df
                var r = rank + dr
                while (isOnBoard(f, r)) {
                    val code = board[squareAt(f, r)]
                    if (code != 0) {
                        if (PieceCode.sideOf(code) == by) {
                            val type = PieceCode.typeOf(code)
                            if (type != null && type in pieceKinds) return true
                        }
                        break
                    }
                    f += df
                    r += dr
                }
            }
        }
        return false
    }

    // -- Move generation --------------------------------------------------

    fun generateLegalMoves(side: Side = sideToMove): List<ChessMove> {
        val pseudo = generatePseudo(side)
        val legal = ArrayList<ChessMove>(pseudo.size)
        for (move in pseudo) {
            val next = makeMove(move)
            if (!next.isInCheck(side)) legal.add(move)
        }
        // Order: captures and promotions first so the (stub) engine and hint
        // look one step less silly. MVV-LVA-lite.
        legal.sortWith { a, b -> moveScore(b).compareTo(moveScore(a)) }
        return legal
    }

    private fun moveScore(move: ChessMove): Int {
        var score = 0
        val victim = board[move.to]
        if (victim != 0) {
            score += 10 * pieceValue(PieceCode.typeOf(victim)) -
                pieceValue(PieceCode.typeOf(board[move.from]))
        }
        if (move.to == epSquare && PieceCode.typeOf(board[move.from]) == PieceType.PAWN) {
            score += 10 * pieceValue(PieceType.PAWN)
        }
        if (move.promotion != null) score += pieceValue(move.promotion)
        return score
    }

    private fun generatePseudo(side: Side): List<ChessMove> {
        val moves = ArrayList<ChessMove>(48)
        for (sq in 0..63) {
            val code = board[sq]
            if (code == 0 || PieceCode.sideOf(code) != side) continue
            when (PieceCode.typeOf(code)) {
                PieceType.PAWN -> genPawn(moves, sq, side)
                PieceType.KNIGHT -> genSteps(moves, sq, side, KNIGHT_STEPS)
                PieceType.BISHOP -> genSlides(moves, sq, side, DIAGONALS)
                PieceType.ROOK -> genSlides(moves, sq, side, STRAIGHTS)
                PieceType.QUEEN -> {
                    genSlides(moves, sq, side, DIAGONALS)
                    genSlides(moves, sq, side, STRAIGHTS)
                }
                PieceType.KING -> {
                    genSteps(moves, sq, side, KING_STEPS)
                    genCastles(moves, sq, side)
                }
                null -> continue
            }
        }
        return moves
    }

    private fun genPawn(moves: MutableList<ChessMove>, sq: Int, side: Side) {
        val file = fileOf(sq)
        val rank = rankOf(sq)
        val dir = if (side == Side.WHITE) 1 else -1
        val startRank = if (side == Side.WHITE) 1 else 6
        val oneRank = rank + dir
        if (oneRank in 0..7) {
            val one = squareAt(file, oneRank)
            if (board[one] == 0) {
                addPawnMove(moves, sq, one, oneRank == side.promoRank())
                val twoRank = rank + 2 * dir
                if (rank == startRank) {
                    val two = squareAt(file, twoRank)
                    if (board[two] == 0) moves.add(ChessMove(sq, two))
                }
            }
            for (df in intArrayOf(-1, 1)) {
                val f = file + df
                if (f !in 0..7) continue
                val target = squareAt(f, oneRank)
                val victim = board[target]
                if (victim != 0 && PieceCode.sideOf(victim) == side.opponent()) {
                    addPawnMove(moves, sq, target, oneRank == side.promoRank())
                } else if (target == epSquare) {
                    moves.add(ChessMove(sq, target))
                }
            }
        }
    }

    private fun addPawnMove(moves: MutableList<ChessMove>, from: Int, to: Int, isPromo: Boolean) {
        if (!isPromo) {
            moves.add(ChessMove(from, to))
            return
        }
        moves.add(ChessMove(from, to, PieceType.QUEEN))
        moves.add(ChessMove(from, to, PieceType.ROOK))
        moves.add(ChessMove(from, to, PieceType.BISHOP))
        moves.add(ChessMove(from, to, PieceType.KNIGHT))
    }

    private fun genSteps(
        moves: MutableList<ChessMove>,
        sq: Int,
        side: Side,
        steps: Array<Pair<Int, Int>>,
    ) {
        val file = fileOf(sq)
        val rank = rankOf(sq)
        for ((df, dr) in steps) {
            val f = file + df
            val r = rank + dr
            if (!isOnBoard(f, r)) continue
            val target = squareAt(f, r)
            val victim = board[target]
            if (victim == 0 || PieceCode.sideOf(victim) == side.opponent()) {
                moves.add(ChessMove(sq, target))
            }
        }
    }

    private fun genSlides(
        moves: MutableList<ChessMove>,
        sq: Int,
        side: Side,
        dirs: Array<Pair<Int, Int>>,
    ) {
        val file = fileOf(sq)
        val rank = rankOf(sq)
        for ((df, dr) in dirs) {
            var f = file + df
            var r = rank + dr
            while (isOnBoard(f, r)) {
                val target = squareAt(f, r)
                val victim = board[target]
                if (victim == 0) {
                    moves.add(ChessMove(sq, target))
                } else {
                    if (PieceCode.sideOf(victim) == side.opponent()) {
                        moves.add(ChessMove(sq, target))
                    }
                    break
                }
                f += df
                r += dr
            }
        }
    }

    private fun genCastles(moves: MutableList<ChessMove>, sq: Int, side: Side) {
        val home = if (side == Side.WHITE) 4 else 60 // e1 / e8
        if (sq != home || isInCheck(side)) return
        val foe = side.opponent()
        if (side == Side.WHITE) {
            if (castling and 1 != 0 && board[5] == 0 && board[6] == 0 &&
                !isAttacked(5, foe) && !isAttacked(6, foe)
            ) {
                moves.add(ChessMove(sq, 6))
            }
            if (castling and 2 != 0 && board[1] == 0 && board[2] == 0 && board[3] == 0 &&
                !isAttacked(3, foe) && !isAttacked(2, foe)
            ) {
                moves.add(ChessMove(sq, 2))
            }
        } else {
            if (castling and 4 != 0 && board[61] == 0 && board[62] == 0 &&
                !isAttacked(61, foe) && !isAttacked(62, foe)
            ) {
                moves.add(ChessMove(sq, 62))
            }
            if (castling and 8 != 0 && board[57] == 0 && board[58] == 0 && board[59] == 0 &&
                !isAttacked(59, foe) && !isAttacked(58, foe)
            ) {
                moves.add(ChessMove(sq, 58))
            }
        }
    }

    // -- Apply ------------------------------------------------------------

    fun makeMove(move: ChessMove): Position {
        val next = board.copyOf()
        val mover = board[move.from]
        val moverSide = PieceCode.sideOf(mover) ?: sideToMove
        val moverType = PieceCode.typeOf(mover)
        var nextCastling = castling
        var nextEp = -1
        var nextHalf = halfmove + 1
        val isPawn = moverType == PieceType.PAWN
        val captured = board[move.to]

        if (isPawn || captured != 0) nextHalf = 0

        // En passant capture: the victim is beside the pawn, not on the target.
        val isEpCapture = isPawn && move.to == epSquare && captured == 0
        if (isEpCapture) {
            val victimSq = move.to + if (moverSide == Side.WHITE) -8 else 8
            next[victimSq] = 0
        }

        next[move.from] = 0
        next[move.to] = if (move.promotion != null) {
            PieceCode.of(moverSide, move.promotion)
        } else {
            mover
        }

        // Castling: the king moves two squares; carry the rook.
        if (moverType == PieceType.KING && kotlin.math.abs(move.to - move.from) == 2) {
            if (move.to > move.from) {
                next[move.from + 3] = 0
                next[move.from + 1] = PieceCode.of(moverSide, PieceType.ROOK)
            } else {
                next[move.from - 4] = 0
                next[move.from - 1] = PieceCode.of(moverSide, PieceType.ROOK)
            }
        }

        // Double push sets the ep target.
        if (isPawn && kotlin.math.abs(move.to - move.from) == 16) {
            nextEp = (move.from + move.to) / 2
        }

        // Castling rights: king moves, or a rook moves / is captured from its home.
        if (moverType == PieceType.KING) {
            nextCastling = nextCastling and if (moverSide == Side.WHITE) 12 else 3
        }
        for ((sq, mask) in ROOK_HOME_MASKS) {
            if (move.from == sq || move.to == sq) nextCastling = nextCastling and mask.inv()
        }

        val nextFull = fullmove + if (sideToMove == Side.BLACK) 1 else 0
        return Position(next, sideToMove.opponent(), nextCastling, nextEp, nextHalf, nextFull)
    }

    // -- End-of-game facts (threefold lives in GameTree, which owns history) -

    fun hasInsufficientMaterial(): Boolean {
        var minorCount = 0
        var bishopColorParity = -1
        var sameColorBishopsOnly = true
        for (sq in 0..63) {
            when (PieceCode.typeOf(board[sq])) {
                null, PieceType.KING -> continue
                PieceType.BISHOP -> {
                    minorCount++
                    val parity = (fileOf(sq) + rankOf(sq)) % 2
                    if (bishopColorParity == -1) {
                        bishopColorParity = parity
                    } else if (bishopColorParity != parity) {
                        sameColorBishopsOnly = false
                    }
                }
                PieceType.KNIGHT -> {
                    minorCount++
                    sameColorBishopsOnly = false
                }
                else -> return false
            }
        }
        if (minorCount == 0) return true
        if (minorCount == 1) return true
        return sameColorBishopsOnly
    }

    // -- SAN (for the move list and review) --------------------------------

    fun toSan(move: ChessMove): String {
        if (PieceCode.typeOf(board[move.from]) == PieceType.KING &&
            kotlin.math.abs(move.to - move.from) == 2
        ) {
            val castle = if (move.to > move.from) "O-O" else "O-O-O"
            return castle + checkSuffix(makeMove(move))
        }
        val moverType = PieceCode.typeOf(board[move.from])
        val isCapture = board[move.to] != 0 ||
            (moverType == PieceType.PAWN && move.to == epSquare)
        val sb = StringBuilder()
        if (moverType == PieceType.PAWN) {
            if (isCapture) sb.append('a' + fileOf(move.from)).append('x')
            sb.append(squareName(move.to))
            if (move.promotion != null) sb.append('=').append(promoLetter(move.promotion))
        } else {
            sb.append(promoLetter(moverType ?: PieceType.QUEEN))
            sb.append(disambiguation(move))
            if (isCapture) sb.append('x')
            sb.append(squareName(move.to))
        }
        sb.append(checkSuffix(makeMove(move)))
        return sb.toString()
    }

    private fun disambiguation(move: ChessMove): String {
        val moverType = PieceCode.typeOf(board[move.from]) ?: return ""
        val others = generateLegalMoves(sideToMove).filter {
            it.to == move.to && it.from != move.from &&
                PieceCode.typeOf(board[it.from]) == moverType
        }
        if (others.isEmpty()) return ""
        val sameFile = others.any { fileOf(it.from) == fileOf(move.from) }
        val sameRank = others.any { rankOf(it.from) == rankOf(move.from) }
        return when {
            !sameFile -> "${'a' + fileOf(move.from)}"
            !sameRank -> "${'1' + rankOf(move.from)}"
            else -> squareName(move.from)
        }
    }

    private fun checkSuffix(next: Position): String {
        if (!next.isInCheck(next.sideToMove)) return ""
        return if (next.generateLegalMoves().isEmpty()) "#" else "+"
    }
}

private val KNIGHT_STEPS = arrayOf(
    1 to 2, 2 to 1, 2 to -1, 1 to -2, -1 to -2, -2 to -1, -2 to 1, -1 to 2,
)

private val KING_STEPS = arrayOf(
    1 to 0, 1 to 1, 0 to 1, -1 to 1, -1 to 0, -1 to -1, 0 to -1, 1 to -1,
)

private val DIAGONALS = arrayOf(1 to 1, 1 to -1, -1 to 1, -1 to -1)

private val STRAIGHTS = arrayOf(1 to 0, -1 to 0, 0 to 1, 0 to -1)

private val ORTHO_TYPES = setOf(PieceType.ROOK, PieceType.QUEEN)
private val DIAG_TYPES = setOf(PieceType.BISHOP, PieceType.QUEEN)

private val SLIDERS = listOf(ORTHO_TYPES to STRAIGHTS, DIAG_TYPES to DIAGONALS)

/** Rook home squares -> rights bit cleared when the rook leaves or is taken there. */
private val ROOK_HOME_MASKS = listOf(0 to 2, 7 to 1, 56 to 8, 63 to 4)

/** Michniewski base values, shared with the engine's eval when it lands. */
fun pieceValue(type: PieceType?): Int = when (type) {
    PieceType.PAWN -> 100
    PieceType.KNIGHT -> 320
    PieceType.BISHOP -> 330
    PieceType.ROOK -> 500
    PieceType.QUEEN -> 900
    PieceType.KING -> 20000
    null -> 0
}
