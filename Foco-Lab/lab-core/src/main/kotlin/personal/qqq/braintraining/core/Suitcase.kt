package personal.qqq.braintraining.core

import kotlin.random.Random

/**
 * Fase 2 (docs/DISENO-JUEGOS-V2.md §2): La maleta del técnico. "Me voy de instalación y en mi
 * maleta llevo…". One round of the 12-round flow is one chain: the chain starts with
 * [SuitcaseTask.start] items shown one by one, then grows by one item after each full recall until
 * it reaches [SuitcaseTask.target] or the second mistake ends it. Everything here depends only on
 * (level, seed): the board animates, this object decides.
 */
enum class SuitCategory(val title: String) {
    CABLE("Cable"),
    PORT("Puerto"),
    COMMAND("Comando"),
    DEVICE("Dispositivo"),
    HOP("Salto"),
}

/**
 * [key] is stable (album card `suitcase:<key>`); [layer] is the OSI layer of a device; [twin] the
 * look-alike decoy.
 */
data class SuitItem(
    val key: String,
    val category: SuitCategory,
    val label: String,
    val detail: String,
    val layer: Int = 0,
    val twin: String? = null,
)

/** Recall order of a chain. */
enum class SuitRule {
    ARRIVAL,
    LAYER,
    TRACE,
}

/** Level table (§2 "Progresión 1–10"). */
data class SuitLevel(
    val categories: List<SuitCategory>,
    val tray: Int,
    val twins: Boolean,
    val target: Int,
    val layerRounds: Boolean,
    val traceRounds: Boolean,
)

/**
 * One chain. [chain] holds the [target] items in arrival order; [decoys] are tray fillers that
 * never belong to the chain (look-alikes first from level 5). [showMs] is how long each new item
 * stays on screen.
 */
data class SuitcaseTask(
    val level: Int,
    val seed: Long,
    val rule: SuitRule,
    val chain: List<SuitItem>,
    val decoys: List<SuitItem>,
    val start: Int,
    val target: Int,
    val tray: Int,
    val showMs: Long,
    val hint: String,
) {
    /**
     * What must be tapped, in order, to recall the first [length] items. LAYER: stable sort by OSI
     * layer (ties keep arrival order).
     */
    fun order(length: Int): List<SuitItem> {
        val first = chain.take(length)
        return if (rule == SuitRule.LAYER) first.sortedBy { it.layer } else first
    }
}

/**
 * What a played chain leaves in the round's detail: longest chain recalled, help used, the item
 * failed and what was tapped instead, items seen.
 */
data class SuitcaseLog(
    val length: Int,
    val helped: Boolean,
    val target: Int,
    val miss: String = "",
    val tapped: String = "",
    val seen: List<String> = emptyList(),
) {
    val reached
        get() = length >= target

    /** Clean = target reached without help; this is the round's "correct" for Adaptive. */
    val clean
        get() = reached && !helped

    /**
     * Points 0..100: length against the target; a chain finished with help is worth 50 % (§2
     * "Reglas de error").
     */
    val points
        get() = (100 * minOf(length, target) / target).let { if (helped) it / 2 else it }

    fun encode() =
        "len=$length;help=${if(helped)1 else 0};target=$target;miss=$miss;as=$tapped;seen=${seen.joinToString(",")}"

    companion object {
        fun decode(text: String?): SuitcaseLog? =
            runCatching {
                    val m =
                        text!!.split(';').associate {
                            val i = it.indexOf('=')
                            it.substring(0, i) to it.substring(i + 1)
                        }
                    SuitcaseLog(
                        m.getValue("len").toInt(),
                        m["help"] == "1",
                        m.getValue("target").toInt(),
                        m["miss"] ?: "",
                        m["as"] ?: "",
                        m["seen"].orEmpty().split(',').filter { it.isNotBlank() },
                    )
                }
                .getOrNull()
    }
}

/** In-game tuning, always announced (§2 "Zona de reto visible"). */
data class SuitTuning(val extraMs: Long = 0, val extraDecoys: Int = 0, val note: String? = null)

