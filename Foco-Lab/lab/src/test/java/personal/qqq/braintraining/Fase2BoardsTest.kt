package personal.qqq.braintraining

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import personal.qqq.braintraining.core.*
import personal.qqq.braintraining.design.BrainTheme

/**
 * Fase 2: La maleta del técnico and Vigía de alertas with "Animaciones" off: they render, play with
 * buttons or accessibility actions, report the core log, and no game frame loop runs (FrameProbe).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], qualifiers = "w411dp-h891dp-xhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class Fase2BoardsTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private var result: Pair<Boolean, String>? = null

    private fun show(c: Challenge, motion: Boolean = false) {
        result = null
        compose.setContent {
            BrainTheme {
                CompositionLocalProvider(LocalMotionEnabled provides motion) {
                    var done by remember { mutableStateOf(false) }
                    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
                        when (c.kind) {
                            Kind.SUITCASE ->
                                SuitcaseBoard(c, !done, done, emptyList(), 0, null) { ok, d ->
                                    done = true
                                    result = ok to d
                                }
                            else ->
                                VigilBoard(c, !done, done, emptyList(), 0, null) { ok, d ->
                                    done = true
                                    result = ok to d
                                }
                        }
                    }
                }
            }
        }
        compose.mainClock.autoAdvance = false
        compose.mainClock.advanceTimeByFrame()
    }

    private fun tapTile(item: SuitItem) {
        compose.onNodeWithContentDescription("Ficha ${item.label}").performScrollTo().performClick()
        compose.mainClock.advanceTimeByFrame()
    }

    @Test
    fun suitcaseChainGrowsToTheTargetWithoutAnyFrameLoop() {
        val c = Generator.make("suitcase", 1, 5)
        val t = c.suitcase!!
        val frames = FrameProbe.frames
        show(c)
        compose.onNodeWithText("Nivel 1 · obj ${t.target} · 0/${t.target}").assertExists()
        // The first items are shown one by one, then the suitcase closes and the tray opens.
        compose.onNodeWithText("NUEVO").assertExists()
        compose.mainClock.advanceTimeBy(t.start * (t.showMs + 50) + 200)
        for (len in t.start..t.target) {
            compose
                .onNodeWithText("Mete los $len objetos en orden", substring = true)
                .assertExists()
            t.order(len).forEach(::tapTile)
            compose.mainClock.advanceTimeBy(
                if (len < t.target) t.showMs + Juice.Reveal + 300 else Juice.Hold + 100L
            )
        }
        val (ok, detail) = result!!
        val log = SuitcaseLog.decode(detail)!!
        assertTrue(ok)
        assertEquals(t.target, log.length)
        assertFalse(log.helped)
        assertEquals(t.chain.map { it.key }, log.seen)
        compose.onNodeWithText("¡Cadena completa! ${t.target} de ${t.target}").assertExists()
        assertEquals("no withFrameNanos with animations off", frames, FrameProbe.frames)
    }

    @Test
    fun suitcaseFirstMistakeHelpsSecondEndsTheChain() {
        val c = Generator.make("suitcase", 3, 8)
        val t = c.suitcase!!
        show(c)
        compose.mainClock.advanceTimeBy(t.start * (t.showMs + 50) + 200)
        val tray = SuitcaseTray(t, t.start)
        val wrong = tray.slots.filterNotNull().first { it.key !in t.chain.map { c -> c.key } }
        tapTile(wrong)
        compose.onNodeWithText("Era esta", substring = true).assertExists()
        compose.mainClock.advanceTimeBy(1100)
        // The right one went in with help: the next step continues the same chain.
        t.order(t.start).drop(1).forEach(::tapTile)
        compose.mainClock.advanceTimeBy(t.showMs + Juice.Reveal + 300)
        val second =
            SuitcaseTray(t, t.start + 1).slots.filterNotNull().first {
                it.key !in t.chain.map { c -> c.key }
            }
        tapTile(second)
        compose.mainClock.advanceTimeBy(Juice.Hold + 100L)
        val (ok, detail) = result!!
        val log = SuitcaseLog.decode(detail)!!
        assertFalse(ok)
        assertTrue(log.helped)
        assertEquals(t.start, log.length)
        assertEquals(t.order(t.start)[0].key, log.miss)
        assertEquals(wrong.key, log.tapped)
        compose.onNodeWithText("Cadena de ${t.start} · objetivo ${t.target}").assertExists()
    }

    @Test
    fun suitcaseLongPressShowsTheDescription() {
        val c = Generator.make("suitcase", 1, 2)
        val t = c.suitcase!!
        show(c)
        compose.mainClock.advanceTimeBy(t.start * (t.showMs + 50) + 200)
        val item = t.order(t.start)[0]
        compose
            .onNodeWithContentDescription("Ficha ${item.label}")
            .performScrollTo()
            .performTouchInput { longClick() }
        compose.mainClock.advanceTimeByFrame()
        compose.onNodeWithText("${item.label} · ${item.detail}").assertExists()
    }

    private fun markLine(k: Int, l: VigilLine) {
        val node =
            compose
                .onNodeWithContentDescription("Monitor de registros", substring = true)
                .fetchSemanticsNode()
        val action =
            node.config[SemanticsActions.CustomActions].first {
                it.label == "Marcar ${l.clock} ${l.src} ${l.event}"
            }
        compose.runOnUiThread { action.action() }
        compose.waitForIdle()
        compose.mainClock.advanceTimeByFrame()
    }

    @Test
    fun vigilStepsWithoutFramesAndScoresWithAccessibilityActions() {
        val c =
            (0L..40L).map { Generator.make("vigil", 1, it) }.first { it.vigil!!.groups.size == 2 }
        val t = c.vigil!!
        val frames = FrameProbe.frames
        show(c)
        compose.onNodeWithText("Nv1 · detecta 80 % · falsas ≤ 2").assertExists()
        compose.onNodeWithText("Guardia: ${t.guard}").assertExists()
        val g = t.groups.minBy { it.detectAt }
        // Discrete steps: one line per interval; once the 5th failure is on screen the attack can
        // be marked.
        compose.mainClock.advanceTimeBy(Vigil.timeFor(g.detectAt + 1f, t.intervalMs) + 50)
        markLine(g.detectAt, t.lines[g.detectAt])
        compose.onNodeWithText("Detectadas 1/${t.groups.size} · falsas 0 · lapsos 0").assertExists()
        val noise =
            (0..g.detectAt).first { t.lines[it].group < 0 && it >= g.detectAt - Vigil.VISIBLE + 1 }
        markLine(noise, t.lines[noise])
        compose.onNodeWithText("Detectadas 1/${t.groups.size} · falsas 1 · lapsos 0").assertExists()
        compose.onNodeWithContentDescription("Escudos: 2 y medio de 3").assertExists()
        // The other attack is left to scroll away: a lapse, then the tramo ends by itself.
        compose.mainClock.advanceTimeBy(
            Vigil.timeFor(t.lines.size.toFloat(), t.intervalMs) + Juice.Hold + 500
        )
        val (ok, detail) = result!!
        val log = VigilLog.decode(detail)!!
        assertEquals(1, log.hits)
        assertEquals(1, log.lapses)
        assertEquals(1, log.falses)
        assertFalse(ok)
        assertEquals(log.passed, ok)
        compose.onNodeWithText("Relevo", substring = true).assertExists()
        assertEquals("no withFrameNanos with animations off", frames, FrameProbe.frames)
    }

    @Test
    fun vigilFreezeButtonCountsDown() {
        val c = Generator.make("vigil", 2, 3)
        show(c)
        compose.mainClock.advanceTimeBy(2000)
        compose.onNodeWithText("Congelar (3)").performClick()
        compose.mainClock.advanceTimeByFrame()
        compose.onNodeWithText("Congelar (2)").assertExists()
    }

    @Test
    fun vigilWithMotionUsesOneFrameLoop() {
        val c = Generator.make("vigil", 1, 4)
        val frames = FrameProbe.frames
        show(c, motion = true)
        compose.mainClock.advanceTimeBy(1000)
        assertTrue(FrameProbe.frames > frames)
    }
}
