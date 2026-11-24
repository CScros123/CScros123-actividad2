# Fase 7 — Lote piloto de juegos: Subredes, Phishing y Puertos

Plan: `docs/PLAN-FOCO-LAB.md`, sección 5C y Fase 7. Rama `fase-7`.

> **Importante:** igual que la Fase 1, esto se preparó **sin compilar ni ejecutar Gradle** (a pedido del usuario). Todo se revisó leyendo el código. La sección "Riesgos de compilación" lista dónde podría fallar, con archivo y línea. Solo se tocaron `:lab`, `:lab-core` y documentación de Lab; `:app` y `:foco-core` no cambian.

## Qué cambia para el usuario

En la sesión normal de 12 rondas (y en "Sin tiempo") los tres juegos dejan la pantalla "pregunta + opciones" y pasan a tener mecánica propia. El modo **Relámpago** sigue usando las versiones de 4 opciones (`Generator.make`), que se conservan sin cambios: son rápidas de responder y encajan en 60 segundos.

### Subredes — cuadrícula de octetos con máscara arrastrable
- 32 casillas (4 filas = 4 octetos) con los bits reales de la dirección. A la izquierda de la frontera, bits de red (ámbar); a la derecha, bits de host.
- Se mueve la frontera **arrastrando** sobre la cuadrícula (la barra con "pomo" sigue al dedo), **tocando** una casilla (la frontera pasa justo después de ella) o con los botones **− / +** (alternativa accesible). Cada paso suena y vibra (`FeedbackCue.TAP`).
- Lecturas en vivo: `/prefijo`, máscara, red, broadcast y hosts usables.
- Tres tipos de pregunta (se alternan, nunca el mismo dos rondas seguidas):
  1. **Hosts** — "Una red necesita 50 equipos. Ajusta la máscara de la subred más pequeña donde quepan." Solo vale el prefijo más ajustado (/26).
  2. **Máscara → prefijo** — "La máscara es 255.255.255.192. Arrastra la frontera hasta su prefijo." (la máscara en vivo se oculta hasta responder).
  3. **Misma red** (nivel 4+) — dos direcciones A y B, bits de ambas en cada casilla: "¿Cuál es el prefijo más largo con el que A y B siguen en la misma red?". Enseña a comparar bits (base de la sumarización).
- Escala con el nivel: niveles 1–3 solo en el último octeto (/24–/30) y sin "misma red"; 4–6 hasta /16–/22; 7–10 hasta /8 y /16. Hasta el nivel 6 hay ayudas en vivo (hosts usables en "Hosts", "¿misma red?" y bits distintos marcados en "Misma red"); desde el 7 se ocultan hasta responder.
- Al responder: barra verde con el prefijo correcto si fallaste, y explicación de una línea ("/26: 2^6 − 2 = 62 hosts ≥ 50; un /27 solo admite 30.").

### Phishing — bandeja con correos que se deslizan
- Cada ronda llega una tarjeta a la bandeja (entra deslizándose desde la derecha, con dos tarjetas detrás). **Deslizar a la derecha = Seguro, a la izquierda = Trampa**. Al pasar el umbral aparece el sello "SEGURO"/"TRAMPA" y hay un clic háptico.
- Alternativas: botones "← Trampa" y "Seguro →", y acciones de accesibilidad "Marcar como seguro" / "Marcar como trampa" (TalkBack, menú de acciones).
- La tarjeta se dibuja según el canal: correo (avatar, remitente, asunto, adjunto) o enlace (barra de dirección con candado o "sin cifrar"; los SMS como burbuja).
- Al responder se **resalta la pista exacta** dentro del texto (p. ej. `paypa1.com`, `.pdf.exe`, `@inicio-seguro.ru`), subrayada si fallaste, y una línea "Era una trampa · pista: …". Para los legítimos se resalta el dominio real que lo hace seguro.
- Banco: los 35 escenarios de siempre, ahora con el campo `mark` (texto exacto de la pista). Formatos para la regla de variedad: correo / enlace (se alternan).

