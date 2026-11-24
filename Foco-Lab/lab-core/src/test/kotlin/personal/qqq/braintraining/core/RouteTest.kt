package personal.qqq.braintraining.core

import org.junit.Assert.*
import org.junit.Test

/**
 * Fase 3, Ruta de paquetes: maps without crossings, solvable rounds, unique alternative routes,
 * TTL, checks and migration.
 */
class RouteTest {
    @Test
    fun thousandSeedsGiveConnectedMapsWithoutCrossings() {
        for (s in 0L until 1000L) {
            val l = (s % 10 + 1).toInt()
            val t = Route.make(l, s)
            assertTrue("seed $s", Route.planar(t))
            assertTrue(Route.connected(t.n, t.edges))
            assertEquals(Route.level(l).routers, t.n)
            assertTrue(t.xs.all { it in 0f..1f } && t.ys.all { it in 0f..1f })
            // Routers never overlap.
            for (a in 0 until t.n) for (b in a + 1 until t.n) assertTrue(
                kotlin.math.hypot(t.xs[a] - t.xs[b], t.ys[a] - t.ys[b]) > .12f
            )
        }
    }

    @Test
    fun everyRoundIsSolvableAndDeterministic() {
        for (l in 1..10) for (s in 0L..149L) {
            val t = Route.make(l, s)
            assertEquals(t, Route.make(l, s))
            assertEquals("A", t.names[t.source])
            assertEquals("B", t.names[t.target])
            assertEquals(t.n, t.names.distinct().size)
            // The demonstration follows real links, never the down one (except the alternative
            // question, where it falls after).
            for (i in 0 until t.demo.size - 1) {
                val e = t.edge(t.demo[i], t.demo[i + 1])
                assertTrue(e >= 0)
                if (t.kind != Route.ALT) assertNotEquals(t.down, e)
            }
            for (i in 0 until t.answer.size - 1) assertTrue(t.linked(t.answer[i], t.answer[i + 1]))
            assertTrue(Route.check(t, t.answer, t.dropped).ok)
            val lv = Route.level(l)
            when (lv.kind) {
                Route.ALT -> {
                    assertTrue(t.down >= 0)
                    assertNotEquals(t.demo, t.answer)
                    val cheapest = Route.cheapest(t.n, t.edges, t.costs, t.source, t.target, t.down)
                    assertEquals(cheapest, t.answer)
                    assertEquals(
                        1,
                        Route.dijkstra(t.n, t.edges, t.costs, t.source, t.down).second[t.target],
                    )
                }
                Route.TTL -> {
                    assertTrue(t.ttl >= 2)
                    assertEquals(t.dropped, t.ttl < t.demo.size - 1)
                    if (t.dropped) assertEquals(t.ttl, t.hops) else assertEquals(t.demo, t.answer)
                }
                Route.DOWN -> {
                    assertTrue(t.down >= 0)
                    assertEquals(t.demo, t.answer)
                }
                else -> {
                    assertEquals(-1, t.down)
                    assertTrue(t.hops in lv.hops)
                }
            }
        }
    }

    @Test
    fun levelTableAndLoops() {
        assertEquals(listOf(5, 5, 7, 7, 9, 9, 9, 9, 9, 9), (1..10).map { Route.level(it).routers })
        assertEquals(
            listOf(10, 10, 10, 10, 10, 10, 10, 10, 11, 11),
            (1..10).map { Route.level(it).goal },
        )
        assertTrue((0L..300L).any { Route.make(4, it).loop })
        assertTrue((0L..300L).none { Route.make(1, it).loop })
        // TTL rounds: some packets die on the way, some arrive.
        assertTrue((0L..60L).any { Route.make(9, it).dropped })
        assertTrue((0L..60L).any { !Route.make(9, it).dropped })
        // Costs only appear in the alternative-route levels.
        assertTrue((0L..60L).any { Route.make(7, it).hasCosts })
        assertTrue((0L..60L).none { Route.make(3, it).hasCosts })
    }

    @Test
    fun checkNamesTheKindOfMistake() {
        val t = (0L..200L).map { Route.make(3, it) }.first { it.hops >= 4 && !it.loop }
        val a = t.answer
        assertEquals(Route.ORDER, Route.check(t, listOf(a[0]) + a.drop(1).reversed(), false).error)
        val other = (0 until t.n).first { it !in a }
        assertEquals(Route.DETOUR, Route.check(t, a.dropLast(1) + other, false).error)
        assertEquals(a.size - 2, Route.check(t, a.dropLast(1) + other, false).matched)
        val ttl = (0L..200L).map { Route.make(9, it) }.first { it.dropped }
        assertEquals(Route.TTL_ERROR, Route.check(ttl, ttl.answer, false).error)
        assertEquals(Route.TTL_ERROR, Route.check(ttl, ttl.demo, false).error)
        assertTrue(Route.check(ttl, ttl.answer, true).ok)
    }

    @Test
    fun tuningAndExtraHop() {
        val ok = RouteLog(true, 0, 3, 3, 0, 1)
        val ko = RouteLog(false, 0, 3, 1, Route.DETOUR, 1)
        assertEquals(1, Route.tuning(listOf(ok, ok, ok)).extraHop)
        assertTrue(Route.tuning(listOf(ok, ko, ko)).slow)
        assertEquals(720L, Route.tuning(listOf(ko, ko)).hopMs)
        assertNull(Route.tuning(listOf(ok)).note)
        val t = Route.make(2, 5)
        val more = Route.tuned(t, RouteTuning(extraHop = 1))
        assertEquals(t.hops + 1, more.hops)
        assertEquals(more, Route.tuned(t, RouteTuning(extraHop = 1)))
        val alt = Route.make(7, 5)
        assertSame(alt, Route.tuned(alt, RouteTuning(extraHop = 1)))
    }

    @Test
    fun orbitProgressMigratesTwoLevelsDown() {
        assertEquals(listOf(1, 1, 1, 2, 3, 4, 5, 6, 7, 8), (1..10).map(Route::migratedLevel))
    }

    @Test
    fun logRoundTripsAndEarnsLessons() {
        val log = RouteLog(false, Route.TTL, 5, 3, Route.TTL_ERROR, 9, setOf(0, 6), 4200)
        assertEquals(log, RouteLog.decode(log.encode()))
        assertNull(RouteLog.decode("ok=1"))
        val t = (0L..200L).map { Route.make(9, it) }.first { it.dropped }
        assertTrue(Route.learned(t, true).containsAll(setOf(0, 6, 7)))
        assertTrue(Route.learned(t, false).isEmpty())
        assertEquals(
            Route.DETOUR,
            Route.weakest(
                listOf(RouteLog(false, 0, 3, 1, Route.DETOUR, 1), RouteLog(true, 0, 3, 3, 0, 1))
            ),
        )
        assertEquals(
            2,
            Route.bestRun(
                listOf(
                    RouteLog(true, 0, 3, 3, 0, 1),
                    RouteLog(true, 0, 3, 3, 0, 1),
                    RouteLog(false, 0, 3, 0, 1, 1),
                )
            ),
        )
    }
}
