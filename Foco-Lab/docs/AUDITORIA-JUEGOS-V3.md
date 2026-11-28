# Auditoría de juegos v3

Auditoría y propuesta. **No cambia código.** Base: `main`, versión 1.9 (`ec28183`). Prioridad del encargo: que se
**disfrute jugando**. Un cambio visual no es un cambio de calidad, cada juego conserva su identidad, nada de
funciones por tenerlas, y la implementación va de juego en juego tras la aprobación.

## Resumen ejecutivo

1. Los 11 juegos visibles son **correctos** (111 pruebas de `:lab-core` en verde; 0 estados imposibles en 1 200 semillas por nivel para Vigía, Anti-DDoS y Ruta), pero varios se resuelven **sin pensar**.
2. **Puerto Seguro**: en los niveles 1 a 4, el 100 % de las tareas se resuelven buscando en el panel la misma frase del enunciado (63 % en los niveles 7 a 10).
3. **Consulta Segura** (niveles 1 a 4): el 100 % se resuelve buscando el `?` y hay 4,9 repeticiones en cada partida de 12. **Cazador de logs** (niveles 1 a 4): solo hay 4 pistas distintas (8,1 repeticiones por partida) y la respuesta es la primera línea en el 46 % de las rondas.
4. **Anzuelo**: solo hay 15 cebos en los niveles 1 a 4, así que en 100 partidas solo salen esos 15 retos. Un jugador que siempre responde "Trampa" acierta el 67 %.
5. **El formato de 12 rondas no encaja con los tableros.** Una partida dura unos 7 min en Vigía y en Anti-DDoS, unos 8 min en Parejas y hasta unos 15 min en Maleta en el nivel 9, con un panel de "Solución paso a paso" después de cada oleada o tablero. Para subir de nivel hay que pasar 11 de 12 rondas dos veces seguidas.
6. **Parejas**: con una memoria realista (70 %), la probabilidad de subir de nivel es del 0 al 6 %. Además, SQLi y XSS salen en todos los tableros desde el nivel 5.
7. **Anti-DDoS**: la ayuda promete "cada oleada una regla nueva", pero las reglas son fijas por nivel. El nivel de combo 4 (tier 4) es inalcanzable, el confeti sale en una columna de 1 px y no se oye ningún pulso.
8. **Vigía** (niveles 1 y 2): solo aparece la fuerza bruta y cada ataque se ve en una sola línea. El 18 % de los tramos traen un único ataque.
9. Lo mejor del proyecto: un motor de reglas separado del dibujo, determinista por semilla, un solo bucle de frames por pantalla, `Juice` y `Particles` sin asignaciones de memoria. Hay que conservarlo.
10. Clasificación: **B** Puerto Seguro y Cazador de logs; **C** Consulta Segura; **A** el resto.
11. Orden propuesto: Puerto Seguro, Anti-DDoS (con el formato de partida por juego), Vigía, Parejas, Anzuelo, Cazador de logs, Bits, Subnet, Maleta, Ruta, Consulta Segura con fichas.
12. Pendientes V2: "logs desplazándose" se **descarta**, porque clonaría Vigía. Firewall con bloques se integra en Puerto Seguro (niveles 8 a 10). Detective y Cables esperan a que los 11 juegos cumplan la FASE 3.

## Método

- Lectura de cada camino: generador (`lab-core`), tablero (`lab/...Board.kt`, `PilotViews.kt`), planificador de rondas
  (`Rounds`, `Run.challenge`), veredicto (`accepts`, `*Shift`, `*Log.passed`), `Adaptive`, misiones y álbum.
- `./gradlew :lab-core:test`: 111 pruebas, 0 fallos.
- Simulación desechable **fuera del repositorio** (`scratchpad/sim/`, proyecto Kotlin que enlaza `lab-core.jar` con
  `-Xfriend-paths`): sesiones reales de 12 rondas por `Rounds.make`/`Generator.make` con la semilla de `Run`
  (`seed + i·10007`), 100 sesiones por nivel; 1 200 retos por nivel para Vigía, DDoS y Ruta; bots para Parejas
  (memoria perfecta, nula y 50/70/85 %) y "jugadores de atajo" (siempre "Trampa", buscar `?`, hora fuera de horario…).
- Duraciones calculadas con las constantes del código (`Juice`, `SHOW_MS`, `TRAMO_MS`, `waveMs`, `HOP_MS`).
- Probabilidad de subir de nivel (`Adaptive`: ≥ 85 % en 12 ⇒ 11/12, dos partidas seguidas):
  con un acierto por ronda q = 0,9 → 66 % por partida (43 % dos seguidas); q = 0,8 → 27 % (7,6 %); q = 0,7 → 8,5 % (0,7 %).

---

# FASE 1 — Auditoría

Notación: L = nivel. "Distintos" = retos distintos en 1 200 (100 sesiones × 12). "Rep." = retos repetidos (sin contar
repasos programados) dentro de una misma partida de 12.

## 1.1 Bits (`bits`, tablero BITS)

| Punto | Hallazgo |
|---|---|
| Mecánica | Interruptores por bit con su peso; formatos BUILD (desde 0) y EDIT (registro con otro valor). `Pilot.kt:132-142`, `PilotViews.kt:306-340`. |
| Reglas / objetivo | Claras: que los bits sumen el objetivo. Tiempo 28,5 s (L1) → 15 s (L10). |
| Experiencia | Táctil y agradable; el "lift" del bit y el color ámbar dan respuesta inmediata. |
| Diversión / rejugabilidad | Baja en L1–7: la suma decimal en vivo (`hints=l<8`, `Pilot.kt:136`) convierte el reto en "toca hasta que el número coincida", sin calcular. |
| Variedad (medida) | Respuestas distintas: 15 (L1–4, 4 bits), 31 (L5), 63 (L7), 127 (L9). Distintos 213/1200 en L1; rep. 0,71/12 en L1. |
| Dificultad / progresión | Ancho 4→8 bits es la única palanca; la pista desaparece de golpe en L8. |
| Puntuación | Binaria por ronda; no premia hacerlo con pocos toques ni sin pista. |
| Interfaz | Correcta; pesos en 12 sp `labelSmall` (por debajo del mínimo de 14 sp de la regla común V2 §1.6). |
| Animaciones | Tween de color + spring; nada bloquea la entrada. |
| Sonido / feedback | `TAP` por bit; en feedback se marcan los bits erróneos y "Correcto: …". |
| Rendimiento | Sin bucles; recomposición por toque limitada al tablero. |
| Errores | Ninguno funcional. Diseño: pista decimal en vivo hasta L7 (`Pilot.kt:136`). |

## 1.2 Subnet Sprint (`subnet`, tablero SUBNET)

