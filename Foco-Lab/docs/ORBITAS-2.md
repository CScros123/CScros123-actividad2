# Órbitas 2

Rediseño de Órbitas (🧠 Mente, id `orbit`). El juego sigue retirado de menús, planes y misiones (Fase 3: `route` lo sustituye y hereda su nivel), pero todavía se abre desde «Algo nuevo por descubrir» en el Inicio de Lab. Este cambio mejora ese juego sin tocar la migración.

## Qué cambia en pantalla

| Antes | Ahora |
|---|---|
| Nodos numerados del 1 al 8 | Nodos geométricos sin números (núcleo oscuro y anillo). Se recuerdan lugares, no cifras |
| Progreso repetido tres veces: «RONDA 1 / 12», 12 círculos bajo el tablero y «0 aciertos de 0» | Una sola barra de 12 bloques arriba: esmeralda si aciertas, coral si fallas, el bloque actual late |
| «Observa» arriba a la derecha y en el centro | Solo en el núcleo, dentro de un anillo de tiempo: cian→ámbar al observar, «GIRA» en ámbar, verde con «TU TURNO · 2 de 5» y coral cuando queda poco |
| El nodo se enciende aislado | Un cometa viaja por la órbita desde el nodo anterior y deja una estela que se apaga en 0,6 s |
| Barra «Fantasma del récord» abajo, donde descansan los pulgares | Línea fina de puntos con una marca dorada (el fantasma) dentro del bloque de progreso; el texto «Fantasma: +25» va a la derecha de la ronda |
| Panel «Solución paso a paso» y botón «Siguiente ronda» entre rondas | El núcleo da el veredicto («BIEN +125 · enfoque» o «FALLO · era doble toque») y marca con un anillo discontinuo el nodo que tocaba. La ronda siguiente empieza sola (formato encadenado) |

La mitad inferior de la pantalla queda libre.

## Mecánica: 12 rondas en tres fases

Reglas en `lab-core/.../core/Orbit.kt`.

- **Rondas 1–4, calibración espacial.** Caminos de 3 a 5 luces, a velocidad estándar: cada luz dura siempre 1,1 s en todas las rondas, fases, niveles y modos (`Orbit.STEP_MS`). La dificultad sube por la longitud del camino (el nivel añade hasta 3 luces), los nodos especiales y el giro, nunca por la velocidad.
- **Rondas 5–8, polaridad y doble pulso.** Sin texto nuevo sobre los nodos:
  - **doble anillo**: doble toque (el segundo en menos de 500 ms);
  - **magenta**: se toca el nodo diametralmente opuesto.
- **Rondas 9–12, rotación orbital.** Terminado el camino, la órbita gira 1 o 2 posiciones (45° o 90° con 8 nodos) en un sentido al azar. Los nodos conservan su identidad, así que el patrón hay que rotarlo mentalmente.
- **Destellos señuelo.** Desde la ronda 7 (dos desde la 10) aparece un destello ámbar fuera de la órbita durante la demostración. Si no lo tocas y aciertas, la ronda vale un 25 % más (bono de enfoque).

Puntos por ronda acertada: 100 en la fase 1, 125 en la 2 y 150 en la 3, más el bono. El nivel se sigue calculando con aciertos, como antes; los puntos alimentan el fantasma y el resumen.

## Modos

Se eligen en la pantalla de inicio de la partida y se recuerdan (preferencia `orbit:mode`).

- **Clásico**: las 12 rondas de arriba.
- **Espejo**: el mismo camino, pero se repite del último nodo al primero.
- **Órbita expansiva**: supervivencia; el tablero tiene 6 nodos en las rondas 1–4, 8 en las 5–8 y 10 en las 9–12. El primer fallo termina la partida (las rondas no jugadas se guardan como `survival`) y **no cambia el nivel** (`Formats.measure` devuelve 0 de 0).

El fantasma compara solo con partidas del mismo modo.

## Sonido

Cada posición de la órbita tiene su nota de una pentatónica mayor (do, re, mi, sol, la en dos octavas, `GameTones.note`). Suena al encenderse el nodo y al tocarlo; al completar el camino sin errores suenan juntas como acorde (tres voces como máximo, el límite de `SoundPool`). Respeta «sin sonido», el volumen de efectos de Foco, No molestar y el modo silencio.

## Resumen final: mapa de atención

En el resultado aparece la órbita dividida en cuatro cuadrantes (superior derecho, inferior derecho, inferior izquierdo y superior izquierdo, ya girados), cada uno con su tiempo medio de reacción y sus fallos, coloreados de cian (rápido y preciso) a coral (lento o con fallos). Debajo hay una frase con el cuadrante más lento, el más rápido y el que acumula más fallos.

## Accesibilidad y animaciones

- Sin números en pantalla, pero cada nodo tiene un objetivo invisible para TalkBack con su posición de reloj («Nodo a las 3»), que también se puede activar.
- Con las animaciones desactivadas no hay cometa ni estela y el giro es instantáneo; las luces y el giro siguen ocurriendo porque son el juego.
- El reloj del tablero solo avanza mientras hay fotogramas (máximo 50 ms por fotograma), así que salir de la app no consume tiempo de respuesta.

## Registro por ronda (`OrbitLog`)

`ok=…;mode=classic|mirror|expand;stage=…;nodes=…;len=…;error=0|1|2|3;focus=…;points=…;rt=q:ms,…;miss=q`

`error`: 0 ninguno, 1 nodo equivocado, 2 faltó el doble toque, 3 sin tiempo. Las partidas guardadas antes de este cambio no tienen registro: siguen en el historial, pero no sirven de fantasma ni de mapa.

## Archivos

- `lab-core/.../core/Orbit.kt`: reglas, fases, modos, juez de toques (`OrbitInput`), registro, puntos y mapa de atención.
- `lab-core/.../core/Games.kt`: `Kind.ORBIT`, `Challenge.orbit`, formato encadenado y `measure` para Expansiva.
- `lab/.../OrbitBoard.kt`: tablero (Canvas), barra única, selector de modo y mapa de atención.
- `lab/.../Player.kt`: usa la barra única, el selector y el mapa solo para Órbitas.
- `lab/.../BrainViewModel.kt`: `Run.variant` (modo, guardado en el borrador), `chooseOrbitMode` y fin anticipado de Expansiva.
- `lab/.../Experience.kt`, `HostSound.kt`: notas por posición (`LocalNote`).
- `lab/.../Visuals.kt`: se elimina el tablero numerado.

## Pruebas

- `lab-core`: `OrbitTest` (11 pruebas: fases, velocidad, caminos sin repeticiones seguidas y reproducibles, magenta y Espejo, 6→8→10 nodos, señuelos fuera del anillo, juez de toques simples y dobles, bono de enfoque, cuadrantes tras el giro, registro, fantasma y mapa, Expansiva sin cambio de nivel). `VisualGamesTest` se actualiza al nuevo tipo de ronda.
- `lab`: `VisualScenesTest` comprueba que los 8 nodos son accesibles por su posición de reloj y que el tablero no expone números como texto; guarda la captura `build/screenshots/orbitas-tablero.png`.