### Puertos — panel de firewall con interruptores
- Filas por puerto (`:3389 RDP · escritorio remoto de Windows`) con un interruptor; toda la fila es un `toggleable` con rol `Switch` (TalkBack lo anuncia como interruptor). Las filas abiertas muestran paquetes que circulan (solo con movimiento activado).
- "Aplicar reglas" envía el estado. Tres tipos de tarea:
  1. **Bloquear** (todo abierto): "Cierra el puerto usado para escritorio remoto de Windows". Nivel 7+: varias a la vez ("Cierra todas las bases de datos expuestas").
  2. **Permitir** (todo cerrado): "Servidor nuevo, todo cerrado. Abre solo el puerto para resolver nombres de dominio". Nivel 7+: por función ("Servidor web público: abre solo lo necesario").
  3. **Auditar** (nivel 4+, estado inicial aleatorio): "Servidor de correo. Deja abierto solo lo necesario (SMTP e IMAP) y cierra el resto."
- Escala: 4 / 5 / 6 puertos en el panel (niveles 1–3 / 4–6 / 7–10), banco de 8 / 13 / 18 puertos, el nombre del servicio se da hasta el nivel 3 y la pista de la función hasta el 8.
- Al responder: filas mal puestas con borde rojo y "Debía quedar abierto/cerrado"; filas corregidas con borde verde.
- "Arrastrar servicios a puertos" (segunda mecánica del plan) **no** se hizo en este lote: con los interruptores ya hay tres tipos de tarea limpios y el arrastre entre listas en Compose añade bastante código sin probar en el teléfono. Queda propuesto para el lote siguiente si el piloto se aprueba.

## Integración con el flujo de 12 rondas

- `Kind` gana `SUBNET`, `INBOX` y `FIREWALL`; `Challenge` gana campos opcionales (`format`, `item`, `review`, `subnet`, `inbox`, `firewall`) con valores por defecto: ningún generador existente cambia.
- `Run.challenge` usa ahora `Rounds.make(...)`: para los otros 24 juegos devuelve exactamente `Generator.make(...)` (probado).
- `Player` despacha a `SubnetBoard`, `InboxBoard` y `FirewallBoard`, que llaman a la nueva `BrainViewModel.submit(picked)`. `submit` comprueba fase y tiempo como `choose`, corrige con `Challenge.accepts(picked)` (lógica pura en `:lab-core`) y pasa por el mismo `score(...)`: guardado del resultado, nivel adaptativo, XP, sonidos de acierto/error, "Esto repasa hoy" y tiempo agotado funcionan igual que antes.
- `Outcome` guarda además `item` y `format` (solo en estos tres juegos). Son claves extra en el JSON de rondas; la validación de copias de seguridad de `:lab-data` las ignora, así que no se tocó `:lab-data`.

## Regla de variedad y repaso espaciado (implementados)

En `Rounds` (`lab-core/.../core/Pilot.kt`), función pura y determinista:
- **Variedad:** el formato elegido nunca repite el de la ronda anterior (`avoid`). Con dos formatos se alternan; con tres, nunca dos iguales seguidos.
- **Repaso:** un ítem fallado (incluido tiempo agotado) vuelve, reconstruido desde su semilla, cuando han pasado al menos 2 rondas (`GAP`) y su formato no repite el anterior. Vuelve como mucho 2 veces (`MAX_REPEATS`) y solo mientras su último intento siga fallado. La ronda muestra "· REPASO" junto al número.
- Reproducibilidad: `Pilot.make` siempre consume la misma tirada para el formato, así que forzar el formato original reconstruye el mismo reto (probado).
- `Run.history` se fija al empezar cada ronda, así el reto no cambia durante la pantalla de resultado. En "Sin tiempo" (infinito) el historial se conserva entre bloques de 12 (últimas 12 rondas).
- Limitación: una ronda de repaso cuenta como una ronda más de las 12 (no se añade una ronda 13). Al pausar, la ronda interrumpida se vuelve a generar como siempre; si era un repaso, vuelve el mismo repaso.

## Archivos tocados