| Punto | Hallazgo |
|---|---|
| Mecánica | Arrastrar la frontera de máscara sobre 32 bits; HOSTS, MASK y PAIR. `Pilot.kt:86-122`, `PilotViews.kt:91-153`. Es la mejor mecánica "física" del bloque Redes. |
| Reglas / objetivo | Claras; el enunciado de PAIR es exigente pero bien explicado. |
| Experiencia | Buena: lecturas de red/broadcast en vivo enseñan. |
| Diversión | Con `hints=l<7` (`Pilot.kt:89`) HOSTS muestra los hosts en vivo y PAIR muestra "¿Misma red?" y resalta los bits distintos: se resuelve arrastrando hasta que cambie la lectura. |
| Variedad (medida) | Respuestas distintas: 7 (L1–4), 16 (L5), 24 (L7–10). Distintos 113/1200 en L1; **rep. 1,66/12** en L1–4. |
| Dificultad | Rango de prefijos crece; la pista desaparece en L7 de golpe. |
| Puntuación | Binaria. |
| Interfaz | Buena; casillas de 40/54 dp. |
| Animaciones | Ninguna que estorbe. |
| Sonido / feedback | `TAP` solo al cruzar octeto (bien resuelto); barra verde de la solución. |
| Rendimiento | Sin bucles. |
| Errores | Ninguno funcional. |

## 1.3 Puerto Seguro (`ports`, tablero FIREWALL)

| Punto | Hallazgo |
|---|---|
| Mecánica | Panel de interruptores por puerto + "Aplicar reglas"; BLOCK, ALLOW, AUDIT. `Pilot.kt:163-198`, `PilotViews.kt:261-303`. |
| Reglas / objetivo | Claras. |
| Experiencia | Bonita (paquetes que fluyen, barrido ámbar), pero el contenido no exige saber puertos. |
| Diversión | **Coincidencia literal**: el enunciado dice "…el puerto usado para *{use}*" (`Pilot.kt:184`) y cada fila muestra ese mismo `use` (`PilotViews.kt:289`). En L≤3 además se añade el servicio entre paréntesis. |
| Variedad (medida) | Alta en forma (468/1200 en L1, ~1 010 en L7+), pero **tareas de un objetivo resolubles por coincidencia de texto: 100 % (L1–4), 91 % (L5–6), 63 % (L7–10)**. |
| Dificultad | Más filas y objetivos múltiples desde L7; la palanca real (saber qué hace cada puerto) no se usa. |
| Puntuación | Binaria; no distingue "cerraste uno de más". |
| Interfaz | Clara; filas de ~64 dp. |
| Animaciones | `rememberInfiniteTransition` leída en `drawBehind` (correcto, sin recomposición). |
| Sonido / feedback | `TAP` por interruptor; "Debía quedar abierto/cerrado" por fila. |
| Rendimiento | Correcto. |
| Errores | Diseño grave: `Pilot.kt:184` + `PilotViews.kt:289`. |

## 1.4 Anzuelo (`phishing`, tablero INBOX)

| Punto | Hallazgo |
|---|---|
| Mecánica | Deslizar la carta: derecha seguro, izquierda trampa; pista resaltada tras responder. `Pilot.kt:119-129`, `PilotViews.kt:196-258`. |
| Reglas / objetivo | Claras; "fíjate en el dominio real". |
| Experiencia | La mejor sensación táctil del bloque (sellos SEGURO/TRAMPA, vuelo de la carta). |
| Diversión | Se agota: el banco se memoriza en 2 partidas. |
| Variedad (medida) | Banco 35 cebos (`NetSec.kt:104-140`). Pool por nivel: **15 (L1–4)**, 27 (L5–7), **20 (L8–10, menor que L5)** por el filtro `tier-1..tier` (`Pilot.kt:120-121`). Distintos en 100 partidas: **15** (L1), 27 (L5), 20 (L9). Sin repeticiones dentro de partida (bien: `Rounds.replay`). |
| Dificultad | Por niveles de sutileza; correcto en intención. |
| Puntuación | Binaria; un jugador "siempre Trampa" acierta **67 %** (L1–4), 63 % (L5–7), 60 % (L8–10). |
| Interfaz | Buena; texto del cebo legible, iconos por canal. |
| Animaciones | No bloquean. |
| Sonido / feedback | `TAP` al armar el umbral; pista exacta resaltada. |
| Rendimiento | `PilotViews.kt:230`: `scope.launch{drag.snapTo(v)}` **por cada evento de arrastre** (una corrutina por movimiento). |
| Errores | Pool L8–10 más pequeño que L5–7 (`Pilot.kt:120`); sesgo hacia "trampa" del banco. |

## 1.5 Consulta Segura (`sqlsafe`, CHOICE clásico)

| Punto | Hallazgo |
|---|---|
| Mecánica | Elegir entre 2 consultas/permisos o 4 tipos de ataque. `NetSec.kt:185-195`. Sin tablero propio. |
| Reglas / objetivo | Claras. |
| Experiencia | Leer código en tarjetas monoespaciadas; es examen, no juego. |
| Diversión | Baja: en L1–3 solo hay "¿qué consulta es segura?". |
| Variedad (medida) | Bancos: 10 consultas, 6 permisos, 12 ataques. Distintos: **10 (L1–4)**, 16 (L5–6), 28 (L7+). **Rep. 4,89/12** (L1–4), 2,28/12 (L7+). |
| Dificultad | Solo cambia el tipo de pregunta. |
| Puntuación | Resoluble por "señal de superficie" (la segura contiene `?`/`%s`/`prepare`/lista blanca; el permiso bueno es el más corto): **100 % (L1–4), 92 % (L5–6), 44 % (L7+)**. Posición de la respuesta equilibrada (605/595). |
| Interfaz | `ChoiceCards` mono; correcto. |
| Animaciones | Entrada escalonada, pop y sacudida comunes. |
| Sonido / feedback | `CORRECT`/`RETRY` + explicación. |
| Rendimiento | Trivial. |
| Errores | Ninguno funcional; repetición y señal de superficie (`NetSec.kt:189-191`). |

## 1.6 Cazador de logs (`logs`, CHOICE clásico)

| Punto | Hallazgo |
|---|---|
| Mecánica | 4 líneas de log, tocar la sospechosa. `NetSec.kt:228-232`. |
| Reglas / objetivo | Claras, con contexto (tienda en Madrid, 08–19 h). |
| Experiencia | Lectura forense: buena idea, poco recorrido. |
| Diversión | Baja por repetición. |
| Variedad (medida) | Pistas: 4 (tier 1, L1–4), 8 (L5–7), 9 (L8–10). **Rep. 8,10/12** en L1–4 (la misma pista con otros números), 5,48/12 en L5–7. |
| Dificultad | Por tiers de pista. |
| Puntuación | **Respuesta en la posición 0 en el 46 %** (550/1200) porque las líneas se ordenan por hora (`NetSec.kt:230`) y varias pistas son a las 03:14. La hora fuera de 08–19 delata la respuesta en el 24 % (L1–7). |
| Interfaz | Monoespaciada, 13 sp (por debajo de 16 sp de la regla V2). |
| Animaciones | Comunes. |
| Sonido / feedback | Explicación clara del "por qué". |
| Rendimiento | Trivial. |
| Errores | Sesgo de posición (`NetSec.kt:230`); hora fija `03:14` en dos pistas (`NetSec.kt:214,218`). |

## 1.7 La maleta del técnico (`suitcase`)