object Suitcase {
    const val ID = "suitcase"
    const val MAX_TRAY = 12

    private fun cable(k: String, l: String, d: String, t: String? = null) =
        SuitItem(k, SuitCategory.CABLE, l, d, 1, t)

    private fun port(n: Int, s: String, d: String, t: Int? = null) =
        SuitItem("port$n", SuitCategory.PORT, "$n $s", d, 7, t?.let { "port$it" })

    private fun cmd(k: String, l: String, d: String, t: String? = null) =
        SuitItem(k, SuitCategory.COMMAND, l, d, 7, t)

    private fun dev(k: String, l: String, layer: Int, d: String, t: String? = null) =
        SuitItem(k, SuitCategory.DEVICE, l, d, layer, t)

    /** Fixed bank, in display order. Keys never change (album and saved logs depend on them). */
    val bank: List<SuitItem> =
        listOf(
            cable(
                "cat5e",
                "UTP Cat5e",
                "1 Gbps hasta 100 m: el cable de red de toda la vida",
                "cat6",
            ),
            cable("cat6", "UTP Cat6", "1 Gbps a 100 m y 10 Gbps en tramos de hasta 55 m", "cat5e"),
            cable(
                "cat6a",
                "UTP Cat6A",
                "10 Gbps en los 100 m completos; más grueso y apantallado",
                "cat6",
            ),
            cable(
                "fibramm",
                "Fibra multimodo",
                "Enlaces cortos dentro del edificio, entre armarios",
                "fibrasm",
            ),
            cable(
                "fibrasm",
                "Fibra monomodo",
                "Kilómetros de enlace entre edificios o con el operador",
                "fibramm",
            ),
            cable("coaxial", "Coaxial", "Cable módem, antenas y televisión"),
            cable(
                "consola",
                "Cable de consola",
                "Configura un switch o router por su puerto serie",
            ),
            cable(
                "cruzado",
                "Cable cruzado",
                "Une dos equipos iguales sin switch en medio",
                "directo",
            ),
            cable(
                "directo",
                "Cable directo",
                "Une un equipo con el switch: el más habitual",
                "cruzado",
            ),
            cable("sfp", "Módulo SFP", "Convierte un puerto del switch en una toma de fibra"),
            port(22, "SSH", "Acceso remoto cifrado a la consola", 23),
            port(23, "Telnet", "Acceso remoto sin cifrar: la contraseña viaja en claro", 22),
            port(80, "HTTP", "Web sin cifrar; hoy solo redirige a HTTPS", 443),
            port(443, "HTTPS", "Web cifrada con TLS", 80),
            port(53, "DNS", "Traduce nombres a direcciones IP"),
            port(25, "SMTP", "Envío de correo entre servidores"),
            port(21, "FTP", "Transferencia de ficheros sin cifrar"),
            port(110, "POP3", "Descarga el correo y lo borra del servidor", 143),
            port(143, "IMAP", "Lee el correo dejándolo en el servidor", 110),
            port(3389, "RDP", "Escritorio remoto de Windows"),
            port(3306, "MySQL", "Base de datos: solo desde la red interna"),
            port(161, "SNMP", "Monitorización de equipos de red"),
            cmd("ping", "ping", "Comprueba si un equipo responde (ICMP)", "traceroute"),
            cmd("traceroute", "traceroute", "Muestra cada salto hasta el destino", "ping"),
            cmd("dig", "dig", "Consulta DNS detallada, con cada registro", "nslookup"),
            cmd("nslookup", "nslookup", "Consulta DNS rápida", "dig"),
            cmd("ipconfig", "ipconfig", "Ver la IP en Windows", "ipaddr"),
            cmd("ipaddr", "ip addr", "Ver la IP en Linux", "ipconfig"),
            cmd("netstat", "netstat", "Conexiones y puertos abiertos del propio equipo", "nmap"),
            cmd("nmap", "nmap", "Escanea los puertos de otro equipo (con permiso)", "netstat"),
            cmd("arp", "arp -a", "Tabla que une cada IP con su MAC"),
            cmd("curl", "curl", "Pide una URL desde la terminal"),
            dev("hub", "Hub", 1, "Capa 1: repite la señal a todos los puertos", "switch"),
            dev("repetidor", "Repetidor", 1, "Capa 1: amplifica la señal para llegar más lejos"),
            dev("conversor", "Conversor de medios", 1, "Capa 1: pasa de cobre a fibra"),
            dev("switch", "Switch", 2, "Capa 2: reenvía tramas según la MAC", "hub"),
            dev("ap", "Punto de acceso", 2, "Capa 2: lleva la red cableada al wifi"),
            dev("puente", "Puente", 2, "Capa 2: une dos segmentos de red"),
            dev("router", "Router", 3, "Capa 3: elige la ruta según la IP", "switchl3"),
            dev(
                "switchl3",
                "Switch L3",
                3,
                "Capa 3: un switch que también enruta entre VLAN",
                "router",
            ),
            dev("fwl4", "Firewall L4", 4, "Capa 4: filtra por IP y puerto", "fwl7"),
            dev(
                "balanceol4",
                "Balanceador L4",
                4,
                "Capa 4: reparte conexiones TCP entre servidores",
            ),
            dev("proxy", "Proxy", 7, "Capa 7: hace las peticiones web en tu nombre"),
            dev("fwl7", "Firewall L7", 7, "Capa 7: entiende la aplicación (HTTP, DNS…)", "fwl4"),
            dev("waf", "WAF", 7, "Capa 7: protege una web de inyecciones"),
            dev("balanceol7", "Balanceador L7", 7, "Capa 7: reparte peticiones HTTP según la URL"),
        )

