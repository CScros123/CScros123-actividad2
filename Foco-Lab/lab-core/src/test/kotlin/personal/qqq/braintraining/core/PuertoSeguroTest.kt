package personal.qqq.braintraining.core

import org.junit.Assert.*
import org.junit.Test

/**
 * Paso 1 (docs/AUDITORIA-JUEGOS-V3.md, Fase 6): Puerto Seguro with scenarios, rows without the
 * answer and rule order at 8–10.
 */
class PuertoSeguroTest {
    private val seeds = 0L..999L

    /** What a row shows while the round is being answered. */
    private fun visible(t: FirewallTask, i: Int) =
        listOfNotNull(t.rows[i].port.toString(), t.rows[i].service.takeIf { t.labels })

    private fun String.hasWord(w: String) =
        Regex("(?<![\\p{L}\\p{N}])" + Regex.escape(w.lowercase()) + "(?![\\p{L}\\p{N}])")
            .containsMatchIn(lowercase())

    /**
     * The old shortcut: touch the rows whose visible text (or any word of it) is in the prompt, and
     * apply.
     */
    private fun textBot(c: Challenge): List<Int> {
        val t = c.firewall!!
        val hit =
            t.rows.indices.filter { i ->
                visible(t, i).flatMap { listOf(it) + it.split(" ") }.any { c.prompt.hasWord(it) }
            }
        return when (t.format) {
            Pilot.BLOCK -> t.rows.indices.filter { t.start[it] && it !in hit }
            Pilot.ALLOW -> t.rows.indices.filter { t.start[it] || it in hit }
            else -> if (hit.isEmpty()) t.rows.indices.filter { t.start[it] } else hit
        }
    }

    private fun session(
        level: Int,
        base: Long,
        wrong: (Int) -> Boolean = { false },
    ): List<Challenge> {
        val h = mutableListOf<RoundMark>()
        val out = mutableListOf<Challenge>()
        for (i in 0 until 12) {
            val c = Rounds.make("ports", level, base + i * 10007L, h)
            out += c
            h += RoundMark(c.item, c.format, !wrong(i))
        }
        return out
    }

    @Test
    fun thePromptNeverCarriesTheTextOfTheRightRow() {
        for (l in 1..10) {
            var leaks = 0
            for (s in seeds) {
                val c = Pilot.make("ports", l, s)
                val t = c.firewall!!
                val right =
                    t.rows.indices.filter { t.start[it] != t.expected[it] || t.expected[it] }
                if (right.any { i -> visible(t, i).any { c.prompt.hasWord(it) } }) leaks++
                // Not even the hidden description of a row is quoted.
                assertTrue(
                    "nivel $l semilla $s",
                    t.rows.none { c.prompt.contains(it.use, ignoreCase = true) },
                )
            }
            assertTrue("nivel $l: $leaks de ${seeds.count()}", leaks * 100 < 5 * seeds.count())
        }
    }

    @Test
    fun scenarioTextsNameNoServiceAndNoPort() {
        val names =
            NetSec.portList.flatMap { it.service.split(" ") } +
                NetSec.portList.map { it.port.toString() }
        assertEquals(PortScenarios.bank.size, PortScenarios.bank.map { it.id }.distinct().size)
        assertTrue(PortScenarios.bank.size >= 25)
        for (s in PortScenarios.bank) {
            for (n in names) assertFalse("${s.id}: $n", s.text.hasWord(n))
            assertTrue(
                s.id,
                s.ports.isNotEmpty() &&
                    s.ports.none { it in s.avoid } &&
                    s.traps.none { it in s.ports || it in s.avoid },
            )
            assertTrue(
                s.id,
                (s.ports + s.avoid + s.traps).all { p -> NetSec.portList.any { it.port == p } },
            )
            // The lesson names every port involved, so the line after the round teaches the
            // pairing.
            for (p in s.ports) assertTrue("${s.id}: $p", s.why.hasWord(p.toString()))
            assertFalse(s.why.contains("\n"))
        }
        for (m in PortScenarios.methods) for (n in names) assertFalse("$m: $n", m.hasWord(n))
    }

    @Test
    fun aTextMatchingBotFails() {
        for (l in 1..10) {
            val ok =
                seeds.count {
                    val c = Pilot.make("ports", l, it)
                    c.accepts(textBot(c))
                }
            assertTrue("nivel $l: $ok", ok * 100 < 60 * seeds.count())
            // Doing nothing never works either.
            assertTrue(
                seeds.none {
                    val c = Pilot.make("ports", l, it)
                    c.accepts(c.firewall!!.start.indices.filter { i -> c.firewall!!.start[i] })
                }
            )
        }
    }

