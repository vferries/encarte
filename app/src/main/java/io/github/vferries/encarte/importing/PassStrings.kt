package io.github.vferries.encarte.importing

import android.util.Log

private const val TAG = "PassStrings"

/** A Wallet pass's `xx.lproj/pass.strings` table: `"key" = "value";` entries with C-style comments. */
object PassStrings {
    /** UTF-16 when the file has a UTF-16 BOM or NUL bytes (Apple's tools write UTF-16), UTF-8 otherwise. */
    fun decode(bytes: ByteArray): String {
        fun startsWith(first: Int, second: Int) =
            bytes.size >= 2 && bytes[0] == first.toByte() && bytes[1] == second.toByte()
        return when {
            startsWith(0xFF, 0xFE) -> String(bytes, 2, bytes.size - 2, Charsets.UTF_16LE)
            startsWith(0xFE, 0xFF) -> String(bytes, 2, bytes.size - 2, Charsets.UTF_16BE)
            // Without a BOM, ASCII text in UTF-16LE has its NUL bytes at odd offsets.
            bytes.any { it == 0.toByte() } ->
                String(bytes, if (bytes.size > 1 && bytes[1] == 0.toByte()) Charsets.UTF_16LE else Charsets.UTF_16BE)
            else -> String(bytes, Charsets.UTF_8).removePrefix("\uFEFF")
        }
    }

    /** A malformed entry is skipped up to the end of its line and logged; the rest of the table is kept. */
    fun parse(text: String): Map<String, String> {
        val table = mutableMapOf<String, String>()
        val cursor = Cursor(text)
        while (true) {
            cursor.skipBlanksAndComments()
            if (cursor.atEnd) return table
            val start = cursor.position
            val entry = cursor.entry()
            if (entry != null) {
                table[entry.first] = entry.second
            } else {
                Log.w(TAG, "Malformed pass.strings entry at offset $start skipped")
                cursor.recover(start)
            }
        }
    }

    private class Cursor(private val text: String) {
        var position = 0
        val atEnd: Boolean get() = position >= text.length

        fun entry(): Pair<String, String>? {
            val key = quoted() ?: return null
            skipBlanksAndComments()
            if (!take('=')) return null
            skipBlanksAndComments()
            val value = quoted() ?: return null
            skipBlanksAndComments()
            return if (take(';')) key to value else null
        }

        fun skipBlanksAndComments() {
            while (!atEnd) {
                when {
                    text[position].isWhitespace() -> position++
                    text.startsWith("//", position) -> skipLine()
                    text.startsWith("/*", position) -> {
                        val end = text.indexOf("*/", position + 2)
                        position = if (end < 0) text.length else end + 2
                    }
                    else -> return
                }
            }
        }

        /**
         * Resumes after the furthest point the failed entry reached, never at its start: rewinding would rescan
         * what the entry already read, which is quadratic when a comment or string spans many lines.
         */
        fun recover(entryStart: Int) {
            val atLineStart = position > 0 && text[position - 1] == '\n'
            if (position <= entryStart || !atLineStart) skipLine()
        }

        fun skipLine() {
            val end = text.indexOf('\n', position)
            position = if (end < 0) text.length else end + 1
        }

        private fun take(char: Char): Boolean {
            if (atEnd || text[position] != char) return false
            position++
            return true
        }

        /** A double-quoted string with the escapes \" \\ \n and \t; null when unterminated. */
        private fun quoted(): String? {
            if (!take('"')) return null
            val out = StringBuilder()
            while (!atEnd) {
                when (val char = text[position++]) {
                    '"' -> return out.toString()
                    '\\' -> {
                        if (atEnd) return null
                        when (val escaped = text[position++]) {
                            'n' -> out.append('\n')
                            't' -> out.append('\t')
                            '"', '\\' -> out.append(escaped)
                            else -> out.append('\\').append(escaped)
                        }
                    }
                    else -> out.append(char)
                }
            }
            return null
        }
    }
}