    fun item(key: String) = bank.firstOrNull { it.key == key }

    val keys
        get() = bank.map { it.key }

    fun level(l: Int): SuitLevel {
        require(l in 1..10)
        val c = SuitCategory.values().toList()
        return when (l) {
            1,
            2 -> SuitLevel(listOf(SuitCategory.CABLE), 6, false, 5, false, false)
            3,
            4 -> SuitLevel(listOf(SuitCategory.CABLE, SuitCategory.PORT), 8, false, 7, false, false)
            5,
            6 ->
                SuitLevel(
                    listOf(SuitCategory.CABLE, SuitCategory.PORT, SuitCategory.COMMAND),
                    8,
                    true,
                    9,
                    false,
                    false,
                )
            7,
            8 -> SuitLevel(c - SuitCategory.HOP, 10, true, 11, true, false)
            else -> SuitLevel(c - SuitCategory.HOP, 12, true, 13, true, true)
        }
    }

    /**
     * Items already in the suitcase when the chain starts (shown one by one), so 12 rounds stay
     * within a few minutes.
     */
    fun start(target: Int) = maxOf(2, target - 3)

    const val SHOW_MS = 1500L
    val groupHint = "Truco: agrupa de 3 en 3 — cable, comando, puerto."
    val layerHint = "Capa 1 cable/hub, capa 2 switch, capa 3 router, capa 4 firewall, capa 7 proxy."
    val traceHint =
        "Traceroute: el orden es el de los saltos; «* * *» es un salto que no responde, pero sigue ahí."

