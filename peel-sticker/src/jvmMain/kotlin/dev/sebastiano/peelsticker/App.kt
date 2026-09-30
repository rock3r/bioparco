package dev.sebastiano.peelsticker

import androidx.compose.foundation.background
import androidx.compose.foundation.draganddrop.dragAndDropTarget
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draganddrop.DragAndDropEvent
import androidx.compose.ui.draganddrop.DragAndDropTarget
import androidx.compose.ui.draganddrop.DragData
import androidx.compose.ui.draganddrop.dragData
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.io.File
import java.net.URI
import org.jetbrains.jewel.intui.standalone.theme.IntUiTheme
import org.jetbrains.jewel.ui.component.SegmentedControl
import org.jetbrains.jewel.ui.component.SegmentedControlButtonData
import org.jetbrains.jewel.ui.component.Text
import org.jetbrains.skia.Image

/**
 * The page: a header naming the shine, the sticker on its table, and a tab per shine. Keys 1 to 5
 * pick a shine too, P swaps the picture, and dropping an image file on the window prints it.
 */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun App(modifier: Modifier = Modifier) {
    var mode by remember { mutableStateOf(ShineMode.Prism) }
    var dropped by remember { mutableStateOf<StickerPicture.Dropped?>(null) }
    var picture by remember { mutableStateOf<StickerPicture>(StickerPicture.Ex) }
    val focus = remember { FocusRequester() }
    val dropTarget = remember {
        object : DragAndDropTarget {
            override fun onDrop(event: DragAndDropEvent): Boolean {
                val image = readImage(event.dragData()) ?: return false
                StickerPicture.Dropped(image).also {
                    dropped = it
                    picture = it
                }
                return true
            }
        }
    }

    IntUiTheme(isDark = false) {
        Column(
            modifier =
                modifier
                    .fillMaxSize()
                    .background(PageColors.page)
                    .drawWithCache {
                        // The dots only change with the size: paint them once, then copy them.
                        val dots =
                            ImageBitmap(
                                size.width.toInt().coerceAtLeast(1),
                                size.height.toInt().coerceAtLeast(1),
                            )
                        CanvasDrawScope().draw(this, layoutDirection, Canvas(dots), size) {
                            dotGrid()
                        }
                        onDrawBehind { drawImage(dots) }
                    }
                    .dragAndDropTarget(
                        shouldStartDragAndDrop = { it.dragData() is DragData.FilesList },
                        target = dropTarget,
                    )
                    .onPreviewKeyEvent { event ->
                        if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                        val shortcut =
                            ShineMode.entries.firstOrNull { event.key == numberKey(it.number) }
                        when {
                            shortcut != null -> mode = shortcut
                            event.key == Key.P -> picture = nextPicture(picture, dropped)
                            else -> return@onPreviewKeyEvent false
                        }
                        true
                    }
        ) {
            Header(mode)
            Box(modifier = Modifier.fillMaxWidth().weight(1f).background(PageColors.page)) {
                PeelStage(picture, mode, focus, Modifier.fillMaxSize())
                Text(
                    "Drag an edge to peel · move over it for the shine",
                    modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 16.dp),
                    style = TextStyle(fontSize = 10.5.sp),
                    color = PageColors.hint,
                )
            }
            Box(
                modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
                contentAlignment = Alignment.Center,
            ) {
                ShineTabs(mode) { mode = it }
            }
        }
    }
}

@Composable
private fun Header(mode: ShineMode) {
    Row(
        modifier =
            Modifier.fillMaxWidth().padding(start = 14.dp, end = 8.dp, top = 20.dp, bottom = 14.dp),
        verticalAlignment = Alignment.Bottom,
    ) {
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                "PEEL STICKER",
                style =
                    TextStyle(
                        fontSize = 10.sp,
                        letterSpacing = 1.6.sp,
                        fontWeight = FontWeight.Medium,
                    ),
                color = PageColors.muted,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    "${mode.number}",
                    style = TextStyle(fontSize = 20.sp),
                    color = PageColors.faint,
                )
                Text(
                    mode.label,
                    style = TextStyle(fontSize = 20.sp, fontWeight = FontWeight.Medium),
                    color = PageColors.ink,
                )
            }
        }
        Text(
            "Drop a picture · P swaps",
            style = TextStyle(fontSize = 9.5.sp, fontFamily = FontFamily.Monospace),
            color = PageColors.faint,
        )
    }
}

@Composable
private fun ShineTabs(selected: ShineMode, onSelect: (ShineMode) -> Unit) {
    SegmentedControl(
        buttons =
            ShineMode.entries.map { mode ->
                SegmentedControlButtonData(
                    selected = mode == selected,
                    content = {
                        Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                            Text("${mode.number}", color = PageColors.faint)
                            Text(mode.label)
                        }
                    },
                    onSelect = { onSelect(mode) },
                )
            }
    )
}

private fun numberKey(number: Int): Key =
    when (number) {
        1 -> Key.One
        2 -> Key.Two
        3 -> Key.Three
        4 -> Key.Four
        else -> Key.Five
    }

/** X, then G, then the last dropped picture, if any. */
private fun nextPicture(current: StickerPicture, dropped: StickerPicture?): StickerPicture =
    when (current) {
        StickerPicture.Ex -> StickerPicture.Gee
        StickerPicture.Gee -> dropped ?: StickerPicture.Ex
        else -> StickerPicture.Ex
    }

/** The first dropped file Skia can decode, or null. */
@OptIn(ExperimentalComposeUiApi::class)
private fun readImage(data: DragData): ImageBitmap? {
    val files = (data as? DragData.FilesList)?.readFiles() ?: return null
    for (uri in files) {
        val image = runCatching {
            Image.makeFromEncoded(File(URI(uri)).readBytes()).toComposeImageBitmap()
        }
            .getOrNull()
        if (image != null) return image
    }
    return null
}

/** The dotted paper behind the header and the tabs. The stage covers the middle. */
private fun DrawScope.dotGrid() {
    val step = 13.5.dp.toPx()
    val radius = 0.8.dp.toPx()
    var y = step / 2f
    while (y < size.height) {
        var x = 9.dp.toPx()
        while (x < size.width) {
            drawCircle(PageColors.dot, radius, Offset(x, y))
            x += step
        }
        y += step
    }
}

private object PageColors {
    val page = Color(0xFFF3F2F5)
    val dot = Color(0xFFDDDCE0)
    val ink = Color(0xFF151515)
    val muted = Color(0xFF7A7A7E)
    val faint = Color(0xFFA3A3A8)
    val hint = Color(0xFFA0A0A5)
}
