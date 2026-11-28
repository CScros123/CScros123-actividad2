# Diseño de juegos v2

Documento de diseño, no de implementación. No cambia código. Referencia de calidad: los juegos de
**Redes y seguridad** que ya existen (Bits, Subnet Sprint, Puerto Seguro, Anzuelo; `lab/.../PilotViews.kt`,
`lab-core/.../Pilot.kt`, `NetSec.kt`). Infraestructura que se reutiliza tal cual:

- `Particles` / `CelebrationBurst` (`Particles.kt`): chispas sin asignación por frame salvo en `burst`.
- `FeedbackCue` (`Experience.kt`): `TAP, CORRECT, RETRY, COMPLETE, LEVEL_UP, UNLOCK` con sonido y háptica.
- `LocalMotionEnabled`: con "Animaciones" desactivado no hay bucle de frames, partículas ni sacudidas.
- `BrainViewModel`: flujo de 12 rondas, pausa/reanudar, `history` por ronda, `Adaptive` niveles 1–10
  (sube con 2 bloques ≥ 85 %, baja con 2 bloques < 60 %).
- Lote C (`docs/EXAMEN-JUEGOS.md`): cofre determinista (máx. 3/día), 3 misiones diarias, álbum con `key`
  estable, "Otra vez" y fantasma del récord. Un cofre nunca cambia minutos ni bloqueos de Foco.

Se eliminan: **Destellos**, **Última huella**, **Transforma**, **Cálculo**, **Ruta espacial**.

---

## 0. Menú final

```
🛡️ Redes 6     Bits · Subnet Sprint · Puerto Seguro · Anzuelo · Consulta Segura · Cazador de logs
🧠 Mente 3     La maleta del técnico · Vigía de alertas · Ruta de paquetes
🕵️ Historia 1  Detective de incidentes
🔧 Taller 2    Cables y dispositivos · Firewall con bloques
🎮 Arcade 2    Turno anti-DDoS · Parejas de incidentes
```

Ids propuestos (estables, para álbum, misiones y récords): `suitcase`, `vigil`, `route` (reutiliza el
historial de `orbit` mediante migración de id), `ddos` (sustituye a Pulso), `pairs` (se conserva el id),
`detective`, `cabling`, `acl`. Nuevas áreas en `Area`: `STORY("Historia")`, `WORKSHOP("Taller")`,
`ARCADE("Arcade")`; `MEMORY` pasa a mostrarse como "Mente".

---

## 1. Sistema de juice compartido

Un único archivo `lab/.../Juice.kt` con tokens; ningún juego define duraciones o curvas propias fuera de él.

### 1.1 Duraciones (ms)

| Token | ms | Uso |
|---|---|---|
| `Tick` | 90 | presión de botón, escala 1 → 0,96 → 1 |
| `Quick` | 140 | cambio de color, selección |
| `Move` | 240 | desplazar una ficha o tarjeta |
| `Reveal` | 420 | voltear carta, mostrar solución |
| `Settle` | 650 | barrido de corrección (como Puerto Seguro) |
| `Hold` | 900 | pausa dramática antes del resultado |
| `Celebrate` | 1400 | ráfaga de cofre, récord, subida de nivel |

### 1.2 Curvas

| Token | Definición | Uso |
|---|---|---|
| `EaseOut` | `FastOutSlowInEasing` | entradas, barridos |
| `EaseIn` | `FastOutLinearInEasing` | salidas, descartes |
| `Snappy` | `spring(dampingRatio=.82f, stiffness=260f)` | fichas que se asientan |
| `Bouncy` | `spring(dampingRatio=.5f, stiffness=500f)` | interruptores, aciertos |
| `Linear` | `LinearEasing` | flujo continuo de paquetes |
| `Overshoot` | `CubicBezierEasing(.34f, 1.56f, .64f, 1f)` | aparición de medallas |

### 1.3 Sacudida de pantalla

`ShakeSmall` = 6 dp, 3 oscilaciones, 180 ms, amortiguación exponencial (error leve).
`ShakeMedium` = 12 dp, 4 osc., 260 ms (paquete malicioso que pasa, alerta perdida).
`ShakeBig` = 20 dp, 5 osc., 360 ms (caída del servidor, fin de oleada fallida). Solo en `graphicsLayer.translationX`,
nunca en layout. Con animaciones off: sin sacudida; se sustituye por un borde rojo fijo 2 dp durante 400 ms.

### 1.4 Presets de partículas (sobre `Particles`)

| Preset | count | speed | gravedad | Color |
|---|---|---|---|---|
| `Spark` | 10 | 260 | 0 | acento del juego |
| `Hit` | 18 | 420 | 0 | `JuiceGood #1EDB90` |
| `Shatter` | 14 | 360 | 1200 | `JuiceBad #FF5470` |
| `Confetti` | 5×14 | 520 | 900 | `CelebrationColors` |
| `Trail` | 1 por 16 ms | 40 | 0 | acento, vida 0,35 s |

Regla de rendimiento: `Particles` pasa a usar un pool fijo de 256 `Spark` reciclados (array + índice vivo),
para que `burst` tampoco asigne. Límite duro: 256 chispas vivas.

### 1.5 Paleta de sonido (archivos cortos locales, < 120 ms salvo indicación)

| Cue | Sonido | Háptica |
|---|---|---|
| `TAP` | clic seco | `EFFECT_CLICK` |
| `CORRECT` | dos notas ascendentes (do–mi) | doble pulso suave (existente) |
| `RETRY` | nota grave corta | zumbido 55 ms |
| `COMPLETE` | arpegio 3 notas, 400 ms | patrón existente |
| `LEVEL_UP` | arpegio 4 notas + brillo | patrón existente |
| `UNLOCK` | campanilla | patrón existente |
| `COMBO` (nuevo) | `CORRECT` subido +1 semitono por paso (máx. +7) | `EFFECT_TICK` |
| `ALARM` (nuevo) | dos pitidos alternos, 300 ms | 3 × 30 ms |
| `WHOOSH` (nuevo) | ruido filtrado 180 ms | ninguna |

`COMBO` se implementa con `SoundPool.play(rate=2^(n/12))`, sin archivos nuevos por paso.
Respeta el modo silencio/No molestar como ya hace `ExperienceFeedback`.

### 1.6 Reglas comunes

- Toda animación lee `LocalMotionEnabled`; off ⇒ `snap()`, sin `withFrameNanos`, sin partículas.
- Un solo `withFrameNanos` por pantalla; el estado del mundo vive en arrays primitivos (`FloatArray`,
  `IntArray`) dentro de una clase recordada; el Canvas lee un `frame` `Long` para invalidar.
- Nada de `Path()`, `listOf`, `String.format` ni `TextLayout` dentro de `draw`; textos medidos una vez con
  `rememberTextMeasurer` y cacheados por contenido.
