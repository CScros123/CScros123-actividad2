package personal.qqq.braintraining.core

import java.time.*
import org.junit.Assert.*
import org.junit.Test

/**
 * Lote A of docs/EXAMEN-JUEGOS.md and Fase 2 of docs/DISENO-JUEGOS-V2.md: pruned menu, "Cálculo"
 * and saved history with retired ids.
 */
class CatalogTest {
    private val fase2 = setOf("grid", "working", "matrix", "calc")
    private val legacy =
        listOf(
            "search",
            "pattern",
            "flanker",
            "choice",
            "compass",
            "budget",
            "unitprice",
            "planning",
            "sequence",
            "reaction",
            "math",
            "percent",
            "fraction",
            "equation",
            "ratio",
            "balance",
            "series",
        )

    @Test
    fun atMostFourteenVisibleGames() {
        // 6 Redes + 3 Mente + 2 Arcade in Games (Fase 3 of docs/DISENO-JUEGOS-V2.md, §0); Pulso and
        // Parejas matemáticas left the menu.
        assertEquals(NetSec.ids, Games.visible.filter { it.area == Area.NETSEC }.map { it.id })
        assertEquals(
            listOf("suitcase", "vigil", "route"),
            Games.visible.filter { it.area != Area.NETSEC && it.area != Area.ARCADE }.map { it.id },
        )
        assertEquals(
            listOf("ddos", "pairs"),
            Games.visible.filter { it.area == Area.ARCADE }.map { it.id },
        )
        assertTrue(Games.visible.size <= 14)
        // Fase 3: Órbitas is replaced by Ruta de paquetes; still defined (and hidden) for its
        // history.
        assertTrue(Games.get("orbit").hidden)
        assertEquals("Órbitas", Games.get("orbit").title)
        assertTrue(Games.visible.none { it.id in legacy })
        // Fase 2: Destellos, Última huella, Transforma and Cálculo leave the menu, but stay defined
        // for their history.
        assertTrue(Games.visible.none { it.id in fase2 })
        assertTrue(fase2.all { Games.get(it).hidden })
        // Only one "Pulso" left on screen: the arcade one.
        assertTrue(Games.visible.none { it.title == "Pulso" })
    }

    @Test
    fun historyWithRetiredIdsStillResolves() {
        assertEquals(legacy.toSet(), Games.retiredLoteA)
        assertEquals(legacy.toSet() + fase2 + "orbit", Games.retired)
        val zone = ZoneId.of("Europe/Madrid")
        val today = LocalDate.of(2026, 11, 2)
        val sessions =
            (legacy + fase2 + "orbit" + "ddos").mapIndexed { i, id ->
                EventSession("s$i", id, today.atTime(9, i).atZone(zone).toInstant().toEpochMilli())
            }
        for (s in sessions) {
            val g = Games.get(s.game)
            assertEquals(s.game, g.id)
            assertTrue(g.title.isNotBlank())
            assertTrue(Evaluation.advice(s.game, ErrorProfile(1, 0, 0, 0)).isNotBlank())
            // Old ids still generate (reviewing an old result, a restored draft).
            for (l in 1..10) Rounds.make(s.game, l, 7L)
        }
        // Area totals as home, the garden and the results screen compute them.
        val byArea = sessions.groupBy { Games.get(it.game).area }
        assertEquals(Area.values().toSet() - Area.NETSEC, byArea.keys)
        // An id this version does not know never breaks the history views.
        assertTrue(Games.get("future-game").hidden)
        assertEquals("future-game", Games.get("future-game").id)
        assertFalse(Games.get("route").hidden)
        val snapshot = EventEngine.build(today, today.minusDays(7), sessions, emptyList(), zone)
        assertTrue(snapshot.toString().isNotEmpty())
    }

    /**
     * Fase 2 acceptance: no retired game in missions (any day, any input) nor in Relámpago (network
     * games only).
     */
    @Test
    fun retiredGamesNeverInMissionsNorFlash() {
        val day = LocalDate.of(2026, 11, 9)
        val mind = Games.visible.filter { it.area != Area.NETSEC }.map { it.id }
        for (d in 0L..120L) {
            val ms =
                Missions.today(
                    day.plusDays(d),
                    NetSec.ids.toList(),
                    mind,
                    mapOf("vigil" to 6, "suitcase" to 9, "route" to 5),
                )
            assertTrue(ms.none { it.game in Games.retired })
            // Even a stale caller that still passes retired ids gets none back.
            assertTrue(
                Missions.today(day.plusDays(d), NetSec.ids.toList(), Games.retired.toList()).none {
                    it.game in Games.retired
                }
            )
        }
        assertTrue(NetSec.ids.none { it in Games.retired })
        assertTrue(
            ShortSession.plan(NetSec.ids.first(), NetSec.ids.toList()).none { it in Games.retired }
        )
    }

    @Test
    fun calcRotatesTheOriginalGenerators() {
        val seen = mutableSetOf<String>()
        for (l in 1..10) for (s in 0L..199L) {
            val c = Generator.make("calc", l, s)
            assertEquals(c, Generator.make("calc", l, s))
            assertTrue(c.source in Games.calcModes.flatMap { it.second })
            assertEquals(Kind.CHOICE, c.kind)
            assertEquals(4, c.choices.size)
            assertTrue(c.answer in c.choices.indices)
            if (c.source == "balance") assertEquals(3, c.board.size)
            seen += c.source
        }
        assertEquals(Games.calcModes.flatMap { it.second }.toSet(), seen)
    }

    @Test
    fun workingOffersOnlyDigits() {
        for (l in 1..10) for (s in 0L..499L) {
            val c = Generator.make("working", l, s)
            assertEquals(4, c.choices.size)
            assertEquals(4, c.choices.distinct().size)
            assertTrue(c.choices.all { it.toInt() in 0..9 })
            val n = 2 + (l - 1) / 3
            assertEquals(c.stream[c.stream.size - n], c.choices[c.answer])
        }
    }
}