| Punto | Hallazgo |
|---|---|
| Mecánica | "Me voy de viaje": la maleta crece de uno en uno; recordar en orden tocando la bandeja; 1.ª equivocación ayuda, 2.ª cierra. Reglas ARRIVAL/LAYER/TRACE. `Suitcase.kt`, `SuitcaseBoard.kt`. |
| Reglas / objetivo | Claras; LAYER ordena por capa con **empates por llegada** (`Suitcase.kt:27`) y eso no se explica (`layerHint`). |
| Experiencia | Muy cuidada (tapa, vuelo de la ficha, LEDs del fantasma). |
| Diversión | Buena en las primeras cadenas; larga y monótona después. |
| Variedad (medida) | L1: pool 10 cables, **todos salen en cada partida**; Jaccard entre rondas consecutivas 0,35 (L1) → 0,17 (L5+). Cadenas distintas ≈ 100 %. Reglas en L9: 49 % ARRIVAL, 26 % LAYER, 26 % TRACE. |
| Dificultad | Objetivo 5→13; `start=target−3` (`Suitcase.kt:117`) ⇒ solo 3 crecimientos por cadena. La bandeja guarda hasta `size−1` objetos de la cadena y **un solo señuelo** (`Suitcase.kt:194`): cuanto más larga la cadena, menos discriminación (en L1, con 5 objetos, 5 de 6 fichas son correctas). |
| Puntuación | `clean` exige cero ayudas en la cadena: muy estricto para "acierto de ronda". |
| Interfaz | Buena; pulsación larga para leer el objeto. |
| Animaciones | Vuelos y tapa no bloquean la entrada salvo en SHOW (correcto). |
| Sonido / feedback | `WHOOSH`, `TAP`, `COMBO` por ficha; récord con `LEVEL_UP`. |
| Rendimiento | Bucle solo mientras hay chispas; bien. |
| Errores / duración | Toques por partida: **168 (L1) → 552 (L9)**; duración estimada ≈ 5–6 min (L1) y **≈ 15 min (L9)**. Empates LAYER sin explicar. |

## 1.8 Vigía de alertas (`vigil`)

| Punto | Hallazgo |
|---|---|
| Mecánica | Monitor que sube; tocar alertas reales; escudos, congelar 2 s (×3). Motor de reglas `Vigil.triggers`. |
| Reglas / objetivo | Claras; la cabecera de guardia es una gran idea. |
| Experiencia | Tensión real (latido final, muro de lapsos). |
| Diversión | L1–2 monótona: 12 tramos de fuerza bruta. |
| Variedad (medida) | Patrones L1–2: **100 % fuerza bruta**; L3–4: 75 % hora rara; L9–10: **47 % exfiltración** (el primer objetivo es siempre el patrón más nuevo, `Vigil.kt:120`). **18 % de tramos L1–2 con 1 solo ataque** de 2 (`place(5,6)` falla, `Vigil.kt:124`). |
| Dificultad | Ataques reconocibles con UNA línea (sin contar): **100 % (L1–4)**, 45 % (L5–6), 17 % (L7–8), 61 % (L9–10). Hasta L7 "admin" solo aparece en ataques. |
| Puntuación | Correcta; 0 líneas de ruido disparan una regla y 0 desajustes `detectAt` en 1 200 tramos por nivel. |
| Interfaz | Filas de 48 dp, texto 16 sp; bien. |
| Animaciones | Un bucle por pantalla; sin movimiento ⇒ pasos discretos (bien). |
| Sonido / feedback | `CORRECT`, `RETRY`, `ALARM`; motivo de la falsa alarma (excelente). |
| Rendimiento | `world.top` recompone la fila cada línea y reconstruye `customActions` (`VigilBoard.kt:187`): asignación ~1/s, aceptable. |
| Errores / duración | 12 × 30 s + panel ⇒ **≈ 7 min**. Densidad desigual (18 %). |

## 1.9 Ruta de paquetes (`route`)

| Punto | Hallazgo |
|---|---|
| Mecánica | Ver el paquete recorrer un mapa tipo metro y dibujar el camino; enlace caído, ruta alternativa, TTL. |
| Reglas / objetivo | Claras. |
| Experiencia | Muy buena; rondas cortas. |
| Diversión | Buena; en L1–6 es memoria de un paseo aleatorio, no "decidir como un protocolo" (eso solo ocurre en L7–8, ALT). |
| Variedad (medida) | Distintos **472/1200 (L1–2)**, ≥ 1 153 en L3+. Bucles 7–13 %; TTL descartado 50 %. 0 inválidos (plano, conexo, `check(answer)` ok). Generación máx. 12 ms. |
| Dificultad | Curva suave; L1–2 (5 routers, 3 saltos) muy fácil. |
| Puntuación | Binaria por ruta; errores clasificados (orden/desvío/TTL) bien. |
| Interfaz | Buena; radio de imán 80 px. |
| Animaciones | Bucle solo mientras algo se mueve (`busy()`), correcto. |
| Sonido / feedback | `WHOOSH`, `TAP`, `COMBO`, réplica de tu ruta. |
| Rendimiento | Bien. |
| Errores / duración | Ninguno. ≈ 10 s por ronda ⇒ **≈ 2–2,5 min**: el único tablero cuyo formato de 12 encaja. |

## 1.10 Turno anti-DDoS (`ddos`)

| Punto | Hallazgo |
|---|---|
| Mecánica | Cinta de 20 paquetes al ritmo del reloj de pulsos; tocar para bloquear, deslizar arriba = lista negra. `Ddos.kt`, `DdosBoard.kt`. |
| Reglas / objetivo | **La ayuda promete "Cada oleada el jefe de turno sella una regla nueva"** (`Games.kt:14`), pero `Ddos.level` fija las reglas por nivel (`Ddos.kt:86-91`) y el jefe anuncia la misma regla las 12 oleadas (`DdosBoard.kt:86`). |
| Experiencia | Lo más "arcade"; la congelación de 1 s en cada error (`DdosBoard.kt:57`) corta el ritmo. |
| Diversión | L1–2: tocar los `:23` (4 de 20 paquetes) durante 12 oleadas. |
| Variedad (medida) | Paquetes malos 20 % (L1–2) → 55 % (L9–10); detectables por UN campo (`:23`, `SYN sin ACK`, DNS grande): **100 % (L1–4)**, 75 %, 60 %, 73 %. Motor y generador coinciden (0 desajustes). |
| Dificultad | BPM 90→140 y reglas por nivel; dentro de la partida solo ±BPM. |
| Puntuación | **Tier 4 inalcanzable**: `tier()` pide racha 24 (`Ddos.kt:165`) y una oleada tiene 20 (`Ddos.kt:57`); el borde de tier 4 (`DdosBoard.kt:252`) es código muerto. Los puntos por tier no aparecen en el resultado final ("X/12"). |
| Interfaz | Cinta de 340 dp + cabecera + pie dentro de un `verticalScroll` (`Player.kt:47`): en móviles pequeños hay que desplazar la página en plena oleada. |
| Animaciones | **Confeti de oleada perfecta con ancho `beltX*2+1f`** (`DdosBoard.kt:111`; `beltX`≈0) ⇒ sale en una franja de ~1 px a la izquierda. |
| Sonido / feedback | "Al ritmo", pero **no suena ningún pulso**: sin metrónomo el juego de ritmo no tiene ritmo. |
| Rendimiento | Ejemplar: 12 ranuras fijas, reloj entero, sin asignación por frame. |
| Errores / duración | Los anteriores. 12 × (2 s jefe + 30,7 s + 0,9 s + panel) ⇒ **≈ 7,5 min** en L1. |

