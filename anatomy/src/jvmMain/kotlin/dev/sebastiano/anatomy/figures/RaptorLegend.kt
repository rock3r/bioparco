// Ported from Anatomy by Ryan (https://github.com/wheresryan22/anatomy), MIT licensed.
// See anatomy/LICENSE.
package dev.sebastiano.anatomy.figures

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import dev.sebastiano.anatomy.kit.Palette
import org.jetbrains.jewel.ui.component.Text

/*
 * Under the card: the Ignite button with its lamp, then the eleven assemblies as numbered pills in
 * the legend's order (hover, focus or click one to take the engine apart and light it), then the
 * caption.
 */

@Composable
internal fun RaptorLegend(
    texts: RaptorTexts,
    palette: Palette,
    controls: RaptorControls,
    modifier: Modifier = Modifier,
) {
    val pieces = RaptorEngine.BUILD.pieces
    Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            FireButton(texts, palette, controls)
            Raptor.ORDER.forEachIndexed { index, id ->
                PartButton(
                    index,
                    pieces.getValue(id).name,
                    texts.selected == id,
                    palette,
                    { controls.show(id) },
                    controls::leave,
                )
            }
        }
        Text(RaptorEngine.CAPTION, color = palette.muted, fontSize = 13.sp, lineHeight = 20.sp)
    }
}

@Composable
private fun FireButton(texts: RaptorTexts, palette: Palette, controls: RaptorControls) {
    val lamp =
        when {
            texts.armed -> if (palette.isDark) Color(0xFFFFCF6A) else Color(0xFFC99400)
            texts.firing -> if (palette.isDark) Color(0xFFFF8A3C) else Color(0xFFD9480F)
            else -> if (palette.isDark) Color(0xFF4A4A4A) else Color(0xFFC4C4C4)
        }
    Row(
        Modifier.background(palette.face, RoundedCornerShape(50))
            .border(1.dp, palette.strong.copy(alpha = 0.1f), RoundedCornerShape(50))
            .clickable(onClick = controls::fire)
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Box(Modifier.size(7.dp).background(lamp, CircleShape))
        Text(
            if (texts.firing) "SHUT DOWN" else "IGNITE",
            color = palette.strong,
            fontSize = 10.5.sp,
            fontFamily = FontFamily.Monospace,
            letterSpacing = 0.12.em,
        )
    }
}

@Composable
private fun PartButton(
    index: Int,
    name: String,
    on: Boolean,
    palette: Palette,
    show: () -> Unit,
    hide: () -> Unit,
) {
    val source = remember { MutableInteractionSource() }
    val hovered by source.collectIsHoveredAsState()
    val focused by source.collectIsFocusedAsState()
    val lit = on || hovered || focused
    val shown = remember { BooleanArray(1) }
    val onShow by rememberUpdatedState(show)
    val onHide by rememberUpdatedState(hide)
    LaunchedEffect(hovered, focused) {
        if (hovered || focused) onShow() else if (shown[0]) onHide()
        shown[0] = hovered || focused
    }
    Row(
        Modifier.background(
                if (lit) palette.strong.copy(alpha = 0.04f) else Color.Transparent,
                RoundedCornerShape(50),
            )
            .border(1.dp, palette.strong.copy(alpha = 0.08f), RoundedCornerShape(50))
            .clickable(source, indication = null, onClick = show)
            .padding(horizontal = 10.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(7.dp),
    ) {
        Text(
            "%02d".format(index + 1),
            color = palette.muted,
            fontSize = 10.sp,
            fontFamily = FontFamily.Monospace,
        )
        Text(name, color = if (lit) palette.strong else palette.muted, fontSize = 12.5.sp)
    }
}
