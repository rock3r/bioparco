package dev.sebastiano.layoutmorph

/** What a card shows in place of a photo. */
enum class Art {
    /** A maker's portrait. */
    Portrait,
    /** A photographed piece: see [Piece.subject]. */
    Photo,
    /**
     * A piece shown as its name and price on a plain card, as the original does for half of them.
     */
    Text,
}

/** The photographed pieces, each painted by `CardArt`. */
enum class Subject {
    None,
    Lamp,
    Throw,
    Chair,
    Carafe,
    Seascape,
}

/**
 * One card: a maker or a piece. [tall] is the card's height over its width in the masonry, where
 * every piece keeps its own proportions. [look] picks a maker's portrait colours.
 */
data class Piece(
    val title: String,
    val detail: String,
    val trailing: String,
    val art: Art,
    val tall: Float,
    val subject: Subject = Subject.None,
    val look: Int = 0,
    val price: Int = 0,
    val stock: Int = 0,
    val rating: Float = 0f,
) {
    val isMaker: Boolean
        get() = art == Art.Portrait
}

/**
 * The autumn edit of a small makers' shop: fourteen pieces from ten makers, in the order the grid
 * reads them (row by row, eight across) and the list reads them (column by column, eight down).
 */
object ShopCollection {
    const val NAME = "Fern & Flint"

    val pieces: List<Piece> =
        listOf(
            product("Travertine tray", "Owen Clarke · Honed travertine", 96, 12, 4.8f, 0.95f),
            maker("Nora Lind", "Ceramicist · Bergen", 2, look = 0, tall = 1.32f),
            product(
                "Opal table lamp",
                "Ines Duarte · Hand-blown glass",
                248,
                14,
                4.8f,
                1.2f,
                Subject.Lamp,
            ),
            maker("Amara Okafor", "Weaver · Accra", 2, look = 1, tall = 1.18f),
            product("Stoneware mug pair", "Nora Lind · Speckled stoneware", 58, 30, 4.9f, 1.25f),
            maker("Ines Duarte", "Glassblower · Lisbon", 2, look = 2, tall = 1.3f),
            product("Wool floor cushion", "Freya Holm · Felted wool", 134, 8, 4.7f, 1.0f),
            product(
                "Oat linen throw",
                "Amara Okafor · Stonewashed linen",
                186,
                9,
                4.9f,
                1.05f,
                Subject.Throw,
            ),
            maker("Tomás Vidal", "Woodworker · Porto", 2, look = 3, tall = 1.25f),
            product("Oak serving board", "Tomás Vidal · White oak, oiled", 72, 21, 4.8f, 0.82f),
            maker("Owen Clarke", "Stonemason · Bath", 1, look = 4, tall = 1.32f),
            product(
                "Lounge chair",
                "Leo Brandt · Woven seat and oak",
                1_240,
                3,
                4.7f,
                1.3f,
                Subject.Chair,
            ),
            maker("Leo Brandt", "Upholsterer · Hamburg", 1, look = 5, tall = 1.2f),
            product("Beeswax taper pair", "Maya Patel · Pure beeswax", 24, 40, 4.9f, 1.32f),
            maker("Kenji Mori", "Metalworker · Osaka", 1, look = 6, tall = 1.3f),
            product(
                "Linen napkin set",
                "Amara Okafor · Washed linen, set of 4",
                42,
                26,
                4.8f,
                0.9f,
            ),
            product(
                "Night carafe set",
                "Ines Duarte · Mouth-blown glass",
                64,
                11,
                4.7f,
                0.85f,
                Subject.Carafe,
            ),
            maker("Sam Reyes", "Photographer · Valparaíso", 1, look = 7, tall = 1.25f),
            product("Brass bookends", "Kenji Mori · Solid brass, pair", 110, 7, 4.8f, 1.25f),
            maker("Maya Patel", "Chandler · Jaipur", 1, look = 8, tall = 1.32f),
            product("Walnut stool", "Tomás Vidal · Black walnut", 390, 4, 4.9f, 1.4f),
            product(
                "Dusk at the cape",
                "Sam Reyes · Archival pigment print",
                120,
                25,
                4.8f,
                0.9f,
                Subject.Seascape,
            ),
            maker("Freya Holm", "Felter · Reykjavík", 1, look = 9, tall = 1.3f),
            product("Ash glaze vase", "Nora Lind · Wood-fired stoneware", 84, 6, 4.8f, 1.2f),
        )

    val pieceCount: Int = pieces.count { !it.isMaker }
    val makerCount: Int = pieces.count { it.isMaker }

    /** The pieces' mean rating, to one decimal. */
    val averageRating: Float =
        pieces.filter { !it.isMaker }.map { it.rating }.average().let { (it * 10).toInt() / 10f }

    private fun maker(name: String, detail: String, count: Int, look: Int, tall: Float) =
        Piece(
            title = name,
            detail = detail,
            trailing = if (count == 1) "1 piece" else "$count pieces",
            art = Art.Portrait,
            tall = tall,
            look = look,
        )

    private fun product(
        name: String,
        detail: String,
        price: Int,
        stock: Int,
        rating: Float,
        tall: Float,
        subject: Subject = Subject.None,
    ) =
        Piece(
            title = name,
            detail = detail,
            trailing = formatPrice(price),
            art = if (subject == Subject.None) Art.Text else Art.Photo,
            tall = tall,
            subject = subject,
            price = price,
            stock = stock,
            rating = rating,
        )
}

/** `$1,240` style prices: whole dollars with a thousands comma. */
fun formatPrice(dollars: Int): String {
    val digits = dollars.toString()
    val grouped = digits.reversed().chunked(3).joinToString(",").reversed()
    return "$$grouped"
}
