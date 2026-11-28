package personal.qqq.braintraining.core

import kotlin.random.Random

/**
 * Fase 3 (docs/DISENO-JUEGOS-V2.md §4): Ruta de paquetes, the redesign of Órbitas. A Mini-Metro
 * style map: routers are stations, links are coloured lines. A packet travels A → … → B; then you
 * are the routing protocol and draw its path.
 *
 * Maps are generated on a soft hexagonal grid (only neighbouring slots are linked, positions
 * jittered a little) and every map is checked for crossings and connectivity; the tests run 1000
 * seeds. Shortest paths use Dijkstra with link costs and the alternative-route question only uses
 * maps where the answer is unique.
 */
data class RouteLevel(val routers: Int, val hops: IntRange, val kind: Int, val goal: Int)

/**
 * One round. Positions are normalized (0..1, portrait map). [edges] holds pairs (from, to); [costs]
 * one cost per edge. [demo] is the route the packet shows (A first), [answer] what the player must
 * reproduce (A first), [down] the edge drawn as down (−1 none), [ttl] the TTL the packet leaves
 * with (0: no TTL) and [dropped] whether the answer ends with "✖ descartado".
 */
data class RouteTask(
    val level: Int,
    val seed: Long,
    val kind: Int,
    val xs: FloatArray,
    val ys: FloatArray,
    val names: List<String>,
    val edges: IntArray,
    val costs: IntArray,
    val demo: List<Int>,
    val answer: List<Int>,
    val down: Int,
    val ttl: Int,
    val dropped: Boolean,
    val loop: Boolean,
    val extraHop: Int = 0,
) {
    val n
        get() = xs.size

    val edgeCount
        get() = edges.size / 2

    val source
        get() = answer.first()

    val target
        get() = demo.last()

    val hops
        get() = answer.size - 1

    fun edge(u: Int, v: Int): Int {
        for (e in 0 until edgeCount) {
            val a = edges[2 * e]
            val b = edges[2 * e + 1]
            if ((a == u && b == v) || (a == v && b == u)) return e
        }
        return -1
    }

    fun linked(u: Int, v: Int) = edge(u, v).let { it >= 0 && it != down }

    val hasCosts
        get() = costs.any { it > 1 }

    override fun equals(other: Any?) =
        other is RouteTask &&
            level == other.level &&
            seed == other.seed &&
            kind == other.kind &&
            xs.contentEquals(other.xs) &&
            ys.contentEquals(other.ys) &&
            names == other.names &&
            edges.contentEquals(other.edges) &&
            costs.contentEquals(other.costs) &&
            demo == other.demo &&
            answer == other.answer &&
            down == other.down &&
            ttl == other.ttl &&
            dropped == other.dropped &&
            loop == other.loop &&
            extraHop == other.extraHop

    override fun hashCode() = (seed * 31 + level).toInt() * 31 + answer.hashCode()
}

data class RouteCheck(val ok: Boolean, val error: Int, val matched: Int)

/**
 * Announced in-game tuning (§4): three right in a row → +1 hop; two wrong → demonstration 20 %
 * slower.
 */
data class RouteTuning(val extraHop: Int = 0, val slow: Boolean = false, val note: String? = null) {
    val hopMs
        get() = if (slow) (Route.HOP_MS * 1.2).toLong() else Route.HOP_MS
}

/** What one round leaves in its detail. */
data class RouteLog(
    val ok: Boolean,
    val kind: Int,
    val hops: Int,
    val matched: Int,
    val error: Int,
    val level: Int,
    val learned: Set<Int> = emptySet(),
    val ms: Long = 0,
) {
    fun encode() =
        "ok=$ok;kind=$kind;hops=$hops;matched=$matched;error=$error;level=$level;learned=${learned.sorted().joinToString(",")};ms=$ms"

    companion object {
        fun decode(text: String?): RouteLog? =
            runCatching {
                    val m =
                        text!!.split(';').associate {
                            val i = it.indexOf('=')
                            it.substring(0, i) to it.substring(i + 1)
                        }
                    RouteLog(
                        m.getValue("ok").toBooleanStrict(),
                        m.getValue("kind").toInt(),
                        m.getValue("hops").toInt(),
                        m.getValue("matched").toInt(),
                        m.getValue("error").toInt(),
                        m.getValue("level").toInt(),
                        m["learned"]
                            .orEmpty()
                            .split(',')
                            .filter { it.isNotBlank() }
                            .map { it.toInt() }
                            .toSet(),
                        m["ms"]?.toLong() ?: 0,
                    )
                }
                .getOrNull()
    }
}