    @Test
    fun everyTaskHasOneSolutionAndNoDebatableRow() {
        for (l in 1..10) for (s in seeds) {
            val c = Pilot.make("ports", l, s)
            val t = c.firewall!!
            assertTrue(c.accepts(t.expectedOpen))
            // Exactly one of the 2^n panels is accepted.
            val n = t.rows.size
            assertEquals(
                1,
                (0 until (1 shl n)).count { m ->
                    c.accepts((0 until n).filter { m shr it and 1 == 1 })
                },
            )
            assertTrue(
                t.why.isNotBlank() && !t.why.contains("\n") && c.explanation.startsWith(t.why)
            )
            assertTrue(t.hint.startsWith("Método:"))
            assertEquals(l <= 2, t.labels)
            // A hint never names a port or a service of the panel.
            for (i in t.rows.indices) {
                assertFalse(t.hint.hasWord(t.rows[i].port.toString()))
                assertFalse(t.hint.hasWord(t.rows[i].service))
            }
            if (t.format == Pilot.ORDER) {
                assertTrue(l >= 8)
                assertTrue(t.rules.size in 3..4)
            } else {
                assertTrue(t.rules.isEmpty())
                val sc = PortScenarios.bank.single { it.id == t.key }
                val ports = t.rows.map { it.port }
                assertTrue(ports.none { it in sc.avoid })
                val targets =
                    ports.filterIndexed { i, _ ->
                        if (t.format == Pilot.BLOCK) !t.expected[i] else t.expected[i]
                    }
                assertTrue(targets.isNotEmpty() && targets.all { it in sc.ports })
                // No port of the scenario is left in the panel as a distractor.
                assertTrue(ports.filter { it in sc.ports } == targets)
                if (l <= 6) assertTrue(sc.ports.size == 1 || t.format == Pilot.AUDIT)
                assertTrue(Album.forAnswer("ports", c.explanation, emptySet()) != null)
            }
        }
    }

    @Test
    fun difficultyGrowsWithTheLevel() {
        fun share(l: Int, f: (FirewallTask) -> Boolean) =
            seeds.count { f(Pilot.make("ports", l, it).firewall!!) } * 100 / seeds.count()
        for (l in 1..7) assertEquals(0, share(l) { it.format == Pilot.ORDER })
        assertTrue(share(8) { it.format == Pilot.ORDER } in 15..35)
        for (l in 9..10) assertTrue(share(l) { it.format == Pilot.ORDER } in 40..60)
        for (l in 1..3) assertEquals(0, share(l) { it.format == Pilot.AUDIT })
        for (l in 4..10) assertTrue(share(l) { it.format == Pilot.AUDIT } >= 15)
        // Several ports to decide at once only from level 7.
        for (l in 7..10) assertTrue(
            share(l) { t ->
                t.format != Pilot.ORDER &&
                    t.format != Pilot.AUDIT &&
                    t.start.indices.count { t.start[it] != t.expected[it] } > 1
            } >= 10
        )
        // More time to read the rule list than a scenario, and never less than 20 s.
        for (l in 1..10) for (f in Pilot.formats("ports", l)) assertTrue(
            PortScenarios.deadline(l, f) >= 20000
        )
        for (l in 2..10) assertTrue(
            PortScenarios.deadline(l, Pilot.BLOCK) < PortScenarios.deadline(l - 1, Pilot.BLOCK)
        )
    }

    @Test
    fun deterministicBySeed() {
        for (l in 1..10) for (s in 0L..199L) {
            assertEquals(Pilot.make("ports", l, s), Pilot.make("ports", l, s))
            val c = Pilot.make("ports", l, s, exclude = setOf("c-consola", "o-casa", "r-web"))
            assertEquals(
                c,
                Pilot.make("ports", l, s, exclude = setOf("c-consola", "o-casa", "r-web")),
            )
            assertEquals(
                c.copy(review = true),
                Pilot.make(
                        "ports",
                        l,
                        s,
                        forced = c.format,
                        exclude = setOf("c-consola", "o-casa", "r-web"),
                    )
                    .copy(review = true),
            )
        }
    }

    @Test
    fun noScenarioRepeatsInASessionAndTaskTypesMix() {
        for (l in 1..10) for (s in 0L..99L) {
            val rounds = session(l, s * 7919)
            assertEquals(
                "nivel $l semilla $s",
                12,
                rounds.map { it.firewall!!.key }.distinct().size,
            )
            assertEquals(12, rounds.map { it.prompt + it.firewall!!.rules }.distinct().size)
            assertTrue(rounds.map { it.format }.distinct().size >= 2)
            for (i in 1 until 12) assertNotEquals(rounds[i - 1].format, rounds[i].format)
            // With mistakes, only the reviews repeat.
            val mixed = session(l, s * 7919) { (it + s).toInt() % 3 == 0 }
            assertEquals(
                mixed.count { !it.review },
                mixed.filter { !it.review }.map { it.firewall!!.key }.distinct().size,
            )
        }
    }

