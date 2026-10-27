package dev.sebastiano.departureboard

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.clearText
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.sebastiano.bioparco.tracing.Tracing
import java.time.Instant
import kotlinx.coroutines.delay
import org.jetbrains.jewel.ui.component.OutlinedButton
import org.jetbrains.jewel.ui.component.Text
import org.jetbrains.jewel.ui.component.TextField

/** Each row of a whole-board scramble starts this long after the one above it. */
private const val ROW_LAG_MS = 110L

/** A minute ticking over ripples down the rows a little faster. */
private const val TICK_ROW_LAG_MS = 60L

private val PillShape = RoundedCornerShape(percent = 50)
private val PanelShape = RoundedCornerShape(12.dp)

@Composable
internal fun DepartureBoardStage(modifier: Modifier = Modifier) {
    val board = remember { BoardState() }
    var cities by remember { mutableStateOf(Cities.board) }
    var now by remember { mutableStateOf(Instant.now()) }

    fun writeAll(scramble: Scramble, rowLagMs: Long) {
        val at = Instant.now()
        now = at
        cities.forEachIndexed { row, city ->
            board.write(row, cityRow(city, at), scramble, delayMs = row * rowLagMs)
        }
    }

    fun putOnBoard(city: City) {
        // A city already on the board scrambles in place; a new one takes the bottom row.
        val row = cities.indexOf(city).takeIf { it >= 0 } ?: cities.lastIndex
        cities = cities.toMutableList().also { it[row] = city }
        board.write(row, cityRow(city, Instant.now()), Scramble.Board)
    }

    LaunchedEffect(board) { board.runFrames() }
    LaunchedEffect(board) {
        writeAll(Scramble.Board, ROW_LAG_MS)
        while (true) {
            delay(untilNextMinute(Instant.now()))
            writeAll(Scramble.Tick, TICK_ROW_LAG_MS)
        }
    }

    Box(modifier.fillMaxSize().background(DepartureBoardPalette.Background)) {
        BoardCanvas(
            board = board,
            description = cities.joinToString(", ") { cityRow(it, now).trim() },
            onClick = { writeAll(Scramble.Board, ROW_LAG_MS) },
            modifier = Modifier.fillMaxSize(),
        )
        BottomBar(
            date = dateLabel(now),
            onRefresh = { writeAll(Scramble.Board, ROW_LAG_MS) },
            onPick = ::putOnBoard,
            modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(20.dp),
        )
    }
}

@Composable
private fun BoardCanvas(
    board: BoardState,
    description: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val cache = remember { AtlasCache() }
    Canvas(
        modifier
            .testTag(DepartureBoardTags.BOARD)
            .semantics { contentDescription = description }
            .clickable(interactionSource = null, indication = null, onClick = onClick)
    ) {
        // The only read of the frame time: a scramble redraws this canvas and nothing else.
        val nowMs = board.frameMillis
        Tracing.section("board") { drawBoard(board.model, nowMs, cache) }
    }
}

@Composable
private fun BottomBar(
    date: String,
    onRefresh: () -> Unit,
    onPick: (City) -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(modifier) {
        OutlinedButton(
            onClick = onRefresh,
            modifier = Modifier.align(Alignment.BottomStart).testTag(DepartureBoardTags.REFRESH),
        ) {
            Text("Refresh")
        }
        CitySearch(onPick = onPick, modifier = Modifier.align(Alignment.BottomCenter))
        Text(
            date,
            color = DepartureBoardPalette.Text,
            modifier =
                Modifier.align(Alignment.BottomEnd)
                    .testTag(DepartureBoardTags.DATE)
                    .background(DepartureBoardPalette.Pill, PillShape)
                    .padding(horizontal = 14.dp, vertical = 7.dp),
        )
    }
}

@Composable
private fun CitySearch(onPick: (City) -> Unit, modifier: Modifier = Modifier) {
    val query = rememberTextFieldState()
    val suggestions = searchCities(query.text.toString(), Cities.all, limit = 5)

    fun pick(city: City) {
        onPick(city)
        query.clearText()
    }

    Column(
        modifier.width(340.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        if (suggestions.isNotEmpty()) {
            Suggestions(suggestions, onPick = ::pick)
        }
        SearchField(query, onEnter = { suggestions.firstOrNull()?.let(::pick) })
    }
}

@Composable
private fun SearchField(query: TextFieldState, onEnter: () -> Unit) {
    TextField(
        state = query,
        placeholder = { Text("Type a city name…") },
        modifier =
            Modifier.fillMaxWidth().testTag(DepartureBoardTags.SEARCH).onPreviewKeyEvent {
                val enter = it.key == Key.Enter || it.key == Key.NumPadEnter
                if (enter && it.type == KeyEventType.KeyDown) {
                    onEnter()
                    true
                } else {
                    false
                }
            },
    )
}

@Composable
private fun Suggestions(cities: List<City>, onPick: (City) -> Unit) {
    val instant = Instant.now()
    Column(
        Modifier.fillMaxWidth().background(DepartureBoardPalette.Pill, PanelShape).padding(6.dp)
    ) {
        for (city in cities) {
            SuggestionRow(city, gmtLabel(city, instant), onClick = { onPick(city) })
        }
    }
}

@Composable
private fun SuggestionRow(city: City, offset: String, onClick: () -> Unit) {
    val interactions = remember { MutableInteractionSource() }
    val hovered by interactions.collectIsHoveredAsState()
    Row(
        Modifier.fillMaxWidth()
            .clip(PanelShape)
            .background(
                if (hovered) DepartureBoardPalette.PillHover else DepartureBoardPalette.Pill
            )
            .hoverable(interactions)
            .clickable(interactionSource = interactions, indication = null, onClick = onClick)
            .testTag(DepartureBoardTags.suggestion(city.name))
            .padding(horizontal = 10.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(city.name, color = DepartureBoardPalette.Text, maxLines = 1)
        Text(
            "${city.country} · $offset",
            color = DepartureBoardPalette.Muted,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
