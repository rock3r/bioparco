package dev.sebastiano.emailinvite

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.clearText
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import dev.sebastiano.bioparco.tracing.Tracing
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch

private typealias Anim = Animatable<Float, AnimationVector1D>

/**
 * Every moving value of the pill, plus the [InviteFlow] and [GuestStack] behind them. Composition
 * reads only [phase] and [guests], which change once per open, send or close; everything else is
 * read while drawing or in graphics layers, so a frame of motion redraws without recomposing.
 */
@Stable
internal class InviteMotion(private val scope: CoroutineScope) {
    private val flow = InviteFlow()
    private val stack = GuestStack()
    private var transition: Job? = null

    var phase by mutableStateOf(InvitePhase.Collapsed)
        private set

    /** Who is drawn: the shown guests, then one leaving off the back, if any. */
    var guests by mutableStateOf(stack.shown)
        private set

    val address = TextFieldState()

    /** 0 is the "+ Invite" button, 1 the field. Springs a touch past 1, as the video does. */
    val open = Animatable(0f)
    /** 0 fans the avatars out, 1 tucks them behind the front one. */
    val tuck = Animatable(0f)
    /** How many avatars the stack makes room for. */
    val slots = Animatable(stack.shown.size.toFloat())
    /** The plus turning into an envelope. */
    val icon = Animatable(0f)
    /** The dark "Invite" label greying into the placeholder's first word. */
    val label = Animatable(0f)
    /** " by email" sharpening in after it. */
    val tail = Animatable(0f)
    /** The Send button blurring in. */
    val send = Animatable(0f)
    /** Send goes from grey to black once the address is whole. */
    val armed = Animatable(0f)
    val press = Animatable(0f)
    /** Black to blue, and Send to Sent. */
    val sent = Animatable(0f)
    /** The typed address and the button blurring away as the pill closes. */
    val dissolve = Animatable(0f)
    val hover = Animatable(0f)

    // Each arrival gets fresh values that start at 0, swapped in together with the guest ids
    // below, so no frame pairs a new guest with the last arrival's finished motion.

    /** The newest guest's hop from the field into the front of the stack, then its shine. */
    var hop by mutableStateOf(Animatable(1f))
        private set

    var shine by mutableStateOf(Animatable(1f))
        private set

    /** The previous newest guest losing the accent. */
    var calm by mutableStateOf(Animatable(1f))
        private set

    /** The oldest guest fading off the back of a full stack. */
    var leave by mutableStateOf(Animatable(1f))
        private set

    var newestId: Int? by mutableStateOf(null)
        private set

    var calmingId: Int? by mutableStateOf(null)
        private set

    var leavingId: Int? by mutableStateOf(null)
        private set

    private val slotOf = mutableMapOf<Int, Anim>()

    init {
        stack.shown.forEachIndexed { index, guest ->
            slotOf[guest.id] = Animatable(index.toFloat())
        }
    }

    fun slot(guest: Guest): Float = slotOf[guest.id]?.value ?: 0f

    /** Arms Send whenever the typed address becomes whole, and disarms it when it stops being. */
    suspend fun run() {
        snapshotFlow { EmailAddress.isSendable(address.text.toString()) }
            .distinctUntilChanged()
            .collect { sendable -> armed.animateTo(if (sendable) 1f else 0f, tween(ARM_MILLIS)) }
    }

    fun open() {
        if (!flow.open()) return
        Tracing.section("open") {
            phase = InvitePhase.Editing
            restart {
                address.clearText()
                dissolve.snapTo(0f)
                sent.snapTo(0f)
                // The button stops listening for hover once it opens, so it never hears the exit.
                launch { hover.animateTo(0f, tween(HOVER_MILLIS)) }
                launch { open.animateTo(1f, spring(dampingRatio = 0.78f, stiffness = 380f)) }
                launch { tuck.animateTo(1f, spring(dampingRatio = 0.85f, stiffness = 420f)) }
                launch { icon.animateTo(1f, tween(160)) }
                launch { label.animateTo(1f, tween(200, delayMillis = 60)) }
                launch { tail.animateTo(1f, tween(240, delayMillis = 110)) }
                launch { send.animateTo(1f, tween(200, delayMillis = 220)) }
            }
        }
    }