object Route {
    const val ID = "route"
    const val OLD_ID = "orbit"
    const val HOP_MS = 600L
    // Kinds
    const val MEMORY = 0
    const val DOWN = 1
    const val ALT = 2
    const val TTL = 3
    // Errors
    const val OK = 0
    const val ORDER = 1
    const val DETOUR = 2
    const val TTL_ERROR = 3
    val errorNames = listOf("ninguno", "orden", "desvío", "TTL")
    const val COLS = 3
    const val ROWS = 5
    val lessonKeys =
        listOf(
            "hop",
            "loop",
            "linkdown",
            "detour",
            "minhops",
            "lowcost",
            "ttl",
            "timeexceeded",
            "traceroute",
            "nexthop",
        )
    val lessonTitles =
        listOf(
            "Salto",
            "Bucle de enrutamiento",
            "Enlace caído",
            "Desvío",
            "Mínimo de saltos",
            "Ruta de menor coste",
            "TTL",
            "Time Exceeded",
            "Traceroute",
            "Siguiente salto",
        )
    val lessons =
        listOf(
            "Cada router que atraviesa el paquete es un salto: de A a B por R1 y R2 son 3 saltos.",
            "Si la tabla de rutas está mal, el paquete puede volver a un router por el que ya pasó: un bucle. El TTL evita que gire para siempre.",
            "Cuando un enlace cae, los routers dejan de anunciarlo y el tráfico se desvía por otro camino.",
            "Un desvío es más largo, pero llega: la red se recupera sola si hay caminos alternativos.",
            "Sin costes, el protocolo elige la ruta con menos saltos (como una búsqueda en anchura).",
            "Con costes, gana la suma más baja, aunque tenga más saltos: un enlace lento cuesta más.",
            "Cada router resta 1 al TTL; con 0 el paquete se descarta y se avisa al origen.",
            "ICMP Time Exceeded: el aviso que manda el router que descartó un paquete por TTL agotado.",
            "Traceroute envía paquetes con TTL 1, 2, 3… y cada Time Exceeded revela un router del camino.",
            "Ningún router conoce la ruta entera: cada uno solo decide el siguiente salto según su tabla.",
        )

    fun level(l: Int): RouteLevel {
        require(l in 1..10)
        return when (l) {
            1,
            2 -> RouteLevel(5, 3..3, MEMORY, 10)
            3,
            4 -> RouteLevel(7, 4..5, MEMORY, 10)
            5,
            6 -> RouteLevel(9, 5..5, DOWN, 10)
            7,
            8 -> RouteLevel(9, 3..5, ALT, 10)
            else -> RouteLevel(9, 5..6, TTL, 11)
        }
    }

    /** Órbitas progress becomes Ruta de paquetes progress two levels lower (never below 1). */
    fun migratedLevel(orbitLevel: Int) = (orbitLevel - 2).coerceIn(1, 10)

    // ---------- Geometry ----------
    private fun slotX(c: Int, r: Int) = (c + if (r % 2 == 1) .5f else 0f)

    private fun slotY(r: Int) = r * .866f

    private fun neighbours(a: Int, b: Int): Boolean {
        val ca = a % COLS
        val ra = a / COLS
        val cb = b % COLS
        val rb = b / COLS
        if (ra == rb) return kotlin.math.abs(ca - cb) == 1
        if (kotlin.math.abs(ra - rb) != 1) return false
        return if (ra % 2 == 0) cb == ca || cb == ca - 1 else cb == ca || cb == ca + 1
    }