## 1.11 Parejas de incidentes (`pairs`)

| Punto | Hallazgo |
|---|---|
| Mecánica | Memoria clásica con pares y tríos de conceptos; pista tras 3 fallos; barajado parcial L9–10. |
| Reglas / objetivo | Claras; objetivo de intentos por tablero. |
| Experiencia | Lección al emparejar (bien); voltear se nota **lento**: `detectTapGestures(onDoubleTap=…)` (`PairsBoard.kt:191`) retrasa cada toque simple el tiempo de doble toque (~300 ms). |
| Diversión | Buena por tablero; 12 tableros es mucho (L7: 24 cartas, ≈ 40 s cada uno ⇒ ≈ 8 min). |
| Variedad (medida) | Tableros distintos ≥ 1 149/1200; entradas distintas por partida 19,8 (L1) → 34,4 (L7) de 41. **SQLi y XSS forzadas en todos los tableros desde L5** (`PairGame.kt:148`): 12 de 12. |
| Dificultad | Objetivo = 1,6–1,9 × grupos (`PairGame.kt:133`). Bot memoria perfecta: 9,2 intentos (obj. 10, pasa 99 %). **Memoria 70 %: pasa 64 % (L1), 41 % (L3), 50 % (L5); P(subir) 3,5 %, 0 %, 0,3 %.** Memoria 85 %: P(subir) 49 %, 7 %, 39 %. |
| Puntuación | `score` interno (10/20) no visible en el resultado. |
| Interfaz | Rejilla de 3×4 a 4×6; temas cosméticos que no cambian el tablero (probado). |
| Animaciones | Volteo, ola final, arco de pareja; correctas. |
| Sonido / feedback | `TAP`, `COMBO`, `RETRY`, `WHOOSH` en barajado. |
| Rendimiento | Bucle solo con chispas; bien. |
| Errores | Retraso por doble toque (`PairsBoard.kt:191`); objetivo demasiado estricto; temas forzados (`PairGame.kt:148`). |

## 1.12 Modos y piezas transversales

| Pieza | Hallazgo |
|---|---|
| Player 12 rondas | Para tableros, cada oleada/tramo/tablero es "1 ronda" y tras cada una aparece el panel genérico "Solución paso a paso" + "Siguiente ronda" (`Player.kt:117-130`), con el mismo texto de reglas 12 veces. El tablero ya emite su cue y `score()` emite otro `CORRECT/RETRY` (`BrainViewModel.kt:226`): doble señal. Pausar cambia la semilla (`nonce`, `BrainViewModel.kt:32`): la ronda en curso de un tablero se pierde entera. |
| Adaptive | 11/12 dos veces seguidas (`Games.kt:129`). Razonable para preguntas de 10 s; desproporcionado para unidades de 30–70 s. |
| Relámpago | 60 s, combo ×2/×3; solo Redes. Bien; usa los tableros piloto. |
| Sin tiempo / Infinito | En tableros de tiempo real (Vigía, DDoS) "sin cronómetro" no cambia nada: su reloj es el juego. |
| Práctica 3 min | Solo Redes, 6 retos; correcto. |
| Engagement | Cofre (≤ 3/día), misiones, álbum, fantasma y "Otra vez": sanos. Misión "Combo ×3 en una oleada" exige racha 16 de 20 (dura pero posible). |
| Juice / Particles | Tokens de duración, curvas, sacudidas y presets; pool de 256 chispas sin asignación. |
| Inicio | `HeroCard` lee `animateFloat(...).value` **en composición** (`LiteApp.kt:118`) y el jardín igual (`ProgressViews.kt:141`): la pantalla de inicio recompone a la frecuencia de pantalla mientras se ve. |
| Ocultos | 22 ids retirados (`Games.kt` Lote A, Fase 2, Fase 3) y Meteoros/Parejas/Pulso clásicos tras "Mostrar ejercicios clásicos": solo historial; no se auditan. |

---

# FASE 2 — Diagnóstico por juego

Prioridad = impacto en la experiencia (1–5) / esfuerzo (S = 1, M = 2, L = 3).

### Puerto Seguro — **B (rediseñar el contenido; conservar el panel)**
- **Estado:** panel precioso, tarea de lectura literal. **Fortalezas:** interruptores, barrido, AUDIT con estado inicial
  mixto. **Debilidades:** el enunciado contiene la frase de la fila (100 % en L1–4). **Repetitivo:** "cierra el de X".
- **Oportunidad:** que la fila muestre solo `:puerto` (y el servicio solo en L1–2) y que el enunciado sea un **escenario**
  ("El auditor vio contraseñas en claro hacia el servidor de ficheros") que hay que traducir a puertos.
- **Rediseño:** escenarios con 1–3 puertos implicados, AUDIT con "servidor heredado" y, en L8–10, el orden de reglas
  (lo que V2 llamaba Firewall con bloques). **Prioridad 4/1 = 4,0.**

### Turno anti-DDoS — **A (conservar y mejorar)**
- **Estado:** el mejor motor del proyecto, la peor curva dentro de la partida. **Fortalezas:** reloj sin deriva, pool fijo,
  reglas con prioridad explícita, explicaciones por error. **Debilidades:** 12 oleadas iguales, sin pulso audible,
  congelación de 1 s, tier 4 imposible, confeti roto, panel entre oleadas, puntos invisibles.
- **Oportunidad:** cumplir lo que promete la ayuda: **una regla nueva por oleada dentro de la partida**.
- **Rediseño:** partida = 5 oleadas encadenadas (sin panel intermedio), la regla del nivel entra en la 2.ª o 3.ª oleada
  con el sello del jefe, clic de pulso cada beat, puntuación visible. **Prioridad 5/2 = 2,5.**

### Vigía de alertas — **A**
- **Fortalezas:** motor de reglas impecable (0 falsos disparos), feedback del "por qué", congelar. **Debilidades:**
  L1–4 se resuelve con una sola línea; densidad desigual; 7 min. **Repetitivo:** fuerza bruta ×12 en L1–2.
- **Rediseño:** partida = 4 tramos (≈ 2,5 min), introducir el señuelo "5 fallos desde 5 IP internas" ya en L2 para que
  haya que **contar** de verdad, y garantizar `targets` ataques por tramo. **Prioridad 3/1 = 3,0.**

### Parejas de incidentes — **A**
- **Fortalezas:** bancos con lección, tríos, temas que no alteran el tablero. **Debilidades:** objetivo inalcanzable
  para memoria humana, toque lento, SQLi/XSS fijas, 12 tableros. **Prioridad 3/1 = 3,0.**
- **Rediseño:** partida = 3 tableros crecientes, objetivo calibrado al bot de memoria 70 % (pasa ≈ 80 %), estrellas
  por intentos (1–3) en lugar de aprobado/suspenso, quitar el retraso de doble toque.

### Anzuelo — **A**
- **Fortalezas:** gesto, pista exacta, sin repetición en partida. **Debilidades:** 15 cebos en L1–4, sesgo "trampa".
- **Rediseño:** generador de cebos por plantillas (marca × truco × canal) con pista calculada, 50/50 seguro/trampa,
  bandeja con 2–3 mensajes donde a veces hay que elegir **cuál** es el falso. **Prioridad 4/2 = 2,0.**