    fun make(level: Int, seed: Long): SuitcaseTask {
        val lv = level(level)
        val r = Random(seed)
        val roll = r.nextInt(4)
        val rule =
            when {
                lv.traceRounds && roll == 0 -> SuitRule.TRACE
                lv.layerRounds && roll == 1 -> SuitRule.LAYER
                else -> SuitRule.ARRIVAL
            }
        val target = lv.target
        if (rule == SuitRule.TRACE) {
            val (chain, decoys) = trace(target, r)
            return SuitcaseTask(
                level,
                seed,
                rule,
                chain,
                decoys,
                start(target),
                target,
                lv.tray,
                SHOW_MS,
                traceHint,
            )
        }
        val pool =
            if (rule == SuitRule.LAYER) bank.filter { it.category == SuitCategory.DEVICE }
            else bank.filter { it.category in lv.categories }
        // Mixed categories: draw round-robin so 2–4 categories really alternate.
        val chain =
            if (rule == SuitRule.LAYER) pool.shuffled(r).take(minOf(target, pool.size))
            else {
                val byCat =
                    lv.categories.map { c ->
                        pool.filter { it.category == c }.shuffled(r).toMutableList()
                    }
                val out = mutableListOf<SuitItem>()
                var i = r.nextInt(byCat.size)
                while (out.size < target) {
                    val q = byCat[i % byCat.size]
                    if (q.isNotEmpty()) out += q.removeAt(0)
                    i++
                    if (byCat.all { it.isEmpty() }) break
                }
                out
            }
        val inChain = chain.map { it.key }.toSet()
        val others =
            (if (rule == SuitRule.LAYER)
                    bank.filter {
                        it.category == SuitCategory.DEVICE || it.category == SuitCategory.CABLE
                    }
                else pool)
                .filter { it.key !in inChain }
                .shuffled(r)
        val twins =
            if (lv.twins)
                chain.mapNotNull { it.twin }.distinct().filter { it !in inChain }.mapNotNull(::item)
            else emptyList()
        val padding =
            bank.filter { it.key !in inChain && it.category !in lv.categories }.shuffled(r)
        val decoys = (twins + others + padding).distinctBy { it.key }
        return SuitcaseTask(
            level,
            seed,
            rule,
            chain,
            decoys,
            start(chain.size),
            chain.size,
            lv.tray,
            SHOW_MS,
            if (rule == SuitRule.LAYER) layerHint else groupHint,
        )
    }

    /**
     * Traceroute variant (level 9–10): hops with IP and growing latency, one "* * *", one
     * transatlantic jump and a dip after it.
     */
    private fun trace(n: Int, r: Random): Pair<List<SuitItem>, List<SuitItem>> {
        val timeout = r.nextInt(3, n - 1)
        val ocean = r.nextInt(3, n - 2).let { if (it == timeout) it + 1 else it }
        val isp = r.nextInt(2, 250)
        val chain = mutableListOf<SuitItem>()
        var ms = 1
        val ips = mutableListOf<String>()
        for (i in 0 until n) {
            val ip =
                when (i) {
                    0 -> "192.168.1.1"
                    1 -> "10.$isp.0.1"
                    n - 1 -> "203.0.113.${r.nextInt(2,250)}"
                    else ->
                        "${listOf(62,80,154,195)[i%4]}.${r.nextInt(1,255)}.${r.nextInt(1,255)}.${r.nextInt(1,255)}"
                }
            ms =
                when {
                    i == 0 -> 1
                    i == ocean -> ms + r.nextInt(80, 141)
                    i == ocean + 1 -> maxOf(ms - r.nextInt(60, 120), ms / 3)
                    else -> ms + r.nextInt(2, 9)
                }
            ips += ip
            val what =
                when (i) {
                    0 -> "puerta de enlace"
                    1 -> "tu operador"
                    n - 1 -> "destino"
                    ocean -> "enlace transatlántico"
                    else ->
                        if (i == ocean + 1) "responde antes: ICMP con poca prioridad"
                        else "red de tránsito"
                }
            chain +=
                if (i == timeout)
                    SuitItem(
                        "hop$i",
                        SuitCategory.HOP,
                        "* * *",
                        "Salto ${i+1}: no responde (filtra ICMP), pero el paquete sigue",
                    )
                else SuitItem("hop$i", SuitCategory.HOP, "$ip · $ms ms", "Salto ${i+1}: $what")
        }
        // Look-alikes: neighbouring addresses and latencies that never appear in the route.
        val decoys =
            List(MAX_TRAY + 2) { k ->
                val base = ips[1 + k % (n - 2)]
                val parts = base.split('.')
                val ip = "${parts[0]}.${parts[1]}.${parts[2]}.${(parts[3].toInt()+1+k)%254+1}"
                SuitItem(
                    "decoy$k",
                    SuitCategory.HOP,
                    "$ip · ${3+r.nextInt(2,190)} ms",
                    "No era de esta ruta",
                )
            }
        val labels = chain.map { it.label }.toSet()
        return chain to
            decoys.filter { it.label !in labels && it.label.substringBefore(" ·") !in ips }
    }

