package personal.qqq.braintraining.core

import kotlin.math.roundToInt
import kotlin.random.Random

enum class Area(val title: String) {
    MEMORY("Mente"),
    ATTENTION("Atención"),
    SPEED("Velocidad"),
    LOGIC("Lógica"),
    MATH("Matemáticas"),
    REAL("Vida cotidiana"),
    NETSEC("Redes y seguridad"),
    ARCADE("Arcade"),
}

/**
 * [hidden] games stay defined so saved history keeps working (`Games.get`), but no menu or plan
 * offers them.
 */
data class Game(
    val id: String,
    val title: String,
    val area: Area,
    val description: String,
    val instructions: String,
    val symbol: String,
    val hidden: Boolean = false,
)

object Games {
    val all =
        listOf(
                Game(
                    "suitcase",
                    "La maleta del técnico",
                    Area.MEMORY,
                    "Me voy de instalación y en mi maleta llevo…",
                    "Cada ronda entra un objeto nuevo en la maleta: un cable, un comando, un puerto… La maleta se cierra y tocas en la bandeja todos los objetos, en orden, desde el primero. Mantén pulsada una ficha para leer qué es. El primer fallo te ayuda y sigues; el segundo cierra la cadena.",
                    "▣",
                ),
                Game(
                    "vigil",
                    "Vigía de alertas",
                    Area.ATTENTION,
                    "Turno de noche en el SOC",
                    "Las líneas del registro suben por el monitor. Casi todo es ruido normal: toca solo las alertas reales (fuerza bruta, horas raras, escaneos…). Si una alerta sale por arriba sin marcar pierdes un escudo; una falsa alarma cuesta medio. Mantén pulsado el monitor para congelarlo 2 s (3 veces por partida).",
                    "◉",
                ),
                Game(
                    "route",
                    "Ruta de paquetes",
                    Area.MEMORY,
                    "Eres el protocolo de enrutamiento",
                    "Mira el paquete viajar de A a B por los routers. Después dibuja su camino: toca los routers en orden o arrastra el dedo por ellos; tocar el último lo deshace. Hay enlaces caídos, rutas alternativas y, al final, TTL: si se agota, marca dónde muere el paquete.",
                    "⇢",
                ),
                Game(
                    "ddos",
                    "Turno anti-DDoS",
                    Area.ARCADE,
                    "Eres el filtro frente al servidor",
                    "Los paquetes suben por la cinta al ritmo del turno. Toca los maliciosos para bloquearlos y deja pasar los legítimos. Una partida son 5 oleadas seguidas: en la tercera el jefe de turno sella una regla nueva (puerto 23, SYN sin ACK, más de 3 por IP…) y la quinta es el pico, más rápida. Cada acierto seguido sube el combo, hasta ×4, y un fallo lo rompe. Desliza hacia arriba una tarjeta para mandar su IP a la lista negra.",
                    "⛨",
                ),
                Game(
                    "pairs",
                    "Parejas de incidentes",
                    Area.ARCADE,
                    "Ataque ↔ defensa, alerta ↔ causa",
                    "Voltea dos cartas: si son pareja (puerto y servicio, ataque y defensa, alerta y causa) se quedan; si no, se vuelven. Desde el nivel 7 hay tríos: ataque, alerta y defensa. Toca dos veces una pareja hecha para leer su lección.",
                    "⧉",
                ),
                Game(
                    "orbit",
                    "Órbitas",
                    Area.MEMORY,
                    "Recuerda un camino de luz",
                    "Un cometa recorre la órbita y enciende nodos sin números: recuerda los lugares y tócalos en el mismo orden. Rondas 5–8: el doble anillo pide doble toque y el magenta, el nodo opuesto. Rondas 9–12: la órbita gira antes de tu turno. Ignora los destellos de fuera para ganar el bono de enfoque.",
                    "◌",
                ),
                Game(
                    "compass",
                    "Rumbo inverso",
                    Area.ATTENTION,
                    "Cambia de regla sin perder el rumbo",
                    "Lee la regla de cada ronda. Si pide dirección opuesta, responde al contrario de la flecha; si pide la misma, sigue la flecha. La regla siempre está escrita, además del color.",
                    "➜",
                ),
                Game(
                    "balance",
                    "Balanza",
                    Area.MATH,
                    "Descubre el peso que falta",
                    "Los dos lados pesan lo mismo. Todas las cajas pesan igual. Resta el peso conocido y divide lo que queda entre las cajas. Comprueba tu respuesta en la balanza.",
                    "⚖",
                ),
                Game(
                    "grid",
                    "Destellos",
                    Area.MEMORY,
                    "Tu memoria, celda a celda",
                    "Observa las casillas iluminadas. Cuando se oculten, selecciona exactamente las mismas y pulsa Comprobar.",
                    "▦",
                ),
                Game(
                    "sequence",
                    "Ecos",
                    Area.MEMORY,
                    "Sigue el hilo",
                    "Observa el orden de los destellos. Después toca las casillas en el mismo orden. Una casilla puede repetirse.",
                    "↝",
                ),
                Game(
                    "working",
                    "Última huella",
                    Area.MEMORY,
                    "Mantén la información activa",
                    "Verás números uno a uno. Al terminar, recuerda el número situado a la distancia indicada desde el final.",
                    "◈",
                ),
                Game(
                    "search",
                    "Punto de enfoque",
                    Area.ATTENTION,
                    "Encuentra la diferencia",
                    "Encuentra y toca el símbolo indicado. Solo hay uno igual entre los distractores.",
                    "◎",
                ),
                Game(
                    "pattern",
                    "Intruso",
                    Area.ATTENTION,
                    "Detecta lo que no encaja",
                    "Lee la regla y selecciona el único número que no la cumple.",
                    "◇",
                ),
                Game(
                    "flanker",
                    "Centro",
                    Area.ATTENTION,
                    "Ignora el ruido",
                    "Mira solo la flecha del centro. Indica si apunta a la izquierda o a la derecha, ignorando las demás.",
                    "⇄",
                ),
                Game(
                    "reaction",
                    "Pulso clásico",
                    Area.SPEED,
                    "Responde en el momento justo",
                    "Espera a que el panel cambie y diga ¡Ahora! Entonces tócalo. Tocar antes cuenta como salida anticipada.",
                    "ϟ",
                ),
                Game(
                    "choice",
                    "Decisión",
                    Area.SPEED,
                    "Una regla, una respuesta",
                    "Clasifica el número según la regla mostrada. La precisión importa más que tocar sin pensar.",
                    "⌁",
                ),
                Game(
                    "series",
                    "Siguiente",
                    Area.LOGIC,
                    "Encuentra la regla",
                    "Completa la serie. Las reglas usan sumas, multiplicaciones o incrementos alternos.",
                    "⋯",
                ),
                Game(
                    "math",
                    "Cálculo claro",
                    Area.MATH,
                    "Agilidad con números",
                    "Resuelve la operación respetando los paréntesis. Todas las respuestas son números enteros.",
                    "±",
                ),
                Game(
                    "percent",
                    "Porcentajes prácticos",
                    Area.MATH,
                    "Descuentos, propinas y aumentos",
                    "Calcula el porcentaje y distingue la parte del total. Revisa la solución antes de avanzar.",
                    "%",
                ),
                Game(
                    "fraction",
                    "Fracciones útiles",
                    Area.MATH,
                    "Reparte y combina cantidades",
                    "Calcula una fracción de una cantidad o combina fracciones con el mismo denominador. Las respuestas son enteras.",
                    "½",
                ),
                Game(
                    "equation",
                    "Encuentra x",
                    Area.MATH,
                    "Resuelve lo desconocido",
                    "Despeja x: deshaz las operaciones en orden inverso. Comprueba sustituyendo el resultado.",
                    "x",
                ),
                Game(
                    "ratio",
                    "Proporciones",
                    Area.MATH,
                    "Escala recetas y cantidades",
                    "Calcula primero la cantidad por unidad y después multiplícala por las unidades necesarias.",
                    "∝",
                ),
                Game(
                    "budget",
                    "Presupuesto",
                    Area.REAL,
                    "Decide cuánto puedes gastar",
                    "Identifica ingresos y gastos; calcula lo disponible o el tiempo necesario para ahorrar.",
                    "$",
                ),
                Game(
                    "unitprice",
                    "Compra inteligente",
                    Area.REAL,
                    "Compara el costo por unidad",
                    "Divide el precio entre las unidades de cada paquete. Elige el más económico por unidad, no solo el precio más bajo.",
                    "≋",
                ),
                Game(
                    "planning",
                    "Tiempo y planificación",
                    Area.REAL,
                    "Organiza viajes y tareas",
                    "Relaciona distancia, velocidad, duración y capacidad. Lee las unidades y resuelve paso a paso.",
                    "◷",
                ),
                Game(
                    "matrix",
                    "Transforma",
                    Area.LOGIC,
                    "Razona con formas",
                    "Completa la cuadrícula. Cada fila sigue la misma regla de avance por el ciclo de símbolos mostrado.",
                    "▧",
                ),
                Game(
                    "calc",
                    "Cálculo",
                    Area.MATH,
                    "Operaciones, partes, incógnitas y series",
                    "Cada ronda trae un modo distinto: operaciones, porcentajes, fracciones y proporciones, encontrar x o la balanza, y series. Todas las respuestas son números enteros; revisa la solución paso a paso.",
                    "∑",
                ),
            )
            .map { if (it.id in retired) it.copy(hidden = true) else it } + NetSec.games
    /**
     * Eliminated (search…planning) or merged (sequence → orbit, reaction → arcade Pulso, the math
     * family → calc) in the games review, Lote A.
     */
    val retiredLoteA: Set<String>
        get() =
            setOf(
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

    /**
     * Fase 2 of docs/DISENO-JUEGOS-V2.md: Destellos, Última huella, Transforma and Cálculo leave
     * the menu (Ruta espacial is an arcade session outside [all]).
     */
    val retiredFase2: Set<String>
        get() = setOf("grid", "working", "matrix", "calc")

    /**
     * Fase 3: Órbitas becomes Ruta de paquetes (its level migrates, see [Route.migratedLevel]); its
     * history stays readable.
     */
    val retiredFase3: Set<String>
        get() = setOf("orbit")

    /**
     * Hidden from menus, missions, plans and Relámpago; their history keeps resolving through
     * [get].
     */
    val retired: Set<String>
        get() = retiredLoteA + retiredFase2 + retiredFase3

    /** What menus and plans may offer. */
    val visible
        get() = all.filter { !it.hidden }

    /** "Cálculo" modes: each round picks a mode, then one of its original generators. */
    val calcModes =
        listOf(
            "Operaciones" to listOf("math"),
            "Partes" to listOf("percent", "fraction", "ratio"),
            "Incógnitas" to listOf("equation", "balance"),
            "Series" to listOf("series"),
        )

    /**
     * Never throws: an id this version does not know (a newer backup, for instance) resolves to a
     * hidden placeholder.
     */
    fun get(id: String) =
        all.firstOrNull { it.id == id } ?: Game(id, id, Area.MEMORY, "", "", "?", hidden = true)
}

/**
 * SUBNET, INBOX, FIREWALL and BITS (Lote B) are the pilot mechanics (see Pilot.kt); their data
 * travels in the matching task field.
 */
/**
 * SUITCASE and VIGIL (Fase 2) are self-contained boards: the board plays the whole round and
 * reports its log.
 */
/** DDOS, ROUTE and PAIRS (Fase 3) are board kinds too; so is ORBIT (Órbitas 2, see Orbit.kt). */
enum class Kind {
    GRID,
    SEQUENCE,
    WORKING,
    CHOICE,
    REACTION,
    MATRIX,
    SUBNET,
    INBOX,
    FIREWALL,
    BITS,
    SUITCASE,
    VIGIL,
    DDOS,
    ROUTE,
    PAIRS,
    ORBIT,
}

/**
 * [format] is the question type inside a game (variety rule), [item] the seed that rebuilds this
 * exact challenge and [review] marks a spaced-repetition round.
 */
data class Challenge(
    val kind: Kind,
    val prompt: String,
    val choices: List<String> = emptyList(),
    val answer: Int = 0,
    val targets: List<Int> = emptyList(),
    val stream: List<String> = emptyList(),
    val columns: Int = 3,
    val exposureMs: Long = 1500,
    val deadlineMs: Long = 15000,
    val explanation: String = "",
    val board: List<String> = emptyList(),
    val mono: Boolean = false,
    val format: Int = 0,
    val item: Long = 0L,
    val review: Boolean = false,
    val subnet: SubnetTask? = null,
    val inbox: InboxTask? = null,
    val firewall: FirewallTask? = null,
    val source: String = "",
    val bits: BitsTask? = null,
    val suitcase: SuitcaseTask? = null,
    val vigil: VigilTask? = null,
    val ddos: DdosTask? = null,
    val route: RouteTask? = null,
    val pairs: IncidentBoard? = null,
    val orbit: OrbitTask? = null,
)

object Generator {
    /**
     * [unit] is the position inside the partida (the wave, for games whose [GameFormat] plans it);
     * −1 when it does not matter.
     */
    fun make(id: String, level: Int, seed: Long, unit: Int = -1): Challenge {
        require(level in 1..10)
        val r = Random(seed)
        val l = level
        fun options(answer: Int): Pair<List<String>, Int> {
            val nums = linkedSetOf(answer)
            while (nums.size < 4) nums += answer + r.nextInt(-12 - l, 13 + l)
            val list = nums.shuffled(r).map(Int::toString)
            return list to list.indexOf(answer.toString())
        }
        fun numeric(prompt: String, answer: Int, explanation: String): Challenge {
            val (o, a) = options(answer)
            return Challenge(
                Kind.CHOICE,
                prompt,
                o,
                a,
                deadlineMs = 120000,
                explanation = "Respuesta: $answer\n\n$explanation",
            )
        }
        return when (id) {
            // Órbitas 2: the round inside the partida ([unit]) picks the phase; the mode (Espejo,
            // Expansiva) comes from the run.
            Orbit.ID -> Orbit.challenge(l, r.nextLong(), unit.coerceAtLeast(0))
            "compass" -> {
                val dir = r.nextInt(4)
                val opposite = l < 4 || r.nextBoolean()
                val a = (dir + if (opposite) 2 else 0) % 4
                val names = listOf("arriba", "derecha", "abajo", "izquierda")
                Challenge(
                    Kind.CHOICE,
                    if (opposite) "DIRECCIÓN OPUESTA" else "MISMA DIRECCIÓN",
                    listOf("↑ Arriba", "→ Derecha", "↓ Abajo", "← Izquierda"),
                    a,
                    board = listOf(dir.toString(), opposite.toString()),
                    deadlineMs = (14000 - l * 600).toLong(),
                    explanation =
                        "La flecha apunta hacia ${names[dir]}.\nLa regla pide ${if(opposite)"la dirección contraria" else "seguir esa dirección"}: ${names[a]}.\nPrimero identifica la regla y después decide.",
                )
            }
            "balance" -> {
                val x = r.nextInt(1, 8 + l * 2)
                val copies = if (l < 4) 1 else r.nextInt(2, 4)
                val extra = r.nextInt(1, 4 + l)
                val total = copies * x + extra
                numeric(
                        "Ambos lados pesan lo mismo. $copies ${if(copies==1)"caja" else "cajas"} + $extra kg = $total kg.\n¿Cuántos kg pesa una caja?",
                        x,
                        "1. Quita el peso conocido: $total − $extra = ${total-extra} kg.\n2. Reparte entre $copies ${if(copies==1)"caja" else "cajas"}: ${total-extra} ÷ $copies = $x kg.\n3. Comprueba: $copies × $x + $extra = $total kg.",
                    )
                    .copy(board = listOf(copies.toString(), extra.toString(), total.toString()))
            }
            "grid" -> {
                val cols = if (l < 5) 3 else if (l < 9) 4 else 5
                val n = 2 + (l - 1) * 7 / 9
                Challenge(
                    Kind.GRID,
                    "Recuerda las casillas",
                    targets = (0 until cols * cols).shuffled(r).take(n),
                    columns = cols,
                    exposureMs = 2600,
                    deadlineMs = 25000,
                    explanation = "Había $n casillas iluminadas.",
                )
            }
            "sequence" ->
                Challenge(
                    Kind.SEQUENCE,
                    "Recuerda el orden",
                    targets = List(3 + (l - 1) * 7 / 9) { r.nextInt(9) },
                    exposureMs = 650,
                    deadlineMs = 30000,
                    explanation = "Repite los destellos en su orden exacto.",
                )
            "working" -> {
                val n = 2 + (l - 1) / 3
                val seq = List(5 + l / 2) { r.nextInt(10).toString() }
                val digit = seq[seq.size - n].toInt()
                val pool = linkedSetOf(digit)
                while (pool.size < 4) pool += r.nextInt(10)
                val opts = pool.shuffled(r).map(Int::toString)
                val a = opts.indexOf(digit.toString())
                Challenge(
                    Kind.WORKING,
                    "¿Qué número estaba $n posiciones desde el final?",
                    opts,
                    a,
                    stream = seq,
                    exposureMs = 850,
                    deadlineMs = 20000,
                    explanation =
                        "Secuencia: ${seq.joinToString(" · ")}. Respuesta: ${seq[seq.size-n]}.",
                )
            }
            "search" -> {
                val n = 9 + (l - 1) / 3 * 3
                val a = r.nextInt(n)
                val target = if (l < 5) "⊕" else "⊙"
                Challenge(
                    Kind.CHOICE,
                    "Encuentra: $target",
                    List(n) {
                        if (it == a) target else if (l < 5) "⊗" else listOf("⊕", "⊗", "◎").random(r)
                    },
                    a,
                    deadlineMs = (16000 - l * 700).toLong(),
                    explanation = "Busca el símbolo exacto, no solo su contorno.",
                )
            }
            "pattern" -> {
                val n = if (l < 6) 4 else 6
                val a = r.nextInt(n)
                val nums = (1..50).shuffled(r).take(n).map { it * 2 }.toMutableList()
                nums[a]++
                Challenge(
                    Kind.CHOICE,
                    "Todos son pares, excepto uno",
                    nums.map(Int::toString),
                    a,
                    deadlineMs = (18000 - l * 600).toLong(),
                    explanation = "${nums[a]} es impar: no es divisible entre 2.",
                )
            }
            "flanker" -> {
                val right = r.nextBoolean()
                val f = if (r.nextBoolean()) "→" else "←"
                val c = if (right) "→" else "←"
                Challenge(
                    Kind.CHOICE,
                    "$f  $f  $c  $f  $f",
                    listOf("← Izquierda", "Derecha →"),
                    if (right) 1 else 0,
                    deadlineMs = (6500 - l * 350).toLong(),
                    explanation = "La flecha central era $c.",
                )
            }
            "reaction" ->
                Challenge(
                    Kind.REACTION,
                    "Espera la señal",
                    exposureMs = r.nextLong(1500, 4001),
                    deadlineMs = (2500 - l * 100).toLong(),
                    explanation = "La señal marca el inicio; anticiparse no ayuda.",
                )
            "choice" -> {
                val n = r.nextInt(2, 20 + l * 12)
                if (l < 6)
                    Challenge(
                        Kind.CHOICE,
                        "$n\n¿Par o impar?",
                        listOf("Par", "Impar"),
                        n % 2,
                        deadlineMs = (6500 - l * 300).toLong(),
                        explanation = if (n % 2 == 0) "$n es par." else "$n es impar.",
                    )
                else
                    Challenge(
                        Kind.CHOICE,
                        "$n\n¿Es múltiplo de 3?",
                        listOf("Sí", "No"),
                        if (n % 3 == 0) 0 else 1,
                        deadlineMs = (8000 - l * 300).toLong(),
                        explanation =
                            if (n % 3 == 0) "$n ÷ 3 = ${n/3}." else "$n no es divisible entre 3.",
                    )
            }
            "series" -> {
                val a = r.nextInt(1, 15)
                val d = r.nextInt(2, 5 + l)
                if (l < 5)
                    numeric(
                        List(4) { a + it * d }.joinToString(" · ") + " · ?",
                        a + 4 * d,
                        "Suma $d en cada paso.",
                    )
                else if (l < 8) {
                    val m = r.nextInt(2, 4)
                    val seq = mutableListOf(a)
                    repeat(4) { seq += seq.last() * m }
                    numeric(
                        seq.take(4).joinToString(" · ") + " · ?",
                        seq[4],
                        "Multiplica por $m en cada paso.",
                    )
                } else {
                    val e = d + 2
                    numeric(
                        "$a · ${a+d} · ${a+d+e} · ${a+2*d+e} · ?",
                        a + 2 * d + 2 * e,
                        "Alterna +$d y +$e.",
                    )
                }
            }
            "math" -> {
                val a = r.nextInt(2, 10 + l * 3)
                val b = r.nextInt(2, 10 + l)
                when (r.nextInt(if (l < 4) 2 else if (l < 7) 4 else 5)) {
                    0 ->
                        numeric(
                            "$a + $b = ?",
                            a + b,
                            "1. Descompón $b en decenas y unidades.\n2. Suma esas partes a $a: ${a+b}.",
                        )
                    1 -> {
                        val total = a + b
                        numeric(
                            "$total − $b = ?",
                            a,
                            "1. Busca cuánto falta desde $b hasta $total.\n2. $b + $a = $total; por tanto, $total − $b = $a.",
                        )
                    }
                    2 ->
                        numeric(
                            "$a × $b = ?",
                            a * b,
                            "1. Multiplicar es sumar $a, $b veces.\n2. $a × $b = ${a*b}.",
                        )
                    3 ->
                        numeric(
                            "${a*b} ÷ $b = ?",
                            a,
                            "1. Reparte ${a*b} en $b grupos iguales.\n2. Cada grupo tiene $a. Comprueba: $a × $b = ${a*b}.",
                        )
                    else -> {
                        val c = r.nextInt(2, 8)
                        numeric(
                            "($a + $b) × $c = ?",
                            (a + b) * c,
                            "1. Resuelve el paréntesis: $a + $b = ${a+b}.\n2. Multiplica: ${a+b} × $c = ${(a+b)*c}.",
                        )
                    }
                }
            }
            "percent" -> {
                val price = r.nextInt(2, 8 + l) * 100
                val pct = listOf(5, 10, 15, 20, 25, 30, 50).take(if (l < 4) 4 else 7).random(r)
                val part = price * pct / 100
                if (l < 5)
                    numeric(
                        "¿Cuánto es el $pct % de $price?",
                        part,
                        "1. Un 1 % es $price ÷ 100 = ${price/100}.\n2. Multiplica por $pct: ${price/100} × $pct = $part.",
                    )
                else if (r.nextBoolean())
                    numeric(
                        "Una compra cuesta $$price. Tiene $pct % de descuento. ¿Cuánto pagas?",
                        price - part,
                        "1. Descuento: $price × $pct ÷ 100 = $$part.\n2. Resta el descuento: $price − $part = $${price-part}.",
                    )
                else
                    numeric(
                        "Una cuenta de $$price lleva $pct % de propina. ¿Cuál es el total?",
                        price + part,
                        "1. Propina: $price × $pct ÷ 100 = $$part.\n2. Suma la propina: $price + $part = $${price+part}.",
                    )
            }
            "fraction" -> {
                val d = r.nextInt(3, 5 + l)
                val n = r.nextInt(1, d)
                val unit = r.nextInt(2, 6 + l)
                val total = d * unit
                if (l < 6)
                    numeric(
                        "¿Cuánto es $n/$d de $total?",
                        n * unit,
                        "1. Divide en $d partes: $total ÷ $d = $unit.\n2. Toma $n partes: $unit × $n = ${unit*n}.",
                    )
                else {
                    val other = r.nextInt(1, d)
                    numeric(
                        "¿Cuánto es ($n/$d + $other/$d) de $total?",
                        (n + other) * unit,
                        "1. Igual denominador: suma los numeradores, $n + $other = ${n+other}.\n2. $total ÷ $d = $unit.\n3. $unit × ${n+other} = ${(n+other)*unit}.",
                    )
                }
            }
            "equation" -> {
                val x = r.nextInt(1, 5 + l * 2)
                val a = if (l < 4) 1 else r.nextInt(2, 3 + l)
                val b = r.nextInt(1, 5 + l)
                val y = a * x + b
                numeric(
                    "${if(a==1)"x" else "${a}x"} + $b = $y\n¿Cuánto vale x?",
                    x,
                    "1. Resta $b a ambos lados: ${a}x = ${y-b}.\n2. Divide entre $a: x = ${y-b} ÷ $a = $x.\n3. Comprueba: $a × $x + $b = $y.",
                )
            }
            "ratio" -> {
                val people = r.nextInt(2, 5)
                val portions = r.nextInt(3, 6 + l)
                val per = r.nextInt(2, 5 + l) * 25
                val amount = people * per
                numeric(
                    "Una receta para $people personas usa $amount g de arroz. ¿Cuántos gramos necesitas para $portions personas?",
                    per * portions,
                    "1. Por persona: $amount ÷ $people = $per g.\n2. Para $portions personas: $per × $portions = ${per*portions} g.\nEn tu día: escala ingredientes antes de cocinar.",
                )
            }
            "budget" -> {
                val income = r.nextInt(5, 10 + l) * 100
                val rent = r.nextInt(2, 5) * 50
                val food = r.nextInt(2, 5) * 25
                val saved = r.nextInt(1, 4) * 25
                if (l < 6)
                    numeric(
                        "Tienes $$income. Pagas $$rent de alquiler, $$food de comida y reservas $$saved para ahorro. ¿Cuánto queda disponible?",
                        income - rent - food - saved,
                        "1. Suma lo comprometido: $rent + $food + $saved = ${rent+food+saved}.\n2. Disponible: $income − ${rent+food+saved} = $${income-rent-food-saved}.\nEn tu día: aparta el ahorro antes de gastar.",
                    )
                else {
                    val weeks = r.nextInt(2, 8 + l)
                    val cost = saved * weeks
                    numeric(
                        "Quieres comprar algo de $$cost y puedes ahorrar $$saved por semana. Partiendo de cero, ¿cuántas semanas necesitas?",
                        weeks,
                        "1. Objetivo ÷ ahorro semanal: $cost ÷ $saved = $weeks.\n2. Comprueba: $weeks × $saved = $$cost.\nSupuesto: ahorras la misma cantidad cada semana.",
                    )
                }
            }
            "unitprice" -> {
                val na = r.nextInt(2, 6)
                val nb = r.nextInt(3, 7 + l)
                val cheap = r.nextInt(2, 6)
                val expensive = cheap + r.nextInt(1, 5)
                val aCheap = r.nextBoolean()
                val ua = if (aCheap) cheap else expensive
                val ub = if (aCheap) expensive else cheap
                Challenge(
                    Kind.CHOICE,
                    "Paquete A: $na unidades por $${na*ua}.\nPaquete B: $nb unidades por $${nb*ub}.\nMisma calidad y tamaño. ¿Cuál cuesta menos por unidad?",
                    listOf("Paquete A", "Paquete B"),
                    if (aCheap) 0 else 1,
                    deadlineMs = 120000,
                    explanation =
                        "Respuesta: paquete ${if(aCheap)"A" else "B"}.\n\n1. A: ${na*ua} ÷ $na = $$ua por unidad.\n2. B: ${nb*ub} ÷ $nb = $$ub por unidad.\n3. $$cheap es menor que $$expensive.\nEn tu día: compara precios por kg, litro o unidad.",
                )
            }
            "planning" -> {
                if (l < 5) {
                    val tasks = r.nextInt(3, 7 + l)
                    val mins = r.nextInt(2, 8)
                    val pause = r.nextInt(3, 10)
                    numeric(
                        "Tienes $tasks tareas de $mins minutos cada una y una sola pausa de $pause minutos. ¿Cuántos minutos necesitas en total?",
                        tasks * mins + pause,
                        "1. Tareas: $tasks × $mins = ${tasks*mins} min.\n2. Añade la pausa: ${tasks*mins} + $pause = ${tasks*mins+pause} min.\nEn tu día: incluye descansos en la planificación.",
                    )
                } else {
                    val speed = listOf(30, 60, 90).random(r)
                    val hours = r.nextInt(1, 2 + l)
                    val distance = speed * hours
                    numeric(
                        "Un viaje es de $distance km a una velocidad constante de $speed km/h, sin paradas. ¿Cuántas horas tarda?",
                        hours,
                        "1. Tiempo = distancia ÷ velocidad.\n2. $distance ÷ $speed = $hours h.\n3. Comprueba: $speed × $hours = $distance km.\nEn un viaje real, añade tiempo para tráfico y paradas.",
                    )
                }
            }
            "matrix" -> {
                val s = if (l < 5) listOf("↑", "→", "↓", "←") else listOf("○", "△", "□", "◇")
                val step = if (l < 8) 1 else 2
                val start = r.nextInt(4)
                val board =
                    List(3) { (start + it) % 4 }
                        .flatMap { b -> List(3) { s[(b + it * step) % 4] } }
                        .toMutableList()
                val a = s.indexOf(board.last())
                board[8] = "?"
                Challenge(
                    Kind.MATRIX,
                    "Ciclo: ${s.joinToString(" → ")} → ${s.first()}",
                    s,
                    a,
                    board = board,
                    deadlineMs = 40000,
                    explanation = "En cada fila avanza $step posiciones por columna en el ciclo.",
                )
            }
            "calc" -> {
                val mode = Games.calcModes[r.nextInt(Games.calcModes.size)].second
                val sub = mode[r.nextInt(mode.size)]
                make(sub, l, r.nextLong()).copy(source = sub)
            }
            Suitcase.ID -> {
                val t = Suitcase.make(l, r.nextLong())
                Challenge(
                    Kind.SUITCASE,
                    "Me voy de instalación y en mi maleta llevo…",
                    deadlineMs = 600000,
                    suitcase = t,
                    explanation = Suitcase.explanation(t),
                )
            }
            Vigil.ID -> {
                val t = Vigil.make(l, r.nextLong())
                Challenge(
                    Kind.VIGIL,
                    "Guardia: ${t.guard} · marca solo las alertas reales",
                    deadlineMs = 600000,
                    vigil = t,
                    explanation = Vigil.explanation(t),
                )
            }
            Ddos.ID -> {
                val t =
                    Ddos.make(l, r.nextLong(), if (unit < 0) Ddos.SEAL_WAVE else unit % Ddos.WAVES)
                Challenge(
                    Kind.DDOS,
                    "Oleada · " + t.rules.sorted().joinToString(" · ") { Ddos.ruleShort[it] },
                    deadlineMs = 600000,
                    ddos = t,
                    explanation = Ddos.explanation(t),
                )
            }
            Route.ID -> {
                val t = Route.make(l, r.nextLong())
                Challenge(
                    Kind.ROUTE,
                    Route.prompt(t),
                    deadlineMs = 600000,
                    route = t,
                    explanation = Route.explanation(t),
                )
            }
            IncidentPairs.ID -> {
                val t = IncidentPairs.make(l, r.nextLong())
                Challenge(
                    Kind.PAIRS,
                    "Empareja las cartas de incidente",
                    deadlineMs = 600000,
                    pairs = t,
                    explanation = IncidentPairs.explanation(t),
                )
            }
            in NetSec.ids -> NetSec.make(id, l, r)
            else -> error("Juego desconocido: $id")
        }
    }
}

/**
 * Paso 2 (docs/AUDITORIA-JUEGOS-V3.md): the shape of a partida, per game. [units] is how many units
 * (rondas, oleadas…) it has, [chained] plays them one after another without the "Solución paso a
 * paso" panel in between (the game shows its own summary) and [packets] makes [Adaptive] count what
 * happened inside the units (packets) instead of units passed.
 */
data class GameFormat(
    val units: Int = 12,
    val unit: String = "ronda",
    val plural: String = "rondas",
    val chained: Boolean = false,
    val packets: Boolean = false,
    val about: String = "12 rondas con tiempo. Prioriza acertar.",
) {
    val label
        get() = "$units $plural"
}

object Formats {
    val DEFAULT = GameFormat()
    private val ddos =
        GameFormat(
            Ddos.WAVES,
            "oleada",
            "oleadas",
            chained = true,
            packets = true,
            about =
                "${Ddos.WAVES} oleadas seguidas, unos 3 minutos. Subes de nivel con el 85 % de los paquetes bien en dos partidas seguidas.",
        )
    private val vigil =
        GameFormat(
            Vigil.TRAMOS,
            "tramo",
            "tramos",
            chained = true,
            packets = true,
            about =
                "${Vigil.TRAMOS} tramos seguidos, unos 2 minutos y medio. Subes de nivel detectando el 85 % de los ataques en dos partidas seguidas.",
        )
    /**
     * Órbitas 2: the board shows its own verdict and the single segmented bar, so rounds follow one
     * another without the panel.
     */
    private val orbit =
        GameFormat(
            Orbit.ROUNDS,
            "ronda",
            "rondas",
            chained = true,
            about =
                "12 rondas en tres fases: calibración, polaridad y rotación. Sin números: recuerda lugares.",
        )