### Cazador de logs — **B (rediseñar)**
- **Estado:** 4 líneas, 4 pistas. **Debilidades:** 8,1 repeticiones por partida, posición 0 en el 46 %, hora delatora.
- **Oportunidad:** reutilizar el **generador estructurado de Vigía** (`VigilLine` + `triggers`) para un análisis
  **forense en reposo**: 10–14 líneas fijas, 1–3 sospechosas, filtros por columna (usuario, IP, hora). Identidad distinta
  de Vigía (sin desplazamiento ni presión de tiempo). **Prioridad 4/2 = 2,0.**

### Bits — **A**
- Pista decimal hasta L7 ⇒ graduarla (L1–3 suma en vivo; L4–6 solo "frío/caliente" ±; L7+ nada); mini-cadena de
  3 conversiones contrarreloj como formato. **Prioridad 2/1 = 2,0.**

### Subnet Sprint — **A**
- Pistas hasta L6 ⇒ graduarlas; añadir formato "¿caben N equipos en esta red?" con tarjeta de destino; repartir las
  respuestas para bajar las 1,66 repeticiones. **Prioridad 2/1 = 2,0.**

### La maleta del técnico — **A**
- **Fortalezas:** identidad muy clara, presentación excelente. **Debilidades:** duración (≈ 15 min en L9), bandeja con un
  solo señuelo al final, empates LAYER sin explicar. **Rediseño:** partida = 3 cadenas; los señuelos crecen con la
  cadena (mínimo 2–3 "gemelos"); explicar empates. **Prioridad 3/2 = 1,5.**

### Ruta de paquetes — **A**
- **Fortalezas:** formato y duración correctos, mapas válidos. **Debilidades:** L1–2 poco variado (472/1200) y la decisión
  de enrutamiento llega tarde. **Rediseño:** mapas de 6 routers en L1–2 y una ronda "predice el siguiente salto" desde
  L3. **Prioridad 2/1 = 2,0.**

### Consulta Segura — **C (reconstruir parcialmente)**
- **Estado:** examen de 2 opciones, resoluble por superficie. **Oportunidad:** V2 Fase 5, "consulta con fichas":
  construir la sentencia arrastrando fichas (`?`, parámetro, concatenación, lista blanca) y verla ejecutarse con un dato
  malicioso. Conserva bancos y claves del álbum. **Prioridad 4/3 = 1,3.**

## Tabla resumen (por prioridad)

| # | Juego | Clase | Impacto | Esfuerzo | Prioridad | Problema principal |
|---|---|---|---|---|---|---|
| 1 | Puerto Seguro | B | 4 | S | 4,0 | 100 % de tareas por coincidencia de texto (L1–4) |
| 2 | Vigía de alertas | A | 3 | S | 3,0 | L1–4 se resuelve con una línea; 7 min |
| 3 | Parejas de incidentes | A | 3 | S | 3,0 | P(subir) ≈ 0–6 % con memoria 70 %; toque lento |
| 4 | Turno anti-DDoS | A | 5 | M | 2,5 | 12 oleadas iguales, sin pulso, tier 4 imposible |
| 5 | Anzuelo | A | 4 | M | 2,0 | 15 cebos en L1–4; "siempre trampa" = 67 % |
| 6 | Cazador de logs | B | 4 | M | 2,0 | 8,1 repeticiones/12; respuesta 1.ª en 46 % |
| 7 | Bits | A | 2 | S | 2,0 | Suma en vivo hasta L7 |
| 8 | Subnet Sprint | A | 2 | S | 2,0 | Pistas que dan la respuesta hasta L6 |
| 9 | Ruta de paquetes | A | 2 | S | 2,0 | L1–2 poco variado |
| 10 | La maleta del técnico | A | 3 | M | 1,5 | 168–552 toques por partida; 1 señuelo |
| 11 | Consulta Segura | C | 4 | L | 1,3 | Resoluble por "?"; 4,9 repeticiones/12 |

El **orden del plan** (FASE 6) sube Anti-DDoS al paso 2 pese a su 2,5: introduce el *formato de partida por juego*
que después reutilizan Vigía, Parejas y Maleta, y es el juego con más impacto.

---

# FASE 3 — Criterios de calidad comunes

Cada criterio se verifica con una prueba en `lab-core` (bots sobre el motor puro) o una medida reproducible.

| # | Criterio | Cómo se verifica |
|---|---|---|
| Q1 | **Sin atajo trivial**: ningún jugador de atajo (siempre la misma opción, buscar texto literal, buscar `?`, primera línea, pista en vivo) supera el 60 % en el nivel donde actúa. | Prueba con bots por juego sobre 1 000 semillas por nivel. |
| Q2 | **Sin coincidencia literal** enunciado ↔ opción correcta. | Prueba: `prompt` no contiene el texto de la fila/opción correcta en > 5 % de 1 000 semillas. |
| Q3 | **Anti-repetición en partida**: ≤ 1 reto repetido (fuera de repasos) en una partida. | Prueba con `Rounds.make` y 100 sesiones. |
| Q4 | **Anti-repetición entre partidas**: en 10 partidas seguidas, ≥ 70 % de retos no vistos en las 3 anteriores. | Prueba de cobertura del banco/generador. |
| Q5 | **Posición equilibrada**: índice de respuesta dentro de ±10 % del uniforme. | Prueba de distribución. |
| Q6 | **Duración de partida 2–5 min** en todos los niveles. | Prueba que suma las constantes de tiempo del juego (`Juice`, `SHOW_MS`, `waveMs`…). |
| Q7 | **Curva justa**: un bot de habilidad "objetivo" del nivel sube en ≤ 3 partidas; uno claramente inferior no sube. | Simulación de `Adaptive` con bots. |
| Q8 | **Estados alcanzables**: todo premio/tier/misión es alcanzable; ningún nivel imposible. | Prueba: máximo teórico ≥ umbral (p. ej. `tier(WAVE)` ≥ tier máximo). |
| Q9 | **Reproducible por semilla**: mismo (nivel, semilla) ⇒ mismo reto y mismo veredicto. | Pruebas existentes; ampliarlas a cada cambio. |
| Q10 | **Animaciones no cambian el juego**: con "Animaciones" off, el mismo guion de toques da el mismo log. | Prueba que conduce `*Shift`/`*Play` en ambos modos; `FrameProbe.frames == 0` sin movimiento. |
| Q11 | **Controles responsivos**: respuesta visual ≤ 1 frame al toque; sin retrasos de doble toque; el tablero no desplaza la página mientras se juega. | Prueba de UI (Robolectric) + revisión de `pointerInput`. |
| Q12 | **Rendimiento estable**: un bucle de frames por pantalla, nada animado leído en composición, sin asignaciones por frame. | `FrameProbe`, prueba de pool (`Spark.created`), búsqueda de `.value` de `InfiniteTransition` fuera de draw. |
| Q13 | **Puntuación real**: lo que se muestra al final es lo que se guarda y lo que cuentan misiones/fantasma. | Prueba encode/decode + resumen. |
| Q14 | **Cada error explica por qué** en ≤ 1 s, sin bloquear el siguiente gesto más de `Juice.Hold`. | Revisión + prueba de que cada veredicto negativo trae motivo. |

## Identidad (lo que NO debe cambiar)

