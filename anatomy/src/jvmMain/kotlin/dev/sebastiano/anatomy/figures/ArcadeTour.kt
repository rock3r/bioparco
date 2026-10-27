// Ported from Anatomy by Ryan (https://github.com/wheresryan22/anatomy), MIT licensed.
// See anatomy/LICENSE.
package dev.sebastiano.anatomy.figures

internal class ArcadeTour(private val m: ArcadeMachine) {
    var clock = 0.0
    private var index = 0
    private val times = listOf(1.4, 9.5, 10.4, 11.4, 12.6, 13.3, 15.2, 24.0, 27.5)

    fun resume() {
        m.touring = true
        clock = if (m.power) 9.0 else 0.0
        index = times.indexOfFirst { it > clock }
    }

    fun tick(dt: Double) {
        clock += dt
        while (index < times.size && clock >= times[index]) {
            when (index++) {
                0,
                6 -> m.insertCoin()
                1 -> m.steer(1.0, 0.5)
                2 -> m.steer(-1.0, 0.6)
                3,
                5 -> m.shoot()
                4 -> m.steer(1.0, 0.35)
                7 -> m.setPower(false)
                8 -> {
                    clock = 0.0
                    index = 0
                    m.pile = 0
                    m.stack = 7
                }
            }
        }
    }
}