- Objetivo 120 fps en S23 Ultra (presupuesto 8,3 ms), mínimo aceptable 60 fps en gama media.
- Tipografía: `FontFamily.Monospace` para IPs, puertos, comandos y logs; Material `titleLarge`/`bodyLarge`
  para el resto. Mínimo 16 sp en juego, 14 sp en etiquetas.
- Paleta base del área Redes: fondo `#161B26`, acento `NetAmber #FFB300`, tinta `#2B1D00`; bien
  `#1EDB90`, mal `#FF5470`, info `#7CF5FF`. Cada juego añade un acento propio (ver su sección).

### 1.7 Zona de reto global (concepto, se completa en Fase 5)

Cada juego muestra arriba una **pastilla de reto**: `Nivel 6 · objetivo 85 % · llevas 9/10`. Al tocarla se
explica la regla: "Si aciertas 85 % en dos partidas seguidas, subes; si bajas de 60 % dos veces, bajas".
Dentro de la partida, cada juego puede ajustar micro-dificultad (velocidad, distractores) y **lo dice**:
"⚡ +10 % velocidad porque llevas 5 seguidas". Nunca hay ajuste oculto.

---

## 2. La maleta del técnico (🧠 Mente) — `suitcase`

**Fantasía/tema.** Vas a una visita técnica. "Me voy de instalación y en mi maleta llevo…" y cada ronda
añades un objeto: un cable UTP Cat6, `ping`, el puerto 443, un switch… La maleta se llena y no puedes
abrirla para mirar.

**Qué enseña.**
- Memoria de trabajo serial (cadena acumulativa de 3 a 14 elementos).
- Vocabulario de redes asociado: cable↔uso, comando↔propósito, puerto↔servicio, dispositivo↔capa OSI.
- Variante avanzada: orden de saltos de un `traceroute` (gateway → ISP → IXP → destino) y su latencia creciente.