    private fun orient(ax: Float, ay: Float, bx: Float, by: Float, cx: Float, cy: Float): Float =
        (bx - ax) * (cy - ay) - (by - ay) * (cx - ax)

    /** Proper crossing of two segments that share no endpoint. */
    fun crosses(x: FloatArray, y: FloatArray, a: Int, b: Int, c: Int, d: Int): Boolean {
        if (a == c || a == d || b == c || b == d) return false
        val d1 = orient(x[a], y[a], x[b], y[b], x[c], y[c])
        val d2 = orient(x[a], y[a], x[b], y[b], x[d], y[d])
        val d3 = orient(x[c], y[c], x[d], y[d], x[a], y[a])
        val d4 = orient(x[c], y[c], x[d], y[d], x[b], y[b])
        return (d1 > 0) != (d2 > 0) && (d3 > 0) != (d4 > 0)
    }

    fun planar(t: RouteTask): Boolean {
        for (e in 0 until t.edgeCount) for (f in e + 1 until t.edgeCount) if (
            crosses(
                t.xs,
                t.ys,
                t.edges[2 * e],
                t.edges[2 * e + 1],
                t.edges[2 * f],
                t.edges[2 * f + 1],
            )
        )
            return false
        return true
    }

    fun connected(n: Int, edges: IntArray, skip: Int = -1): Boolean {
        val seen = BooleanArray(n)
        val stack = ArrayDeque<Int>()
        stack += 0
        seen[0] = true
        while (stack.isNotEmpty()) {
            val u = stack.removeLast()
            for (e in 0 until edges.size / 2) {
                if (e == skip) continue
                val a = edges[2 * e]
                val b = edges[2 * e + 1]
                val v = if (a == u) b else if (b == u) a else -1
                if (v >= 0 && !seen[v]) {
                    seen[v] = true
                    stack += v
                }
            }
        }
        return seen.all { it }
    }

    /**
     * Cheapest path cost from [s] to every node, ignoring edge [skip]; and the number of cheapest
     * paths (capped at 2).
     */
    fun dijkstra(
        n: Int,
        edges: IntArray,
        costs: IntArray,
        s: Int,
        skip: Int = -1,
    ): Pair<IntArray, IntArray> {
        val dist = IntArray(n) { Int.MAX_VALUE }
        val ways = IntArray(n)
        val done = BooleanArray(n)
        dist[s] = 0
        ways[s] = 1
        repeat(n) {
            var u = -1
            for (i in 0 until n) if (
                !done[i] && dist[i] != Int.MAX_VALUE && (u < 0 || dist[i] < dist[u])
            )
                u = i
            if (u < 0) return dist to ways
            done[u] = true
            for (e in 0 until edges.size / 2) {
                if (e == skip) continue
                val a = edges[2 * e]
                val b = edges[2 * e + 1]
                val v = if (a == u) b else if (b == u) a else continue
                val nd = dist[u] + costs[e]
                if (nd < dist[v]) {
                    dist[v] = nd
                    ways[v] = ways[u]
                } else if (nd == dist[v]) ways[v] = minOf(2, ways[v] + ways[u])
            }
        }
        return dist to ways
    }

    /** The cheapest path s → t (null when unreachable), by walking back over tight edges. */
    fun cheapest(
        n: Int,
        edges: IntArray,
        costs: IntArray,
        s: Int,
        t: Int,
        skip: Int = -1,
    ): List<Int>? {
        val (dist, _) = dijkstra(n, edges, costs, s, skip)
        if (dist[t] == Int.MAX_VALUE) return null
        val path = mutableListOf(t)
        var v = t
        while (v != s) {
            var prev = -1
            for (e in 0 until edges.size / 2) {
                if (e == skip) continue
                val a = edges[2 * e]
                val b = edges[2 * e + 1]
                val u = if (a == v) b else if (b == v) a else continue
                if (dist[u] != Int.MAX_VALUE && dist[u] + costs[e] == dist[v]) {
                    prev = u
                    break
                }
            }
            v = prev
            path += v
        }
        return path.reversed()
    }

