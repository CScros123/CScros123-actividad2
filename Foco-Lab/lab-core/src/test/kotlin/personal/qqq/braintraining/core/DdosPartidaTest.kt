package personal.qqq.braintraining.core

import kotlin.random.Random
import org.junit.Assert.*
import org.junit.Test

/**
 * Paso 2 (docs/AUDITORIA-JUEGOS-V3.md, FASE 6): the Turno anti-DDoS partida of 5 chained waves and
 * the per-game format. Acceptance: rule sealed in wave 3, top combo reachable, pulse per beat
 * without drift, partida of 2–5 min, Adaptive by packets, mission still reachable and the same
 * script giving the same log.
 */
class DdosPartidaTest {
    /** Plays one wave: [act] says whether the player gets packet i right. Returns the log. */
    private fun play(t: DdosTask, carry: Int = 0, act: (Int) -> Boolean = { true }): DdosLog {
        val w = DdosShift(t, carry)
        val step = Ddos.intervalMs(t.bpm)
        for (i in t.packets.indices) {
            w.arrive(i)
            val right = act(i)
            if (w.truth[i].block == right) w.block(i)
            w.deliver(i)
            w.decay(step)
        }
        return w.log(t.bpm)
    }

    private fun partida(
        level: Int,
        seed: Long,
        act: (Int, Int) -> Boolean = { _, _ -> true },
    ): List<DdosLog> {
        val logs = mutableListOf<DdosLog>()
        for (wave in 0 until Ddos.WAVES) logs +=
            play(Ddos.make(level, seed + wave * 10007L, wave), logs.lastOrNull()?.streak ?: 0) {
                act(wave, it)
            }
        return logs
    }

    @Test
    fun theBossSealsTheNewRuleInWaveThreeOfEveryPartida() {
        for (l in 1..10) {
            val new = Ddos.sealed(l)
            val lv = Ddos.level(l)
            assertEquals(lv.rules.max(), new)
            for (w in 0 until Ddos.WAVES) {
                val p = Ddos.plan(l, w)
                assertEquals(
                    "level $l wave $w",
                    if (w == Ddos.SEAL_WAVE) new else Ddos.NONE,
                    p.sealed,
                )
                assertEquals(if (w < Ddos.SEAL_WAVE) lv.rules - new else lv.rules, p.rules)
                assertTrue(p.rules.isNotEmpty())
                assertEquals(lv.bpm + if (w == Ddos.PEAK_WAVE) Ddos.PEAK_BPM else 0, p.bpm)
                assertEquals(w == Ddos.PEAK_WAVE, p.peak)
            }
            for (s in 0L..99L) for (w in 0 until Ddos.WAVES) {
                val t = Ddos.make(l, s, w)
                val v = Ddos.label(t)
                assertEquals(w, t.wave)
                assertEquals(Ddos.plan(l, w).rules, t.rules)
                assertEquals(Ddos.plan(l, w).sealed, t.sealed)
                t.packets.forEachIndexed { i, pk ->
                    assertEquals("level $l seed $s wave $w packet $i", pk.bad, v[i].block)
                }
                // Before the seal no packet belongs to the new rule; from the seal on it always
                // decides at least one.
                assertEquals(
                    "level $l seed $s wave $w",
                    w >= Ddos.SEAL_WAVE,
                    v.any { it.rule == new },
                )
                for (rule in t.rules) assertTrue(
                    "level $l seed $s wave $w rule $rule",
                    v.any { it.rule == rule },
                )
                assertTrue(v.count { it.block } in 3..t.packets.size / 2)
            }
        }
        // Each pair of levels starts from what the previous pair ended with.
        for (l in 3..10) assertEquals(Ddos.level(l - 2).rules, Ddos.plan(l, 0).rules)
        // The 12-round flow passes the wave through the generator.
        for (w in 0 until Ddos.WAVES) assertEquals(w, Generator.make(Ddos.ID, 4, 9, w).ddos!!.wave)
        assertEquals(Ddos.SEAL_WAVE, Generator.make(Ddos.ID, 4, 9).ddos!!.wave)
        assertEquals(Generator.make(Ddos.ID, 4, 9, 3), Rounds.make(Ddos.ID, 4, 9, emptyList(), 3))
    }

