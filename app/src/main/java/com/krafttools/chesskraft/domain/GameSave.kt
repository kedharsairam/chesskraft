/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 Kedhar Sairam
 */
package com.krafttools.chesskraft.domain

import com.krafttools.chesskraft.engine.Difficulty

/**
 * The shape of a save file, and the codec that reads and writes it.
 *
 * Pure Kotlin — no Context, no File, no Android. The store that owns the bytes
 * lives in `data/`; this is only the format and the arithmetic around it, which
 * is what makes the format testable in a plain JVM test with nothing mocked.
 *
 * The moves are stored as UCI from the standard start position rather than as
 * FENs. That is deliberate: a replayed game is re-derived by the rules, so a
 * save cannot carry a position the rules would not have produced. There is no
 * separate "board state" to fall out of step with the move list.
 */
data class GameSave(
    /** Schema version. Bumped only if the meaning of a field changes. */
    val version: Int = CURRENT_VERSION,
    val inProgress: SavedGame?,
    val history: List<FinishedGame>,
) {
    /**
     * Pushes [entry] onto the history, newest first, and drops the oldest once
     * the cap is reached. A history that grows without bound is a slow leak on
     * a device nobody will clear.
     */
    fun withFinished(entry: FinishedGame, cap: Int = HISTORY_CAP): GameSave =
        copy(history = (listOf(entry) + history).take(cap))

    companion object {
        const val CURRENT_VERSION: Int = 1

        /** Kept short on purpose: it is a glance at recent games, not an archive. */
        const val HISTORY_CAP: Int = 20

        /** Nothing saved at all — what a first launch reads. */
        fun empty(): GameSave = GameSave(inProgress = null, history = emptyList())

        /** Parses a save file, or null when the text is not one. See [fromJson]. */
        fun fromJson(text: String): GameSave? = SaveJson.read(text)
    }
}

/** One game in progress, as it will be picked back up. */
data class SavedGame(
    val playerSide: Side,
    val difficulty: Difficulty,
    val flipped: Boolean,
    val timeControlMs: Long?,
    val clockWhiteMs: Long?,
    val clockBlackMs: Long?,
    /** UCI moves from the standard start position, oldest first. */
    val moves: List<String>,
)

/**
 * One finished game, reduced to the facts worth remembering. No board: there is
 * nothing to replay, and the result is in words because that is what the app
 * shows everywhere else.
 */
data class FinishedGame(
    /** Plain words, e.g. "You win" / "Draw agreed" — never a code. */
    val result: String,
    /** Plies played, which is the length the history row shows. */
    val moveCount: Int,
    val difficulty: Difficulty,
    val playerSide: Side,
    /** Wall-clock ms when the game ended. */
    val endedAtMs: Long,
)

// -- The codec ------------------------------------------------------------

/**
 * Minimal JSON writer and reader, hand-rolled.
 *
 * kotlinx.serialization is not on the classpath and org.json is an Android
 * framework class, which a plain JVM unit test cannot see — using it here would
 * mean this file could only be tested on a device. The format is a handful of
 * strings, longs and booleans, so a small reader is less code than the
 * dependency would have been and it is testable everywhere.
 *
 * The reader is total: anything it does not understand returns null, and every
 * parse failure is a null rather than a throw. A corrupt save must cost the
 * player their game, never the launch.
 */
private object SaveJson {

    fun write(save: GameSave): String = buildString {
        append('{')
        appendField("version", save.version)
        append(',')
        append("\"inProgress\":")
        append(
            if (save.inProgress == null) "null"
            else writeGame(save.inProgress),
        )
        append(',')
        append("\"history\":[")
        save.history.forEachIndexed { index, entry ->
            if (index > 0) append(',')
            append(writeFinished(entry))
        }
        append(']')
        append('}')
    }