    /**
     * Two failed chains in a row: +0,5 s per item. Three clean chains in a row: +1 decoy on the
     * tray.
     */
    fun tuning(previous: List<SuitcaseLog>): SuitTuning {
        val fails = previous.takeLastWhile { !it.reached }.size
        val clean = previous.takeLastWhile { it.clean }.size
        return when {
            fails >= 2 -> SuitTuning(500, 0, "🧭 Te doy 0,5 s extra")
            clean >= 3 -> SuitTuning(0, 1, "+1 señuelo")
            else -> SuitTuning()
        }
    }

    fun explanation(t: SuitcaseTask) =
        when (t.rule) {
            SuitRule.LAYER ->
                "Regla de capas: mete los dispositivos ordenados por su capa OSI, no por orden de llegada.\n$layerHint"
            SuitRule.TRACE -> "Ruta completa: ${t.chain.joinToString(" → "){it.label}}.\n$traceHint"
            else -> "Cadena: ${t.chain.joinToString(" · "){it.label}}.\n$groupHint"
        }

    /** Item the player fails most across logs, as "Te cuesta 23 vs 22". */
    fun weakest(logs: List<SuitcaseLog>): String? {
        val m =
            logs
                .filter { it.miss.isNotBlank() }
                .groupingBy { it.miss to it.tapped }
                .eachCount()
                .maxByOrNull { it.value }
                ?.key ?: return null
        fun name(k: String) = item(k)?.label?.substringBefore(' ') ?: k
        return if (m.second.isNotBlank()) "Te cuesta ${name(m.first)} vs ${name(m.second)}"
        else "Te cuesta ${name(m.first)}"
    }
}

/**
 * The tray during one recall pass: [size] fixed slots. It always holds the window of chain items
 * still to tap (as many as fit next to one decoy) plus decoys; a used slot is refilled with the
 * next chain item or a fresh decoy, so the item expected next is always on the tray, whatever the
 * chain length.
 */
class SuitcaseTray(val task: SuitcaseTask, val length: Int, extraDecoys: Int = 0) {
    val order = task.order(length)
    val size = (task.tray + extraDecoys).coerceAtMost(Suitcase.MAX_TRAY)
    /** Item in each slot (never null after construction). */
    val slots = arrayOfNulls<SuitItem>(size)
    var step = 0
        private set

    private var nextChain = minOf(length, size - 1)
    private var nextDecoy = 0
    private val r = Random(task.seed * 31 + length)

    init {
        val start = order.take(nextChain).toMutableList()
        while (start.size < size) start += decoy()
        start.shuffled(r).forEachIndexed { i, it -> slots[i] = it }
    }

    private fun decoy(): SuitItem {
        val onTray = slots.mapNotNull { it?.key }.toSet()
        for (k in 0 until task.decoys.size) {
            val d = task.decoys[(nextDecoy + k) % task.decoys.size]
            if (d.key !in onTray) {
                nextDecoy = (nextDecoy + k + 1) % task.decoys.size
                return d
            }
        }
        return task.decoys[nextDecoy++ % task.decoys.size]
    }

    val done
        get() = step >= order.size

    val expected
        get() = order.getOrNull(step)

    fun slotOf(item: SuitItem?) = slots.indexOfFirst { it != null && it.key == item?.key }

    /**
     * Taps [slot]: true and the slot refills when it held the expected item; false (nothing
     * changes) otherwise.
     */
    fun take(slot: Int): Boolean {
        if (done || slots[slot]?.key != expected?.key) return false
        step++
        slots[slot] = null
        slots[slot] = if (nextChain < length) order[nextChain++] else decoy()
        return true
    }
}