**Bucle principal (30 s).** Aparece el objeto nuevo con su icono y una línea ("`ssh` · 22 · acceso remoto
cifrado"), 1,5 s. La maleta se cierra. Tocas en orden todos los objetos de la cadena desde una bandeja de
8–12 fichas (las de la cadena + señuelos). Al acertar la cadena entera se añade otro objeto.

**Controles y gestos.** Tocar ficha = meterla en la maleta (vuela con `Move` + `EaseIn`). Deslizar hacia
abajo la última ficha = deshacer una vez por cadena. Mantener pulsado = ver la descripción (no cuenta tiempo).

**Progresión 1–10.**

| Nivel | Cambio | Objetivo |
|---|---|---|
| 1–2 | 1 categoría (cables), bandeja de 6, sin señuelos parecidos | cadena 5 |
| 3–4 | 2 categorías mezcladas, bandeja de 8 | cadena 7 |
| 5–6 | señuelos parecidos (Cat5e vs Cat6, 22 vs 23) | cadena 9 |
| 7–8 | **regla de orden**: "dispositivos ordenados por capa OSI" en lugar de orden de llegada en 1 de cada 4 rondas | cadena 11 |
| 9–10 | variante `traceroute`: saltos con IP y ms; debes recordar el orden y detectar el salto con `* * *` | cadena 13 |

Ronda del flujo de 12 = una cadena; se puntúa la longitud alcanzada contra el objetivo del nivel.

**Reglas de error.** Primer fallo en una cadena: la ficha rebota, se muestra la correcta 1 s y **sigues**
(cadena marcada "con ayuda", vale 50 %). Segundo fallo: la cadena termina. Nunca se pierde la partida
entera por un fallo.

**Situaciones que obligan a pensar.**
1. En la bandeja están `22 SSH` y `23 Telnet`; tu cadena tenía "acceso remoto **cifrado**".
2. Regla de nivel 7: llevas `router, switch, hub`; al añadir `firewall L7` debes reordenar mentalmente por capa.
3. Traceroute: el salto 4 tiene 180 ms y el 5 tiene 40 ms; recuerdas que el 4 era el enlace transatlántico.

**Zona de reto visible.** "Objetivo 9 · récord 11". Si fallas 2 cadenas seguidas, el siguiente objeto se
muestra 0,5 s más y lo anuncia: "🧭 Te doy 0,5 s extra". Si haces 3 limpias, la bandeja gana un señuelo: "+1 señuelo".

**Pistas que enseñan.** Tras un fallo: "Truco: agrupa de 3 en 3 — *cable, comando, puerto*". En nivel 7:
"Capa 1 cable/hub, capa 2 switch, capa 3 router, capa 7 proxy".

**Feedback y juice.** Ficha que entra: escala 1 → 0,6 + `Move` hacia la maleta, `Spark` acento al cerrar,
sonido `TAP`; la maleta "respira" (escala 1 → 1,04 → 1 con `Bouncy`). Cadena completa: la maleta se cierra
con `Overshoot` 420 ms, `COMBO` con tono creciente por eslabón, `Hit` sobre la cerradura. Error: ficha
rebota (`Snappy`), `ShakeSmall`, `RETRY`. Cadena récord: `Confetti` + `LEVEL_UP`. Off: solo color y texto.

**Diseño visual.** Acento `#FFB300` sobre cuero oscuro `#2A1E14`; fichas `surface` con borde ámbar.

```
┌──────────────────────────── 1440 ─┐
│ ← Maleta   Nivel 6 · obj 9 · 7/9  │  barra de reto (160 px)
│                                   │
│      ┌─────────────────────┐      │
│      │   ▣ MALETA  (7)     │      │  maleta 1100×900, contador grande
│      │   ● ● ● ● ● ● ○ ○   │      │  eslabones como LEDs
│      └─────────────────────┘      │
│   NUEVO:  [🔌 Cat6 · 1 Gbps]      │  tarjeta del objeto nuevo
│                                   │
│  [22 SSH] [23 Tel] [Cat6] [ping]  │  bandeja 2×4 / 3×4, celdas 300×220
│  [443 ] [switch] [fibra] [dig ]   │
│                    ↶ deshacer     │
└───────────────────────────── 3088 ┘
```

**Pantalla de resultado.** Cadena máxima, cadenas limpias / con ayuda, el objeto en el que más fallas
("Te cuesta 23 vs 22"), fantasma del récord como línea de LEDs grises.

**Integración.** Álbum: cada objeto nuevo visto = carta (`suitcase:port22`). Misión: "Llega a una cadena de 10".
Cofre: al terminar los 12 rondas. Fantasma: longitud de cadena por ronda del récord.

**Riesgos y medición.** Riesgo: frustración por cadenas largas → regla "con ayuda". Medir: % de partidas
terminadas, mediana de "Otra vez" < 10 s tras el resultado, cadena media creciente en 7 días.

**Esfuerzo: M.** Lógica en `lab-core/Suitcase.kt` (banco con `key`, generador por seed, regla de orden).
Vista: `LazyVerticalGrid` no; rejilla fija con `Row`/`Column` y un Canvas para la maleta y LEDs.

---

## 3. Vigía de alertas (🧠 Mente) — `vigil`

**Fantasía/tema.** Turno de noche en el SOC. Un monitor desliza alertas; casi todas son ruido normal.
Tú eres el último filtro antes de que alguien entre.

**Qué enseña.**
- Atención sostenida (tarea de vigilancia tipo Mackworth): objetivos raros (8–15 %) entre ruido.
- Patrones reales: fuerza bruta (≥ 5 `Failed password` misma IP en < 60 s), inicio de sesión a horas
  raras (03:12 de un usuario de oficina), escaneo de puertos (misma IP, puertos consecutivos), viaje imposible
  (Madrid 10:00, Tokio 10:05), exfiltración (pico de bytes salientes).

**Bucle principal (30 s).** Las líneas suben a ritmo constante (1 cada 1,2 s en nivel 1). Tocas la línea
anómala (o el grupo, en patrones de varias líneas). Cada 30 s hay "relevo": resumen breve de aciertos y
fallos. Una ronda del flujo = un tramo de 30 s; 12 tramos = 6 min.

**Controles y gestos.** Tocar línea = marcar. Pulsación larga = congelar el monitor 2 s para leer (máx. 3
por partida, contador visible). Deslizar para retroceder no está permitido: la tensión es que pasa.

**Progresión 1–10.**

| Nivel | Cambio | Objetivo |
|---|---|---|
| 1–2 | 1 patrón (fuerza bruta), 15 % objetivos, 1,2 s/línea | ≥ 80 % detectados, ≤ 2 falsas alarmas |
| 3–4 | + horas raras, 12 %, 1,0 s | ≥ 80 %, ≤ 2 |
| 5–6 | + escaneo de puertos (requiere ver 3 líneas), 10 %, 0,9 s | ≥ 85 %, ≤ 2 |
| 7–8 | + viaje imposible, señuelos (5 fallos de IPs distintas = no es ataque), 9 % | ≥ 85 %, ≤ 1 |
| 9–10 | + exfiltración, 2 columnas (auth y firewall), 8 %, 0,8 s | ≥ 90 %, ≤ 1 |

**Penalización de lapsos.** Alerta real que sale por arriba sin marcar = "lapso": `ShakeMedium`, la línea
se queda fija en rojo en un "muro de la vergüenza" lateral, −1 escudo (3 escudos por tramo; 0 escudos =
tramo fallado, pero sigue la partida). Falsa alarma = −½ escudo y mensaje de por qué era normal.

**Tensión creciente.** Dentro de cada tramo la velocidad sube 3 % cada 10 s; el fondo pasa de azul noche
a ámbar en los últimos 5 s; latido grave cada 2 s en los últimos 10 s (`ALARM` muy suave, desactivable).

**Situaciones que obligan a pensar.**
1. Cinco `Failed password for admin` en 40 s, pero desde 5 IPs distintas de la red interna: ruido de un script mal configurado, no fuerza bruta dirigida (nivel 7+).
2. `login ok user=marta 03:12 src=10.0.4.7` — marta trabaja de 9 a 17, pero hoy es día de guardia (aparece en la cabecera "Guardia: marta"). No es anomalía.
3. `203.0.113.9 → :21, :22, :23, :25` en 4 líneas no consecutivas, intercaladas con tráfico normal.

**Zona de reto visible.** Cabecera: "Nivel 5 · detecta 85 % · falsas ≤ 2 · 🛡🛡🛡". Ajuste en partida
anunciado: si 2 tramos perfectos, "⚡ monitor +10 %"; si 2 tramos fallados, "🧭 monitor −10 %".

**Pistas que enseñan.** Tras un lapso, tarjeta de 3 s: "Fuerza bruta = **misma IP**, muchos fallos,
poco tiempo. Mira la columna `src`". Tras una falsa alarma: "Distintas IPs internas ≈ error de configuración".

**Feedback y juice.** Línea marcada correcta: se ilumina verde, `Hit` desde el borde izquierdo, la línea se
"archiva" deslizándose a la derecha 240 ms `EaseIn`, `CORRECT`. Falsa alarma: parpadeo ámbar 2×90 ms,
`RETRY`. Lapso: `ShakeMedium`, `ALARM`, línea roja se pega al muro lateral con `Snappy`. Fin de tramo
perfecto: `Spark` en los 3 escudos + `COMPLETE`. Scroll: desplazamiento sub-píxel continuo con `Linear`
(nunca saltos de línea). Off: el monitor avanza por pasos discretos de una línea, sin parpadeos.

**Diseño visual.** Fondo `#0E1420`, texto `#C8D3E6` monoespaciado, objetivo resaltado solo al tocar;
acento `#7CF5FF`. Columnas: hora · src · evento.

```
┌────────────────────────────────────┐
│ ← Vigía  Nv5 · 85% · falsas≤2 🛡🛡🛡 │
│ Guardia: marta   Tramo 4/12  ▓▓▓░  │
├──────────────────────────────┬─────┤
│ 02:58:11 10.0.2.4  login ok  │ ▌RE │ muro de lapsos (160 px)
│ 02:58:12 10.0.3.9  dns query │ ▌   │
│ 02:58:14 203.0.113.9 :22 syn │     │ monitor 1240 px, ~22 líneas
│ 02:58:15 10.0.2.4  GET /api  │     │ líneas de 110 px, 18 sp mono
│   ↑ suben                    │     │
│ ...                          │     │
├──────────────────────────────┴─────┤
│ ❄ congelar (3)                     │
└────────────────────────────────────┘
```

**Pantalla de resultado.** Detecciones / lapsos / falsas, gráfico de aciertos por tramo (se ve la fatiga),
patrón más fallado, fantasma: línea del récord por tramo.

**Integración.** Álbum: cada patrón detectado por primera vez (`vigil:bruteforce`). Misión: "Un tramo sin
lapsos en nivel ≥ 5". Cofre normal. Fantasma: precisión por tramo.

**Riesgos y medición.** Riesgo: aburrimiento (es lo que mide) → tensión creciente y relevos cortos.
Medir: abandono antes del tramo 6 (< 15 %), d′ (sensibilidad) creciente entre sesiones.

**Esfuerzo: M.** `lab-core/Vigil.kt` genera el flujo completo del tramo por seed (líneas como índices a
plantillas + parámetros en `IntArray`). Vista: Canvas único que dibuja ~24 líneas con `TextMeasurer` cacheado
por plantilla; el toque se resuelve por `y` + offset de scroll.

---

## 4. Ruta de paquetes (🧠 Mente; rediseño de Órbitas) — `route`

**Fantasía/tema.** Mapa estilo Mini Metro: routers como estaciones, enlaces como líneas de colores. Un
paquete recorre la red; después tú eres el protocolo de enrutamiento y debes reproducir su camino.

**Qué enseña.**
- Memoria visoespacial de secuencias (sustituye a Órbitas/Ecos).
- Nociones de enrutamiento: salto, enlace caído, ruta alternativa, TTL que decrementa y descarta.

**Bucle principal (30 s).** El paquete (punto brillante con estela) viaja A → … → B, 600 ms por salto.
Desaparece. Tocas los routers en orden (o arrastras un trazo continuo). Al soltar, el paquete recorre tu
camino; si coincide, llega con fiesta.

**Controles y gestos.** Tocar routers en orden, o arrastrar el dedo por ellos (snap a 80 px de radio).
Tocar el último elegido = deshacer.

**Progresión 1–10.**

| Nivel | Cambio | Objetivo |
|---|---|---|
| 1–2 | 5 routers, ruta de 3 saltos | 10/12 |
| 3–4 | 7 routers, 4–5 saltos | 10/12 |
| 5–6 | 9 routers, **enlace caído** (línea roja punteada) durante la demostración: debes recordar el desvío | 10/12 |
| 7–8 | **pregunta de ruta alternativa**: "el enlace R3–R5 cae; ¿qué ruta tomaría?" (mínimo saltos) | 10/12 |
| 9–10 | **TTL**: el paquete sale con TTL=4; si tu ruta tiene más saltos debes marcar dónde muere (ICMP Time Exceeded) | 11/12 |

**Situaciones que obligan a pensar.**
1. La demostración volvió por R2 dos veces (bucle): debes repetir el bucle, no "arreglarlo".
2. Nivel 7: dos rutas de 3 saltos; la pista dice "coste del enlace verde = 2": eliges la de menor coste.
3. Nivel 9: TTL=3 y destino a 4 saltos; la respuesta correcta es tocar el tercer router y "✖ descartado".

**Zona de reto visible.** "Nivel 6 · 9 routers · 5 saltos · 7/10". Tres aciertos seguidos: "+1 salto";
dos fallos: "demostración 20 % más lenta".

**Pistas que enseñan.** Tras fallo: se repite la demostración con números en cada router. En nivel 9:
"Cada router resta 1 al TTL; con 0 el paquete se descarta y se avisa al origen".

**Feedback y juice.** Paquete: núcleo 14 px + `Trail` acento, avanza por la línea con `Linear` y frena en
cada router con `EaseOut` 80 ms; el router "pulsa" (anillo 1 → 1,6, alfa 1 → 0, 300 ms). Tu trazo: línea
que se dibuja con `drawLine` progresivo. Acierto: el destino se expande, `Hit`, `COMBO`. Error: el paquete
se desvía al router equivocado, se apaga con `Shatter` y `ShakeSmall`, la ruta correcta aparece en blanco
punteado 900 ms. Off: los routers se numeran en la demostración, sin movimiento.

**Diseño visual.** Fondo papel oscuro `#12161F`, líneas de metro en `#FFB300`, `#7CF5FF`, `#FF6B9A`,
`#B69CFF`; routers círculos blancos con borde 6 px. Disposición en rejilla hexagonal suave para evitar cruces.

```
┌────────────────────────────────────┐
│ ← Ruta   Nv6 · 5 saltos · 7/10     │
│   TTL 4 ▮▮▮▮                       │
│      (R1)━━━━(R2)                  │
│       ┃  ╲     ┃                   │  mapa 1300×1900
│      (R4)  (R3)┅┅(R5)  ┅ caído     │
│       ┃      ┃    ┃                │
│      (A)━━━(R6)━━(B)               │
│                                    │
│   Dibuja la ruta de A a B          │
│   [↶ deshacer]      [✓ enviar]     │
└────────────────────────────────────┘
```

**Pantalla de resultado.** Rutas exactas, saltos máximos recordados, tipo de fallo (orden, desvío, TTL).

**Integración.** Álbum: "Time Exceeded", "Ruta de menor coste". Misión: "5 rutas seguidas en nivel ≥ 5".
Fantasma: aciertos por ronda. Migración: el progreso de `orbit` se copia a `route` (nivel −2, mínimo 1).

**Riesgos y medición.** Riesgo: mapas ilegibles → generador que rechaza grafos con cruces. Medir: tiempo
de respuesta por salto, errores de "orden" vs "desvío".

**Esfuerzo: M.** `lab-core/Route.kt`: grafo pequeño (`IntArray` de aristas), BFS para ruta corta y
alternativa, generador por seed con prueba de no-cruces. Vista: Canvas con posiciones precalculadas.

---

## 5. Turno anti-DDoS (🎮 Arcade; rediseño de Pulso) — `ddos`

**Fantasía/tema.** Papers, Please con ritmo: eres el filtro frente al servidor. Los paquetes llegan por una
cinta al compás; cada oleada, el jefe de turno te da una regla nueva.

**Qué enseña.**
- Inhibición y cambio de regla (lo que hacía Pulso, con contenido).
- Mitigación real: bloquear puertos inseguros (23 Telnet), rate-limit por IP, SYN flood, listas de bloqueo,
  geobloqueo, firma de payload, excepción para el monitor interno.

**Bucle principal (30 s).** Oleada de ~20 paquetes a 90–140 BPM. Cada paquete es una tarjeta con
`IP · puerto · flags · tamaño`. Toca para bloquear; no toques para dejar pasar. Un indicador de carga del
servidor sube con cada malicioso que pasa y baja con el tiempo.

**Controles y gestos.** Tocar tarjeta = bloquear (cae a la papelera). Deslizar hacia arriba una IP en la
cabecera = añadirla a la lista negra (nivel 5+), lo que bloquea automáticamente sus siguientes paquetes.

**Progresión 1–10.**

| Nivel | Regla de la oleada | BPM | Objetivo |
|---|---|---|---|
| 1–2 | Bloquea puerto 23 | 90 | ≥ 85 % |
| 3–4 | + bloquea SYN sin ACK posterior (SYN flood) | 100 | ≥ 85 % |
| 5–6 | + rate-limit: más de 3 de la misma IP en la ventana → bloquea el 4.º (lista negra disponible) | 110 | ≥ 85 % |
| 7–8 | reglas que **cambian a mitad** de oleada ("ahora 8080 es legítimo: despliegue") + excepción 10.0.0.5 | 125 | ≥ 88 % |
| 9–10 | 2 carriles (web y DNS), amplificación DNS (respuestas grandes no solicitadas) | 140 | ≥ 90 % |

**Combo y oleadas.** Combo ×1 → ×2 (8 seguidas) → ×3 (16) → ×4 (24). Se rompe al fallar. Una ronda del
flujo = una oleada; 12 oleadas. Entre oleadas: "parte del jefe" de 2 s con la regla nueva sellada.

**Situaciones que obligan a pensar.**
1. Paquete al 23 desde 10.0.0.5 cuando la excepción dice "el monitor interno siempre pasa": prioridad de reglas.
2. Tercer paquete de 198.51.100.7 en la ventana: todavía pasa; el cuarto no.
3. Mitad de oleada: "despliegue: 8080 abierto"; los 8080 anteriores bloqueados estaban bien, los siguientes no.

**Zona de reto visible.** "Nv7 · 125 BPM · ≥ 88 %". Ajuste anunciado: 3 oleadas perfectas → "+5 BPM";
servidor caído → "−10 BPM la próxima".

**Pistas que enseñan.** Tras fallo, el paquete se congela 1 s con la regla que lo atrapaba resaltada.
Ficha de regla: "Telnet (23) envía credenciales en claro: bloquéalo y usa SSH (22)".

**Feedback y juice.** Llegada al compás: cada tarjeta entra con `EaseOut` sincronizado al beat (el beat se
calcula del reloj de frames, no de `delay`). Bloqueo correcto: tarjeta se parte en dos mitades que caen
con gravedad 1800 px/s², `Hit` rojo-ámbar, `COMBO`. Legítimo bloqueado: tarjeta vuelve con `Snappy`,
`RETRY`, combo a 0. Malicioso que pasa: vuela al servidor, `ShakeMedium`, barra de carga pulsa roja.
Servidor caído (carga 100 %): `ShakeBig`, pantalla desatura 600 ms, `ALARM`. Combo ×4: borde de la cinta
brilla con gradiente animado. Off: tarjetas aparecen y desaparecen sin movimiento; carga como número.

**Diseño visual.** Fondo `#140F1F`, cinta `#1F1830`, acento magenta `#FF6B9A` + ámbar; servidor arriba.

```
┌────────────────────────────────────┐
│ Nv7 · 125BPM · ≥88%   COMBO ×3     │
│  [ SERVIDOR ]  carga ▓▓▓░░░░ 38%   │
│  Regla: bloquea :23 · >3/IP · 8080✓│  sello del jefe
├────────────────────────────────────┤
│   ┌──────────────────────────┐     │
│   │198.51.100.7 :443 ACK 1.2k│ ↑   │  cinta, tarjetas 1200×200
│   └──────────────────────────┘     │  suben hacia el servidor
│   ┌──────────────────────────┐     │
│   │203.0.113.4  :23  SYN  60 │     │
│   └──────────────────────────┘     │
│  Lista negra: 198.51.100.7 ✕       │
└────────────────────────────────────┘
```

**Pantalla de resultado.** Oleadas superadas, combo máximo, legítimos bloqueados (coste de negocio),
maliciosos que pasaron, regla más fallada.

**Integración.** Álbum: cada regla (`ddos:synflood`). Misión: "Combo ×3 en una oleada". Relámpago puede
usar una oleada suelta. Fantasma: carga del servidor del récord como línea tenue.

**Riesgos y medición.** Riesgo: leer 4 campos a 140 BPM es imposible → solo el campo relevante va en
grande, los otros en 14 sp. Medir: precisión por campo, % de oleadas con caída.

**Esfuerzo: L.** `lab-core/Ddos.kt`: motor de reglas con prioridad y prueba de cada regla. Vista: cinta en
Canvas con pool de 12 tarjetas, beat desde `withFrameNanos`.

---

## 6. Parejas de incidentes (🎮 Arcade; rediseño de Parejas) — `pairs`

**Fantasía/tema.** Un tablero de cartas de incidente boca abajo. Emparejas ataque↔defensa,
alerta↔causa, puerto↔servicio. Temas desbloqueables cambian el dorso y el contenido.

**Qué enseña.** Memoria de localización + asociaciones: SQLi↔consultas parametrizadas, XSS↔escapar salida,
phishing↔MFA, ransomware↔copias offline, `Failed password ×50`↔fuerza bruta, 53↔DNS, 3389↔RDP.

**Bucle principal (30 s).** Volteas dos cartas; si son pareja, se quedan y se encadena racha; si no, se
vuelven. Cada ronda = un tablero (3×4 a 4×6).

**Controles y gestos.** Tocar para voltear. Doble toque en una pareja hecha = ver la lección.

**Progresión 1–10.**

| Nivel | Tablero | Contenido | Objetivo |
|---|---|---|---|
| 1–2 | 3×4 | puerto↔servicio | ≤ 1,6 × pares en intentos |
| 3–4 | 4×4 | + ataque↔defensa | ≤ 1,6 × |
| 5–6 | 4×5 | + alerta↔causa, cartas parecidas (SQLi / XSS) | ≤ 1,7 × |
| 7–8 | 4×6 | tríos ocasionales (ataque, alerta, defensa) | ≤ 1,8 × |
| 9–10 | 4×6 | barajado parcial: tras 4 fallos seguidos 2 cartas cambian de sitio (se anuncia y se anima) | ≤ 1,9 × |

**Temas desbloqueables** (cosméticos del cofre, `Cosmetic.skin`): "Clásico ámbar", "Terminal verde",
"Neón SOC", "Papel de incidente". Solo cambian dorso, paleta y fuente; nunca dificultad.

**Situaciones que obligan a pensar.**
1. Ves "XSS" y luego "Escapar salida HTML"; antes viste "Consultas parametrizadas": ¿cuál es de SQLi?
2. "Puerto 25" y dos candidatos "SMTP" y "SMTPS (465)".
3. Trío en nivel 7: tienes alerta y ataque; la defensa está entre dos cartas que viste.

**Zona de reto visible.** "Nv5 · 20 cartas · ≤ 17 intentos · llevas 9". Racha de 4: "🔥 racha ×2 puntos".

**Pistas que enseñan.** Al formar pareja, lección de una línea bajo el tablero 2 s (desde `NetSec`).
Tras 3 fallos seguidos: la carta vista hace más tiempo parpadea 1 vez ("la viste aquí").

**Feedback y juice.** Volteo 3D: `graphicsLayer.rotationY` 0 → 180 en `Reveal` con `EaseOut`, cambio de
cara a 90°, `cameraDistance = 12 * density`; leve elevación (sombra 2 → 8 dp). Pareja: ambas cartas saltan
(escala 1 → 1,12 → 1, `Bouncy`), se unen con un arco de luz, `Hit`, `COMBO` con tono creciente por racha.
Fallo: vuelven con 300 ms de espera, `ShakeSmall` solo en las dos cartas, `RETRY` suave. Tablero
completo: cartas giran en ola diagonal (retraso 40 ms por celda), `Confetti`, `COMPLETE`. Off: cambio de cara
instantáneo, sin giro.

**Diseño visual.** Por tema; base `#161B26` y dorso ámbar con patrón de circuito dibujado en Canvas.

```
┌────────────────────────────────────┐
│ ← Parejas  Nv5 · ≤17 · 9   🔥×2    │
│  ┌───┐┌───┐┌───┐┌───┐              │
│  │ ▦ ││SQL││ ▦ ││ ▦ │              │  cartas 300×400, 4 columnas
│  └───┘└───┘└───┘└───┘              │
│  ... 5 filas ...                   │
│ ─────────────────────────────────  │
│ SQLi ↔ consultas parametrizadas:   │  lección (260 px)
│ el dato nunca se concatena.        │
└────────────────────────────────────┘
```

**Pantalla de resultado.** Intentos vs óptimo, racha máxima, parejas aprendidas nuevas.

**Integración.** Álbum: cada pareja (`pairs:sqli-param`). Temas en el cofre. Misión: "Racha de 5".
Fantasma: intentos por tablero del récord.

**Riesgos y medición.** Riesgo: es suerte al principio → puntuación sobre intentos *después* de ver ambas
cartas. Medir: tasa de "segundo volteo informado" (la carta ya se había visto).

**Esfuerzo: S–M.** `PairGame.kt` ya existe: se añade banco de pares con `key`, tríos y barajado parcial.
Vista: `graphicsLayer` por carta, sin Canvas global.

---

## 7. Detective de incidentes (🕵️ Historia) — `detective`

**Fantasía/tema.** Return of the Obra Dinn / SQL Murder Mystery en pequeño: casos cortos de una empresa
ficticia ("Panadería Nube S.L."). Algo ha pasado; tienes logs, una tabla de puertos y un mapa de subredes.
Rellenas el tablero de deducción: **quién** (IP/usuario), **cómo** (vector), **cuándo**, **qué falló**.

**Qué enseña.** Razonamiento deductivo y síntesis de todo lo demás: leer logs (Cazador/Vigía), subredes
(Subnet Sprint), puertos (Puerto Seguro), phishing (Anzuelo), inyección (Consulta Segura), rutas (Ruta).

**Bucle principal (30 s).** Abres una pista (log, correo, captura de tabla ARP, regla de firewall), subrayas
la línea relevante (se guarda como "evidencia"), arrastras la evidencia a una casilla del tablero. Cuando
3 de 4 casillas están llenas, puedes "acusar". Al estilo Obra Dinn, el juego confirma **de 2 en 2**
casillas correctas (no dice cuál falla).

**Controles y gestos.** Tocar pestañas de pista; mantener + arrastrar para subrayar texto (snap a línea);
arrastrar evidencia a casilla; pellizcar el tablero para ampliar.

**Progresión 1–10.** No usa 12 rondas: un caso = una partida (3–8 min); `Adaptive` recibe 12 "marcas"
derivadas (casillas y preguntas intermedias) para no romper el flujo.

| Nivel | Caso tipo | Pistas | Casillas |
|---|---|---|---|
| 1–2 | correo de phishing → robo de credenciales | 3 | 3 |
| 3–4 | fuerza bruta SSH desde una IP externa | 4 | 4 |
| 5–6 | SQLi en el formulario web, con IP interna señuelo | 5 | 4 |
| 7–8 | movimiento lateral: el atacante está en otra subred (/26) | 6 | 5 |
| 9–10 | caso doble: un falso positivo encubre el real; reloj desfasado 1 h en un servidor | 7 | 6 |

Objetivo por nivel: resolver con ≤ 2 acusaciones fallidas.

**Situaciones que obligan a pensar.**
1. La IP 10.0.1.70 aparece en el log web; la subred de RR. HH. es 10.0.1.64/26, así que el equipo es de RR. HH., no de TI.
2. El log del servidor B va 1 h adelantado (lo indica su cabecera NTP); el "acceso posterior" en realidad fue anterior.
3. El correo dice `micr0soft-login.com`; la regla de firewall muestra que el 443 saliente a esa IP se permitió a las 09:02.

**Zona de reto visible.** "Caso 5 · 4 casillas · acusaciones 0/2 · pistas abiertas 3/5". Ayuda opcional
anunciada: tras 2 acusaciones fallidas se ofrece "🔎 resalta una pista útil" (cuenta como "con ayuda").

**Pistas que enseñan.** Cada pista tiene un "¿Cómo se lee esto?" plegable (formato de log, notación CIDR).
Al cerrar el caso: cronología animada con la explicación de cada evidencia.

**Feedback y juice.** Subrayado: marcador ámbar que se extiende con `EaseOut` 240 ms. Evidencia arrastrada:
tarjeta con sombra y leve rotación ±3° al levantar (`Bouncy`). Acusar: `Hold` 900 ms con tambor (latido),
luego sello "2 CORRECTAS" que cae con `Overshoot` + `ShakeSmall`. Caso resuelto: los hilos rojos del tablero
se tensan y brillan, `Confetti`, `COMPLETE`; carta del caso al álbum con `UNLOCK`. Off: sellos sin caída.

**Diseño visual.** Corcho oscuro `#1C1712`, tarjetas papel `#EDE3CF` con tinta `#2B1D00`, hilos `#FF5470`.

```
┌────────────────────────────────────┐
│ ← Caso 5: "La tarta que se filtró" │
│ [Correo][Log web][Firewall][Subred]│ pestañas
│ ┌────────────────────────────────┐ │
│ │ 09:02 10.0.1.70 POST /login    │ │ pista (1300×1200), mono 16 sp
│ │ 09:02 ... ' OR 1=1 --  ▔▔▔▔▔▔  │ │ subrayado
│ └────────────────────────────────┘ │
│ ┌──── TABLERO ────────────────────┐│
│ │ QUIÉN [10.0.1.70] CÓMO [ SQLi ] ││ 1300×900
│ │ CUÁNDO[ 09:02  ]  FALLO[  ?   ] ││ hilos entre casillas
│ └─────────────────────────────────┘│
│ Evidencias: ▭ ▭ ▭     [ACUSAR]     │
└────────────────────────────────────┘
```

**Pantalla de resultado.** Cronología del ataque, qué juego practica cada pista ("Repasa Subnet Sprint"),
acusaciones usadas, tiempo.

**Integración.** Álbum: carta por caso resuelto (portada ilustrada en Canvas). Misiones: "Resuelve un caso".
Cofre: 1 por caso. Fantasma: tiempo del récord por caso. Enlaza a otros juegos desde el resultado.

**Riesgos y medición.** Riesgo: casos escritos a mano son caros → plantillas de caso con variables
(IPs, horas, usuarios) por seed, 8 plantillas = 40+ variantes. Riesgo de texto largo en móvil → pistas
de ≤ 8 líneas. Medir: casos empezados vs terminados, uso de "¿Cómo se lee?".

**Esfuerzo: L.** `lab-core/Cases.kt` (plantillas, validador de solución única con prueba), vista con
`Column`s normales + Canvas solo para hilos.

---

## 8. Cables y dispositivos (🔧 Taller) — `cabling`

**Fantasía/tema.** Packet Tracer de bolsillo: una mesa de trabajo con PCs, switches, routers, un servidor y
una consola. Conectas con el cable correcto; luego configuras VLANs y la tabla de rutas. El ping final es la
prueba.

**Qué enseña.**
- Tipo de cable: directo (PC–switch), cruzado (PC–PC, switch–switch sin Auto-MDIX), consola (portátil–puerto
  de consola), fibra (distancias > 100 m), y por qué.
- VLAN: puertos de acceso vs troncal (802.1Q); dos PCs en VLAN distintas no se ven sin router.
- Enrutamiento: next-hop, ruta por defecto, red de destino con prefijo.

**Bucle principal (30 s).** Arrastras desde un puerto a otro; eliges el cable en un selector radial de 4.
Pulsas "▶ ping": el paquete viaja y se ve dónde muere y por qué.

**Controles y gestos.** Arrastrar puerto→puerto; al soltar aparece el selector radial (deslizar hacia el
cable y soltar). Tocar cable = cambiarlo/quitarlo. Tocar puerto de switch = ciclo VLAN 10/20/troncal. Tabla
de rutas: filas con fichas (destino, prefijo, next-hop) elegidas de listas, sin teclado.

**Progresión 1–10.**

| Nivel | Contenido | Objetivo |
|---|---|---|
| 1–2 | 2–3 dispositivos, elegir cable | ping OK en ≤ 1 intento extra |
| 3–4 | + consola y fibra (edificio a 300 m) | ídem |
| 5–6 | VLAN: 4 PCs, 2 VLAN, un troncal | ping dentro de VLAN sí, entre VLAN no (y se pregunta por qué) |
| 7–8 | router-on-a-stick, gateway por PC | ping entre VLAN OK |
| 9–10 | 2 routers, tabla de rutas con next-hop y ruta por defecto | ping extremo a extremo + a "Internet" |

**Situaciones que obligan a pensar.**
1. Dos switches antiguos sin Auto-MDIX: el directo no levanta enlace; necesitas cruzado.
2. PC1 VLAN 10 y PC2 VLAN 20 en el mismo switch: el ping falla aunque los cables estén bien.
3. R1 tiene ruta a 192.168.2.0/24 pero R2 no tiene ruta de vuelta: el ping llega pero la respuesta muere.

**Zona de reto visible.** "Nv7 · intentos de ping 1/3 · óptimo 6 conexiones". Tres pings fallidos ⇒ se
ofrece el "modo depuración" (anunciado): cada salto muestra su tabla.

**Pistas que enseñan.** Cuando el ping muere, globo en ese punto: "Enlace caído: mismo tipo de equipo con
cable directo"; "Sin ruta a 192.168.2.0/24 en R2"; "La respuesta no sabe volver".

**Feedback y juice.** Cable: se dibuja como curva Bézier que sigue al dedo con retraso `Snappy` (sensación de
cable físico, cuelga con "gravedad" de 6 % del largo). Al conectar: LED del puerto pasa de ámbar parpadeante
(link negociando, 600 ms) a verde, `TAP` + `CORRECT` suave; cable incorrecto: LED rojo, chispa `Shatter`
pequeña (6), `RETRY`. Ping: paquete con `Trail` recorre cables; éxito = eco de vuelta + `Hit` en origen +
`COMPLETE`; fallo = paquete se apaga con `Shatter` en el punto exacto + `ShakeSmall`. Off: LEDs fijos, ping
como lista de saltos.

**Diseño visual.** Mesa `#151A22`, dispositivos como iconos vectoriales simples (Canvas), cables: directo
`#7CF5FF`, cruzado `#FF6B9A`, consola `#B69CFF` (plano), fibra `#FFB300` (con brillo).

```
┌────────────────────────────────────┐
│ ← Cables  Nv7 · ping 1/3 · óptimo 6│
│                                    │
│   [PC1]●──────●[SW1]●══════●[R1]   │ mesa 1340×1700
│   v10        ●  ●  ●   troncal     │ puertos 48 px con LED
│   [PC2]●─────┘  │                  │
│   v20           └──●[PC3]          │
│                                    │
│ ┌ Tabla R1 ──────────────────────┐ │
│ │ 192.168.2.0 /24 → 10.0.0.2  ✕  │ │ fichas
│ │ 0.0.0.0     /0  → 203.0.113.1  │ │
│ └────────────────────────────────┘ │
│        [ ▶ ping PC1 → PC3 ]        │
└────────────────────────────────────┘
```

**Pantalla de resultado.** Conexiones vs óptimo, pings usados, errores por concepto (cable, VLAN, ruta).

**Integración.** Álbum: cada tipo de cable y "Router-on-a-stick". Misión: "Ping a la primera en nivel ≥ 5".
Fantasma: número de intentos del récord.

**Riesgos y medición.** Riesgo: arrastres imprecisos en pantalla curva → puertos con hitbox de 96 px y
zona muerta de 40 px en los bordes. Medir: cancelaciones de arrastre, errores de "cable" que bajan en 3 sesiones.

**Esfuerzo: L.** `lab-core/Lab.kt`: simulador mínimo (enlace sube si tipo de cable válido; switching por
VLAN; routing por prefijo más largo; ping ida/vuelta) con pruebas. Vista Canvas con `Path` reutilizado
(`path.reset()`).

---

## 9. Firewall con bloques (🔧 Taller) — `acl`

**Fantasía/tema.** Human Resource Machine / Zachtronics: construyes una ACL con bloques apilables; luego
pasa un lote de tráfico de prueba por ella. Cada regla tiene coste; el reto es pasar todas las pruebas con
el menor número de bloques y de pasos de evaluación.

**Qué enseña.**
- Las ACL se evalúan **de arriba abajo, gana la primera coincidencia**, con *deny* implícito al final.
- Campos: origen/destino con prefijo, protocolo, puerto, dirección, estado (`established`).
- Principio de mínimo privilegio y orden específico → general.

**Bucle principal (30 s).** Arrastras bloques (`PERMITIR`, `DENEGAR`, con fichas de campo) a la pila.
Pulsas "▶ probar": 10–20 paquetes caen por la pila; cada uno se detiene en la regla que lo atrapa y va a
"pasa" o "bloqueado". Comparas con lo esperado.

**Controles y gestos.** Arrastrar bloque de la paleta a la pila; arrastrar dentro de la pila para reordenar;
tocar ficha para ciclar valores (puerto: 22/80/443/53/any). Deslizar bloque fuera = borrar.
Botón "paso a paso" para ver la evaluación de un paquete.

**Progresión 1–10.**

| Nivel | Requisito | Mejor conocido |
|---|---|---|
| 1–2 | "Solo web (80, 443) entrante" | 2 bloques |
| 3–4 | + SSH solo desde 10.0.5.0/24 | 3 |
| 5–6 | + DNS saliente; orden importa (una regla general mal colocada tapa una específica) | 4 |
| 7–8 | `established` para respuestas; bloquear una IP concreta antes del permiso general | 5 |
| 9–10 | dos interfaces (entrada/salida), restricciones de bloques máximos | 6–7 |

Puntuación: pruebas superadas (debe ser 100 %), bloques usados vs mejor, pasos de evaluación totales vs mejor
(histograma estilo Zachtronics con tu marca).

**Situaciones que obligan a pensar.**
1. `PERMITIR any → 443` arriba hace que el `DENEGAR 198.51.100.7` de abajo no sirva nunca.
2. Olvidar `established`: las respuestas a las consultas DNS salientes mueren en el deny implícito.
3. Con un /23 en lugar de dos /24 ahorras un bloque y los pasos bajan.

**Zona de reto visible.** "Nv6 · pruebas 14/16 · bloques 5 (mejor 4) · pasos 41 (mejor 33)". Nada se
ajusta solo; se ofrece "ver 1 prueba que falla".

**Pistas que enseñan.** Paquete fallido: se resalta la regla que lo atrapó y la que debía atraparlo.
Regla nunca usada en las pruebas: aparece atenuada con "regla sombreada" y su motivo.

**Feedback y juice.** Bloque arrastrado: encaje magnético con `Snappy` y "clac" (`TAP`); la pila se abre
para hacer hueco (`Move`). Prueba: paquetes caen como fichas por la pila (`Linear` 120 ms por regla, frenazo
`EaseOut` en la que coincide, la regla destella). Pasa: sale por la derecha a verde con `Spark`; bloqueado:
cae a la izquierda con `Shatter`. Todo correcto: pila brilla de arriba abajo en ola (30 ms por bloque),
`COMPLETE`; nuevo mejor: `Confetti` + `LEVEL_UP`. Esperado vs obtenido distinto: `ShakeSmall` en el paquete.
Off: tabla de resultados por paquete sin animación.

**Diseño visual.** Fondo `#141821`, bloques `PERMITIR` `#1EDB90`, `DENEGAR` `#FF5470`, fichas en `surface`,
texto mono.

```
┌────────────────────────────────────┐
│ ← Firewall Nv6 · 14/16 · 5b · 41p  │
│ Requisito: web entrante, SSH solo  │
│ desde 10.0.5.0/24, DNS saliente    │
├──────────────┬─────────────────────┤
│ PALETA       │ PILA (ACL entrada)  │
│ [PERMITIR]   │ 1 DENEGAR 198.51..  │ bloques 860×140
│ [DENEGAR ]   │ 2 PERMITIR tcp 443  │
│ [ip][puerto] │ 3 PERMITIR tcp 22 ← │ 10.0.5.0/24
│ [est.]       │ · deny implícito    │
├──────────────┴─────────────────────┤
│ Pruebas: ●●●●●●●●●●●●●●○○          │ cada bolita = paquete
│ [paso a paso]          [▶ probar]  │
└────────────────────────────────────┘
```

**Pantalla de resultado.** Histograma bloques y pasos con tu marca y la del fantasma; solución propia
guardada; "ver solución de referencia" solo tras superar.

**Integración.** Álbum: "Deny implícito", "Regla sombreada", "established". Misión: "Iguala el mejor en un nivel".
Fantasma: tu mejor solución por nivel.

**Riesgos y medición.** Riesgo: lento de manipular → máximo 8 bloques, fichas grandes. Medir: tiempo hasta
primer "probar", nº de iteraciones por nivel (≥ 2 indica exploración, > 8 frustración).

**Esfuerzo: L.** `lab-core/Acl.kt`: evaluador (primera coincidencia, CIDR con `Ipv4` existente),
generador de pruebas que cubre cada requisito, cálculo de mejor con búsqueda acotada en pruebas unitarias.

---

## 10. Plan por fases

### Fase 2 — poda + Maleta + Vigía
- Retirar Destellos, Última huella, Transforma, Cálculo, Ruta espacial (añadir a `Games.retired`; conservar
  historial; ocultar del menú, de misiones y de Relámpago).
- `Juice.kt` con tokens y pool de partículas.
- La maleta del técnico y Vigía de alertas completos.
- Nuevas áreas del menú.

**Aceptación:** el menú muestra exactamente las secciones de §0 (con los juegos aún no hechos ocultos);
ningún juego retirado aparece en misiones ni Relámpago (prueba); `Suitcase` y `Vigil` generan la misma
partida con la misma seed (prueba); `Particles` no asigna tras el calentamiento (prueba con contador del
pool); con Animaciones off no corre ningún `withFrameNanos` (prueba de Compose como `QuickBoardsTest`);
120 fps estables en S23 Ultra medido con `FrameMetrics` (≤ 1 % frames > 8,3 ms en un tramo de Vigía);
copia y restauración conservan récords y álbum.

### Fase 3 — Anti-DDoS + Ruta de paquetes + Parejas de incidentes
- `ddos` sustituye a Pulso; `route` sustituye a Órbitas con migración de progreso; `pairs` con bancos nuevos,
  tríos y temas.

**Aceptación:** motor de reglas de `Ddos` con prueba por regla y prioridad (excepción > bloqueo); el beat no
deriva más de 5 ms en 60 s; el generador de `Route` no produce cruces (prueba con 1000 seeds); el progreso de
`orbit` aparece en `route`; los temas de Parejas no cambian la dificultad (prueba: misma seed, mismo tablero);
cada juego ofrece al menos 10 cartas de álbum con `key` estable.

### Fase 4 — Detective + Cables
- `Cases.kt` con 8 plantillas; `Lab.kt` simulador de cables, VLAN y rutas.

**Aceptación:** cada caso tiene solución única (prueba que enumera combinaciones); el ping del simulador
coincide con una tabla de 20 escenarios conocidos (prueba); los casos enlazan a los juegos que repasan;
un caso completo dura de 3 a 8 min en prueba con 3 personas; arrastres con < 5 % de cancelaciones.

### Fase 5 — Firewall + Consulta Segura con fichas + logs desplazándose + zona de reto global
- `acl` completo.
- Consulta Segura: construir la consulta con fichas (`?`, parámetro, concatenación) en vez de elegir.
- Cazador de logs: las líneas se desplazan como en Vigía (reutiliza su vista).
- Pastilla de reto global (§1.7) en todos los juegos.

**Aceptación:** evaluador ACL con prueba de primera coincidencia, deny implícito y CIDR; el "mejor conocido"
de cada nivel se verifica en prueba; todos los juegos muestran la pastilla de reto y cada ajuste en partida
genera un mensaje visible (prueba: ningún ajuste sin `ChallengeNote`); Consulta Segura y Cazador de logs
mantienen sus bancos y `key` del álbum; la tasa de "Otra vez" media del menú no baja respecto a la v1.6.

---

## 11. Cómo sabremos que es divertido (común)

Telemetría local (sin red, visible en Estadísticas): partidas terminadas / empezadas, "Otra vez" en < 10 s,
sesiones por juego en 7 días, nivel medio a 14 días, abandonos por ronda. Umbral para mantener un juego:
≥ 70 % terminadas y ≥ 25 % "Otra vez". Prueba cualitativa: 3 sesiones grabadas con el usuario, anotando
dónde sonríe, dónde suspira y dónde mira la hora.
