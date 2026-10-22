package io.github.jayyo.readeverything

import io.github.jayyo.readeverything.core.Csv
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CsvTest {
    @Test fun quotesCommasAndNewlines() {
        val t = Csv.parse("a,b\r\n\"x, y\",\"say \"\"hi\"\"\"\n\"two\nlines\",\n")
        assertEquals(listOf(listOf("a", "b"), listOf("x, y", "say \"hi\""), listOf("two\nlines", "")), t.rows)
    }

    @Test fun lastRecordWithoutNewlineAndEmptyQuotedField() {
        assertEquals(listOf(listOf("a"), listOf("")), Csv.parse("a\n\"\"").rows)
        assertEquals(listOf(listOf("a", "")), Csv.parse("a,").rows)
        assertEquals(emptyList<List<String>>(), Csv.parse("").rows)
    }

    @Test fun tabsAndRowCap() {
        assertEquals(listOf(listOf("a", "b")), Csv.parse("a\tb", '\t').rows)
        val t = Csv.parse("1\n2\n3\n4\n", maxRows = 2)
        assertEquals(2, t.rows.size)
        assertTrue(t.truncated)
    }

    @Test fun quoteInsideAnUnquotedFieldIsLiteral() {
        assertEquals(listOf(listOf("B-001", "Monitor 27\"", "Office 1")), Csv.parse("B-001,Monitor 27\",Office 1\n").rows)
    }

    @Test fun wideRowsAreCapped() {
        val t = Csv.parse(",".repeat(100_000) + "\nx\n")
        assertEquals(Csv.MAX_COLS, t.rows[0].size)
        assertTrue(t.truncated)
    }
}
