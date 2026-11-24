package personal.qqq.braintraining.core

import java.time.LocalDate
import kotlin.random.Random
import org.junit.Assert.*
import org.junit.Test

/** Lote C: cofre, misiones, álbum y casi récord. */
class EngagementTest {
    private val day = LocalDate.of(2026, 11, 8)

    // ---------- Cofre ----------
    @Test
    fun chestIsDeterministic() {
        for (d in 0L..60L) for (k in 0 until Rewards.DAILY_MAX) {
            val date = day.plusDays(d)
            assertEquals(Rewards.roll(date, k, Album.keys), Rewards.roll(date, k, Album.keys))
        }
        // The same wallet opens the same chest every time.
        val w = Wallet().earn(day)!!.earn(day)!!
        assertEquals(w.open(), w.open())
    }

    @Test
    fun chestVariesAndCoversEveryKind() {
        val kinds =
            (0L..200L)
                .flatMap { d ->
                    (0 until 3).map { Rewards.roll(day.plusDays(d), it, Album.keys).kind }
                }
                .toSet()
        assertEquals(RewardKind.values().toSet(), kinds)
    }

    @Test
    fun atMostThreeChestsPerDay() {
        var w = Wallet()
        repeat(Rewards.DAILY_MAX) { w = w.earn(day)!! }
        assertNull(w.earn(day))
        assertFalse(w.canEarn(day))
        assertEquals(3, w.pending.size)
        // Opening does not give back a slot: the cap counts chests earned, not pending.
        w = w.open()!!.first
        assertNull(w.earn(day))
        // A new day starts again; unopened chests are kept.
        w = w.earn(day.plusDays(1))!!
        assertEquals(3, w.pending.size)
        assertEquals(1, w.earnedOn(day.plusDays(1)))
    }

    @Test
    fun openingAppliesTheRewardOnce() {
        var w = Wallet().earn(day)!!
        val (next, reward) = w.open()!!
        assertTrue(next.pending.isEmpty())
        assertEquals(1, next.opened)
        assertNull(next.open())
        when (reward.kind) {
            RewardKind.POINTS -> assertEquals(reward.points, next.bonus)
            RewardKind.CARD -> assertTrue(reward.card in next.cards)
            RewardKind.COSMETIC -> assertTrue(reward.cosmetic in next.cosmetics)
            RewardKind.PHRASE -> assertTrue(reward.phrase in next.phrases)
        }
    }

    @Test
    fun cardRewardsAreLockedCardsAndFallBackWhenAlbumIsFull() {
        for (d in 0L..100L) {
            val r = Rewards.roll(day.plusDays(d), 0, listOf("ports:22"))
            if (r.kind == RewardKind.CARD) assertEquals("ports:22", r.card)
        }
        for (d in 0L..100L) {
            val r =
                Rewards.roll(
                    day.plusDays(d),
                    1,
                    emptyList(),
                    Rewards.cosmetics.map { it.id }.toSet(),
                )
            assertTrue(r.kind == RewardKind.POINTS || r.kind == RewardKind.PHRASE)
        }
    }

    /**
     * Acceptance (Lote C): a chest never changes Foco's leisure minutes or blocking. Rewards expose
     * no time at all.
     */
    @Test
    fun rewardsNeverGiveLeisureTime() {
        val timeWords =
            Regex(
                "(?i)minut|time|tiempo|ocio|leisure|budget|presupuesto|ms$|millis|second|segundo|pause|pausa|unlock(ed)?app|rule"
            )
        listOf(
                Reward::class.java,
                Wallet::class.java,
                Cosmetic::class.java,
                ChestTicket::class.java,
            )
            .forEach { c ->
                c.declaredFields
                    .filter { !java.lang.reflect.Modifier.isStatic(it.modifiers) }
                    .forEach { f ->
                        assertFalse("${c.simpleName}.${f.name}", timeWords.containsMatchIn(f.name))
                    }
            }
        RewardKind.values().forEach { assertFalse(it.name, timeWords.containsMatchIn(it.name)) }
        // Whatever a chest gives, opening it only changes points, cards, cosmetics or phrases.
        for (d in 0L..120L) {
            var w = Wallet().earn(day.plusDays(d))!!
            val (n, r) = w.open()!!
            assertEquals(
                n.copy(
                    bonus = 0,
                    cards = emptySet(),
                    cosmetics = emptySet(),
                    skin = null,
                    accent = null,
                    phrases = emptySet(),
                    theme = null,
                ),
                w.copy(pending = emptyList(), opened = 1),
            )
            assertTrue(r.points in 0..50)
        }
        // The core never references Foco (its rules, repository or budget live in another module).
        assertTrue(runCatching { Class.forName("es.foco.next.data.Repository") }.isFailure)
    }