    fun submit() {
        val typed = address.text.toString()
        if (!flow.submit(typed)) return
        Tracing.section("send") {
            phase = InvitePhase.Sent
            restart {
                launch {
                    press.animateTo(1f, tween(70))
                    press.animateTo(0f, spring(dampingRatio = 0.5f, stiffness = 600f))
                }
                delay(40)
                sent.animateTo(1f, tween(120))
                // The video turns blue about 110 ms after the press and starts closing at 370 ms.
                delay(SENT_HOLD_MILLIS)
                close(arriving = EmailAddress.initialOf(typed))
                flow.settle()
                phase = InvitePhase.Collapsed
            }
        }
    }

    fun cancel() {
        if (!flow.cancel()) return
        Tracing.section("cancel") {
            phase = InvitePhase.Collapsed
            restart { close(arriving = null) }
        }
    }

    fun hovering(over: Boolean) {
        scope.launch { hover.animateTo(if (over) 1f else 0f, tween(HOVER_MILLIS)) }
    }

    /** Folds the field back into the button, bringing in the new guest if there is one. */
    private suspend fun close(arriving: Char?) = coroutineScope {
        // The video staggers it: the address blurs away first while Sent stays crisp a moment,
        // and the stack fans out (with the new guest landing) a beat before the field folds,
        // so the pill briefly grows before it shrinks.
        launch { dissolve.animateTo(1f, tween(220)) }
        launch { send.animateTo(0f, tween(200, delayMillis = 90)) }
        launch { tail.animateTo(0f, tween(120)) }
        launch { label.animateTo(0f, tween(220, delayMillis = 120)) }
        launch { icon.animateTo(0f, tween(180, delayMillis = 100)) }
        launch { tuck.animateTo(0f, spring(dampingRatio = 0.75f, stiffness = 300f)) }
        launch {
            delay(FOLD_DELAY_MILLIS)
            // Slower than opening: the video takes about 350 ms to fold back.
            open.animateTo(0f, spring(dampingRatio = 0.86f, stiffness = 240f))
        }
        if (arriving != null) Tracing.section("arrive") { scope.arrive(arriving) }
        delay(CLOSE_MILLIS)
        address.clearText()
        dissolve.snapTo(0f)
        sent.snapTo(0f)
        armed.snapTo(0f)
    }

    private fun CoroutineScope.arrive(initial: Char) {
        val change = stack.invite(initial)
        val previous = newestId
        newestId = change.arrived.id
        slotOf[change.arrived.id] = Animatable(0f)
        stack.shown.forEachIndexed { index, guest ->
            val slot = slotOf.getValue(guest.id)
            launch {
                slot.animateTo(index.toFloat(), spring(dampingRatio = 0.75f, stiffness = 380f))
            }
        }
        launch {
            slots.animateTo(
                stack.shown.size.toFloat(),
                spring(dampingRatio = 0.8f, stiffness = 420f),
            )
        }
        if (previous != null) {
            val calming = Animatable(0f)
            calm = calming
            calmingId = previous
            launch { calming.animateTo(1f, tween(500, delayMillis = 150)) }
        }
        val left = change.left
        leavingId = left?.id
        guests = if (left != null) stack.shown + left else stack.shown
        if (left != null) {
            val leaving = Animatable(0f)
            leave = leaving
            launch {
                val slot = slotOf.getValue(left.id)
                launch { slot.animateTo(GuestStack.MAX_SHOWN.toFloat(), tween(300)) }
                leaving.animateTo(1f, tween(300))
                slotOf.remove(left.id)
                leavingId = null
                guests = stack.shown
            }
        }
        val hopping = Animatable(0f)
        val shining = Animatable(0f)
        hop = hopping
        shine = shining
        launch {
            hopping.animateTo(1f, tween(HOP_MILLIS, easing = LinearEasing))
            shining.animateTo(1f, tween(SHINE_MILLIS, easing = FastOutSlowInEasing))
        }
    }

    /** Runs [block] as the only transition: starting one cancels whatever was still moving. */
    private fun restart(block: suspend CoroutineScope.() -> Unit) {
        transition?.cancel()
        transition = scope.launch { block() }
    }

    private companion object {
        const val ARM_MILLIS = 160
        const val HOVER_MILLIS = 120
        const val SENT_HOLD_MILLIS = 230L
        const val CLOSE_MILLIS = 480L
        const val FOLD_DELAY_MILLIS = 90L
        const val HOP_MILLIS = 380
        const val SHINE_MILLIS = 520
    }
}
