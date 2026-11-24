package personal.qqq.braintraining.core

import kotlin.random.Random
import org.junit.Assert.*
import org.junit.Test

/**
 * Fase 3, Turno anti-DDoS: rule engine (one test per rule and priority), generator, live wave, beat
 * clock and log.
 */
class DdosTest {
    private fun p(
        ip: String = "198.51.100.9",
        port: Int = 443,
        flag: Int = Ddos.ACK,
        size: Int = 500,
    ) = DdosPacket(ip, port, flag, size)

    private val all = setOf(Ddos.TELNET, Ddos.SYN, Ddos.RATE, Ddos.DEPLOY, Ddos.MONITOR, Ddos.AMP)

    @Test
    fun eachRuleBlocksItsPacket() {
        assertEquals(
            DdosVerdict(true, Ddos.TELNET),
            Ddos.verdict(p(port = 23), setOf(Ddos.TELNET), 0, false),
        )
        assertEquals(
            DdosVerdict(false, Ddos.NONE),
            Ddos.verdict(p(port = 23), setOf(Ddos.SYN), 0, false),
        )
        assertEquals(
            DdosVerdict(true, Ddos.SYN),
            Ddos.verdict(p(flag = Ddos.SYN_HALF), setOf(Ddos.SYN), 0, false),
        )
        assertFalse(Ddos.verdict(p(flag = Ddos.SYN_OK), setOf(Ddos.SYN), 0, false).block)
        // Rate-limit: the third packet of an IP still passes, the fourth does not.
        assertFalse(Ddos.verdict(p(), setOf(Ddos.RATE), 2, false).block)
        assertEquals(DdosVerdict(true, Ddos.RATE), Ddos.verdict(p(), setOf(Ddos.RATE), 3, false))
        // Deployment: 8080 blocked before, legitimate after.
        assertEquals(
            DdosVerdict(true, Ddos.DEPLOY),
            Ddos.verdict(p(port = 8080), setOf(Ddos.DEPLOY), 0, false),
        )
        assertEquals(
            DdosVerdict(false, Ddos.DEPLOY),
            Ddos.verdict(p(port = 8080), setOf(Ddos.DEPLOY), 0, true),
        )
        assertEquals(
            DdosVerdict(true, Ddos.AMP),
            Ddos.verdict(p(port = 53, flag = Ddos.RESP, size = 3200), setOf(Ddos.AMP), 0, false),
        )
        assertFalse(
            Ddos.verdict(p(port = 53, flag = Ddos.RESP, size = 300), setOf(Ddos.AMP), 0, false)
                .block
        )
        assertFalse(Ddos.verdict(p(port = 22), all, 0, true).block)
    }

    @Test
    fun exceptionWinsOverEveryBlock() {
        val m = Ddos.MONITOR_IP
        for (packet in
            listOf(
                p(m, 23),
                p(m, 443, Ddos.SYN_HALF),
                p(m, 8080),
                p(m, 53, Ddos.RESP, 4000),
            )) assertEquals(DdosVerdict(false, Ddos.MONITOR), Ddos.verdict(packet, all, 9, false))
        // Without the exception rule the monitor is an ordinary IP.
        assertTrue(Ddos.verdict(p(m, 23), all - Ddos.MONITOR, 0, false).block)
        // Deployment comes before Telnet's neighbour rules: 8080 after the switch passes even from
        // a busy IP.
        assertFalse(Ddos.verdict(p(port = 8080), all, 5, true).block)
    }

    @Test
    fun sameSeedSameWaveAndLabelsMatchTheEngine() {
        for (l in 1..10) for (s in 0L..199L) {
            val t = Ddos.make(l, s)
            assertEquals(t, Ddos.make(l, s))
            assertEquals(Ddos.level(l).size, t.packets.size)
            val v = Ddos.label(t)
            t.packets.forEachIndexed { i, pk ->
                assertEquals("level $l seed $s packet $i", pk.bad, v[i].block)
            }
            val lv = Ddos.level(l)
            assertEquals(lv.rules, t.rules)
            assertEquals(lv.bpm, t.bpm)
            // Every rule of the level catches (or, for the exception, saves) at least one packet.
            for (rule in lv.rules) assertTrue(
                "level $l seed $s rule $rule",
                v.any { it.rule == rule },
            )
            assertTrue(v.count { it.block } in 3..t.packets.size / 2)
            assertTrue(v.count { !it.block } >= 8)
            if (lv.lanes == 2) assertTrue(t.packets.any { it.lane == 1 })
        }
        assertNotEquals(Ddos.make(5, 1), Ddos.make(5, 2))
    }

    @Test
    fun levelTableFollowsTheDesign() {
        assertEquals(
            listOf(90, 90, 100, 100, 110, 110, 125, 125, 140, 140),
            (1..10).map { Ddos.level(it).bpm },
        )
        assertEquals(
            listOf(85, 85, 85, 85, 85, 85, 88, 88, 90, 90),
            (1..10).map { Ddos.level(it).goal },
        )
        assertEquals(1, Ddos.plan(1, 0).rules.size)
        assertEquals(2, Ddos.level(1).rules.size)
        assertTrue(Ddos.AMP in Ddos.level(9).rules)
        assertEquals(2, Ddos.level(10).lanes)
        assertTrue(Ddos.make(5, 3).blacklist)
        assertFalse(Ddos.make(2, 3).blacklist)
    }