    @Test
    fun walletRoundTripsAndRejectsGarbage() {
        var w = Wallet().earn(day)!!.earn(day)!!
        w =
            w.open()!!
                .first
                .unlock("ports:22")
                .apply(Reward(RewardKind.COSMETIC, cosmetic = "skin:solar"))
                .apply(Reward(RewardKind.PHRASE, phrase = 3))
        assertEquals(w, Wallet.decode(w.encode()))
        assertEquals(Wallet(), Wallet.decode(Wallet().encode()))
        for (bad in
            listOf(
                "",
                "v=2",
                "v=1\nearned=9",
                "v=1\nminutes=5",
                "v=1\ncosmetics=skin:nope",
                "v=1\npending=2026-11-08#7",
                "v=1\nbonus=-3",
            )) assertTrue(bad, runCatching { Wallet.decode(bad) }.isFailure)
        assertEquals(Wallet(), Wallet.decodeOrEmpty("basura"))
        // Unknown album keys from another version are dropped, not fatal.
        assertEquals(setOf("ports:22"), Wallet.decode("v=1\ncards=ports:22,ports:99999").cards)
    }

    @Test
    fun equipOnlyOwnedCosmetics() {
        val w =
            Wallet()
                .apply(Reward(RewardKind.COSMETIC, cosmetic = "skin:solar"))
                .apply(Reward(RewardKind.COSMETIC, cosmetic = "skin:aurora"))
        assertEquals("skin:aurora", w.skin)
        assertEquals("skin:solar", w.equip("skin:solar").skin)
        assertEquals(w, w.equip("accent:coral"))
    }

    // ---------- Misiones ----------
    @Test
    fun missionsAreThreeDeterministicAndUnlockedOnly() {
        for (d in 0L..90L) {
            val date = day.plusDays(d)
            val ms = Missions.today(date, listOf("bits", "subnet"), listOf("orbit", "calc"))
            assertEquals(3, ms.size)
            assertEquals(
                ms,
                Missions.today(date, listOf("bits", "subnet"), listOf("orbit", "calc")),
            )
            assertEquals(3, ms.map { it.id }.distinct().size)
            ms.forEach { m ->
                assertTrue(m.title.isNotBlank())
                assertTrue(m.target > 0)
                if (m.kind != MissionKind.SHORT)
                    assertTrue(m.game in listOf("bits", "subnet", "orbit", "calc"))
            }
            assertEquals(MissionKind.STREAK, ms[0].kind)
            assertEquals(MissionKind.SCORE, ms[1].kind)
        }
        // With nothing unlocked, Bits is always available.
        assertEquals("bits", Missions.today(day, emptyList(), emptyList())[0].game)
    }

    @Test
    fun missionProgressKeepsTheBestAndCompletes() {
        val ms =
            listOf(
                DailyMission(
                    "streak:subnet",
                    MissionKind.STREAK,
                    "subnet",
                    5,
                    "Acierta 5 subredes seguidas",
                ),
                DailyMission(
                    "score:calc",
                    MissionKind.SCORE,
                    "calc",
                    9,
                    "Completa Cálculo con 9/12",
                ),
                DailyMission("short", MissionKind.SHORT, "short", 4, "x"),
            )
        var p = MissionProgress()
        p = Missions.record(ms, p, Play(ChestSource.RUN, "subnet", correct = 7, bestStreak = 3))
        // Total hits count (7), not only the consecutive ones (3): a mistake erases nothing.
        assertEquals(5, p.value(ms[0]))
        assertTrue(p.done(ms[0]))
        assertEquals(0, p.value(ms[1]))
        p = Missions.record(ms, p, Play(ChestSource.FLASH, "subnet", bestStreak = 6, score = 90))
        assertTrue(p.done(ms[0]))
        p = Missions.record(ms, p, Play(ChestSource.RUN, "subnet", bestStreak = 1))
        assertTrue(p.done(ms[0])) // best is kept
        p = Missions.record(ms, p, Play(ChestSource.FLASH, "calc", correct = 12))
        assertFalse(p.done(ms[1])) // only 12-round runs count for SCORE
        p = Missions.record(ms, p, Play(ChestSource.RUN, "calc", correct = 9))
        assertTrue(p.done(ms[1]))
        assertFalse(p.allDone(ms))
        p = Missions.record(ms, p, Play(ChestSource.SHORT, "subnet", correct = 4, bestStreak = 4))
        assertTrue(p.allDone(ms))
        assertEquals(5, p.value(ms[0]))
        assertEquals(p, MissionProgress.decode(p.encode()))
        assertEquals(
            p.copy(rewarded = true),
            MissionProgress.decode(p.copy(rewarded = true).encode()),
        )
        assertTrue(runCatching { MissionProgress.decode("rewarded=quizá") }.isFailure)
    }

