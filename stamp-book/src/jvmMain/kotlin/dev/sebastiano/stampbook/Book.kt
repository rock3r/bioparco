package dev.sebastiano.stampbook

import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp

/**
 * Where everything sits, in dp, inside the [Content] box. The closed book sits at its left; open,
 * the two stamps fan out to its right and the whole spread fills the box, as in the video, where
 * the book itself never moves.
 */
internal object Book {
    val Content = DpSize(440.dp, 372.dp)

    /** The cover's top-left corner. The cover hinges on its left edge. */
    val FolderOrigin = DpOffset(0.dp, 24.dp)
    val Folder = DpSize(220.dp, 272.dp)
    val Corner = 22.dp

    /**
     * The back board shares the cover's spine, top and bottom, and is a little wider, about 6% as
     * in the video, so it peeks out past the cover's free edge.
     */
    val Board = DpSize(Folder.width + 14.dp, Folder.height)

    /** The pale "quiet" stamp: it slides half out and stays on the back board. */
    val StampA = DpSize(150.dp, 232.dp)
    val StampAClosed = DpOffset(38.dp, 20.dp)
    val StampAOpen = DpOffset(124.dp, 22.dp)

    /** The teal bird stamp: it slides all the way out, past the board's edge. */
    val StampB = DpSize(158.dp, 222.dp)
    val StampBClosed = DpOffset(66.dp, 16.dp)
    val StampBOpen = DpOffset(282.dp, 10.dp)

    /** The bird stamp sits a little crooked inside and nearly straight once out. */
    const val B_CLOSED_TURN = 3.5f
    const val B_OPEN_TURN = -2f

    /**
     * The cover's turn about its hinge. Negative turns its free edge towards the viewer, as a real
     * cover opens: a few degrees ajar when shut, about 60° open, as in the video.
     */
    const val COVER_CLOSED_TURN = -5f
    const val COVER_OPEN_TURN = -60f

    /** The swatch row's middle, below the book. */
    val SwatchCentreY = FolderOrigin.y + Folder.height + 44.dp
    val SwatchSize = 26.dp
    val SwatchGap = 4.dp

    /** Hovering this opens the book: the closed book, cover and board. */
    fun opener(density: Density): Area =
        with(density) {
            Area(
                left = FolderOrigin.x.toPx(),
                top = FolderOrigin.y.toPx(),
                right = (FolderOrigin.x + Board.width).toPx(),
                bottom = (FolderOrigin.y + Board.height).toPx(),
            )
        }

    /** Hovering anywhere over the open spread keeps it open, but not down at the swatches. */
    fun keeper(density: Density): Area =
        with(density) {
            Area(
                left = 0f,
                top = (FolderOrigin.y - 14.dp).toPx(),
                right = Content.width.toPx(),
                bottom = (FolderOrigin.y + Folder.height + 18.dp).toPx(),
            )
        }
}
