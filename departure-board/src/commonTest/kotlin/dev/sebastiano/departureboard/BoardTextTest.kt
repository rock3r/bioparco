package dev.sebastiano.departureboard

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class BoardTextTest {
    @Test
    fun `city names fold to the board's capitals`() {
        assertEquals("SAO PAULO", boardText("São Paulo"))
        assertEquals("ZURICH", boardText("Zürich"))
        assertEquals("BOGOTA", boardText("Bogotá"))
        assertEquals("REYKJAVIK", boardText("Reykjavík"))
    }

    @Test
    fun `characters the board cannot show become blanks`() {
        assertEquals("A B", boardText("A™B"))
    }

    @Test
    fun `a row is the time, a gutter, then the city padded to its field`() {
        val row = rowText("06:28", "Los Angeles")

        assertEquals(BOARD_COLUMNS, row.length)
        assertEquals("06:28 LOS ANGELES    ", row)
        assertEquals(' ', row[GUTTER_COLUMN])
    }

    @Test
    fun `a long city is cut at its field`() {
        assertEquals("12:00 THE REALLY LONG", rowText("12:00", "The really long name"))
    }

    @Test
    fun `clock text is zero-padded`() {
        assertEquals("06:08", clockText(hour = 6, minute = 8))
        assertEquals("22:29", clockText(hour = 22, minute = 29))
    }

    @Test
    fun `offsets read like the reference`() {
        assertEquals("GMT-7", gmtLabel(offsetSeconds = -7 * 3600))
        assertEquals("GMT+9", gmtLabel(offsetSeconds = 9 * 3600))
        assertEquals("GMT+5:30", gmtLabel(offsetSeconds = 5 * 3600 + 30 * 60))
        assertEquals("GMT-9:30", gmtLabel(offsetSeconds = -(9 * 3600 + 30 * 60)))
        assertEquals("GMT", gmtLabel(offsetSeconds = 0))
    }

    @Test
    fun `every city fits its field`() {
        for (city in Cities.all) {
            assertTrue(boardText(city.name).length <= CITY_CELLS, city.name)
        }
    }
}
