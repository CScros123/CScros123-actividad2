package personal.qqq.braintraining.core

import kotlin.random.Random

/**
 * One line of an ordered rule list (Puerto Seguro, levels 8–10): allow or deny the ports
 * [lo]..[hi].
 */
data class FwRule(val allow: Boolean, val lo: Int, val hi: Int) {
    fun matches(port: Int) = port in lo..hi

    val any
        get() = lo <= 0 && hi >= 65535

    val target
        get() = if (any) "todo" else if (lo == hi) ":$lo" else ":$lo–$hi"

    val label
        get() = "${if(allow)"PERMITIR" else "DENEGAR"} $target"
}

/**
 * First-match firewall: rules are read top to bottom, the first one that matches decides; no match
 * means deny.
 */
object FirstMatch {
    /** Index of the rule that decides [port], or −1 when none matches (implicit deny). */
    fun index(rules: List<FwRule>, port: Int) = rules.indexOfFirst { it.matches(port) }

    fun allows(rules: List<FwRule>, port: Int) =
        rules.firstOrNull { it.matches(port) }?.allow ?: false

    /**
     * One-line verdict for [port], shown after answering ("regla 2 (DENEGAR :20–25): denegado").
     */
    fun decided(rules: List<FwRule>, port: Int): String {
        val i = FirstMatch.index(rules, port)
        return if (i < 0) "sin regla: denegado"
        else "regla ${i+1} (${rules[i].label}): ${if(rules[i].allow)"pasa" else "denegado"}"
    }
}

/**
 * Paso 1 (docs/AUDITORIA-JUEGOS-V3.md): Puerto Seguro asks with scenarios. The prompt describes a
 * symptom or a need and never names the service or the port; the panel shows the port (and the
 * service only at levels 1–2), so the player has to know what listens where. Levels 8–10 add ORDER:
 * an ordered allow/deny list whose real effect has to be worked out.
 */
internal object PortScenarios {
    const val CLOSE = 0
    const val OPEN = 1
    const val ROLE = 2
    /** Hints teach how to reason, never which row to touch. */
    val methods =
        listOf(
            "Método: casi todo servicio antiguo que viaja en claro tiene un gemelo cifrado en otro puerto. Decide cuál de los dos describe el caso.",
            "Método: enumera las funciones del equipo, una a una. Cada función es un puerto; todo lo que no esté en tu lista se cierra.",
            "Método: las bases de datos escuchan en puertos altos de cuatro cifras, cada fabricante el suyo. Los servicios clásicos viven por debajo de 1024.",
            "Método: el correo usa tres puertos: uno para entregar entre servidores, otro para descargar y otro para sincronizar el buzón. ¿Cuál describe el caso?",
            "Método: separa consola de texto (puertos bajos y vecinos) de escritorio gráfico (puertos altos de cuatro cifras).",
            "Método: la web clásica tiene pareja, sin cifrar y cifrada, y a veces un puerto extra de pruebas. Lee si el caso habla de candado o de texto en claro.",
            "Método: descarta primero web, correo y consolas. Lo que queda son servicios de infraestructura: nombres, monitorización, directorio, carpetas.",
            "Método: toma un puerto y baja por la lista. Párate en la primera regla que lo nombra o lo incluye en su rango: esa decide. Si llegas al final sin coincidencia, está denegado.",
        )
    const val M_CLEAR = 0
    const val M_ROLE = 1
    const val M_DB = 2
    const val M_MAIL = 3
    const val M_REMOTE = 4
    const val M_WEB = 5
    const val M_INFRA = 6
    const val M_ORDER = 7

    class Scenario(
        val id: String,
        val kind: Int,
        val ports: List<Int>,
        val text: String,
        val why: String,
        val method: Int,
        val avoid: Set<Int> = emptySet(),
        val traps: List<Int> = emptyList(),
    )