| Juego | Identidad intocable |
|---|---|
| Bits | Interruptores con peso de posición; construir el número, no elegirlo. |
| Subnet Sprint | Arrastrar la frontera red/host sobre los 32 bits. |
| Puerto Seguro | Ser el firewall: interruptores por puerto y "Aplicar reglas". |
| Anzuelo | Deslizar un mensaje a seguro/trampa y ver la pista exacta resaltada. |
| Consulta Segura | Código real de varios lenguajes; la defensa es separar código y datos. |
| Cazador de logs | Leer con calma un registro y señalar lo anómalo (forense, sin prisa visual). |
| La maleta | "Me voy de viaje y en mi maleta llevo…": cadena que crece de uno en uno y se recuerda en orden. |
| Vigía | Turno de noche: flujo que sube, casi todo ruido, escudos y falsas alarmas con su porqué. |
| Ruta de paquetes | Mapa tipo metro; ver el paquete y dibujar su camino. |
| Turno anti-DDoS | Cinta al ritmo; tocar para bloquear; el jefe sella reglas. |
| Parejas | Memoria de cartas boca abajo con conceptos que se emparejan y enseñan al casar. |

---

# FASE 4 — Propuestas individuales

### 4.1 Puerto Seguro (paso 1)
- **Conservar:** panel, interruptores, barrido "regla aplicada", AUDIT con estado inicial mixto, objetivos múltiples.
- **Eliminar:** el `use` en la fila (desde L3) y el servicio entre paréntesis del enunciado (desde L3): son la respuesta.
- **Modificar:** enunciados como **escenarios** generados desde un banco de ~25 situaciones (síntoma → puertos): "Un
  becario sube webs por un cliente que envía la contraseña en claro" ⇒ cerrar 21. Fila: `:21` grande; servicio visible
  solo L1–2; uso visible solo tras responder.
- **Nuevas mecánicas (justificadas):** L8–10 "orden de reglas" (2–4 reglas allow/deny con primera coincidencia), que es
  lo que V2 §9 pedía como juego aparte; aquí sube el techo sin crear otro juego.
- **Diversión:** pasa de "buscar la frase" a "deducir qué servicio delata el síntoma".
- **Anti-repetición:** escenario × servidor × distractores; ≤ 1 repetido por partida (Q3).
- **Dificultad:** L1–2 servicio visible; L3–6 solo puerto; L7 objetivos múltiples; L8–10 orden de reglas.
- **Visual:** ninguno nuevo; sello "REGLA APLICADA" con `Juice.Settle` ya existe.

### 4.2 Turno anti-DDoS (paso 2)
- **Conservar:** `DdosBeat`, `DdosShift`, prioridad de reglas, lista negra, explicaciones, pool de 12 ranuras.
- **Eliminar:** el panel "Solución paso a paso" entre oleadas; la congelación de 1 s (sustituir por la tarjeta errónea
  marcada y la lección en el pie sin parar la cinta, salvo en L1).
- **Modificar:** partida = **5 oleadas encadenadas** (≈ 2,5–3 min). Oleada 1–2: reglas ya conocidas; oleada 3: el jefe
  sella la regla nueva del nivel; oleada 5: "pico" (+10 BPM). Esto cumple la ayuda. `tier()` con umbrales 5/10/15 por
  oleada o racha que cruza oleadas (Q8). Confeti con el ancho real de la cinta (`DdosBoard.kt:111`). Mostrar puntos al final.
- **Nuevas mecánicas:** **clic de pulso** con `SoundPool` (muestra `lab_touch` a volumen bajo) en cada beat y acento en el
  primer beat de cada paquete; sin archivos nuevos. Justificación: es un juego de ritmo.
- **Diversión:** progresión dentro de la partida, ritmo audible, sin cortes.
- **Anti-repetición:** cada oleada mezcla reglas distintas; IP hostiles con prefijos rotados.
- **Dificultad:** `Adaptive` cuenta **paquetes** (≥ 85 % de 100) y no oleadas.
- **Visual:** borde de combo ya existe; activar el de tier máximo alcanzable.

### 4.3 Vigía de alertas (paso 3)
- **Conservar:** motor de reglas, escudos, congelar, motivos de falsa alarma, latido final.
- **Eliminar:** el panel entre tramos (resumen corto propio del tablero).
- **Modificar:** partida = 4 tramos; garantizar `targets` ataques (reintentar `place` con otra semilla derivada en vez de
  `continue`, `Vigil.kt:124`); señuelo de "admin desde IP internas" desde L2; el primer objetivo rota entre patrones
  conocidos (no siempre el más nuevo, `Vigil.kt:120`).
- **Nuevas mecánicas:** ninguna.
- **Diversión:** hay que contar y comparar desde el principio; menos minutos, más intensidad.
- **Anti-repetición:** ≥ 2 patrones por partida desde L2.
- **Dificultad:** Q1 con bot "una línea basta" < 60 % desde L3.
- **Visual:** nada nuevo.

### 4.4 Parejas de incidentes (paso 4)
- **Conservar:** bancos, tríos, lección al casar, temas, pista "la viste aquí".
- **Eliminar:** el retraso de doble toque (`PairsBoard.kt:191`): leer la lección con pulsación larga o un botón ⓘ en
  la pareja hecha.
- **Modificar:** partida = 3 tableros (pequeño, medio, grande del nivel); objetivo calibrado con el bot de memoria 70 %
  (≈ 80 % de aprobados) y **estrellas** 1–3 por intentos; SQLi/XSS solo con probabilidad (no forzadas, `PairGame.kt:148`).
- **Diversión:** el fallo deja de ser binario; el tablero grande es el clímax.
- **Anti-repetición:** ninguna entrada en más de 2 de los 3 tableros.
- **Dificultad:** Q7 con bots de 50/70/85 %.
- **Visual:** estrellas con `Juice.Overshoot` en la ola final (ya existe la ola).

### 4.5 Anzuelo (paso 5)
- **Conservar:** gesto, sellos, pista resaltada, no repetición por partida.
- **Modificar:** generador de cebos por plantillas: marca (12) × truco (homógrafo, subdominio de adorno, `@` en URL,
  guion, TLD cambiado, punycode, doble extensión, acortador, remitente gratuito) × canal (correo, enlace, SMS),
  con `mark` calculado; los seguros también por plantilla (subdominios legítimos). Proporción 50/50 por partida.
  Corregir el pool L8–10 (`Pilot.kt:120`).
- **Nuevas mecánicas:** en L6+ ronda "bandeja": 3 mensajes, uno falso, tocarlo. Justificación: rompe el sesgo binario.
- **Diversión:** siempre hay un cebo nuevo; las rondas de bandeja dan un "¡lo pillé!".
- **Anti-repetición:** Q4 ≥ 70 %; el banco escrito se conserva como "casos reales" (álbum intacto).
- **Dificultad:** Q1 "siempre trampa" ≈ 50 %.
- **Visual:** pila de 3 cartas ya dibujada; reutilizarla.

### 4.6 Cazador de logs (paso 6)
- **Conservar:** bancos de pistas y textos de "por qué" (claves del álbum), monoespaciado, contexto del servidor.
- **Eliminar:** el orden por hora que pone la respuesta primero; las 4 opciones fijas.
- **Modificar:** registro de 10–14 líneas en reposo generado con el modelo estructurado de Vigía (`VigilLine`,
  `Vigil.triggers` como verificador), con 1–3 líneas sospechosas que hay que marcar y luego "cerrar caso".