| Archivo | Cambio |
|---|---|
| `lab-core/src/main/kotlin/personal/qqq/braintraining/core/Pilot.kt` | **Nuevo.** `Ipv4`, `SubnetTask`, `InboxTask`, `FirewallTask`, `RoundMark`, `Challenge.accepts`, `Pilot` (generadores) y `Rounds` (variedad + repaso). |
| `lab-core/src/main/kotlin/personal/qqq/braintraining/core/Games.kt` | `Kind` + 3 valores; `Challenge` + 6 campos opcionales. |
| `lab-core/src/main/kotlin/personal/qqq/braintraining/core/NetSec.kt` | `Bait` con campo `mark` (35 pistas exactas); `Bait`/`baits`/`Port`/`portList` pasan de `private` a `internal`; instrucciones de los 3 juegos. Los generadores de 4 opciones no cambian. |
| `lab-core/src/test/kotlin/personal/qqq/braintraining/core/PilotTest.kt` | **Nuevo.** 9 pruebas. |
| `lab/src/main/java/personal/qqq/braintraining/PilotViews.kt` | **Nuevo.** `SubnetBoard`, `InboxBoard`, `FirewallBoard`. |
| `lab/src/main/java/personal/qqq/braintraining/BrainViewModel.kt` | `Outcome.item/format/mark()`, `Run.history`, `Run.challenge` vía `Rounds`, `submit()`, historial en `next()` y al recuperar borrador, "Esto repasa hoy" sin duplicados. |
| `lab/src/main/java/personal/qqq/braintraining/Player.kt` | Despacho a los 3 tableros y etiqueta "· Repaso". |
| `docs/FASE-7-PILOTO.md` | Este documento. |

## Pruebas nuevas (`:lab-core`, `PilotTest`, 9 `@Test`)

1. `ipv4Math` — máscara, red, broadcast, hosts (/24, /26, /30, /31, /32, /0), bits y prefijo común con valores conocidos (192.168.10.77/26 → .64/.127, 62 hosts).
2. `pilotChallengesAreDeterministicBySeed` — misma semilla ⇒ mismo reto (`Pilot` y `Rounds`), formato válido para el nivel, y formato forzado ⇒ reto idéntico.
3. `subnetAnswersAreValid` — por formato: el prefijo es el más ajustado para los hosts pedidos; la máscara del enunciado corresponde al prefijo; en "misma red" la respuesta es el prefijo común (misma red con /p, distinta con /p+1). Escalado por nivel y aparición de los 3 formatos.
4. `everyBaitHasItsClueInTheText` — las 35 pistas existen en su texto.
5. `phishingCardsHighlightTheClue` — rango de pista válido, respuesta 0/1 coherente, canal = formato en todos los niveles.
6. `firewallTasksAreSolvableAndScale` — puertos distintos y ordenados, tamaño por nivel, estado inicial ≠ solución, `accepts` acepta la solución y rechaza el estado inicial; hasta el nivel 6 bloquear/permitir cambian exactamente un interruptor.
7. `neverTheSameFormatTwiceInARow` — sesiones simuladas de 12 rondas (todo bien, todo mal, mixto) en los 3 juegos y 10 niveles.
8. `failedItemsComeBackLaterInTheSession` — un fallo en la ronda 1 vuelve una sola vez entre las rondas 4 y 7, idéntico y marcado como repaso; con todo mal, nadie aparece más de 3 veces; con todo bien, no hay repasos.
9. `otherGamesAndFlashModeAreUnchanged` — `Rounds.make` = `Generator.make` para los otros juegos; Relámpago sigue con `Kind.CHOICE`.

Ninguna prueba existente se borró ni se modificó. `NetSecTest` y `RulesTest` siguen probando `Generator.make` (sin cambios de comportamiento).

Total esperado de `@Test`: `:lab-core` 29 → 38; `:lab` sigue en 19.

Comando: `./gradlew :lab-core:test :lab:testDebugUnitTest :app:assembleDebug` (o el completo de la regla 5).

## Checklist por juego (qué verificar en el Samsung)

| Punto | Subredes | Phishing | Puertos |
|---|---|---|---|
| Mecánica distinta | Arrastrar frontera sobre 32 bits | Deslizar tarjetas | Interruptores de firewall |
| Feedback visible | Celdas ámbar/gris, barra y pomo, barra verde con la solución | Sellos al deslizar, borde rojo/verde, pista resaltada | Fila verde al abrir, paquetes, bordes rojo/verde al corregir |
| Feedback sonoro/háptico | Clic por cada bit que se mueve; acierto/error al comprobar | Clic al pasar el umbral; acierto/error | Clic por interruptor; acierto/error |
| Explicación de una línea | Primera línea de la explicación | "Trampa:/Seguro: …" + pista | "3389 → RDP: …" |
| 60 fps | Sin bucle; solo recompone al cambiar el prefijo | Arrastre en `graphicsLayer` (sin recomposición por frame) | Paquetes en fase de dibujo con una sola transición infinita |
| Claro y oscuro | Ámbar con tinta oscura en ambos; resto con colores del tema | Colores del tema (contenedores error/secundario) | Colores del tema |
| Aprende algo concreto | Prefijo ↔ hosts ↔ máscara; comparar bits | Leer el dominio real | Puerto ↔ servicio; mínimo privilegio |

