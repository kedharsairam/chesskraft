/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 Kedhar Sairam
 *
 * Negamax with principal-variation search, alpha-beta, and mate-distance
 * pruning. Move order is MVV-LVA captures first, then killers, then
 * history. Quiescence searches captures only (full evasions when in
 * check) behind a stand-pat window, capped by the same ply ceiling as
 * the main search. Iterative deepening at the root keeps the best move
 * of the last fully completed iteration, so the hard time cap can fire
 * anywhere without losing a usable answer.
 *
 * Buffers are allocated once per Searcher and indexed by ply: nothing
 * is allocated inside the node loop. The clock is read every 1024 nodes.
 */
package com.krafttools.chesskraft.engine

class Searcher(val maxDepth: Int, val maxMillis: Long) {
    var nodes: Long = 0L
        private set

    private var deadline: Long = 0L
    private var stopped = false

    private val buf = Array(MAX_PLY) { IntArray(MoveGen.BUFFER_SIZE) }
    private val ord = Array(MAX_PLY) { IntArray(MoveGen.BUFFER_SIZE) }
    private val killers = Array(MAX_PLY) { IntArray(2) }
    private val history = IntArray(15 * 128)

    val rootMoves = IntArray(MoveGen.BUFFER_SIZE)
    val rootScores = IntArray(MoveGen.BUFFER_SIZE)
    var rootCount = 0
        private set
    var bestMove: Int = NO_MOVE
        private set
    var bestScore: Int = 0
        private set

    fun findBest(pos: Position): Int {
        nodes = 0L
        stopped = false
        deadline = System.currentTimeMillis() + maxMillis
        bestMove = NO_MOVE
        rootCount = MoveGen.legalMoves(pos, rootMoves)
        if (rootCount == 0) return NO_MOVE
        for (i in 0 until rootCount) rootScores[i] = 0
        if (rootCount == 1) {
            bestMove = rootMoves[0]
            return bestMove
        }
        val depthCap = maxDepth.coerceAtLeast(1).coerceAtMost(MAX_DEPTH)
        var depth = 1
        while (depth <= depthCap) {
            if (searchRoot(pos, depth)) {
                sortRoot()
                bestMove = rootMoves[0]
                bestScore = rootScores[0]
                if (isMateScore(bestScore)) break
            } else {
                break
            }
            if (System.currentTimeMillis() >= deadline) break
            depth++
        }
        if (bestMove == NO_MOVE) {
            bestMove = rootMoves[0]
        }
        return bestMove
    }

    /** One iterative-deepening iteration. True when it ran to completion. */
    private fun searchRoot(pos: Position, depth: Int): Boolean {
        val iter = IntArray(rootCount)
        var alpha = -INF
        for (i in 0 until rootCount) {
            val m = rootMoves[i]
            pos.makeMove(m)
            val score = if (i == 0) {
                -search(pos, depth - 1, -INF, INF, 1)
            } else {
                var s = -search(pos, depth - 1, -alpha - 1, -alpha, 1)
                if (!stopped && s > alpha && s < INF) {
                    s = -search(pos, depth - 1, -INF, -alpha, 1)
                }
                s
            }
            pos.unmakeMove()
            if (stopped) return false
            iter[i] = score
            if (score > alpha) alpha = score
        }
        for (i in 0 until rootCount) rootScores[i] = iter[i]
        return true
    }

    private fun sortRoot() {
        for (i in 1 until rootCount) {
            val m = rootMoves[i]
            val s = rootScores[i]
            var j = i - 1
            while (j >= 0 && rootScores[j] < s) {
                rootMoves[j + 1] = rootMoves[j]
                rootScores[j + 1] = rootScores[j]
                j--
            }
            rootMoves[j + 1] = m
            rootScores[j + 1] = s
        }
    }