- **Nuevas mecánicas:** chips de filtro (usuario/IP/hora) que resaltan columnas: es la herramienta real del analista.
- **Descartado:** "logs desplazándose" (V2 Fase 5) porque duplicaría Vigía.
- **Diversión:** investigación con herramientas en vez de adivinar entre 4.
- **Anti-repetición:** líneas generadas; Q3/Q5.
- **Dificultad:** número de sospechosas y señuelos (1 → 3); tiempo holgado.
- **Visual:** fondo `NetBg`, cursor de selección; sin desplazamiento.

### 4.7 Bits (paso 7)
- **Conservar:** interruptores y pesos. **Modificar:** pista graduada (L1–3 suma; L4–6 "te faltan/sobran" sin número;
  L7+ nada). **Nuevo formato:** "ráfaga": 3 números seguidos en el mismo registro (EDIT encadenado); justifica el
  ritmo sin añadir otro juego. **Dificultad:** Q1 con bot "subir/bajar hasta que coincida". Pesos a 14 sp.

### 4.8 Subnet Sprint (paso 8)
- **Conservar:** frontera arrastrable y lecturas. **Modificar:** pistas graduadas como Bits; repartir prefijos objetivo
  (evitar los 1,66 repetidos). **Nuevo formato** (L5+): "¿cabe?" — dada una red y N equipos, ajustar y confirmar si cabe
  sin desperdiciar más de la mitad. **Dificultad:** Q1 con bot "arrastra hasta que la lectura cambie".

### 4.9 La maleta del técnico (paso 9)
- **Conservar:** cadena incremental, tapa, vuelo, ayuda en el primer fallo, reglas LAYER/TRACE.
- **Modificar:** partida = 3 cadenas; bandeja con al menos `min(3, size/3)` señuelos siempre, priorizando gemelos;
  explicar empates de capa ("misma capa: por orden de llegada"); `clean` → estrellas (sin ayuda / con ayuda).
- **Diversión:** cada cadena importa; menos toques repetidos (Q6: ≤ 5 min).
- **Anti-repetición:** en L1–2 mezclar 2 categorías para no ver los mismos 10 cables cada partida.

### 4.10 Ruta de paquetes (paso 10)
- **Conservar:** todo el mapa, réplica y errores clasificados. **Modificar:** L1–2 con 6 routers (sube la variedad);
  desde L3, 1 de cada 4 rondas "siguiente salto" (tocar el router al que el paquete irá con la tabla dada).
  **Justificación:** la fantasía "eres el protocolo" llega antes; la memoria sigue siendo la base.

### 4.11 Consulta Segura con fichas (paso 11)
- **Conservar:** bancos (consultas, permisos, ataques) y claves del álbum.
- **Reconstruir:** tablero de fichas: la consulta con huecos y fichas (`"+ name +"`, `?`, `name`, `escape()`,
  lista blanca). Al pulsar "Ejecutar" se lanza un dato malicioso del banco de ataques y se ve el resultado (fila filtrada
  o "0 filas"). Permisos: arrastrar privilegios al rol hasta el mínimo que funcione.
- **Diversión:** construir y ver romperse (o resistir) la consulta.
- **Dificultad:** L1 un hueco; L5 varios lenguajes; L8 `ORDER BY` con lista blanca.

---

# FASE 5 — Identidad visual arcade moderna

**Qué ya da `Juice.kt`/`Particles.kt`:** duraciones (`Tick…Celebrate`), curvas (`EaseOut`, `Snappy`, `Bouncy`,
`Overshoot`), `springAt` sin asignaciones, sacudidas por `graphicsLayer` con alternativa sin movimiento, presets de
partículas con pool de 256, paleta mínima (`Good`, `Bad`, `Info`, `NetAmber`, `NetInk`, `NetBg`) y `FrameProbe`.

**Huecos:** cada tablero define sus colores privados (`Leather`, `Night`, `ShiftBg`, `Paper`…); no hay tokens de
superficie/tinta de juego ni acento por juego; tableros oscuros fijos sobre el tema claro de la app; no hay escala
tipográfica de HUD (marcador, combo, nivel) ni cifras tabulares; no hay pantalla de resultado de tablero común (cada uno
pinta su pie y luego aparece el `ResultScreen` genérico "X/12"); no hay transición entre unidades de una partida; el
sonido son 4 muestras con cambio de tono y sin pulso.

### Tokens propuestos (`ArcadeTokens`, junto a `Juice`)

| Token | Valor | Uso |
|---|---|---|
| `Bg0` / `Bg1` / `Bg2` | `#0B0F17` / `#121826` / `#1A2233` | fondo de juego, panel, celda |
| `Line` | `#2A3550` | bordes 1 dp |
| `Ink` / `InkDim` | `#EAF0FA` / `#8C9AB5` | texto principal / secundario |
| `Good` / `Bad` / `Info` / `Warn` | `#1EDB90` / `#FF5470` / `#7CF5FF` / `#FFB300` | ya en `Juice` |
| Acento por juego | Bits `#FFB300` · Subnet `#7CF5FF` · Puertos `#2FE0B0` · Anzuelo `#FF8A65` · Consulta `#B69CFF` · Logs `#C6FF5A` · Maleta `#E0A050` · Vigía `#59B8FF` · Ruta (líneas actuales) · DDoS `#FF6B9A` · Parejas (tema elegido) | pastilla, partículas `Spark`, borde de combo |

Los tableros usan siempre `Bg*` (también con tema claro: el juego es un "dispositivo" dentro de la app) y su acento.

- **Tipografía:** HUD con `FontFamily.Monospace` + `FontFeature("tnum")` para marcadores; `titleLarge` Black para combo y
  nivel; mínimo 16 sp en juego, 14 sp en etiquetas (corrige pesos de Bits 12 sp y logs 13 sp).
- **Iconografía:** Material Rounded actual; un icono por juego (los `symbol` de `Game`) como sello en la pastilla.
- **Animaciones / transiciones:** entre unidades de partida, barrido horizontal del tablero (`Move`, `EaseOut`) con
  cuenta "2/5"; nunca bloquea la entrada más de `Juice.Hold`.
- **Efectos:** `Spark` del acento al acertar; `Shatter` solo en errores graves (paquete que tumba el servidor, lapso).
- **Feedback:** contador que "rueda" (`Snappy`) al sumar puntos; texto de motivo en el pie, no en diálogos.
- **Pantalla de resultado de tablero común:** estrellas o puntos del juego, mejor marca, curva por unidad (la de DDoS ya
  existe y se generaliza), "Otra vez" y la lección más fallada. Sustituye al "X/12" para tableros.
- **Menús / navegación:** la tarjeta de cada juego muestra acento, nivel, mejor marca y duración estimada ("≈ 3 min").
- **Personalidad dentro del sistema:** la piel del tablero (cuero de la maleta, noche del SOC, cinta violeta de DDoS,
  papel del metro) se conserva como capa propia por encima de `Bg*`; el sistema unifica marco, HUD, resultado y tipos.
- **Rendimiento:** `HeroCard` y el jardín deben leer la animación en `drawBehind` (`LiteApp.kt:118`,
  `ProgressViews.kt:141`) para no recomponer el inicio a 60–120 Hz.

