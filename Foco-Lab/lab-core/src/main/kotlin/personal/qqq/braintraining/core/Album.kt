package personal.qqq.braintraining.core

/**
 * Lote C: one card per concept of Redes y seguridad, built from the lessons that already exist in
 * [NetSec] (puertos, cebos, consultas, permisos, ataques e indicios de logs) plus a few base cards
 * of Bits and Subredes. [key] is stable: it comes from the content (port number or a hash of the
 * item's own text), never from the position in the list, so reordering a bank does not change which
 * cards the user owns.
 */
data class AlbumCard(val key: String, val game: String, val title: String, val lesson: String)

object Album {
    /**
     * FNV-1a 32 bits, in hex: stable across runs and JVMs (String.hashCode would also do, but this
     * is explicit).
     */
    internal fun hash(text: String): String {
        var h = 0x811c9dc5L
        for (ch in text) {
            h = h xor ch.code.toLong()
            h = (h * 0x01000193L) and 0xFFFFFFFFL
        }
        return h.toString(16).padStart(8, '0')
    }

    private fun line(text: String) = text.replace("\n", " ").replace(Regex("\\s+"), " ").trim()

    private val concepts =
        listOf(
            AlbumCard(
                "bits:posicion",
                "bits",
                "Valor de posición",
                "En binario cada posición vale el doble que la de su derecha: 1, 2, 4, 8, 16, 32, 64, 128.",
            ),
            AlbumCard(
                "bits:octeto",
                "bits",
                "Un octeto",
                "8 bits forman un octeto: van de 0 (00000000) a 255 (11111111). Una IPv4 son 4 octetos.",
            ),
            AlbumCard(
                "bits:restar",
                "bits",
                "Pasar a binario",
                "Para pasar a binario resta potencias de 2 de mayor a menor: si cabe escribes 1, si no cabe escribes 0.",
            ),
            AlbumCard(
                "subnet:prefijo",
                "subnet",
                "El prefijo",
                "El /24 cuenta los bits de red: los unos de la máscara. Lo que queda a la derecha son bits de host.",
            ),
            AlbumCard(
                "subnet:hosts",
                "subnet",
                "Hosts usables",
                "Una subred tiene 2^bits de host direcciones; se restan dos, la de red y la de broadcast.",
            ),
            AlbumCard(
                "subnet:bloque",
                "subnet",
                "Tamaño de bloque",
                "En el octeto que corta la máscara, el bloque es 256 menos su valor: con 255.255.255.192 las redes van de 64 en 64.",
            ),
        )

    /** Every card, in a fixed display order (game by game). */
    val cards: List<AlbumCard> by lazy {
        val list = mutableListOf<AlbumCard>()
        list += concepts.filter { it.game == "bits" }
        list += concepts.filter { it.game == "subnet" }
        NetSec.portList.forEach { p ->
            list +=
                AlbumCard("ports:${p.port}", "ports", "${p.port} · ${p.service}", line(p.lesson))
        }
        NetSec.baits.forEach { b ->
            list +=
                AlbumCard(
                    "phishing:${hash(b.text)}",
                    "phishing",
                    (if (b.phishing) "Trampa · " else "Seguro · ") + b.mark,
                    line(b.clue),
                )
        }
        NetSec.queries.forEach { q ->
            list +=
                AlbumCard(
                    "sqlsafe:q${hash(q.bad)}",
                    "sqlsafe",
                    "Consulta parametrizada",
                    line(q.lesson),
                )
        }
        NetSec.grants.forEach { g ->
            list +=
                AlbumCard(
                    "sqlsafe:g${hash(g.need)}",
                    "sqlsafe",
                    "Mínimo privilegio",
                    line(g.lesson),
                )
        }
        NetSec.attacks.forEach { a ->
            list += AlbumCard("sqlsafe:a${hash(a.payload)}", "sqlsafe", a.type, line(a.lesson))
        }
        NetSec.clues.forEach { c ->
            list += AlbumCard("logs:${hash(c.why)}", "logs", "Indicio de log", line(c.why))
        }
        // Fase 2: one card per suitcase item seen and per alert pattern detected.
        Suitcase.bank.forEach { i ->
            list +=
                AlbumCard(
                    "suitcase:${i.key}",
                    "suitcase",
                    i.label,
                    "${i.category.title} · ${i.label}: ${i.detail}.",
                )
        }
        Vigil.patternKeys.forEachIndexed { p, k ->
            list += AlbumCard("vigil:$k", "vigil", Vigil.patternNames[p], Vigil.lessons[p])
        }
        // Fase 3: Ruta de paquetes, Turno anti-DDoS (one card per rule and lesson) and Parejas de
        // incidentes (one per bank entry).
        Route.lessonKeys.forEachIndexed { i, k ->
            list += AlbumCard("route:$k", "route", Route.lessonTitles[i], Route.lessons[i])
        }
        Ddos.lessonKeys.forEachIndexed { i, k ->
            list += AlbumCard("ddos:$k", "ddos", Ddos.lessonTitles[i], Ddos.lessons[i])
        }
        IncidentPairs.bank.forEach { e ->
            list += AlbumCard("pairs:${e.key}", "pairs", e.texts.joinToString(" ↔ "), e.lesson)
        }
        list.toList()
    }
    val keys: Set<String> by lazy { cards.map { it.key }.toSet() }

    fun get(key: String) = cards.firstOrNull { it.key == key }

    /**
     * The card a correct answer of [game] earns, or null. Bank games match the card whose lesson
     * appears in the challenge [explanation]; Bits and Subredes give their next base card not yet
     * in [owned].
     */
    fun forAnswer(game: String, explanation: String, owned: Set<String>): AlbumCard? {
        if (game == "bits" || game == "subnet")
            return cards.firstOrNull { it.game == game && it.key !in owned }
        val text = line(explanation)
        return cards.firstOrNull { it.game == game && it.key !in owned && text.contains(it.lesson) }
    }

    /** Games with album cards, in display order. */
    val games
        get() = NetSec.ids + listOf(Suitcase.ID, Vigil.ID, Route.ID, Ddos.ID, IncidentPairs.ID)

    /**
     * Cards a finished board round earns (Fase 2), from its log [detail]: every suitcase item seen,
     * every alert pattern detected. Only cards not in [owned], in album order.
     */
    fun forBoard(game: String, detail: String?, owned: Set<String>): List<AlbumCard> {
        val keys =
            when (game) {
                Suitcase.ID -> SuitcaseLog.decode(detail)?.seen?.map { "suitcase:$it" }.orEmpty()
                Vigil.ID ->
                    VigilLog.decode(detail)
                        ?.found
                        ?.map { "vigil:${Vigil.patternKeys[it]}" }
                        .orEmpty()
                Route.ID ->
                    RouteLog.decode(detail)
                        ?.learned
                        ?.mapNotNull { Route.lessonKeys.getOrNull(it) }
                        ?.map { "route:$it" }
                        .orEmpty()
                Ddos.ID ->
                    DdosLog.decode(detail)
                        ?.learned
                        ?.mapNotNull { Ddos.lessonKeys.getOrNull(it) }
                        ?.map { "ddos:$it" }
                        .orEmpty()
                IncidentPairs.ID -> PairsLog.decode(detail)?.keys?.map { "pairs:$it" }.orEmpty()
                else -> emptyList()
            }.toSet()
        return cards.filter { it.key in keys && it.key !in owned }
    }

    /** Milestones that deserve a celebration (every 10 cards and the full album). */
    fun milestone(before: Int, after: Int) =
        after > before && (after / 10 > before / 10 || after == cards.size)
}