    fun read(text: String): GameSave? {
        val root = JsonReader(text).readValue() as? Map<*, *> ?: return null
        val version = root.long("version") ?: return null
        if (version != GameSave.CURRENT_VERSION.toLong()) return null
        val rawGame = root["inProgress"]
        val game = when (rawGame) {
            null, JsonNull -> null
            is Map<*, *> -> readGame(rawGame) ?: return null
            else -> return null
        }
        val rawHistory = root["history"]
        if (rawHistory !is List<*>) return null
        val history = ArrayList<FinishedGame>(rawHistory.size)
        for (element in rawHistory) {
            val entry = element as? Map<*, *> ?: return null
            history += readFinished(entry) ?: return null
        }
        return GameSave(version = version.toInt(), inProgress = game, history = history)
    }

    private fun writeGame(game: SavedGame): String = buildString {
        append('{')
        appendField("playerSide", game.playerSide.name)
        append(',')
        appendField("difficulty", game.difficulty.name)
        append(',')
        appendField("flipped", game.flipped)
        append(',')
        appendLongOrNull("timeControlMs", game.timeControlMs)
        append(',')
        appendLongOrNull("clockWhiteMs", game.clockWhiteMs)
        append(',')
        appendLongOrNull("clockBlackMs", game.clockBlackMs)
        append(',')
        append("\"moves\":[")
        game.moves.forEachIndexed { index, move ->
            if (index > 0) append(',')
            append(quote(move))
        }
        append(']')
        append('}')
    }

    private fun readGame(map: Map<*, *>): SavedGame? {
        val side = Side.entries.firstOrNull { it.name == map["playerSide"] } ?: return null
        val difficulty = Difficulty.entries.firstOrNull { it.name == map["difficulty"] } ?: return null
        val flipped = map["flipped"] as? Boolean ?: return null
        val moves = map["moves"] as? List<*> ?: return null
        val ucis = ArrayList<String>(moves.size)
        for (move in moves) {
            ucis += move as? String ?: return null
        }
        return SavedGame(
            playerSide = side,
            difficulty = difficulty,
            flipped = flipped,
            timeControlMs = map.nullableLong("timeControlMs"),
            clockWhiteMs = map.nullableLong("clockWhiteMs"),
            clockBlackMs = map.nullableLong("clockBlackMs"),
            moves = ucis,
        )
    }

    private fun writeFinished(entry: FinishedGame): String = buildString {
        append('{')
        appendField("result", entry.result)
        append(',')
        appendField("moveCount", entry.moveCount)
        append(',')
        appendField("difficulty", entry.difficulty.name)
        append(',')
        appendField("playerSide", entry.playerSide.name)
        append(',')
        appendField("endedAtMs", entry.endedAtMs)
        append('}')
    }

    private fun readFinished(map: Map<*, *>): FinishedGame? {
        val result = map["result"] as? String ?: return null
        val moveCount = map.long("moveCount")?.toInt() ?: return null
        val difficulty = Difficulty.entries.firstOrNull { it.name == map["difficulty"] } ?: return null
        val side = Side.entries.firstOrNull { it.name == map["playerSide"] } ?: return null
        val endedAt = map.long("endedAtMs") ?: return null
        return FinishedGame(
            result = result,
            moveCount = moveCount,
            difficulty = difficulty,
            playerSide = side,
            endedAtMs = endedAt,
        )
    }

    private fun StringBuilder.appendField(name: String, value: String) {
        append(quote(name)).append(':').append(quote(value))
    }

    private fun StringBuilder.appendField(name: String, value: Long) {
        append(quote(name)).append(':').append(value)
    }

    private fun StringBuilder.appendField(name: String, value: Int) {
        appendField(name, value.toLong())
    }

    private fun StringBuilder.appendField(name: String, value: Boolean) {
        append(quote(name)).append(':').append(value)
    }

    private fun StringBuilder.appendLongOrNull(name: String, value: Long?) {
        append(quote(name)).append(':')
        append(value?.toString() ?: "null")
    }

    /** JSON string literal. Escapes the two characters that would end it early. */
    private fun quote(text: String): String = buildString {
        append('"')
        for (ch in text) {
            when (ch) {
                '"' -> append("\\\"")
                '\\' -> append("\\\\")
                else -> append(ch)
            }
        }
        append('"')
    }
}

