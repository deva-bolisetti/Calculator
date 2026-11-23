package com.deva.ledgercalc.data

import android.content.Context
import com.deva.ledgercalc.domain.HistoryEntry
import com.deva.ledgercalc.domain.Tape

/**
 * Keeps the last tape and the finished-tape history in SharedPreferences.
 *
 * A tape is a handful of rows of text, so a database would buy nothing here: one JSON blob is
 * written atomically by the framework and restored synchronously, which means the first frame
 * already shows the previous tape instead of flashing an empty one.
 *
 * The encoding itself lives in [TapeCodec]; this class only moves strings and keeps a copy of any
 * blob it could not read, so a damaged file is diagnosable instead of silently deleted.
 */
class TapeStore(context: Context) : TapeRepository {

    private val prefs = context.applicationContext
        .getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    override fun load(): Tape {
        val raw = prefs.getString(KEY_TAPE, null) ?: return Tape()
        return try {
            TapeCodec.decodeTape(raw)
        } catch (_: Exception) {
            stash(KEY_TAPE_UNREADABLE, raw)
            Tape()
        }
    }

    override fun save(tape: Tape) {
        prefs.edit().putString(KEY_TAPE, TapeCodec.encodeTape(tape)).apply()
    }

    override fun loadHistory(): List<HistoryEntry> {
        val raw = prefs.getString(KEY_HISTORY, null) ?: return emptyList()
        return try {
            TapeCodec.decodeHistory(raw)
        } catch (_: Exception) {
            stash(KEY_HISTORY_UNREADABLE, raw)
            emptyList()
        }
    }

    override fun saveHistory(entries: List<HistoryEntry>) {
        prefs.edit().putString(KEY_HISTORY, TapeCodec.encodeHistory(entries)).apply()
    }

    /**
     * Keeps the first unreadable blob under a side key.
     *
     * Only the first one is kept: a repeating failure would overwrite the interesting copy with a
     * later, less interesting one.
     */
    private fun stash(key: String, raw: String) {
        if (prefs.contains(key)) return
        prefs.edit().putString(key, raw).apply()
    }

    private companion object {
        const val PREFS = "ledgercalc"
        const val KEY_TAPE = "tape"
        const val KEY_HISTORY = "history"
        const val KEY_TAPE_UNREADABLE = "tape.unreadable"
        const val KEY_HISTORY_UNREADABLE = "history.unreadable"
    }
}
