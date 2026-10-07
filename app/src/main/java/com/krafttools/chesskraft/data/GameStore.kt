/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 Kedhar Sairam
 */
package com.krafttools.chesskraft.data

import android.content.Context
import com.kraft.core.AppError
import com.kraft.core.KraftResult
import com.krafttools.chesskraft.domain.GameSave
import com.krafttools.chesskraft.domain.toJson
import java.io.File
import java.io.IOException

/**
 * Reads and writes the save file.
 *
 * The interface is what the rest of the app depends on, and the file
 * implementation is a detail behind it: a test passes a memory double and never
 * touches a disk. There is no singleton and no `object` with a var, so nothing
 * here is global mutable state — the store is handed in.
 *
 * Internal storage means no permission, and a single small JSON file means no
 * Room and no DataStore: a handful of records does not earn a database.
 */
interface GameStore {
    /** The whole save, or a failure. Missing and unreadable are different. */
    fun load(): KraftResult<GameSave>

    /**
     * Writes [save], replacing whatever was there.
     *
     * Writing a [GameSave] with no in-progress game is how a game is forgotten
     * while the finished-game history stays — there is no separate delete,
     * because one file holding both is the point.
     */
    fun save(save: GameSave): KraftResult<Unit>
}

/**
 * The real store: one JSON file in the app's internal files directory.
 *
 * Writes go to a temporary file and are renamed over the target. A half-written
 * file is a corrupt save, and a rename is the one operation the filesystem
 * makes atomic, so a process killed mid-write leaves the previous save intact
 * instead of a truncated one.
 */
class FileGameStore(
    private val file: File,
) : GameStore {

    override fun load(): KraftResult<GameSave> = try {
        if (!file.exists()) {
            KraftResult.Failure(AppError.DataError.NotFound)
        } else {
            GameSave.fromJson(file.readText())
                ?.let { KraftResult.Success(it) }
                ?: KraftResult.Failure(AppError.DataError.Parse("save file is not readable"))
        }
    } catch (e: IOException) {
        KraftResult.Failure(AppError.Unknown(e, e.message))
    }

    override fun save(save: GameSave): KraftResult<Unit> = try {
        val text = save.toJson()
        file.parentFile?.mkdirs()
        val temp = tempFile()
        temp.writeText(text)
        if (!temp.renameTo(file)) {
            // Some filesystems refuse a rename onto an existing file. Falling
            // back to a direct write is still better than losing the save, and
            // the reader copes with a truncated file by answering null.
            file.writeText(text)
            temp.delete()
        }
        KraftResult.Success(Unit)
    } catch (e: IOException) {
        KraftResult.Failure(AppError.Unknown(e, e.message))
    }

    /** Beside the save rather than in a temp directory: the rename must be same-volume. */
    private fun tempFile(): File {
        val parent = file.parentFile ?: return File("${file.path}.tmp")
        return File(parent, "${file.name}.tmp")
    }

    companion object {
        /** The one file this app keeps. Versioned so a future format can migrate. */
        const val FILE_NAME: String = "chesskraft-save-v1.json"

        /** Builds the real store against internal storage. Needs no permission. */
        fun inInternalStorage(context: Context): FileGameStore =
            FileGameStore(File(context.filesDir, FILE_NAME))
    }
}