    @Test
    fun bestStreakCountsConsecutiveHits() {
        assertEquals(0, Missions.bestStreak(emptyList()))
        assertEquals(3, Missions.bestStreak(listOf(true, false, true, true, true, false, true)))
    }

    // ---------- Álbum ----------
    @Test
    fun albumHasAboutNinetyFourUniqueCardsWithText() {
        val cards = Album.cards
        assertTrue("${cards.size}", cards.size >= 94)
        assertEquals(cards.size, cards.map { it.key }.distinct().size)
        assertEquals(cards.size, cards.map { it.lesson }.distinct().size)
        cards.forEach { c ->
            assertTrue(c.key, c.title.isNotBlank())
            assertTrue(c.key, c.lesson.length >= 20)
            assertFalse(c.key, c.lesson.contains('\n'))
            assertTrue(c.game in Album.games)
            assertTrue(Regex("[a-z0-9:]+").matches(c.key))
        }
        Album.games.forEach { id -> assertTrue(id, cards.any { it.game == id }) }
        assertEquals(NetSec.ids.toList(), Album.games.take(NetSec.ids.size))
        assertEquals(18, cards.count { it.game == "ports" })
        assertEquals(35, cards.count { it.game == "phishing" })
    }

    @Test
    fun albumKeysAreStableAndIndependentOfOrder() {
        assertEquals("ports:22", Album.cards.first { it.game == "ports" }.key)
        assertEquals(Album.hash("abc"), Album.hash("abc"))
        assertNotEquals(Album.hash("abc"), Album.hash("abd"))
        assertEquals(
            "1a47e90b",
            Album.hash("abc"),
        ) // FNV-1a 32 of "abc": the format of saved keys never changes
    }

    @Test
    fun correctAnswersEarnTheirCard() {
        for (id in listOf("ports", "phishing", "sqlsafe", "logs")) for (s in 0L..60L) {
            val c = Generator.make(id, 5, s)
            val card = Album.forAnswer(id, c.explanation, emptySet())
            assertNotNull("$id $s", card)
            assertEquals(id, card!!.game)
            assertNull(Album.forAnswer(id, c.explanation, setOf(card.key)))
        }
        val first = Album.forAnswer("subnet", "", emptySet())!!
        assertEquals("subnet", first.game)
        assertNotEquals(first, Album.forAnswer("subnet", "", setOf(first.key)))
        assertTrue(Album.milestone(9, 10))
        assertFalse(Album.milestone(10, 11))
        assertTrue(Album.milestone(Album.cards.size - 1, Album.cards.size))
    }

    @Test
    fun boardRoundsEarnTheCardsTheyShowed() {
        // Fase 2: suitcase items seen and alert patterns detected; keys stable (suitcase:<item>,
        // vigil:<pattern>).
        assertEquals(Suitcase.bank.size, Album.cards.count { it.game == "suitcase" })
        assertEquals(5, Album.cards.count { it.game == "vigil" })
        assertNotNull(Album.get("suitcase:port22"))
        assertNotNull(Album.get("vigil:bruteforce"))
        val log = SuitcaseLog(5, false, 5, seen = listOf("cat6", "port22", "hop3"))
        assertEquals(
            listOf("suitcase:cat6", "suitcase:port22"),
            Album.forBoard("suitcase", log.encode(), emptySet()).map { it.key },
        )
        assertEquals(
            listOf("suitcase:port22"),
            Album.forBoard("suitcase", log.encode(), setOf("suitcase:cat6")).map { it.key },
        )
        val v =
            VigilLog(
                2,
                1,
                0,
                3,
                4,
                0,
                found = setOf(Vigil.BRUTE, Vigil.SCAN),
                missed = setOf(Vigil.ODD),
            )
        assertEquals(
            listOf("vigil:bruteforce", "vigil:portscan"),
            Album.forBoard("vigil", v.encode(), emptySet()).map { it.key },
        )
        assertTrue(Album.forBoard("ports", "x", emptySet()).isEmpty())
        assertTrue(Album.forBoard("vigil", null, emptySet()).isEmpty())
    }

    @Test
    fun suitcaseAndVigilMissions() {
        val days =
            (0L..200L).map {
                Missions.today(
                    day.plusDays(it),
                    listOf("bits"),
                    listOf("suitcase", "vigil", "orbit"),
                    mapOf("vigil" to 5, "suitcase" to 1),
                )
            }
        val chain = days.flatten().first { it.kind == MissionKind.CHAIN }
        assertEquals("suitcase", chain.game)
        assertEquals(5, chain.target)
        val clean = days.flatten().first { it.kind == MissionKind.CLEAN }
        assertEquals("vigil", clean.game)
        assertEquals(5, clean.minLevel)
        assertEquals(
            10,
            Missions.today(day, listOf("bits"), listOf("suitcase"), mapOf("suitcase" to 9))
                .first { it.game == "suitcase" }
                .target,
        )
        var p = MissionProgress()
        p = Missions.record(listOf(chain, clean), p, Play(ChestSource.RUN, "suitcase", best = 4))
        assertFalse(p.done(chain))
        p = Missions.record(listOf(chain, clean), p, Play(ChestSource.RUN, "suitcase", best = 5))
        assertTrue(p.done(chain))
        p =
            Missions.record(
                listOf(chain, clean),
                p,
                Play(ChestSource.RUN, "vigil", best = 2, level = 4),
            )
        assertFalse(p.done(clean)) // below level 5
        p =
            Missions.record(
                listOf(chain, clean),
                p,
                Play(ChestSource.RUN, "vigil", best = 1, level = 5),
            )
        assertTrue(p.done(clean))
        assertEquals(p, MissionProgress.decode(p.encode()))
    }