    private fun close(
        id: String,
        ports: List<Int>,
        text: String,
        why: String,
        method: Int,
        avoid: Set<Int> = emptySet(),
        traps: List<Int> = emptyList(),
    ) = Scenario(id, CLOSE, ports, text, why, method, avoid, traps)

    private fun open(
        id: String,
        ports: List<Int>,
        text: String,
        why: String,
        method: Int,
        avoid: Set<Int> = emptySet(),
        traps: List<Int> = emptyList(),
    ) = Scenario(id, OPEN, ports, text, why, method, avoid, traps)

    private fun role(
        id: String,
        ports: List<Int>,
        text: String,
        why: String,
        avoid: Set<Int> = emptySet(),
        traps: List<Int> = emptyList(),
    ) = Scenario(id, ROLE, ports, text, why, M_ROLE, avoid, traps)

    /**
     * [avoid] keeps ports that would make the answer debatable out of the panel; [traps] are the
     * tempting neighbours shown first.
     */
    val bank =
        listOf(
            // --- Threats: close what allows them ---
            close(
                "c-consola",
                listOf(23),
                "El auditor capturó la contraseña del administrador mientras tecleaba comandos en la consola remota del router.",
                "Telnet (23) envía todo en claro, también la contraseña. SSH (22) hace lo mismo, pero cifrado.",
                M_CLEAR,
                traps = listOf(22),
            ),
            close(
                "c-camaras",
                listOf(23),
                "Las cámaras IP baratas traen una consola remota antigua con usuario de fábrica, y una botnet ya las está probando.",
                "Las botnets tipo Mirai entran por Telnet (23) con contraseñas de fábrica.",
                M_CLEAR,
                traps = listOf(22, 80),
            ),
            close(
                "c-becario",
                listOf(21),
                "Un becario sube las páginas con un programa de transferencia de archivos que envía usuario y contraseña en claro.",
                "FTP (21) transfiere archivos sin cifrar; lo sustituye SFTP, que va dentro de SSH (22).",
                M_CLEAR,
                setOf(445),
                listOf(22, 80),
            ),
            close(
                "c-escritorio",
                listOf(3389),
                "Llegan miles de intentos de inicio de sesión desde Internet contra el escritorio remoto del servidor Windows de contabilidad.",
                "RDP (3389) es el escritorio remoto de Windows: expuesto, atrae fuerza bruta y ransomware.",
                M_REMOTE,
                setOf(5900, 445),
                listOf(22, 23),
            ),
            close(
                "c-intranet",
                listOf(80),
                "El formulario de acceso de la intranet viaja sin candado: cualquiera en la wifi puede leer las contraseñas.",
                "HTTP (80) es la web en claro; la misma web con candado va por HTTPS (443).",
                M_WEB,
                setOf(8080),
                listOf(443),
            ),
            close(
                "c-rele",
                listOf(25),
                "Tu servidor aparece en listas negras: desconocidos lo usan como relé para enviar spam.",
                "SMTP (25) entrega correo entre servidores; abierto a cualquiera se convierte en relé de spam.",
                M_MAIL,
                traps = listOf(110, 143),
            ),
            close(
                "c-amplifica",
                listOf(53),
                "Alguien usa tu resolvedor de nombres, abierto a Internet, para amplificar un ataque contra un tercero.",
                "DNS (53) resuelve nombres; abierto al mundo sirve para ataques de amplificación.",
                M_INFRA,
                traps = listOf(161),
            ),
            close(
                "c-solovpn",
                listOf(22),
                "Política nueva: nadie administra los servidores Linux por consola cifrada desde Internet; solo a través de la VPN.",
                "La consola cifrada es SSH (22): se cierra hacia Internet y se llega por la VPN.",
                M_REMOTE,
                setOf(23),
                listOf(3389, 443),
            ),
            close(
                "c-wannacry",
                listOf(445),
                "Un ransomware del estilo de WannaCry salta de equipo en equipo por las carpetas compartidas de Windows.",
                "SMB (445) comparte carpetas en Windows; por ahí se propagó WannaCry.",
                M_INFRA,
                traps = listOf(3389, 21),
            ),
            close(
                "c-wordpress",
                listOf(3306),
                "Un escáner de Internet encontró respondiendo directamente la base de datos del WordPress de la empresa.",
                "WordPress guarda sus datos en MySQL (3306), que solo debe oír a la red interna.",
                M_DB,
                traps = listOf(5432, 1433, 80),
            ),
            close(
                "c-public",
                listOf(161),
                "La monitorización lee el estado de los switches con la comunidad 'public': cualquiera puede leer su configuración.",
                "SNMP (161) usa la 'comunidad' como contraseña en claro; 'public' es la de fábrica.",
                M_INFRA,
                traps = listOf(53, 389),
            ),
            close(
                "c-descarga",
                listOf(110),
                "Un cliente de correo antiguo descarga los mensajes al PC y los borra del servidor, con la contraseña en claro.",
                "POP3 (110) descarga y borra; IMAP (143) sincroniza y SMTP (25) entrega.",
                M_MAIL,
                traps = listOf(143, 25),
            ),
            close(
                "c-pantallas",
                listOf(5900),
                "Soporte controla la pantalla de varios Mac y Linux con una herramienta sin cifrar, expuesta a Internet.",
                "VNC (5900) es el control de pantalla multiplataforma; RDP (3389) es el de Windows.",
                M_REMOTE,
                traps = listOf(3389, 22),
            ),
            close(
                "c-panel",
                listOf(8080),
                "Un desarrollador dejó un panel de pruebas escuchando en el puerto secundario de la web y nadie lo recuerda.",
                "El 8080 es el puerto web secundario: paneles de prueba y proxies olvidados.",
                M_WEB,
                traps = listOf(80, 443),
            ),
            close(
                "c-directorio",
                listOf(389),
                "Las consultas al directorio de usuarios y grupos de la empresa viajan en claro desde Internet.",
                "LDAP (389) consulta el directorio de usuarios; su versión cifrada es LDAPS (636).",
                M_INFRA,
                traps = listOf(445, 161),
            ),
            close(
                "c-sa",
                listOf(1433),
                "El servidor de bases de datos de Microsoft recibe fuerza bruta contra su cuenta 'sa'.",
                "SQL Server escucha en el 1433; 'sa' es su cuenta de administrador.",
                M_DB,
                traps = listOf(3306, 5432, 3389),
            ),
            close(
                "c-elefante",
                listOf(5432),
                "La base de datos del elefante azul, la que usa el equipo de datos, responde directamente desde Internet.",
                "El elefante es PostgreSQL (5432); como toda base de datos, solo para la red interna.",
                M_DB,
                traps = listOf(3306, 1433),
            ),
            close(
                "m-claro",
                listOf(21, 23),
                "Auditoría: fuera toda consola y toda transferencia de archivos que envíe la contraseña en claro.",
                "Telnet (23) y FTP (21) van en claro; SSH (22) cubre las dos funciones cifrando.",
                M_CLEAR,
                setOf(25, 80, 110, 143, 161, 389, 445, 8080, 5900),
                listOf(22),
            ),
            close(
                "m-bases",
                listOf(3306, 5432, 1433),
                "Ninguna base de datos puede responder directamente desde Internet.",
                "MySQL (3306), PostgreSQL (5432) y SQL Server (1433): las bases de datos, solo en la red interna.",
                M_DB,
                traps = listOf(3389, 8080),
            ),
            close(
                "m-remoto",
                listOf(3389, 5900),
                "Una campaña de ransomware entra por las herramientas de escritorio remoto gráfico: ciérralas todas.",
                "RDP (3389) y VNC (5900) son los escritorios gráficos remotos.",
                M_REMOTE,
                setOf(22, 23),
                listOf(3306, 445),
            ),
            close(
                "m-correo",
                listOf(25, 110, 143),
                "La empresa migró el correo a la nube: este servidor ya no debe ofrecer ningún servicio de correo.",
                "El correo son tres puertos: SMTP (25), POP3 (110) e IMAP (143).",
                M_MAIL,
                traps = listOf(53, 389),
            ),
            close(
                "m-websinclave",
                listOf(80, 8080),
                "Solo se permite web cifrada: fuera todo lo que sirva páginas sin candado.",
                "HTTP (80) y su puerto secundario (8080) van sin cifrar; HTTPS (443) se queda.",
                M_WEB,
                traps = listOf(443),
            ),
            close(
                "m-windows",
                listOf(445, 3389),
                "Ese servidor Windows ya no debe ofrecer hacia Internet ni carpetas compartidas ni escritorio remoto.",
                "SMB (445) son las carpetas compartidas y RDP (3389) el escritorio remoto de Windows.",
                M_INFRA,
                setOf(5900),
                listOf(21, 22),
            ),
            // --- Needs: open what makes them work ---
            open(
                "o-tienda",
                listOf(443),
                "Tienda online nueva: los clientes deben poder comprar viendo el candado en el navegador.",
                "El candado es HTTPS (443): web dentro de TLS.",
                M_WEB,
                setOf(80, 8080),
                listOf(22),
            ),
            open(
                "o-casa",
                listOf(22),
                "La administradora necesita entrar en la consola del servidor Linux desde casa, de forma cifrada.",
                "La consola cifrada es SSH (22); Telnet (23) haría lo mismo en claro.",
                M_CLEAR,
                traps = listOf(23, 3389),
            ),
            open(
                "o-nombres",
                listOf(53),
                "Los equipos de la oficina preguntan a este servidor qué dirección IP tiene cada nombre de dominio, y no obtienen respuesta.",
                "Traducir nombres a direcciones IP es DNS (53).",
                M_INFRA,
                traps = listOf(80),
            ),
            open(
                "o-rebotes",
                listOf(25),
                "Otros servidores de correo no consiguen entregarte mensajes: rebotan todos.",
                "La entrega entre servidores de correo es SMTP (25).",
                M_MAIL,
                traps = listOf(110, 143),
            ),
            open(
                "o-gestor",
                listOf(3389),
                "El gestor trabajará desde casa, por la VPN, viendo el escritorio de su PC Windows de la oficina.",
                "El escritorio remoto de Windows es RDP (3389).",
                M_REMOTE,
                setOf(5900),
                listOf(22, 23),
            ),
            open(
                "o-certificado",
                listOf(80),
                "Para renovar el certificado, la autoridad debe poder visitar una página de validación servida sin cifrar.",
                "La web sin cifrar es HTTP (80); la validación del certificado llega por ahí.",
                M_WEB,
                setOf(8080),
                listOf(443),
            ),
            open(
                "o-maquina",
                listOf(21),
                "Una máquina industrial antigua solo sabe subir sus informes con el protocolo clásico de transferencia de archivos, sin cifrar.",
                "La transferencia clásica de archivos es FTP (21).",
                M_CLEAR,
                setOf(445),
                listOf(22, 23),
            ),
            open(
                "o-conmutador",
                listOf(23),
                "Un conmutador muy antiguo solo se deja configurar por una consola de texto que viaja en claro; se acepta desde la red de gestión.",
                "La consola remota sin cifrar es Telnet (23).",
                M_CLEAR,
                traps = listOf(22),
            ),
            open(
                "o-buzon",
                listOf(143),
                "Los empleados quieren ver el mismo buzón, sincronizado, en el móvil y en el portátil.",
                "Sincronizar el buzón es IMAP (143); POP3 (110) solo descarga.",
                M_MAIL,
                traps = listOf(110, 25),
            ),
            open(
                "o-facturas",
                listOf(110),
                "Un programa de facturación antiguo descarga el correo a su disco y lo borra del servidor.",
                "Descargar y borrar es POP3 (110); IMAP (143) sincroniza.",
                M_MAIL,
                traps = listOf(143, 25),
            ),
            open(
                "o-carpeta",
                listOf(445),
                "La oficina necesita la carpeta compartida de Windows del servidor de ficheros.",
                "Las carpetas compartidas de Windows van por SMB (445).",
                M_INFRA,
                setOf(21),
                listOf(3389),
            ),
            open(
                "o-sondas",
                listOf(161),
                "El sistema de monitorización debe poder preguntar a los switches por su carga y su temperatura.",
                "Monitorizar equipos de red es SNMP (161).",
                M_INFRA,
                traps = listOf(53),
            ),
            open(
                "o-blog",
                listOf(3306),
                "El WordPress no arranca: no consigue llegar a su base de datos, que está en otro servidor.",
                "WordPress habla con MySQL (3306).",
                M_DB,
                traps = listOf(5432, 1433, 80),
            ),
            open(
                "o-fichajes",
                listOf(389),
                "La aplicación de fichajes debe validar a los empleados contra el directorio de usuarios de la empresa.",
                "El directorio de usuarios se consulta por LDAP (389).",
                M_INFRA,
                traps = listOf(445, 161),
            ),
            open(
                "o-informes",
                listOf(5432),
                "El servidor de informes necesita llegar a la base de datos del elefante azul del equipo de datos.",
                "El elefante es PostgreSQL (5432).",
                M_DB,
                traps = listOf(3306, 1433),
            ),
            open(
                "o-erp",
                listOf(1433),
                "El ERP no conecta con su servidor de bases de datos de Microsoft.",
                "SQL Server escucha en el 1433.",
                M_DB,
                traps = listOf(3306, 5432, 3389),
            ),
            open(
                "o-soporte",
                listOf(5900),
                "Soporte necesita ver la pantalla de un equipo Linux con la herramienta multiplataforma de siempre.",
                "El control de pantalla multiplataforma es VNC (5900).",
                M_REMOTE,
                traps = listOf(3389, 22),
            ),
            open(
                "o-proxy",
                listOf(8080),
                "El proxy de la oficina escucha en el puerto secundario de la web y los navegadores deben alcanzarlo.",
                "El puerto web secundario, típico de proxies, es el 8080.",
                M_WEB,
                traps = listOf(80, 443),
            ),
            // --- Roles: leave open exactly what the machine is for ---
            role(
                "r-web",
                listOf(80, 443),
                "Servidor web público: sirve páginas con candado y redirige a ellas desde la versión sin cifrar.",
                "Una web pública necesita HTTPS (443) y HTTP (80) solo para redirigir.",
                setOf(8080),
                listOf(22, 21),
            ),
            role(
                "r-nombres",
                listOf(53),
                "Este equipo solo resuelve nombres de dominio para la oficina.",
                "Resolver nombres es DNS (53): cada puerto extra es superficie de ataque.",
                traps = listOf(80, 25),
            ),
            role(
                "r-correo",
                listOf(25, 143),
                "Servidor de correo: recibe de otros servidores y los empleados sincronizan sus buzones.",
                "SMTP (25) recibe de otros servidores e IMAP (143) sincroniza los buzones.",
                setOf(110),
                listOf(53, 80),
            ),
            role(
                "r-linuxweb",
                listOf(22, 443),
                "Servidor Linux con una web con candado, administrado a distancia por consola cifrada.",
                "Consola cifrada, SSH (22), y web cifrada, HTTPS (443): nada más.",
                setOf(23, 80, 8080),
                listOf(21, 3389),
            ),
            role(
                "r-elefante",
                listOf(22, 5432),
                "Base de datos del elefante azul que los administradores gestionan por consola cifrada.",
                "SSH (22) para administrar y PostgreSQL (5432) para los datos.",
                setOf(23),
                listOf(3306, 1433),
            ),
            role(
                "r-ficheros",
                listOf(445, 3389),
                "Servidor Windows de ficheros de la oficina: carpetas compartidas y administración por escritorio remoto.",
                "SMB (445) comparte las carpetas y RDP (3389) da el escritorio remoto de Windows.",
                setOf(5900, 21),
                listOf(22, 80),
            ),
            role(
                "r-bastion",
                listOf(22),
                "Servidor de salto: lo único que ofrece es consola cifrada.",
                "Un bastión solo expone SSH (22).",
                traps = listOf(23, 3389),
            ),
            role(
                "r-dnsadmin",
                listOf(22, 53),
                "Resolvedor de nombres de la oficina que se administra por consola cifrada.",
                "DNS (53) para los nombres y SSH (22) para administrarlo.",
                setOf(23),
                listOf(80, 25),
            ),
            role(
                "r-switch",
                listOf(22, 161),
                "Switch gestionado: responde a la monitorización y se configura por consola cifrada.",
                "SNMP (161) para monitorizar y SSH (22) para configurar.",
                setOf(23),
                listOf(53, 80),
            ),
            role(
                "r-pasarela",
                listOf(25),
                "Pasarela de correo saliente: solo habla con otros servidores de correo; nadie lee buzones en ella.",
                "Entre servidores de correo se habla SMTP (25); los buzones (110, 143) no pintan nada aquí.",
                traps = listOf(110, 143),
            ),
            role(
                "r-heredado",
                listOf(443),
                "Servidor heredado: hoy solo ofrece una API con candado; lo demás quedó abierto de otra época.",
                "Una API cifrada es HTTPS (443); lo heredado se cierra.",
                setOf(80, 8080),
                listOf(21, 23),
            ),
        )
    private val closes = bank.filter { it.kind == CLOSE }
    private val opens = bank.filter { it.kind == OPEN }
    private val roles = bank.filter { it.kind == ROLE }