/**
 * Reads the whole document or nothing.
 *
 * Hand-written rather than token-at-a-time because there is exactly one shape to
 * read and it nests two levels. Anything unexpected — a bare word, a missing
 * colon, trailing junk — is a null, which is what the launch path wants.
 */
private class JsonReader(private val text: String) {
    private var at = 0

    fun readValue(): Any? {
        skipSpace()
        if (at >= text.length) return null
        return when (text[at]) {
            '{' -> readObject()
            '[' -> readArray()
            '"' -> readString()
            't', 'f' -> readBoolean()
            'n' -> readNull()
            else -> readNumber()
        }
    }

    private fun skipSpace() {
        while (at < text.length && text[at].isWhitespace()) at++
    }

    private fun readObject(): Map<String, Any?>? {
        at++ // '{'
        val out = LinkedHashMap<String, Any?>()
        skipSpace()
        if (at < text.length && text[at] == '}') {
            at++
            return out
        }
        while (true) {
            skipSpace()
            val key = readString() ?: return null
            skipSpace()
            if (at >= text.length || text[at] != ':') return null
            at++
            val value = readValue() ?: return null
            out[key] = value
            skipSpace()
            if (at >= text.length) return null
            when (text[at]) {
                ',' -> at++
                '}' -> {
                    at++
                    return out
                }
                else -> return null
            }
        }
    }

    private fun readArray(): List<Any?>? {
        at++ // '['
        val out = ArrayList<Any?>()
        skipSpace()
        if (at < text.length && text[at] == ']') {
            at++
            return out
        }
        while (true) {
            out += readValue() ?: return null
            skipSpace()
            if (at >= text.length) return null
            when (text[at]) {
                ',' -> at++
                ']' -> {
                    at++
                    return out
                }
                else -> return null
            }
        }
    }

    private fun readString(): String? {
        if (at >= text.length || text[at] != '"') return null
        at++
        val out = StringBuilder()
        while (at < text.length) {
            when (val ch = text[at]) {
                '"' -> {
                    at++
                    return out.toString()
                }
                '\\' -> {
                    at++
                    if (at >= text.length) return null
                    when (val esc = text[at]) {
                        '"', '\\', '/' -> out.append(esc)
                        'n' -> out.append('\n')
                        't' -> out.append('\t')
                        'r' -> out.append('\r')
                        else -> return null
                    }
                    at++
                }
                else -> {
                    out.append(ch)
                    at++
                }
            }
        }
        return null
    }

    private fun readBoolean(): Boolean? = when {
        text.startsWith("true", at) -> {
            at += 4
            true
        }
        text.startsWith("false", at) -> {
            at += 5
            false
        }
        else -> null
    }

    private fun readNull(): Any? {
        if (!text.startsWith("null", at)) return null
        at += 4
        return JsonNull
    }

    private fun readNumber(): Any? {
        val start = at
        if (at < text.length && (text[at] == '-' || text[at] == '+')) at++
        var digits = 0
        while (at < text.length && text[at].isDigit()) {
            at++
            digits++
        }
        if (digits == 0) return null
        return text.substring(start, at).toLongOrNull()
    }
}

/** Distinguishes a JSON null from "absent", which [Map.get] cannot. */
private object JsonNull

/** Long read of a required number field, or null when it is absent or not a number. */
private fun Map<*, *>.long(key: String): Long? = this[key] as? Long

/** Long read of an optional field: a JSON null is a null value, not a failure. */
private fun Map<*, *>.nullableLong(key: String): Long? = when (val raw = this[key]) {
    null, JsonNull -> null
    else -> raw as? Long
}

// -- Public codec ---------------------------------------------------------

/**
 * Serialises this save. Pure, deterministic, and safe to call on any thread.
 *
 * The parser is [GameSave.Companion.fromJson] on the companion: both halves of
 * one format, with the reader next to its writer.
 */
fun GameSave.toJson(): String = SaveJson.write(this)