**A comprobar a mano en el teléfono (nada de esto se ha visto en un dispositivo):**
- Subredes: el arrastre vertical sobre la cuadrícula mueve la frontera y **no** desplaza la pantalla (la pantalla de juego tiene scroll vertical); la frontera sigue al dedo con precisión en las 4 filas; tocar una casilla deja la frontera justo después; con muchos pasos rápidos el clic no satura (SoundPool de 3 canales).
- Phishing: umbral de 96 dp cómodo; la tarjeta vuelve al centro si no se pasa el umbral; con "reducir movimiento" no hay rotación ni entrada deslizada; TalkBack lee el mensaje y ofrece las dos acciones.
- Puertos: TalkBack anuncia cada fila como interruptor con su estado; los paquetes se mueven fluidos y se detienen al responder o sin movimiento.
- Las tres: legibilidad en claro, oscuro y AMOLED; que los tiempos límite (Subredes 35–57 s, Phishing 12–21 s, Puertos 17–30 s) sean razonables; que tras 12 rondas se guarde el resultado y el nivel suba/baje.
- Perfil de GPU en las opciones de desarrollador (barras por debajo de la línea de 16 ms) durante el arrastre y con los paquetes.

## Riesgos de compilación (revisar si falla)

1. `lab/src/main/java/personal/qqq/braintraining/PilotViews.kt:258` — `rememberInfiniteTransition(label=…)` llamado dentro de un `if` como expresión. Es válido en Compose; si el compilador protestara, sacarlo fuera del `if` y no leer su valor cuando `motion` es falso.
2. `PilotViews.kt:111` — función local `at()` dentro del lambda suspendido de `pointerInput` y `detectDragGestures(onDragStart=…){change,_->…}`. Firma de foundation 1.7 (BOM 2024.09.03). Si falla, mover `at` a una función privada que reciba `size`.
3. `PilotViews.kt:220` — `detectHorizontalDragGestures(onDragEnd=…,onDragCancel=…){change,dx->…}` con `when` como última expresión en `onDragEnd` (se espera `Unit`; las ramas devuelven `Unit`/`Job`). Si da error de tipo, añadir `Unit` al final del lambda.
4. `PilotViews.kt:225` — `customActions=listOf(CustomAccessibilityAction(…){…})` dentro de `semantics(mergeDescendants=true)`.
5. `PilotViews.kt:174,177,181` — iconos `Icons.Rounded.Attachment`, `LockOpen` y `Sms` (de `material-icons-extended`, ya es dependencia). `Icons.AutoMirrored.Rounded.ArrowForward` se importa explícitamente (línea 16).
6. `PilotViews.kt:257` — `t.start.toMutableStateList()` (`androidx.compose.runtime`). `PilotViews.kt:271` — `toggleable(value,enabled,role){…}` de `androidx.compose.foundation.selection`.
7. `lab/src/main/java/personal/qqq/braintraining/BrainViewModel.kt:24` — `.apply{if(item!=null){put("item",item)…}}`: depende del *smart cast* de `item` (propiedad `val` del mismo módulo) para elegir `JSONObject.put(String,long)`. Si fallara, usar `put("item",item as Long)`.
8. `lab-core/src/main/kotlin/personal/qqq/braintraining/core/Pilot.kt:25,29` — propiedades `val clue get()=…` y `val expectedOpen get()=…` sin tipo explícito en el cuerpo de una `data class` (sintaxis válida; si molesta, añadir `: String` / `: List<Int>`).
9. `Pilot.kt:140` — `val targets…;val avoid…` asignados en las ramas de un `when` (asignación diferida de `val`). Válido en Kotlin 1.9.
10. `Pilot.kt:97` — `Random.nextLong(until)` con `until = 1L shl k` (≥ 1). Válido.

## Sin probar en dispositivo

Todo lo visual y táctil de esta fase. Pendiente que el usuario apruebe el piloto (Fase 7, criterio "El usuario aprueba el piloto antes de continuar") antes de pasar al lote siguiente (Bits, Logs, SQL).