    fun deadline(l: Int, f: Int) =
        if (f == Pilot.ORDER) (50000 - l * 2000).toLong() else (36000 - l * 1500).toLong()

    fun make(l: Int, r: Random, f: Int, exclude: Set<String>): Challenge =
        if (f == Pilot.ORDER) order(l, r, exclude) else scenario(l, r, f, exclude)

    private fun scenario(l: Int, r: Random, f: Int, exclude: Set<String>): Challenge {
        val pool = NetSec.portList.take(if (l <= 3) 8 else if (l <= 6) 13 else NetSec.portList.size)
        val known = pool.map { it.port }.toSet()
        val size = if (l <= 3) 4 else if (l <= 6) 5 else 6
        val multi = l >= 7 && r.nextBoolean()
        fun List<Scenario>.fit() = filter { s -> s.ports.all { it in known } }
        val single = { s: Scenario -> s.ports.size == 1 }
        val prefer: List<Scenario>
        val alt: List<Scenario>
        when (f) {
            Pilot.AUDIT -> {
                prefer = roles.fit()
                alt = opens.fit()
            }
            Pilot.ALLOW ->
                if (multi) {
                    prefer = roles.fit().filterNot(single)
                    alt = opens.fit()
                } else {
                    prefer = opens.fit()
                    alt = prefer
                }
            else ->
                if (multi) {
                    prefer = closes.fit().filterNot(single)
                    alt = closes.fit().filter(single)
                } else {
                    prefer = closes.fit().filter(single)
                    alt = prefer
                }
        }
        val options =
            prefer
                .filter { it.id !in exclude }
                .ifEmpty { alt.filter { it.id !in exclude } }
                .ifEmpty { prefer }
        val s = options[r.nextInt(options.size)]
        // A threat that spans several ports shows only some of them (at least two): every one shown
        // has to be closed.
        val shown =
            if (s.kind == CLOSE && s.ports.size > 2)
                s.ports.shuffled(r).take(r.nextInt(2, s.ports.size + 1))
            else s.ports
        val banned = s.avoid + s.ports
        val traps = s.traps.filter { it in known && it !in banned }.shuffled(r)
        val rest = pool.map { it.port }.filter { it !in banned && it !in traps }.shuffled(r)
        val ports = (shown + (traps + rest).take((size - shown.size).coerceAtLeast(0))).sorted()
        val rows = ports.map { n -> NetSec.portList.first { it.port == n } }
        val expected =
            ports.map {
                val target = it in shown
                if (f == Pilot.BLOCK) !target else target
            }
        val start =
            when (f) {
                Pilot.BLOCK -> List(rows.size) { true }
                Pilot.ALLOW -> List(rows.size) { false }
                else -> {
                    val m = MutableList(rows.size) { r.nextBoolean() }
                    if (m == expected) {
                        val i = r.nextInt(rows.size)
                        m[i] = !m[i]
                    }
                    m.toList()
                }
            }
        val many = shown.size > 1
        val ask =
            when (f) {
                Pilot.BLOCK ->
                    if (many) "Cierra todos los puertos implicados."
                    else "Cierra el puerto que lo permite."
                Pilot.ALLOW ->
                    if (many) "Todo está cerrado: abre solo lo necesario."
                    else "Todo está cerrado: abre solo el puerto necesario."
                else -> "Deja abierto solo lo necesario y cierra el resto."
            }
        val lesson = NetSec.portList.first { it.port == shown.min() }.lesson
        return Challenge(
            Kind.FIREWALL,
            "${s.text}\n$ask",
            deadlineMs = deadline(l, f),
            firewall =
                FirewallTask(
                    f,
                    rows.map { FirewallRow(it.port, it.service, it.use) },
                    start,
                    expected,
                    labels = l <= 2,
                    key = s.id,
                    why = s.why,
                    hint = methods[s.method],
                ),
            explanation = "${s.why}\n$lesson",
        )
    }

