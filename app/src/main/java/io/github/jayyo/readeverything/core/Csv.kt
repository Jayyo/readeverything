package io.github.jayyo.readeverything.core

/** Small RFC 4180-style parser: quoted fields, doubled quotes, CRLF, newlines inside quotes. */
object Csv {
    data class Table(val rows: List<List<String>>, val truncated: Boolean)

    const val MAX_COLS = 200
    const val MAX_CELLS = 200_000

    /** Rows beyond [maxRows], columns beyond [MAX_COLS] and cells beyond [MAX_CELLS] are dropped. */
    fun parse(text: String, delimiter: Char = ',', maxRows: Int = 20_000): Table {
        var cells = 0
        var cut = false
        fun addField(row: MutableList<String>, value: String) {
            if (row.size < MAX_COLS) { row.add(value); cells++ } else cut = true
        }
        val rows = ArrayList<List<String>>()
        var row = ArrayList<String>()
        val field = StringBuilder()
        var inQuotes = false
        var recordHasData = false   // tells a real last record from a trailing newline
        var i = 0
        while (i < text.length) {
            val ch = text[i]
            if (inQuotes) {
                if (ch == '"') {
                    if (i + 1 < text.length && text[i + 1] == '"') {
                        field.append('"'); i++
                    } else {
                        inQuotes = false
                    }
                } else {
                    field.append(ch)
                }
            } else when {
                // A quote opens a quoted field only at the start of a field;
                // elsewhere (27" monitor) it is just a character, as in Excel.
                ch == '"' && field.isEmpty() -> { inQuotes = true; recordHasData = true }
                ch == delimiter -> { addField(row, field.toString()); field.setLength(0); recordHasData = true }
                ch == '\n' -> {
                    addField(row, field.toString()); field.setLength(0)
                    rows.add(row); row = ArrayList(); recordHasData = false
                    if (rows.size >= maxRows || cells >= MAX_CELLS) return Table(rows, cut || i + 1 < text.length)
                }
                ch == '\r' -> Unit
                else -> { field.append(ch); recordHasData = true }
            }
            i++
        }
        if (recordHasData || field.isNotEmpty()) {
            addField(row, field.toString())
            rows.add(row)
        }
        return Table(rows, cut)
    }
}