---

# FASE 6 — Plan incremental (un juego por paso, tras aprobación)

Validación común de cada paso (FASE 7): mecánica correcta · puntuación real · sin errores de progresión · controles
responsivos · sin niveles imposibles · reproducible por semilla · dificultad equilibrada · animaciones sin efecto en el
juego · rendimiento estable. Cada paso añade pruebas en `lab-core` y no rompe las 111 actuales.

| Paso | Juego | Tam. | Pruebas de aceptación |
|---|---|---|---|
| 1 | **Puerto Seguro** (escenarios, fila sin `use`, orden de reglas L8–10) | S | Q2: prompt ∌ texto de fila correcta < 5 % (1 000 semillas × 10 niveles); Q1: bot "buscar texto" < 60 % desde L3; evaluador de primera coincidencia con deny implícito; Q3 ≤ 1 repetido/12; determinismo por semilla; repasos (`Rounds.due`) siguen reconstruyendo el mismo reto. |
| 2 | **Turno anti-DDoS** + *formato de partida por juego* (`GameFormat`: unidades por partida, unidad de `Adaptive`, resumen propio sin panel intermedio) | M | Regla nueva sellada en la oleada 3 de cada partida (prueba); `tier` máximo alcanzable en una partida (Q8); confeti con el ancho de la cinta (prueba de coordenadas); pulso: un `TAP` de pulso por beat con deriva < 5 ms/60 s (`DdosBeat`); Q6 2–5 min; `Adaptive` por paquetes (≥ 85 de 100); misión "Combo ×3" sigue alcanzable; con animaciones off, mismo log para el mismo guion (Q10). |
| 3 | **Vigía** (4 tramos, densidad garantizada, señuelo desde L2) | S | 100 % de tramos con `targets` ataques (1 200 semillas/nivel); Q1 bot "una línea" < 60 % desde L3; 0 disparos de ruido (se mantiene); Q6; congelaciones por partida coherentes con 4 tramos. |
| 4 | **Parejas** (3 tableros, estrellas, objetivo calibrado, sin retraso de doble toque) | S | Bot memoria 70 %: aprueba 75–85 % por tablero; bot 85 %: P(subir) ≥ 50 %; bot 50 %: no sube; ninguna entrada en > 2 de 3 tableros; tema no cambia el tablero (se mantiene); toque → volteo sin espera (prueba de UI). |
| 5 | **Anzuelo** (generador de cebos, 50/50, ronda bandeja L6+) | M | Q1 "siempre trampa" 45–55 %; Q4 ≥ 70 % nuevos en 10 partidas; `mark` siempre subcadena exacta del texto; cebos seguros nunca contienen un truco del catálogo (prueba con el detector de trucos); álbum de los 35 originales intacto. |
| 6 | **Cazador de logs** (forense en reposo con filtros) | M | Generador verificado por `Vigil.triggers` (0 ruido sospechoso, todas las sospechosas disparan); Q5 posición; Q3; el texto de "por qué" de cada pista llega al álbum con su `key`; sin desplazamiento ni bucle de frames. |
| 7 | **Bits** (pista graduada, ráfaga) | S | Q1 bot "subir/bajar hasta coincidir" < 60 % en L4+; ráfaga determinista; etiquetas ≥ 14 sp. |
| 8 | **Subnet Sprint** (pistas graduadas, formato "¿cabe?") | S | Q1 bot "arrastrar hasta que cambie la lectura" < 60 % en L4+; Q3 ≤ 1 repetido; explicación con la cuenta 2^h − 2 correcta (prueba). |
| 9 | **La maleta** (3 cadenas, señuelos crecientes, empates explicados) | M | Q6 ≤ 5 min en L9 (suma de constantes); ≥ 3 señuelos en bandeja en toda cadena ≥ 5; orden LAYER con empates documentado y probado; récord/fantasma/misión "cadena de N" siguen funcionando. |
| 10 | **Ruta de paquetes** (6 routers en L1–2, "siguiente salto") | S | Variedad L1–2 ≥ 900/1200; "siguiente salto" con respuesta única (Dijkstra); 0 mapas no planos o desconectados (se mantiene). |
| 11 | **Consulta Segura con fichas** (V2 Fase 5) | L | Q1 bot "buscar `?`" < 60 %; cada plantilla tiene una única construcción segura (prueba que enumera fichas); ejecución simulada con los 12 ataques del banco; claves del álbum intactas. |
| 12 | Inicio: animaciones leídas en draw (`LiteApp.kt:118`, `ProgressViews.kt:141`) | S | Recomposiciones de `HeroCard` constantes durante 2 s con animaciones on (prueba de UI). |

**Pendientes V2, según encajan en la prioridad:**
- *Firewall con bloques* → integrado en el paso 1 (Puerto Seguro L8–10); no se crea juego aparte.
- *Consulta Segura con fichas* → paso 11.
- *Logs desplazándose* → **descartado** (clon de Vigía); se sustituye por el paso 6.
- *Zona de reto global* → la pastilla ya existe por tablero; se unifica dentro del `GameFormat` del paso 2 (texto de
  nivel/objetivo/ajuste), sin trabajo aparte.
- *Detective de incidentes* y *Cables y dispositivos* → después del paso 11 y solo si los 11 juegos cumplen Q1–Q14;
  Detective puede reutilizar el generador forense del paso 6.

Fuentes de las cifras: simulación en `scratchpad/sim/` (fuera del repositorio; no forma parte del proyecto).

---

## Principios rectores (añadidos por el usuario; mandan sobre todo lo anterior)

1. **Las mejoras deben aumentar realmente la diversión.** Ningún cambio se da por bueno solo porque pase pruebas o se vea mejor. Si tras jugarlo no es más divertido que antes, se revierte o se replantea.
2. **Las pruebas técnicas no sustituyen la prueba humana.** Los bots y las pruebas automáticas comprueban que el juego es correcto y justo; solo una persona jugando puede decir si es divertido.
3. **Cada juego conserva su identidad y tiene personalidad propia.** La identidad visual común no puede convertir los juegos en variantes de uno solo. La "declaración de identidad" de cada juego (Fase 3) es intocable salvo aprobación expresa.

### Cómo se aplican en cada paso del plan

Cada juego pasa por **dos puertas**, en este orden:

- **Puerta técnica (automática):** las pruebas de aceptación del paso (mecánica correcta, puntuación real, sin niveles imposibles, reproducible por semilla, rendimiento estable).
- **Puerta humana (el usuario, en su teléfono):** juega al menos 3 partidas de la versión nueva y responde:
  1. ¿Es más divertido que antes? (sí / igual / no)
  2. ¿Jugarías otra partida ahora mismo? (sí / no)
  3. ¿Qué momento te gustó más y cuál te aburrió o frustró?
  4. ¿Sigue sintiéndose como "ese" juego, con su personalidad?

**Regla:** un paso solo se cierra con las dos puertas pasadas. Si la puerta humana da "igual" o "no", se ajusta ese juego antes de pasar al siguiente; no se acumulan juegos sin validar. El veredicto humano prevalece sobre cualquier métrica.

En cada entrega se incluye además una ficha corta: **qué cambió, por qué debería ser más divertido, y qué de su identidad no se tocó**.