    // ---------- Orden de reglas (niveles 8–10) ----------
    private fun rules(ports: List<Int>, n: Int, l: Int, r: Random): List<FwRule> {
        val out = mutableListOf<FwRule>()
        for (k in 0 until n) {
            if (l >= 10 && k == n - 1 && r.nextInt(10) < 3) {
                out += FwRule(true, 0, 65535)
                break
            }
            val allow = r.nextBoolean()
            out +=
                if (r.nextInt(100) < (if (l <= 8) 35 else 50)) {
                    val i = r.nextInt(ports.size - 1)
                    val j = i + 1 + r.nextInt(minOf(3, ports.size - 1 - i))
                    FwRule(allow, ports[i], ports[j])
                } else ports[r.nextInt(ports.size)].let { FwRule(allow, it, it) }
        }
        return out
    }

    /**
     * Ports an ALLOW names but that end up denied: the trap of reading the list without its order.
     */
    fun shadowed(rules: List<FwRule>, ports: List<Int>) =
        ports.filter { p ->
            rules.any { it.allow && it.matches(p) } && !FirstMatch.allows(rules, p)
        }

    /** Ports that pass although a later DENY names them. */
    fun excepted(rules: List<FwRule>, ports: List<Int>) =
        ports.filter { p ->
            val i = FirstMatch.index(rules, p)
            i >= 0 && rules[i].allow && rules.drop(i + 1).any { !it.allow && it.matches(p) }
        }