    /**
     * A map of [n] routers: a connected cluster of grid slots, neighbour links only, some removed
     * while it stays connected.
     */
    private fun map(
        n: Int,
        r: Random,
        costly: Boolean,
    ): Triple<FloatArray, FloatArray, Pair<IntArray, IntArray>> {
        while (true) {
            val cluster = mutableListOf(r.nextInt(COLS * ROWS))
            while (cluster.size < n) {
                val cand =
                    (0 until COLS * ROWS).filter { s ->
                        s !in cluster && cluster.any { neighbours(it, s) }
                    }
                cluster += cand[r.nextInt(cand.size)]
            }
            cluster.sort()
            val w = slotX(COLS - 1, 1)
            val h = slotY(ROWS - 1)
            val xs =
                FloatArray(n) {
                    val s = cluster[it]
                    .12f + .76f * (slotX(s % COLS, s / COLS) + (r.nextFloat() - .5f) * .22f) / w
                }
            val ys =
                FloatArray(n) {
                    val s = cluster[it]
                    .08f + .84f * (slotY(s / COLS) + (r.nextFloat() - .5f) * .18f) / h
                }
            val list = mutableListOf<Int>()
            for (a in 0 until n) for (b in a + 1 until n) if (neighbours(cluster[a], cluster[b])) {
                list += a
                list += b
            }
            val all = list.toIntArray()
            val keep = BooleanArray(all.size / 2) { true }
            fun kept() =
                IntArray(keep.count { it } * 2).also { out ->
                    var k = 0
                    for (e in keep.indices) if (keep[e]) {
                        out[k++] = all[2 * e]
                        out[k++] = all[2 * e + 1]
                    }
                }
            for (e in (0 until all.size / 2).shuffled(r)) {
                if (r.nextFloat() > .4f) continue
                keep[e] = false
                if (!connected(n, kept())) keep[e] = true
            }
            val edges = kept()
            val costs = IntArray(edges.size / 2) { if (costly && r.nextFloat() < .3f) 2 else 1 }
            val probe =
                RouteTask(
                    0,
                    0,
                    0,
                    xs,
                    ys,
                    emptyList(),
                    edges,
                    costs,
                    listOf(0),
                    listOf(0),
                    -1,
                    0,
                    false,
                    false,
                )
            if (connected(n, edges) && planar(probe)) return Triple(xs, ys, edges to costs)
        }
    }

    private fun edgeOf(edges: IntArray, u: Int, v: Int): Int {
        for (e in 0 until edges.size / 2) {
            val a = edges[2 * e]
            val b = edges[2 * e + 1]
            if ((a == u && b == v) || (a == v && b == u)) return e
        }
        return -1
    }

    private fun names(n: Int, a: Int, b: Int): List<String> {
        var k = 0
        return List(n) { if (it == a) "A" else if (it == b) "B" else "R${++k}" }
    }

    private fun adj(n: Int, edges: IntArray, down: Int = -1): List<List<Int>> {
        val l = List(n) { mutableListOf<Int>() }
        for (e in 0 until edges.size / 2) if (e != down) {
            l[edges[2 * e]] += edges[2 * e + 1]
            l[edges[2 * e + 1]] += edges[2 * e]
        }
        return l
    }

    /**
     * Random walk of exactly [hops] hops from [s]; simple, or with one revisit (a loop) when
     * [loop]; never u → v → u.
     */
    private fun walk(
        n: Int,
        edges: IntArray,
        s: Int,
        hops: Int,
        loop: Boolean,
        r: Random,
        down: Int = -1,
    ): List<Int>? {
        val a = adj(n, edges, down)
        repeat(300) {
            val path = mutableListOf(s)
            var revisits = 0
            while (path.size <= hops) {
                val u = path.last()
                val prev = path.getOrNull(path.size - 2)
                val opts =
                    a[u].filter { v ->
                        v != prev &&
                            (v !in path ||
                                (loop && revisits == 0 && path.size >= 2 && path.size < hops))
                    }
                if (opts.isEmpty()) break
                val v = opts[r.nextInt(opts.size)]
                if (v in path) revisits++
                path += v
            }
            val end = path.last()
            if (path.size == hops + 1 && (!loop || revisits == 1) && path.count { it == end } == 1)
                return path
        }
        return null
    }