    @Test
    fun everyComboTierIsReachableAndTheStreakCrossesWaves() {
        assertEquals(
            listOf(1, 1, 2, 2, 3, 3, 4, 4),
            listOf(0, 4, 5, 9, 10, 14, 15, 400).map { Ddos.tier(it) },
        )
        for (l in 1..10) {
            // One wave is enough for the top tier, and for the "Combo ×3" mission.
            assertTrue(Ddos.tier(Ddos.level(l).size) >= Ddos.MAX_TIER)
            val logs = partida(l, 5)
            assertEquals(Ddos.MAX_TIER, logs.first().maxTier)
            // Perfect partida: the streak never resets, so from wave 2 on every packet scores ×4.
            assertEquals(logs.sumOf { it.total }, logs.last().streak)
            val n = logs.first().total
            assertEquals(4 * 1 + 5 * 2 + 5 * 3 + (n - 14) * 4, logs.first().score)
            assertEquals(n * 4, logs[1].score)
            val run = Ddos.summary(logs)
            assertEquals(run.total, run.correct)
            assertEquals(Ddos.WAVES, run.passed)
            assertEquals(Ddos.MAX_TIER, run.maxTier)
        }
        // A mistake drops one combo tier (x4 -> x3, streak 10) instead of resetting to 0.
        val logs = partida(3, 8) { wave, i -> !(wave == 1 && i == 10) }
        assertEquals(10 + logs[1].total - 11, logs[1].streak)
        assertEquals(logs[1].streak + logs[2].total, logs[2].streak)
        val m =
            Missions.today(java.time.LocalDate.of(2026, 11, 12), listOf("bits"), listOf(Ddos.ID))
                .firstOrNull { it.game == Ddos.ID }
        if (m != null) assertTrue(m.target <= Ddos.MAX_TIER)
    }

    @Test
    fun onePulsePerBeatWithoutDrift() {
        for (bpm in listOf(90, 100, 110, 125, 140, 150)) {
            val b = DdosBeat(bpm)
            val r = Random(bpm)
            var total = 0L
            var pulses = 0L
            var worst = 0L
            while (total < 60_000_000_000L) {
                val dt = r.nextLong(6_000_000L, 20_000_001L)
                val n = b.advance(dt)
                total += dt
                assertTrue(n in 0..1)
                pulses += n
                // The click sounds on the first frame after its beat: never later than one frame,
                // and the delay does not add up.
                if (n == 1) worst = maxOf(worst, b.clockNs - b.timeOfBeatNs(b.beat))
            }
            assertEquals(b.beat, pulses)
            assertEquals(total * bpm / 60_000_000_000L, pulses)
            assertTrue(worst <= 20_000_000L)
            assertTrue(kotlin.math.abs(b.timeOfBeatNs(bpm.toLong()) - 60_000_000_000L) < 5_000_000L)
        }
    }

    @Test
    fun aPartidaLastsBetweenTwoAndFiveMinutesAtEveryLevel() {
        for (l in 1..10) {
            val ms = Ddos.partidaMs(l)
            assertTrue("level $l: $ms ms", ms in 120_000L..300_000L)
        }
        // Tempo tuning (−10 after a fall, +5 after three perfect waves) keeps it inside too.
        for (l in 1..10) for (delta in listOf(-10, 5)) {
            val ms =
                (0 until Ddos.WAVES).sumOf { w ->
                    val p = Ddos.plan(l, w)
                    Ddos.briefMs(w) + Ddos.waveMs(p.bpm + delta, p.size) + Ddos.AFTER_MS
                }
            assertTrue("level $l $delta: $ms", ms in 115_000L..300_000L)
        }
        assertEquals(Ddos.BRIEF_MS, Ddos.briefMs(0))
        assertEquals(Ddos.BRIEF_MS, Ddos.briefMs(Ddos.SEAL_WAVE))
        assertEquals(Ddos.BANNER_MS, Ddos.briefMs(1))
    }