    @Test
    fun fase3MissionsAndThemes() {
        val days =
            (0L..200L)
                .map {
                    Missions.today(
                        day.plusDays(it),
                        listOf("bits"),
                        listOf("route", "ddos", "pairs"),
                        mapOf("route" to 6),
                    )
                }
                .flatten()
        val route = days.first { it.game == "route" }
        assertEquals(MissionKind.CLEAN, route.kind)
        assertEquals(5, route.target)
        assertEquals(5, route.minLevel)
        val ddos = days.first { it.game == "ddos" }
        assertEquals(MissionKind.CHAIN, ddos.kind)
        assertEquals(3, ddos.target)
        val pairs = days.first { it.game == "pairs" }
        assertEquals(5, pairs.target)
        var p =
            Missions.record(
                listOf(route, ddos, pairs),
                MissionProgress(),
                Play(ChestSource.RUN, "route", best = 5, level = 6),
            )
        p =
            Missions.record(
                listOf(route, ddos, pairs),
                p,
                Play(ChestSource.RUN, "ddos", best = 3, level = 2),
            )
        assertTrue(p.done(route))
        assertTrue(p.done(ddos))
        assertFalse(p.done(pairs))
        // Board themes: a cosmetic that only changes the look of Parejas de incidentes, equipped
        // and stored like the others.
        val w = Wallet().apply(Reward(RewardKind.COSMETIC, cosmetic = "theme:neon"))
        assertEquals("theme:neon", w.theme)
        assertNull(w.skin)
        assertNull(w.accent)
        assertEquals(w, Wallet.decode(w.encode()))
        assertNull(w.equip("theme:neon").theme)
        assertEquals(w, w.equip("theme:papel"))
        assertTrue(Album.cards.count { it.game == "route" } >= 10)
        assertTrue(Album.cards.count { it.game == "ddos" } >= 10)
        assertEquals(Album.cards.size, Album.keys.size)
        assertEquals(
            listOf("ddos:telnet", "ddos:blacklist"),
            Album.forBoard(
                    "ddos",
                    DdosLog(
                            1,
                            1,
                            0,
                            0,
                            1,
                            false,
                            1,
                            0,
                            learned = setOf(Ddos.TELNET, Ddos.BLACKLIST),
                        )
                        .encode(),
                    emptySet(),
                )
                .map { it.key },
        )
        assertEquals(
            listOf("route:hop", "route:ttl"),
            Album.forBoard(
                    "route",
                    RouteLog(true, Route.TTL, 4, 4, 0, 9, setOf(0, 6)).encode(),
                    emptySet(),
                )
                .map { it.key },
        )
    }

    // ---------- Casi récord y fantasma ----------
    @Test
    fun nearMissDetection() {
        assertTrue(NearMiss.isNear(9, 10))
        assertFalse(NearMiss.isNear(8, 10))
        assertFalse(NearMiss.isNear(10, 10))
        assertFalse(NearMiss.isNear(11, 10))
        assertFalse(NearMiss.isNear(5, null))
        assertFalse(NearMiss.isNear(0, 0))
        assertTrue(NearMiss.isNear(180, 200))
        assertFalse(NearMiss.isNear(170, 200))
        assertTrue(NearMiss.offerAgain(9, 10, 0))
        assertTrue(NearMiss.offerAgain(9, 10, 1))
        assertFalse(NearMiss.offerAgain(9, 10, 2))
    }

    @Test
    fun ghostFollowsTheBestRun() {
        val g = NearMiss.ghost(listOf(true, true, false, true))
        assertEquals(listOf(1, 2, 2, 3), g)
        assertEquals(0, NearMiss.ghostAt(g, 0))
        assertEquals(2, NearMiss.ghostAt(g, 3))
        assertEquals(3, NearMiss.ghostAt(g, 12))
        val rnd = Random(4)
        repeat(50) {
            val run = List(12) { rnd.nextBoolean() }
            assertEquals(run.count { it }, NearMiss.ghost(run).last())
        }
    }
}