    fun make(level: Int, seed: Long, extraHop: Int = 0): RouteTask {
        val lv = level(level)
        val r = Random(seed)
        while (true) {
            val (xs, ys, ec) = map(lv.routers, r, lv.kind == ALT)
            val (edges, costs) = ec
            val n = lv.routers
            when (lv.kind) {
                ALT -> {
                    // Normal route (unique cheapest), one of its links falls, the unique cheapest
                    // detour is the answer.
                    val s = r.nextInt(n)
                    val (_, ways) = dijkstra(n, edges, costs, s)
                    val far =
                        (0 until n).filter {
                            it != s &&
                                ways[it] == 1 &&
                                (cheapest(n, edges, costs, s, it)?.size ?: 0) - 1 in lv.hops
                        }
                    if (far.isEmpty()) continue
                    val t = far[r.nextInt(far.size)]
                    val normal = cheapest(n, edges, costs, s, t)!!
                    val cut =
                        (0 until normal.size - 1)
                            .map { edgeOf(edges, normal[it], normal[it + 1]) }
                            .shuffled(r)
                    val e =
                        cut.firstOrNull { e ->
                            connected(n, edges, e) && dijkstra(n, edges, costs, s, e).second[t] == 1
                        } ?: continue
                    val alt = cheapest(n, edges, costs, s, t, e)!!
                    return RouteTask(
                        level,
                        seed,
                        ALT,
                        xs,
                        ys,
                        names(n, s, t),
                        edges,
                        costs,
                        normal,
                        alt,
                        e,
                        0,
                        false,
                        false,
                    )
                }
                else -> {
                    val hops =
                        (lv.hops.first + r.nextInt(lv.hops.last - lv.hops.first + 1) + extraHop)
                            .coerceAtMost(n + 1)
                    val loop = level >= 3 && r.nextInt(4) == 0
                    val s = r.nextInt(n)
                    val path =
                        walk(n, edges, s, hops, loop, r)
                            ?: walk(n, edges, s, hops, false, r)
                            ?: continue
                    var down = -1
                    if (lv.kind == DOWN) {
                        val used =
                            (0 until path.size - 1)
                                .map { edgeOf(edges, path[it], path[it + 1]) }
                                .toSet()
                        // A shortcut between two route routers is down: that is why the packet
                        // takes the detour.
                        val shortcuts =
                            (0 until edges.size / 2).filter { e ->
                                val a = edges[2 * e]
                                val b = edges[2 * e + 1]
                                val ia = path.indexOf(a)
                                val ib = path.indexOf(b)
                                ia >= 0 && ib >= 0 && kotlin.math.abs(ia - ib) > 1 && e !in used
                            }
                        val touching =
                            (0 until edges.size / 2).filter { e ->
                                (edges[2 * e] in path) != (edges[2 * e + 1] in path) &&
                                    connected(n, edges, e)
                            }
                        down =
                            (shortcuts.ifEmpty { touching }).let {
                                if (it.isEmpty()) -1 else it[r.nextInt(it.size)]
                            }
                        if (down < 0) continue
                    }
                    val t = path.last()
                    if (lv.kind == TTL) {
                        val ttl = (hops - 2 + r.nextInt(4)).coerceAtLeast(2)
                        val dropped = ttl < hops
                        return RouteTask(
                            level,
                            seed,
                            TTL,
                            xs,
                            ys,
                            names(n, s, t),
                            edges,
                            costs,
                            path,
                            if (dropped) path.take(ttl + 1) else path,
                            -1,
                            ttl,
                            dropped,
                            loop && path.toSet().size < path.size,
                            extraHop,
                        )
                    }
                    return RouteTask(
                        level,
                        seed,
                        lv.kind,
                        xs,
                        ys,
                        names(n, s, t),
                        edges,
                        costs,
                        path,
                        path,
                        down,
                        0,
                        false,
                        path.toSet().size < path.size,
                        extraHop,
                    )
                }
            }
        }
    }