    fun of(game: String) =
        when (game) {
            Ddos.ID -> ddos
            Vigil.ID -> vigil
            Orbit.ID -> orbit
            else -> DEFAULT
        }

    /**
     * How many trials a saved result of [game] may hold: its format, or the 12 rounds every game
     * had before.
     */
    fun valid(game: String, trials: Int) = trials == 12 || trials == of(game).units

    /**
     * What [Adaptive] counts for a finished partida: (correct, total). Games with
     * [GameFormat.packets] add up their unit logs ([details]); every other game, and results saved
     * before the format existed, count units.
     */
    fun measure(game: String, correct: Int, total: Int, details: List<String>): Pair<Int, Int> {
        if (of(game).packets && game == Ddos.ID)
            Ddos.run(details)?.let {
                return it.correct to it.total
            }
        // Órbita expansiva is survival: it ends at the first miss, so it never moves the level.
        if (game == Orbit.ID && Orbit.modeOf(details) == OrbitMode.EXPAND) return 0 to 0
        // Vigía: attacks detected over the partida, on 100, minus 5 points per false alarm.
        if (game == Vigil.ID && details.size == Vigil.TRAMOS) {
            val logs = details.mapNotNull { VigilLog.decode(it) }
            val targets = logs.sumOf { it.targets }
            if (logs.size == details.size && targets > 0)
                return (logs.sumOf { it.hits } * 100 / targets - 5 * logs.sumOf { it.falses })
                    .coerceAtLeast(0) to 100
        }
        return correct to total
    }
}

data class Progress(val level: Int = 1, val up: Int = 0, val down: Int = 0, val blocks: Int = 0)

object Adaptive {
    fun next(
        p: Progress,
        game: String,
        correct: Int,
        total: Int,
        medianMs: Long?,
        falseStarts: Int = 0,
    ): Progress {
        require(total >= 0 && correct in 0..total && falseStarts in 0..total)
        if (total < 12) return p
        val a = correct.toDouble() / total
        val up =
            if (game == "reaction")
                a >= .9 &&
                    falseStarts.toDouble() / total <= .05 &&
                    medianMs != null &&
                    medianMs <= 700 - p.level * 25
            else
                a >= .85 &&
                    (game !in listOf("search", "flanker", "choice", "pattern", "compass") ||
                        (medianMs != null && medianMs <= speedBudget(game, p.level)))
        val down = a < .6 || (game == "reaction" && falseStarts.toDouble() / total > .2)
        val u = if (up) p.up + 1 else 0
        val d = if (down) p.down + 1 else 0
        return when {
            u >= 2 -> Progress((p.level + 1).coerceAtMost(10), blocks = p.blocks + 1)
            d >= 2 -> Progress((p.level - 1).coerceAtLeast(1), blocks = p.blocks + 1)
            else -> p.copy(up = u, down = d, blocks = p.blocks + 1)
        }
    }

    fun speedBudget(game: String, l: Int): Long =
        when (game) {
            "search" -> 9000L - l * 250
            "pattern" -> 10000L - l * 300
            "flanker" -> 4000L - l * 200
            "compass" -> 10000L - l * 400
            else -> 4500L - l * 150
        }

    fun xp(correct: Int, total: Int) =
        if (total < 5) 0 else 10 + (10.0 * correct / total).roundToInt()
}

fun median(values: List<Long>): Long? {
    if (values.isEmpty()) return null
    val s = values.sorted()
    return if (s.size % 2 == 1) s[s.size / 2] else (s[s.size / 2 - 1] + s[s.size / 2]) / 2
}
