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
 * Fase 3: Turno anti-DDoS, Ruta de paquetes and Parejas de incidentes with "Animaciones" off: they
 * render, play with taps, buttons or accessibility actions, report the core log, and no game frame
 * loop runs (FrameProbe).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], qualifiers = "w411dp-h891dp-xhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class Fase3BoardsTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private var result: Pair<Boolean, String>? = null
    /** Every cue the board asked for (paso 2: the pulse of Turno anti-DDoS is one of them). */
    private val cues = mutableListOf<FeedbackCue>()

    private fun show(
        c: Challenge,
        motion: Boolean = false,
        theme: String? = null,
        previous: List<String> = emptyList(),
    ) {
        result = null
        cues.clear()
        compose.setContent {
            BrainTheme {
                CompositionLocalProvider(
                    LocalMotionEnabled provides motion,
                    LocalFeedback provides { cues += it },
                ) {
                    var done by remember { mutableStateOf(false) }
                    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
                        when (c.kind) {
                            Kind.DDOS ->
                                DdosBoard(c, !done, done, previous, previous.size, null) { ok, d ->
                                    done = true
                                    result = ok to d
                                }
                            Kind.ROUTE ->
                                RouteBoard(c, !done, done, emptyList(), 0, null) { ok, d ->
                                    done = true
                                    result = ok to d
                                }
                            else ->
                                PairsBoard(c, !done, done, emptyList(), 0, null, theme) { ok, d ->
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

    private fun click(desc: String) {
        compose
            .onAllNodesWithContentDescription(desc)
            .onFirst()
            .performSemanticsAction(SemanticsActions.OnClick)
        compose.waitForIdle()
        compose.mainClock.advanceTimeBy(32)
    }

    private fun action(node: String, label: String) {
        val n = compose.onNodeWithContentDescription(node, substring = true).fetchSemanticsNode()
        val a = n.config[SemanticsActions.CustomActions].first { it.label == label }
        compose.runOnUiThread { a.action() }
        compose.waitForIdle()
        compose.mainClock.advanceTimeByFrame()
    }

    private fun desc(p: DdosPacket) =
        "Paquete ${p.ip} puerto ${p.port} ${p.flagText} ${p.sizeText} bytes"

    // ---------- Turno anti-DDoS ----------
    @Test
    fun ddosPerfectWaveStepsWithoutFrames() {
        val c = Generator.make("ddos", 1, 3)
        val t = c.ddos!!
        val v = Ddos.label(t)
        val step = Ddos.intervalMs(t.bpm)
        val frames = FrameProbe.frames
        show(c)
        compose.onNodeWithText("Nv1 · 90 BPM · ≥ 85 %").assertExists()
        compose.onNodeWithText("PARTE DEL JEFE DE TURNO").assertExists()
        compose.mainClock.advanceTimeBy(DDOS_BRIEF_MS + 50)
        compose.onAllNodesWithText("PARTE DEL JEFE DE TURNO").assertCountEquals(0)
        for (i in t.packets.indices) {
            if (v[i].block) click(desc(t.packets[i]))
            if (i == 15) compose.onAllNodesWithText("COMBO ×1").assertCountEquals(0)
            compose.mainClock.advanceTimeBy(step)
        }
        compose.mainClock.advanceTimeBy(Ddos.TRAVEL * step + Juice.Hold + 200)
        val (ok, detail) = result!!
        val log = DdosLog.decode(detail)!!
        assertTrue(ok)
        assertTrue(log.perfect)
        assertEquals(20, log.correct)
        assertEquals(0, log.passedBad)
        assertTrue(Ddos.TELNET in log.learned)
        compose.onNodeWithText("Oleada superada").assertExists()
        assertEquals("no withFrameNanos with animations off", frames, FrameProbe.frames)
        // Paso 2: without animations the pulse is one accent per packet that enters, and nothing
        // clicks in between.
        assertEquals(t.packets.size, cues.count { it == FeedbackCue.PULSE_ACCENT })
        assertEquals(0, cues.count { it == FeedbackCue.PULSE })
    }

    // ---------- Paso 2 (docs/AUDITORIA-JUEGOS-V3.md): partida of 5 chained waves ----------
    @Test
    fun ddosFirstWaveBriefsTheRulesOfTheDay() {
        // Wave 1: the rules of the day and the promise of a new one.
        show(Generator.make("ddos", 1, 3, 0))
        compose.onNodeWithText("Regla de hoy: Bloquea :23").assertExists()
        compose.onNodeWithText("En la oleada 3 llega una regla nueva.").assertExists()
        compose.onNodeWithText("Oleada 1/5").assertExists()
        compose.onAllNodesWithText("SELLADO ✓").assertCountEquals(0)
    }

    @Test
    fun ddosPlainWavesGetAShortBanner() {
        // Wave 2: only a short banner; the belt starts after it.
        show(Generator.make("ddos", 1, 3, 1))
        compose.onNodeWithText("OLEADA 2/5").assertExists()
        compose.mainClock.advanceTimeBy(Ddos.BANNER_MS + 50)
        compose.onAllNodesWithText("OLEADA 2/5").assertCountEquals(0)
    }

    @Test
    fun ddosBossSealsTheNewRuleInWaveThree() {
        // Wave 3: the seal, with its lesson, and the rule marked as new in the header.
        val c = Generator.make("ddos", 1, 3, 2)
        assertEquals(Ddos.SYN, c.ddos!!.sealed)
        show(c)
        compose.onNodeWithText("Nueva regla: SYN flood").assertExists()
        compose.onNodeWithText("SELLADO ✓").assertExists()
        compose.onNodeWithText("SYN ✕ · nueva").assertExists()
        assertTrue(FeedbackCue.UNLOCK in cues)
        compose.mainClock.advanceTimeBy(DDOS_BRIEF_MS + 50)
        compose.onAllNodesWithText("SELLADO ✓").assertCountEquals(0)
    }

    @Test
    fun ddosLastWaveIsThePeak() {
        // Wave 5: the peak, 10 BPM faster.
        show(Generator.make("ddos", 1, 3, 4))
        compose.onNodeWithText("OLEADA 5/5 · PICO").assertExists()
        compose.onNodeWithText("Nv1 · 100 BPM · ≥ 85 %").assertExists()
        compose.onNodeWithText("Oleada 5/5 · PICO").assertExists()
    }

    @Test
    fun ddosComboAndPointsCrossWaves() {
        // The wave before left a streak of 20 (combo ×4) and 66 points: this one starts from there.
        val before = DdosLog(20, 20, 0, 0, 4, false, 66, 0, streak = 20).encode()
        val c = Generator.make("ddos", 1, 5, 1)
        val t = c.ddos!!
        val v = Ddos.label(t)
        val step = Ddos.intervalMs(t.bpm)
        show(c, previous = listOf(before))
        compose.onNodeWithText("COMBO ×4").assertExists()
        compose.onNodeWithText("66 pts").assertExists()
        compose.mainClock.advanceTimeBy(Ddos.BANNER_MS + 50)
        for (i in t.packets.indices) {
            if (v[i].block) click(desc(t.packets[i]))
            compose.mainClock.advanceTimeBy(step)
        }
        compose.mainClock.advanceTimeBy(Ddos.TRAVEL * step + Ddos.AFTER_MS + 200)
        val log = DdosLog.decode(result!!.second)!!
        assertTrue(log.perfect)
        assertEquals(20 + t.packets.size, log.streak)
        assertEquals(4 * t.packets.size, log.score)
        assertEquals(Ddos.MAX_TIER, log.maxTier)
        compose.onNodeWithText("${66+4*t.packets.size} pts").assertExists()
    }

    @Test
    fun ddosMistakesDoNotStopTheBeltFromLevelTwo() {
        // Level 2: a legitimate packet is blocked, and the wave still ends exactly when its clock
        // says (level 1 would add a 1 s stop).
        val c = Generator.make("ddos", 2, 8, 0)
        val t = c.ddos!!
        val v = Ddos.label(t)
        show(c)
        compose.mainClock.advanceTimeBy(DDOS_BRIEF_MS + 50)
        val legit = t.packets.indices.first { !v[it].block }
        compose.mainClock.advanceTimeBy(Ddos.arrivalMs(legit, t.bpm))
        click(desc(t.packets[legit]))
        // Block every malicious packet that enters from here on, so the server stays up and the
        // wave runs to its end.
        var now = Ddos.arrivalMs(legit, t.bpm) + 32
        for (i in t.packets.indices) {
            if (i <= legit || !v[i].block) continue
            val at = Ddos.arrivalMs(i, t.bpm) + 60
            compose.mainClock.advanceTimeBy(at - now)
            click(desc(t.packets[i]))
            now = at + 32
        }
        compose.mainClock.advanceTimeBy(Ddos.waveMs(t.bpm, t.packets.size) - now - 100)
        assertNull(result)
        compose.mainClock.advanceTimeBy(100 + Ddos.AFTER_MS + 300)
        val log = DdosLog.decode(result!!.second)!!
        assertEquals(1, log.blockedLegit)
        assertFalse(log.down)
    }

    @Test
    fun ddosPulseClicksOncePerBeat() {
        val c = Generator.make("ddos", 2, 1, 0)
        val t = c.ddos!!
        // With animations the frame loop never goes idle: the clock is driven by hand from the
        // start.
        compose.mainClock.autoAdvance = false
        show(c, motion = true)
        compose.mainClock.advanceTimeBy(DDOS_BRIEF_MS - 100)
        assertEquals(0, cues.count { it == FeedbackCue.PULSE || it == FeedbackCue.PULSE_ACCENT })
        compose.mainClock.advanceTimeBy(100)
        compose.mainClock.advanceTimeBy(10_000)
        // 10 s at 90 BPM are 15 beats: one click each (the frame that ends the briefing may hold
        // one back), accents on every other beat.
        val pulses = cues.count { it == FeedbackCue.PULSE }
        val accents = cues.count { it == FeedbackCue.PULSE_ACCENT }
        val beats = 10_000L * t.bpm / 60_000
        assertTrue(
            "pulses $pulses accents $accents",
            pulses + accents in (beats - 1).toInt()..beats.toInt(),
        )
        assertTrue(kotlin.math.abs(pulses - accents) <= 1)
    }

    @Test
    fun ddosLetsMaliciousPassUntilTheServerFalls() {
        val c = Generator.make("ddos", 3, 8, 0)
        val t = c.ddos!!
        show(c)
        compose.mainClock.advanceTimeBy(DDOS_BRIEF_MS + 50)
        // Block one legitimate packet: it is counted once and the rule explanation appears.
        val legit = t.packets.indices.first { !Ddos.label(t)[it].block }
        compose.mainClock.advanceTimeBy(Ddos.arrivalMs(legit, t.bpm))
        click(desc(t.packets[legit]))
        compose.onNodeWithText("legítimos bloqueados 1 ·", substring = true).assertExists()
        compose.mainClock.advanceTimeBy(Ddos.waveMs(t.bpm) + 6_000)
        val log = DdosLog.decode(result!!.second)!!
        assertFalse(result!!.first)
        assertTrue(log.down)
        assertEquals(1, log.blockedLegit)
        assertTrue(log.passedBad >= 3)
        compose.onNodeWithText("Fin de oleada: servidor caído").assertExists()
    }

    @Test
    fun ddosBlacklistByAccessibilityAction() {
        val c = (0L..30L).map { Generator.make("ddos", 5, it) }.first { true }
        val t = c.ddos!!
        val v = Ddos.label(t)
        val flood = t.packets.indices.filter { v[it].rule == Ddos.RATE && v[it].block }
        show(c)
        // Block every malicious packet up to the 4th of the flood IP (otherwise the server would
        // fall first), then ban that IP.
        compose.mainClock.advanceTimeBy(DDOS_BRIEF_MS + 50)
        for (i in 0 until flood[0]) {
            if (v[i].block)
                click(
                    desc(t.packets[i]) +
                        if (true)
                            ", número ${(0..i).count{t.packets[it].ip==t.packets[i].ip}} de esta IP"
                        else ""
                )
            compose.mainClock.advanceTimeBy(Ddos.intervalMs(t.bpm) - 32)
        }
        action(
            desc(t.packets[flood[0]]) + ", número 4 de esta IP",
            "Lista negra ${t.packets[flood[0]].ip}",
        )
        compose.onNodeWithText("Lista negra: ${t.packets[flood[0]].ip} ✕").assertExists()
    }

    @Test
    fun ddosWithMotionUsesOneFrameLoop() {
        val frames = FrameProbe.frames
        show(Generator.make("ddos", 2, 1), motion = true)
        compose.mainClock.advanceTimeBy(500)
        assertTrue(FrameProbe.frames > frames)
    }

    // ---------- Ruta de paquetes ----------
    private fun demo(t: RouteTask) = (t.demo.size - 1) * Route.HOP_MS + 300 + Juice.Reveal + 50

    @Test
    fun routeDemoThenDrawWithAccessibilityActions() {
        val c = Generator.make("route", 1, 4)
        val t = c.route!!
        val frames = FrameProbe.frames
        show(c)
        compose.onNodeWithText("Nv1 · 5 routers · 3 saltos", substring = true).assertExists()
        compose.onNodeWithText("Observa", substring = true).assertExists()
        compose.mainClock.advanceTimeBy(demo(t))
        compose.onNodeWithText("Dibuja la ruta de A a B").assertExists()
        t.answer.drop(1).forEach { action("Mapa de red", "Router ${t.names[it]}") }
        compose
            .onNodeWithContentDescription(
                "Ruta: " + t.answer.joinToString(" → ") { t.names[it] },
                substring = true,
            )
            .assertExists()
        compose.onNodeWithText("Enviar").performClick()
        compose.mainClock.advanceTimeByFrame()
        compose.onNodeWithText("¡Llegó! Ruta exacta").assertExists()
        compose.mainClock.advanceTimeBy(Juice.Hold + 100L)
        val (ok, detail) = result!!
        val log = RouteLog.decode(detail)!!
        assertTrue(ok)
        assertTrue(log.ok)
        assertEquals(3, log.hops)
        assertTrue(0 in log.learned)
        assertEquals("no withFrameNanos with animations off", frames, FrameProbe.frames)
    }

    @Test
    fun routeUndoAndDetourIsReported() {
        val c =
            (0L..60L)
                .map { Generator.make("route", 3, it) }
                .first { r ->
                    val t = r.route!!
                    (0 until t.n).any { it != t.answer[1] && t.linked(t.source, it) }
                }
        val t = c.route!!
        show(c)
        compose.mainClock.advanceTimeBy(demo(t))
        action("Mapa de red", "Router ${t.names[t.answer[1]]}")
        // Tapping the last router again undoes it; the button too.
        action("Mapa de red", "Router ${t.names[t.answer[1]]}")
        compose
            .onNodeWithContentDescription("Mapa de red con ${t.n} routers. Ruta: A")
            .assertExists()
        action("Mapa de red", "Router ${t.names[t.answer[1]]}")
        compose.onNodeWithText("Deshacer").performClick()
        compose.mainClock.advanceTimeByFrame()
        // A wrong first hop: any neighbour of A that is not on the answer.
        val wrong = (0 until t.n).first { it != t.answer[1] && t.linked(t.source, it) }
        action("Mapa de red", "Router ${t.names[wrong]}")
        compose.onNodeWithText("Enviar").performClick()
        compose.mainClock.advanceTimeByFrame()
        compose.onNodeWithText("Fallo de", substring = true).assertExists()
        compose.mainClock.advanceTimeBy(Juice.Hold + 100L)
        assertFalse(result!!.first)
        assertFalse(RouteLog.decode(result!!.second)!!.ok)
    }

    @Test
    fun routeTtlNeedsTheDiscardMark() {
        val c = (0L..60L).map { Generator.make("route", 9, it) }.first { it.route!!.dropped }
        val t = c.route!!
        show(c)
        compose.mainClock.advanceTimeBy(demo(t))
        compose.onNodeWithContentDescription("TTL ${t.ttl}").assertExists()
        t.answer.drop(1).forEach { action("Mapa de red", "Router ${t.names[it]}") }
        compose.onNodeWithText("✖ descartado").performClick()
        compose.mainClock.advanceTimeByFrame()
        compose.onNodeWithText("Enviar").performClick()
        compose.mainClock.advanceTimeBy(Juice.Hold + 100L)
        val log = RouteLog.decode(result!!.second)!!
        assertTrue(log.ok)
        assertTrue(7 in log.learned)
    }

    // ---------- Parejas de incidentes ----------
    private fun cell(b: IncidentBoard, i: Int) =
        "Carta boca abajo, fila ${i/b.cols+1}, columna ${i%b.cols+1}"

    @Test
    fun pairsBoardIsClearedWithoutFrames() {
        val c = Generator.make("pairs", 1, 6)
        val b = c.pairs!!
        val frames = FrameProbe.frames
        show(c)
        compose.onNodeWithText("Nv1 · 12 cartas · ≤ ${b.target} intentos · llevas 0").assertExists()
        for (g in b.entries.indices) {
            val ids = b.cards.filter { it.entry == g }.map { it.id }
            ids.forEach { click(cell(b, it)) }
            compose
                .onAllNodesWithContentDescription("Pareja hecha: ${b.cards[ids[0]].text}")
                .assertCountEquals(1)
        }
        compose.onNodeWithText(b.entries.last().lesson).assertExists()
        compose.mainClock.advanceTimeBy(Juice.Celebrate + 100L)
        val (ok, detail) = result!!
        val log = PairsLog.decode(detail)!!
        assertTrue(ok)
        assertEquals(b.groups, log.attempts)
        assertEquals(b.groups, log.maxStreak)
        compose.onNodeWithText("¡Tablero limpio!").assertExists()
        assertEquals("no withFrameNanos with animations off", frames, FrameProbe.frames)
    }

    @Test
    fun pairsMissFlipsBackAndThemeOnlyChangesTheLook() {
        val c = Generator.make("pairs", 3, 2)
        val b = c.pairs!!
        show(c, theme = "theme:terminal")
        compose.onNodeWithText("Terminal verde", substring = true).assertExists()
        val a = b.cards.first { it.entry == 0 }.id
        val x = b.cards.first { it.entry == 1 }.id
        click(cell(b, a))
        compose.onNodeWithContentDescription("Carta: ${b.cards[a].text}").assertExists()
        click(cell(b, x))
        compose.onNodeWithText("llevas 1", substring = true).assertExists()
        compose.mainClock.advanceTimeBy(400)
        compose.onAllNodesWithContentDescription("Carta: ", substring = true).assertCountEquals(0)
        // Double tap equivalent: a matched pair shows its lesson again through its accessibility
        // action.
        val ids = b.cards.filter { it.entry == 2 }.map { it.id }
        ids.forEach { click(cell(b, it)) }
        compose.mainClock.advanceTimeBy(4_100)
        action("Pareja hecha: ${b.cards[ids[0]].text}", "Ver lección")
        compose.onNodeWithText(b.entries[2].lesson).assertExists()
    }
}