    @Test
    fun liveWaveScoresComboLoadAndBlacklist() {
        val t = Ddos.make(6, 11)
        val w = DdosShift(t)
        t.packets.indices.forEach { i ->
            w.arrive(i)
            if (w.truth[i].block) assertEquals(DdosShift.Result.OK, w.block(i))
            else assertEquals(DdosShift.Result.OK, w.deliver(i))
        }
        val log = w.log(t.bpm)
        assertTrue(log.perfect)
        assertTrue(log.passed)
        assertEquals(Ddos.tier(Ddos.WAVE), log.maxTier)
        assertEquals(Ddos.MAX_TIER, log.maxTier)
        assertEquals(Ddos.WAVE, log.streak)
        assertTrue(Ddos.RATE in log.learned)
        assertTrue(Ddos.LOAD in log.learned)
        // Malicious packets let through raise the load; three bring the server down.
        val w2 = DdosShift(t)
        val bad = t.packets.indices.filter { w2.truth[it].block }
        bad.take(2).forEach { assertEquals(DdosShift.Result.BAD_PASSED, w2.deliver(it)) }
        assertFalse(w2.down)
        w2.decay(1000)
        assertTrue(w2.load < 80f && w2.load > 75f)
        w2.deliver(bad[2])
        w2.deliver(bad[3])
        assertTrue(w2.down)
        assertTrue(w2.log(t.bpm).down)
        assertFalse(w2.log(t.bpm).passed)
        // Blocking a legitimate packet breaks the combo and is counted once.
        val w3 = DdosShift(t)
        val legit = t.packets.indices.first { !w3.truth[it].block }
        assertEquals(DdosShift.Result.LEGIT_BLOCKED, w3.block(legit))
        assertEquals(0, w3.streak)
        assertEquals(DdosShift.Result.IGNORED, w3.deliver(legit))
        assertEquals(1, w3.log(t.bpm).blockedLegit)
        // Blacklist: ban the flood IP at its 4th packet and its 5th is blocked on arrival.
        val v = Ddos.label(t)
        val flood = t.packets.indices.filter { v[it].rule == Ddos.RATE && v[it].block }
        val w4 = DdosShift(t)
        assertEquals(DdosShift.Result.OK, w4.ban(flood[0]))
        assertEquals(DdosShift.Result.OK, w4.arrive(flood[1]))
        assertEquals(2, w4.state[flood[1]])
        assertTrue(Ddos.BLACKLIST in w4.log(t.bpm).learned)
        assertEquals(DdosShift.Result.IGNORED, DdosShift(Ddos.make(1, 1)).ban(0))
    }

    @Test
    fun beatDoesNotDriftInSixtySeconds() {
        for (bpm in listOf(90, 125, 140, 145)) {
            val b = DdosBeat(bpm)
            val r = Random(bpm)
            var total = 0L
            var lastBeat = 0L
            var maxErr = 0L
            // Jittered frames (6–20 ms) for 60 s.
            while (total < 60_000_000_000L) {
                val dt = r.nextLong(6_000_000L, 20_000_001L)
                b.advance(dt)
                total += dt
                val beat = b.beat
                if (beat != lastBeat) {
                    lastBeat = beat
                    val ideal = beat * 60_000_000_000L / bpm
                    // The beat is detected at the first frame after it: the error is bounded by one
                    // frame, never accumulates.
                    maxErr = maxOf(maxErr, b.clockNs - ideal)
                }
            }
            assertEquals(total, b.clockNs)
            assertEquals(total * bpm / 60_000_000_000L, b.beat)
            assertTrue(maxErr <= 20_000_000L)
            // The beat time itself is exact: beat k at 60 s has an error under 5 ms.
            val k = 60L * bpm / 60
            assertTrue(kotlin.math.abs(b.timeOfBeatNs(k) - 60_000_000_000L) < 5_000_000L)
        }
        assertEquals(Ddos.arrivalMs(19, 90), 19 * 2 * 60_000L / 90)
    }

    @Test
    fun tuningIsAnnounced() {
        val perfect = DdosLog(20, 20, 0, 0, 3, false, 40, 0)
        val down = DdosLog(10, 20, 2, 3, 1, true, 10, 100)
        assertEquals(5, Ddos.tuning(listOf(perfect, perfect, perfect)).bpmDelta)
        assertNotNull(Ddos.tuning(listOf(perfect, perfect, perfect)).note)
        assertEquals(-10, Ddos.tuning(listOf(perfect, down)).bpmDelta)
        assertEquals(0, Ddos.tuning(listOf(perfect)).bpmDelta)
        assertNull(Ddos.tuning(emptyList()).note)
    }

    @Test
    fun logRoundTripsAndNamesTheWeakestRule() {
        val log =
            DdosLog(
                17,
                20,
                1,
                2,
                2,
                false,
                30,
                68,
                mapOf(Ddos.RATE to 2, Ddos.SYN to 1),
                setOf(0, 1, 9),
                88,
                125,
                7,
            )
        assertEquals(log, DdosLog.decode(log.encode()))
        assertNull(DdosLog.decode("x"))
        assertNull(DdosLog.decode(null))
        assertEquals(85, log.accuracy)
        assertFalse(log.passed)
        assertEquals(Ddos.RATE, Ddos.weakest(listOf(log, log.copy(failed = mapOf(Ddos.SYN to 1)))))
    }
}