    fun valid(rules: List<FwRule>, ports: List<Int>, l: Int): Boolean {
        val n = if (l <= 8) 3 else 4
        if (rules.size !in 3..n || rules.distinct().size != rules.size) return false
        if (
            (1 until rules.size).any {
                rules[it].lo == rules[it - 1].lo && rules[it].hi == rules[it - 1].hi
            }
        )
            return false
        // Every rule is there for a reason: it decides some port, or it is an ALLOW that arrives
        // too late.
        val deciding = ports.map { FirstMatch.index(rules, it) }.toSet()
        if (
            rules.indices.any { i ->
                i !in deciding && !(rules[i].allow && ports.any { rules[i].matches(it) })
            }
        )
            return false
        if (rules.indices.count { it !in deciding } > 1) return false
        val open = ports.count { FirstMatch.allows(rules, it) }
        if (open !in 1 until ports.size) return false
        if (shadowed(rules, ports).isEmpty()) return false
        if (l >= 9 && excepted(rules, ports).isEmpty()) return false
        if (l <= 8 && ports.none { FirstMatch.index(rules, it) < 0 }) return false
        return true
    }

    private fun signature(rules: List<FwRule>, ports: List<Int>) =
        "orden:" +
            rules.joinToString(",") { "${if(it.allow)'+' else '-'}${it.lo}-${it.hi}" } +
            "|" +
            ports.joinToString(",")