    @Test
    fun reviewsRebuildTheSameTask() {
        for (l in 1..10) for (s in 0L..49L) {
            val rounds = session(l, s * 104729) { it == 3 }
            val again =
                rounds.withIndex().filter { it.index > 3 && it.value.item == rounds[3].item }
            assertEquals("nivel $l semilla $s", 1, again.size)
            assertTrue(again.single().value.review)
            assertEquals(rounds[3], again.single().value.copy(review = false))
        }
    }

    @Test
    fun quickModesDoNotRepeatEither() {
        for (l in 1..10) for (s in 0L..49L) {
            val seen = mutableSetOf<String>()
            repeat(8) { i ->
                val c = Quick.make("ports", l, s + i * 10007L, seen)
                assertTrue("nivel $l semilla $s", seen.add(c.firewall!!.key))
            }
        }
    }

    // ---------- Orden de reglas ----------
    @Test
    fun firstMatchEvaluator() {
        val rules =
            listOf(
                FwRule(true, 22, 22),
                FwRule(false, 20, 25),
                FwRule(true, 23, 23),
                FwRule(true, 443, 443),
            )
        assertTrue(FirstMatch.allows(rules, 22))
        assertEquals(0, FirstMatch.index(rules, 22)) // allowed before the block
        assertFalse(FirstMatch.allows(rules, 23))
        assertEquals(1, FirstMatch.index(rules, 23)) // its ALLOW arrives too late
        assertFalse(FirstMatch.allows(rules, 21))
        assertFalse(FirstMatch.allows(rules, 20))
        assertFalse(FirstMatch.allows(rules, 25))
        assertTrue(FirstMatch.allows(rules, 443))
        assertFalse(FirstMatch.allows(rules, 80))
        assertEquals(-1, FirstMatch.index(rules, 80)) // implicit deny
        assertFalse(FirstMatch.allows(emptyList(), 80))
        // Order is what matters: the same rules, reversed, decide otherwise.
        assertTrue(FirstMatch.allows(rules.reversed(), 23))
        // A final "allow everything" replaces the implicit deny, but not the rules above it.
        val open = rules + FwRule(true, 0, 65535)
        assertTrue(FirstMatch.allows(open, 80))
        assertFalse(FirstMatch.allows(open, 23))
        assertTrue(open.last().any)
        assertEquals("DENEGAR :20–25", rules[1].label)
        assertEquals("PERMITIR :22", rules[0].label)
        assertEquals("PERMITIR todo", open.last().label)
        assertEquals(listOf(23), PortScenarios.shadowed(rules, listOf(21, 22, 23, 80, 443)))
        assertEquals(listOf(22), PortScenarios.excepted(rules, listOf(21, 22, 23, 80, 443)))
    }

    @Test
    fun ruleOrderTasksFollowTheEvaluator() {
        var count = 0
        for (l in 8..10) for (s in seeds) {
            val c = Pilot.make("ports", l, s)
            val t = c.firewall!!
            if (t.format != Pilot.ORDER) continue
            count++
            val ports = t.rows.map { it.port }
            assertEquals(ports.map { FirstMatch.allows(t.rules, it) }, t.expected)
            assertTrue("nivel $l semilla $s", PortScenarios.valid(t.rules, ports, l))
            assertEquals(if (l == 8) 3 else if (l == 10) 4 else t.rules.size, t.rules.size)
            // Reading only the ALLOW lines is always wrong, and so is closing everything any DENY
            // names.
            assertNotEquals(
                ports.map { p -> t.rules.any { it.allow && it.matches(p) } },
                t.expected,
            )
            assertTrue(PortScenarios.shadowed(t.rules, ports).isNotEmpty())
            if (l >= 9) {
                assertTrue(PortScenarios.excepted(t.rules, ports).isNotEmpty())
                assertTrue(t.start.none { it })
                assertNotEquals(
                    ports.map { p ->
                        t.rules.any { it.allow && it.matches(p) } &&
                            t.rules.none { !it.allow && it.matches(p) }
                    },
                    t.expected,
                )
            } else {
                assertEquals(ports.map { p -> t.rules.any { it.allow && it.matches(p) } }, t.start)
                assertTrue(ports.any { FirstMatch.index(t.rules, it) < 0 })
            }
            assertTrue(t.expected.any { it } && t.expected.any { !it })
            // The explanation walks every port through the list.
            for (p in ports) assertTrue(
                c.explanation.contains(":$p → ${FirstMatch.decided(t.rules,p)}")
            )
            assertFalse(Regex("\\d").containsMatchIn(c.prompt))
        }
        assertTrue(count > 1000)
    }
}
