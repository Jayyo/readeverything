package io.github.jayyo.readeverything.viewer

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@Composable
fun CsvScreen(text: String, tab: Boolean = false, modifier: Modifier = Modifier) {
    val rows = remember(text) { parseDelimited(text, if (tab) '\t' else ',') }
    val cols = remember(rows) { rows.maxOfOrNull { it.size } ?: 0 }
    val hScroll = rememberScrollState()
    LazyColumn(modifier.fillMaxSize()) {
        items(count = rows.size) { r ->
            val row = rows[r]
            Row(Modifier.horizontalScroll(hScroll).padding(vertical = 2.dp, horizontal = 8.dp)) {
                for (c in 0 until cols) {
                    Text(
                        text = row.getOrElse(c) { "" },
                        modifier = Modifier.width(150.dp).padding(horizontal = 6.dp),
                        maxLines = 4,
                        fontWeight = if (r == 0) FontWeight.Bold else FontWeight.Normal,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        }
    }
}

/** Minimal RFC-4180-ish parser: handles quoted fields, escaped quotes, CRLF. */
private fun parseDelimited(text: String, delimiter: Char): List<List<String>> {
    val rows = ArrayList<List<String>>()
    val field = StringBuilder()
    var row = ArrayList<String>()
    var inQuotes = false
    var i = 0
    while (i < text.length) {
        val ch = text[i]
        when {
            inQuotes -> when {
                ch == '"' && i + 1 < text.length && text[i + 1] == '"' -> { field.append('"'); i++ }
                ch == '"' -> inQuotes = false
                else -> field.append(ch)
            }
            ch == '"' -> inQuotes = true
            ch == delimiter -> { row.add(field.toString()); field.setLength(0) }
            ch == '\n' -> { row.add(field.toString()); field.setLength(0); rows.add(row); row = ArrayList() }
            ch == '\r' -> Unit
            else -> field.append(ch)
        }
        i++
    }
    row.add(field.toString())
    if (row.size > 1 || row.firstOrNull()?.isNotEmpty() == true) rows.add(row)
    return rows
}