    private fun order(l: Int, r: Random, exclude: Set<String>): Challenge {
        val all = NetSec.portList.map { it.port }
        var ports = all.shuffled(r).take(6).sorted()
        var list = emptyList<FwRule>()
        var found = false
        for (attempt in 0 until 600) {
            ports = all.shuffled(r).take(6).sorted()
            list =
                rules(
                    ports,
                    when (l) {
                        8 -> 3
                        9 -> 3 + r.nextInt(2)
                        else -> 4
                    },
                    l,
                    r,
                )
            if (valid(list, ports, l) && signature(list, ports) !in exclude) {
                found = true
                break
            }
        }
        // Constructed fallback (never needed in the tested seeds): an exception, a block, a late
        // ALLOW and a plain ALLOW.
        if (!found)
            list =
                listOf(
                        FwRule(true, ports[1], ports[1]),
                        FwRule(false, ports[1], ports[3]),
                        FwRule(true, ports[2], ports[2]),
                        FwRule(true, ports[4], ports[4]),
                    )
                    .let { if (l <= 8) it.drop(1) else it }
        val rows = ports.map { n -> NetSec.portList.first { it.port == n } }
        val expected = ports.map { FirstMatch.allows(list, it) }
        // Level 8 starts from the classic mistake (every port some ALLOW names is on); 9–10 start
        // from an empty panel.
        val start =
            if (l <= 8) ports.map { p -> list.any { it.allow && it.matches(p) } }
            else List(ports.size) { false }
        val prompt =
            if (l <= 8)
                "Un compañero dejó el panel leyendo solo las reglas PERMITIR, sin mirar el orden.\nEl firewall aplica la primera regla que coincide y deniega si ninguna coincide: corrige el panel."
            else
                "El firewall lee la lista de arriba abajo y aplica la primera regla que coincide; si ninguna coincide, deniega.\nEnciende solo los puertos que deja pasar de verdad."
        val trap = shadowed(list, ports).first()
        val by = FirstMatch.index(list, trap)
        val late = list.indexOfFirst { it.allow && it.matches(trap) }
        val why =
            ":$trap queda cerrado: la regla ${by+1} (${list[by].label}) coincide antes que la regla ${late+1} (${list[late].label})."
        val steps = ports.joinToString("\n") { p -> ":$p → ${FirstMatch.decided(list,p)}" }
        return Challenge(
            Kind.FIREWALL,
            prompt,
            deadlineMs = deadline(l, Pilot.ORDER),
            firewall =
                FirewallTask(
                    Pilot.ORDER,
                    rows.map { FirewallRow(it.port, it.service, it.use) },
                    start,
                    expected,
                    labels = false,
                    key = signature(list, ports),
                    why = why,
                    hint = methods[M_ORDER],
                    rules = list,
                ),
            explanation = "$why\n$steps",
        )
    }
}
