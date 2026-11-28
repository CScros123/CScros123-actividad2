package personal.qqq.braintraining

import androidx.compose.ui.graphics.Color
import kotlin.random.Random
import org.junit.Assert.*
import org.junit.Test

/** Fase 2, shared juice (docs/DISENO-JUEGOS-V2.md §1). */
class JuiceTest {
    @Test
    fun particlesDoNotAllocateAfterWarmUp() {
        val p = Particles(Random(1))
        val before = Spark.created
        repeat(400) {
            Juice.burst(p, Juice.Burst.HIT, 10f, 10f, Juice.Good, true)
            Juice.confetti(p, 300f, 50f, true)
            Juice.trail(p, 1f, 1f, Juice.Info, true)
            p.step(.016f, 0f)
            assertTrue(p.live <= Particles.CAPACITY)
        }
        repeat(200) { p.step(.05f) }
        assertFalse(p.active)
        assertEquals("the pool never grows", before, Spark.created)
    }

    @Test
    fun poolIsCappedAndRecyclesDeadSparks() {
        val p = Particles(Random(2))
        repeat(40) { Juice.burst(p, Juice.Burst.HIT, 0f, 0f, Color.Red, true) }
        assertEquals(Particles.CAPACITY, p.live)
        assertFalse(p.emit(0f, 0f, 0f, 0f, 1f, Color.Red, 1f, true))
        p.step(2f)
        assertEquals(0, p.live)
        assertTrue(p.emit(0f, 0f, 0f, 0f, 1f, Color.Red, 1f, true))
        assertEquals(1, p.live)
    }

    /**
     * Paso 2: the confetti of a perfect DDoS wave used to get a 1 px strip as its width; it now
     * covers the belt wherever it sits.
     */
    @Test
    fun confettiSpreadsOverTheStripItIsGiven() {
        val left = 44f
        val width = 900f
        val xs = (0 until 5).map { Juice.confettiX(it, left, width) }
        assertTrue(xs.all { it > left && it < left + width })
        assertTrue(xs.last() - xs.first() >= width * .55f)
        assertEquals(xs.sorted(), xs)
        assertEquals(left + width / 2, xs[2], width * .06f)
        // The old call (width = beltX * 2 + 1 with beltX = 0) put every burst within one pixel.
        assertTrue((0 until 5).map { Juice.confettiX(it, 0f, 1f) }.let { it.max() - it.min() } < 1f)
        val p = Particles(Random(3))
        Juice.confetti(p, left, width, 50f, true)
        assertTrue(p.live >= 5 * Juice.Burst.CONFETTI.count)
    }

    @Test
    fun nothingIsEmittedWithAnimationsOff() {
        val p = Particles(Random(3))
        Juice.burst(p, Juice.Burst.SHATTER, 0f, 0f, Juice.Bad, false)
        Juice.confetti(p, 100f, 0f, false)
        Juice.trail(p, 0f, 0f, Juice.Info, false)
        assertFalse(p.active)
    }

    @Test
    fun tokensMatchTheDesign() {
        assertEquals(
            listOf(90, 140, 240, 420, 650, 900, 1400),
            listOf(
                Juice.Tick,
                Juice.Quick,
                Juice.Move,
                Juice.Reveal,
                Juice.Settle,
                Juice.Hold,
                Juice.Celebrate,
            ),
        )
        assertEquals(
            listOf(6f to 180, 12f to 260, 20f to 360),
            Shake.values().map { it.dp to it.ms },
        )
        assertEquals(listOf(10, 18, 14, 14), Juice.Burst.values().map { it.count })
        assertEquals(1200f, Juice.Burst.SHATTER.gravity, 0f)
        assertTrue(Juice.Overshoot.transform(.6f) > 1f)
        // The draw-phase spring settles at 1 with a small overshoot.
        assertEquals(0f, Juice.springAt(0f), 0f)
        assertEquals(1f, Juice.springAt(3f), .001f)
        assertTrue((1..60).map { Juice.springAt(it / 60f) }.max() < 1.1f)
    }

    @Test
    fun comboRisesOneSemitonePerStepUpToSeven() {
        var last = 0f
        for (n in 1..8) {
            val r = GameTones.comboRate(n)
            assertTrue(r >= last)
            last = r
        }
        assertEquals(1f, GameTones.comboRate(1), .0001f)
        assertEquals(GameTones.semitones(7), GameTones.comboRate(8), .0001f)
        assertEquals(GameTones.comboRate(8), GameTones.comboRate(30), 0f)
        assertEquals(R.raw.lab_correct, GameTones.sample(FeedbackCue.COMBO))
        listOf(FeedbackCue.COMBO, FeedbackCue.ALARM, FeedbackCue.WHOOSH).forEach {
            assertTrue(GameTones.baseVolume(it) in .01f..0.5f)
        }
    }
}