    @Test
    fun adaptiveCountsPacketsNotWaves() {
        val f = Formats.of(Ddos.ID)
        assertEquals(Ddos.WAVES, f.units)
        assertTrue(f.chained)
        assertTrue(f.packets)
        assertEquals("5 oleadas", f.label)
        assertEquals(GameFormat(), Formats.of("ports"))
        assertEquals(12, Formats.of("route").units)
        assertFalse(Formats.of("pairs").chained)
        assertTrue(Formats.valid(Ddos.ID, 5))
        assertTrue(Formats.valid(Ddos.ID, 12))
        assertFalse(Formats.valid("ports", 5))
        assertFalse(Formats.valid(Ddos.ID, 6))
        fun log(c: Int, goal: Int = 85) =
            DdosLog(c, 20, 0, 20 - c, 1, false, c, 0, goal = goal).encode()
        // 17+17+17+17+17 = 85 of 100 packets: counts as a partida that goes up, whatever each
        // wave's own goal says.
        val details = List(5) { log(17, 90) }
        assertEquals(85 to 100, Formats.measure(Ddos.ID, 0, 5, details))
        var p = Progress(3)
        repeat(2) {
            val (c, t) = Formats.measure(Ddos.ID, 0, 5, details)
            p = Adaptive.next(p, Ddos.ID, c, t, null)
        }
        assertEquals(4, p.level)
        // 84 of 100 does not, even with four waves passed.
        val almost = List(4) { log(18) } + log(12)
        assertEquals(84 to 100, Formats.measure(Ddos.ID, 4, 5, almost))
        assertEquals(0, Adaptive.next(Progress(3, up = 1), Ddos.ID, 84, 100, null).up)
        // Under 60 % twice goes down.
        assertEquals(2, Adaptive.next(Progress(3, down = 1), Ddos.ID, 59, 100, null).level)
        // Results saved before the format (12 waves) and other games keep counting units: restoring
        // a backup rebuilds the same levels.
        assertEquals(11 to 12, Formats.measure(Ddos.ID, 11, 12, List(12) { log(20) }))
        assertEquals(4 to 5, Formats.measure(Ddos.ID, 4, 5, listOf("x", "", "", "", "")))
        assertEquals(11 to 12, Formats.measure("vigil", 11, 12, List(12) { "x" }))
        val run = Ddos.run(details)!!
        assertEquals(85, run.accuracy)
        assertEquals(85, run.score)
        assertNull(Ddos.run(details.take(4)))
    }

    /**
     * Q7: a player at the level's target skill goes up within 3 partidas; a clearly weaker one does
     * not.
     */
    @Test
    fun theCurveIsFair() {
        fun rises(level: Int, q: Double, partidas: Int, seed: Long): Boolean {
            val r = Random(seed)
            var p = Progress(level)
            repeat(partidas) { k ->
                val logs = partida(level, seed * 31 + k) { _, _ -> r.nextDouble() < q }
                val run = Ddos.summary(logs)
                p = Adaptive.next(p, Ddos.ID, run.correct, run.total, null)
                if (p.level > level) return true
            }
            return false
        }
        for (l in listOf(1, 4, 7, 9)) {
            val good = (0L until 200L).count { rises(l, .96, 3, it) }
            val weak = (0L until 200L).count { rises(l, .78, 6, it) }
            assertTrue("level $l good $good", good >= 170)
            assertTrue("level $l weak $weak", weak <= 10)
        }
    }

    @Test
    fun wavesOfAPartidaDoNotRepeatTheSameMix() {
        var varied = 0
        val perPrefix = HashMap<String, IntArray>()
        for (l in 1..10) for (s in 0L..99L) {
            val tasks = (0 until Ddos.WAVES).map { Ddos.make(l, s * 7919 + it * 10007L, it) }
            // The mix of a wave: how many malicious packets each rule brings.
            val mixes =
                tasks.map { t ->
                    Ddos.label(t)
                        .filter { it.block }
                        .groupingBy { it.rule }
                        .eachCount()
                        .toSortedMap()
                        .toString()
                }
            if (mixes.distinct().size >= 3) varied++
            assertEquals(tasks.size, tasks.map { it.packets }.distinct().size)
            for (t in tasks) for (pk in t.packets) {
                if (pk.ip == Ddos.MONITOR_IP || pk.ip.startsWith("10.0.53.")) continue
                val a = perPrefix.getOrPut(pk.ip.substringBeforeLast('.')) { IntArray(2) }
                a[if (pk.bad) 1 else 0]++
            }
        }
        assertTrue("varied $varied", varied >= 900)
        // The prefix of an IP gives nothing away: every range carries the same share of malicious
        // packets (±8 points).
        val shares = perPrefix.values.map { it[1] * 100 / (it[0] + it[1]) }
        assertTrue(perPrefix.size >= 8)
        assertTrue("shares $shares", shares.max() - shares.min() <= 8)
    }

    /**
     * Q10: the engine has no clock of its own and no randomness, so the same script of taps leaves
     * the same log.
     */
    @Test
    fun theSameScriptGivesTheSameLog() {
        for (l in 1..10) {
            val r = Random(l)
            val script = List(Ddos.WAVES) { List(Ddos.level(l).size) { r.nextInt(10) > 0 } }
            val a = partida(l, 77) { w, i -> script[w][i] }
            val b = partida(l, 77) { w, i -> script[w][i] }
            assertEquals(a.map { it.encode() }, b.map { it.encode() })
            for (log in a) {
                assertEquals(log, DdosLog.decode(log.encode()))
                assertTrue(log.correct <= log.total)
            }
        }
    }
}