    /**
     * Same task with the announced +1 hop (memory kinds only; the alternative-route question keeps
     * its unique answer).
     */
    fun tuned(t: RouteTask, tuning: RouteTuning) =
        if (tuning.extraHop > 0 && t.kind != ALT) make(t.level, t.seed, tuning.extraHop) else t

    /** Checks a drawn route ([picks], A first) and the "✖ descartado" mark. */
    fun check(t: RouteTask, picks: List<Int>, dropped: Boolean): RouteCheck {
        var m = 0
        while (m < picks.size && m < t.answer.size && picks[m] == t.answer[m]) m++
        val matched = (m - 1).coerceAtLeast(0)
        if (picks == t.answer && dropped == t.dropped) return RouteCheck(true, OK, matched)
        if (
            t.ttl > 0 &&
                (dropped != t.dropped ||
                    (t.dropped && picks.size != t.answer.size && picks == t.demo.take(picks.size)))
        )
            return RouteCheck(false, TTL_ERROR, matched)
        if (picks.size == t.answer.size && picks.sorted() == t.answer.sorted())
            return RouteCheck(false, ORDER, matched)
        return RouteCheck(false, DETOUR, matched)
    }

    fun learned(t: RouteTask, ok: Boolean): Set<Int> {
        if (!ok) return emptySet()
        val s = mutableSetOf(0)
        if (t.hops >= 4) s += 9
        if (t.loop) s += 1
        if (t.kind == DOWN) {
            s += 2
            s += 3
        }
        if (t.kind == ALT) {
            s += 3
            s += 4
            if (t.hasCosts) s += 5
        }
        if (t.ttl > 0) s += 6
        if (t.dropped) {
            s += 7
            s += 8
        }
        return s
    }

    fun tuning(previous: List<RouteLog>): RouteTuning =
        when {
            previous.size >= 3 && previous.takeLast(3).all { it.ok } ->
                RouteTuning(extraHop = 1, note = "⚡ +1 salto: tres rutas seguidas")
            previous.size >= 2 && previous.takeLast(2).none { it.ok } ->
                RouteTuning(slow = true, note = "🧭 demostración 20 % más lenta")
            else -> RouteTuning()
        }

    fun prompt(t: RouteTask) =
        when (t.kind) {
            ALT ->
                "El enlace ${t.names[t.edges[2*t.down]]}–${t.names[t.edges[2*t.down+1]]} cae. ¿Qué ruta tomaría?" +
                    if (t.hasCosts) " (coste rosa = 2)" else " (mínimo de saltos)"
            TTL -> "Dibuja la ruta. Sale con TTL ${t.ttl}: si muere antes, marca ✖ descartado"
            else -> "Dibuja la ruta de A a B"
        }

    fun explanation(t: RouteTask) =
        when (t.kind) {
            ALT ->
                "La ruta alternativa era " +
                    t.answer.joinToString(" → ") { t.names[it] } +
                    ".\n" +
                    lessons[if (t.hasCosts) 5 else 4]
            TTL ->
                "Ruta: " +
                    t.answer.joinToString(" → ") { t.names[it] } +
                    (if (t.dropped) " ✖ (TTL agotado)" else "") +
                    ".\n" +
                    lessons[6]
            else ->
                "Ruta: " +
                    t.answer.joinToString(" → ") { t.names[it] } +
                    ".\n" +
                    lessons[if (t.loop) 1 else 0]
        }

    /** Longest run of right routes in a row (missions) and the most frequent error. */
    fun bestRun(logs: List<RouteLog>) = Missions.bestStreak(logs.map { it.ok })

    fun weakest(logs: List<RouteLog>) =
        logs.filter { !it.ok }.groupingBy { it.error }.eachCount().maxByOrNull { it.value }?.key
}
