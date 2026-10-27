package dev.sebastiano.departureboard

/** A city the board can show, with its IANA time zone. */
data class City(val name: String, val country: String, val zone: String)

/**
 * Cities for the search field. The first five fill the board, as in the reference; the `San…` group
 * is there so typing "san" gives the reference's list of suggestions.
 */
object Cities {
    val all: List<City> =
        listOf(
            City("Los Angeles", "USA", "America/Los_Angeles"),
            City("New York", "USA", "America/New_York"),
            City("London", "UK", "Europe/London"),
            City("Dubai", "UAE", "Asia/Dubai"),
            City("Tokyo", "Japan", "Asia/Tokyo"),
            City("San Francisco", "USA", "America/Los_Angeles"),
            City("San Juan", "Puerto Rico", "America/Puerto_Rico"),
            City("San Salvador", "El Salvador", "America/El_Salvador"),
            City("Santiago", "Chile", "America/Santiago"),
            City("Santo Domingo", "Dominican Republic", "America/Santo_Domingo"),
            City("São Paulo", "Brazil", "America/Sao_Paulo"),
            City("Seattle", "USA", "America/Los_Angeles"),
            City("Seoul", "South Korea", "Asia/Seoul"),
            City("Shanghai", "China", "Asia/Shanghai"),
            City("Singapore", "Singapore", "Asia/Singapore"),
            City("Stockholm", "Sweden", "Europe/Stockholm"),
            City("Sydney", "Australia", "Australia/Sydney"),
            City("Amsterdam", "Netherlands", "Europe/Amsterdam"),
            City("Anchorage", "USA", "America/Anchorage"),
            City("Athens", "Greece", "Europe/Athens"),
            City("Auckland", "New Zealand", "Pacific/Auckland"),
            City("Bangkok", "Thailand", "Asia/Bangkok"),
            City("Berlin", "Germany", "Europe/Berlin"),
            City("Bogotá", "Colombia", "America/Bogota"),
            City("Buenos Aires", "Argentina", "America/Argentina/Buenos_Aires"),
            City("Cairo", "Egypt", "Africa/Cairo"),
            City("Chicago", "USA", "America/Chicago"),
            City("Delhi", "India", "Asia/Kolkata"),
            City("Denver", "USA", "America/Denver"),
            City("Dublin", "Ireland", "Europe/Dublin"),
            City("Helsinki", "Finland", "Europe/Helsinki"),
            City("Hong Kong", "China", "Asia/Hong_Kong"),
            City("Honolulu", "USA", "Pacific/Honolulu"),
            City("Istanbul", "Türkiye", "Europe/Istanbul"),
            City("Jakarta", "Indonesia", "Asia/Jakarta"),
            City("Johannesburg", "South Africa", "Africa/Johannesburg"),
            City("Kathmandu", "Nepal", "Asia/Kathmandu"),
            City("Kuala Lumpur", "Malaysia", "Asia/Kuala_Lumpur"),
            City("Lagos", "Nigeria", "Africa/Lagos"),
            City("Lima", "Peru", "America/Lima"),
            City("Lisbon", "Portugal", "Europe/Lisbon"),
            City("Madrid", "Spain", "Europe/Madrid"),
            City("Manila", "Philippines", "Asia/Manila"),
            City("Mexico City", "Mexico", "America/Mexico_City"),
            City("Milan", "Italy", "Europe/Rome"),
            City("Moscow", "Russia", "Europe/Moscow"),
            City("Mumbai", "India", "Asia/Kolkata"),
            City("Nairobi", "Kenya", "Africa/Nairobi"),
            City("Paris", "France", "Europe/Paris"),
            City("Reykjavík", "Iceland", "Atlantic/Reykjavik"),
            City("Rio de Janeiro", "Brazil", "America/Sao_Paulo"),
            City("Rome", "Italy", "Europe/Rome"),
            City("Toronto", "Canada", "America/Toronto"),
            City("Vancouver", "Canada", "America/Vancouver"),
            City("Zürich", "Switzerland", "Europe/Zurich"),
        )

    /** The board as it opens. */
    val board: List<City> = all.take(BOARD_ROWS)
}

/**
 * Up to [limit] of [cities] whose name matches [query], ignoring case and accents. Names that start
 * with the query come first, then names with a word that does, then names that contain it anywhere;
 * each group in alphabetical order.
 */
fun searchCities(query: String, cities: List<City>, limit: Int = 6): List<City> {
    val key = searchKey(query.trim())
    if (key.isEmpty()) return emptyList()
    return cities
        .mapNotNull { city ->
            val name = searchKey(city.name)
            val rank =
                when {
                    name.startsWith(key) -> 0
                    name.split(' ').any { it.startsWith(key) } -> 1
                    key in name -> 2
                    else -> return@mapNotNull null
                }
            Triple(rank, name, city)
        }
        .sortedWith(compareBy({ it.first }, { it.second }))
        .take(limit)
        .map { it.third }
}