    private fun search(pos: Position, depth: Int, alphaIn: Int, beta: Int, ply: Int): Int {
        nodes++
        if ((nodes and 1023L) == 0L && System.currentTimeMillis() >= deadline) {
            stopped = true
        }
        if (stopped) return 0
        if (pos.halfmove >= 100 || pos.isThreefold() || pos.hasInsufficientMaterial()) return 0
        if (depth <= 0) return quiescence(pos, alphaIn, beta, ply)
        var alpha = alphaIn
        val floor = -MATE + ply
        if (floor > alpha) alpha = floor
        var upper = beta
        val ceiling = MATE - ply
        if (ceiling < upper) upper = ceiling
        if (alpha >= upper) return alpha

        val moves = buf[ply]
        val n = MoveGen.generateAll(pos, moves, 0)
        orderMoves(pos, moves, n, ply)
        var legal = 0
        var best = -INF
        for (i in 0 until n) {
            pickBest(moves, ord[ply], i, n)
            val m = moves[i]
            if (!pos.makeMove(m)) {
                pos.unmakeMove()
                continue
            }
            legal++
            val score = if (legal == 1) {
                -search(pos, depth - 1, -upper, -alpha, ply + 1)
            } else {
                var s = -search(pos, depth - 1, -alpha - 1, -alpha, ply + 1)
                if (!stopped && s > alpha && s < upper) {
                    s = -search(pos, depth - 1, -upper, -alpha, ply + 1)
                }
                s
            }
            pos.unmakeMove()
            if (stopped) return 0
            if (score > best) best = score
            if (score > alpha) alpha = score
            if (alpha >= upper) {
                if (!isCapture(m)) {
                    val piece = movePiece(m)
                    if (killers[ply][0] != m) {
                        killers[ply][1] = killers[ply][0]
                        killers[ply][0] = m
                    }
                    val h = historyIndex(piece, moveTo(m))
                    history[h] += depth * depth
                }
                break
            }
        }
        if (legal == 0) {
            return if (pos.isInCheck(pos.side)) -MATE + ply else 0
        }
        return best
    }

    private fun quiescence(pos: Position, alphaIn: Int, beta: Int, ply: Int): Int {
        nodes++
        if ((nodes and 1023L) == 0L && System.currentTimeMillis() >= deadline) {
            stopped = true
        }
        if (stopped) return 0
        if (pos.halfmove >= 100 || pos.isThreefold() || pos.hasInsufficientMaterial()) return 0
        val inCheck = pos.isInCheck(pos.side)
        var alpha = alphaIn
        if (!inCheck) {
            val stand = Eval.evaluate(pos)
            if (stand >= beta) return beta
            if (stand > alpha) alpha = stand
        }
        if (ply >= MAX_PLY - 1) return alpha
        val moves = buf[ply]
        val n = if (inCheck) MoveGen.generateAll(pos, moves, 0) else MoveGen.generateCaptures(pos, moves, 0)
        orderMoves(pos, moves, n, ply)
        var legal = 0
        for (i in 0 until n) {
            pickBest(moves, ord[ply], i, n)
            val m = moves[i]
            if (!pos.makeMove(m)) {
                pos.unmakeMove()
                continue
            }
            legal++
            val score = -quiescence(pos, -beta, -alpha, ply + 1)
            pos.unmakeMove()
            if (stopped) return 0
            if (score > alpha) alpha = score
            if (alpha >= beta) break
        }
        if (inCheck && legal == 0) return -MATE + ply
        return alpha
    }

    private fun orderMoves(pos: Position, moves: IntArray, n: Int, ply: Int) {
        val scores = ord[ply]
        for (i in 0 until n) {
            scores[i] = orderScore(pos, moves[i], ply)
        }
    }

    private fun orderScore(pos: Position, m: Int, ply: Int): Int {
        if (isCapture(m)) {
            val victim = if (isEpCapture(m)) PAWN else pieceType(moveCaptured(m))
            val attacker = pieceType(movePiece(m))
            var s = 100000 + Eval.pieceValue(victim) * 16 - Eval.pieceValue(attacker)
            if (isPromotion(m)) s += Eval.pieceValue(pieceType(movePromo(m))) * 32
            return s
        }
        if (isPromotion(m)) {
            return 90000 + Eval.pieceValue(pieceType(movePromo(m)))
        }
        if (ply < MAX_PLY) {
            if (m == killers[ply][0]) return 80000
            if (m == killers[ply][1]) return 70000
        }
        return history[historyIndex(movePiece(m), moveTo(m))]
    }

    private fun pickBest(moves: IntArray, scores: IntArray, from: Int, n: Int) {
        var best = from
        for (i in from + 1 until n) {
            if (scores[i] > scores[best]) best = i
        }
        if (best != from) {
            val tm = moves[from]
            moves[from] = moves[best]
            moves[best] = tm
            val ts = scores[from]
            scores[from] = scores[best]
            scores[best] = ts
        }
    }

    private fun historyIndex(piece: Int, to: Int): Int = piece * 128 + to

    companion object {
        const val INF = 30000
        const val MATE = 29000
        const val MAX_PLY = 64
        const val MAX_DEPTH = 32

        fun isMateScore(score: Int): Boolean =
            score > MATE - MAX_PLY * 4 || score < -MATE + MAX_PLY * 4
    }
}
