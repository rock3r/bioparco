package dev.sebastiano.departureboard

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class CitySearchTest {
    private val cities =
        listOf(
            City("Santiago", "Chile", "America/Santiago"),
            City("San Francisco", "USA", "America/Los_Angeles"),
            City("São Paulo", "Brazil", "America/Sao_Paulo"),
            City("Busan", "South Korea", "Asia/Seoul"),
            City("Los Angeles", "USA", "America/Los_Angeles"),
        )

    @Test
    fun `an empty query suggests nothing`() {
        assertTrue(searchCities("  ", cities).isEmpty())
    }

    @Test
    fun `names that start with the query come first, then any word, then anywhere`() {
        assertEquals(
            listOf("San Francisco", "Santiago", "São Paulo", "Busan"),
            searchCities("sa", cities).map { it.name },
        )
        assertEquals(listOf("Los Angeles"), searchCities("ange", cities).map { it.name })
    }

    @Test
    fun `accents and case do not matter`() {
        assertEquals(listOf("São Paulo"), searchCities("SAO P", cities).map { it.name })
        assertEquals(listOf("São Paulo"), searchCities("são", cities).map { it.name })
    }

    @Test
    fun `suggestions stop at the limit`() {
        assertEquals(2, searchCities("a", cities, limit = 2).size)
    }

    @Test
    fun `no city is listed twice`() {
        assertEquals(Cities.all.size, Cities.all.map { it.name }.toSet().size)
    }
}
