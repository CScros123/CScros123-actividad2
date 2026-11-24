# Examen de juegos de Foco Lab

Revisión del código, sin cambios en el código fuente. Rutas relativas a la raíz del repositorio.
Abreviaturas: `G` = `lab-core/.../core/Games.kt`, `N` = `lab-core/.../core/NetSec.kt`, `P` = `lab-core/.../core/Pilot.kt`,
`Pl` = `lab/.../Player.kt`, `L` = `lab/.../LiteApp.kt`, `PV` = `lab/.../PilotViews.kt`, `NV` = `lab/.../NetSecViews.kt`,
`VM` = `lab/.../BrainViewModel.kt`, `SP` = `lab/.../ShortPractice.kt`, `V` = `lab/.../Visuals.kt`, `PG` = `lab/.../ProgressViews.kt`.

Contexto: según el plan (`docs/PLAN-FOCO-LAB.md` 5C), 21 de los 27 ejercicios usan la misma pantalla "pregunta + opciones"
(`Kind.CHOICE`). El examen lo confirma: después de la Fase 7 solo Subredes, Anzuelo y Puerto Seguro tienen una mecánica propia
dentro de Redes. El resto de Redes sigue en 2 o 4 opciones.

---

## 1. Inventario

"Guarda" = escribe un `ResultRow` en Room (12 rondas, `VM.finish`). "Adapt." = `Adaptive.next` (sube o baja de nivel tras 2 bloques).
Las líneas son aproximadas: generador más vista propia (la pantalla común `Player`/`ChoiceCards`, unas 170 líneas, se comparte y no se cuenta).

| id | Nombre | Área | Mecánica real | Dónde se accede | Guarda | Adapt. | Animación / feedback presentes | Líneas |
|---|---|---|---|---|---|---|---|---|
| orbit | Órbitas | Memoria | Secuencia: tocar 8 nodos en círculo en orden | Catálogo "Más ejercicios" | Sí | Sí | Nodos sin transición de color; líneas del camino en el feedback (V:107) | ~15 |
| sequence | Ecos | Memoria | Secuencia en cuadrícula 3×3 (misma `Kind.SEQUENCE` que orbit) | Catálogo | Sí | Sí | Casilla iluminada sin tween (Pl:65) | ~3 |
| grid | Destellos | Memoria | Memorizar casillas y marcarlas + "Comprobar" | Catálogo | Sí | Sí | Sin tween de color ni escala (Pl:65) | ~3 |
| working | Última huella | Memoria | Flujo de dígitos y después 4 opciones | Catálogo | Sí | Sí | Dígito sin entrada ni salida (Pl:69) | ~3 |
| search | Punto de enfoque | Atención | Opciones con glifos (9 a 18) | Catálogo, Entrenamiento de hoy | Sí | Sí | ChoiceCards (escalonado, pop y sacudida) | ~2 |
| pattern | Intruso | Atención | 4 o 6 opciones | Catálogo, Entrenamiento de hoy | Sí | Sí | ChoiceCards | ~2 |
| flanker | Centro | Atención | 2 opciones | Catálogo, Entrenamiento de hoy | Sí | Sí | ChoiceCards | ~2 |
| compass | Rumbo inverso | Atención | 4 opciones con tablero de flecha | Catálogo, Entrenamiento de hoy | Sí | Sí | CompassBoard estático (V:118) | ~10 |
| reaction | Pulso | Velocidad | Esperar la señal y tocar el panel | Catálogo | Sí | Sí | Cambio de color brusco, `clickable` sin pressClick (Pl:70) | ~6 |
| choice | Decisión | Velocidad | 2 opciones (par/impar, múltiplo de 3) | Catálogo | Sí | Sí | ChoiceCards | ~2 |
| series | Siguiente | Lógica | 4 opciones numéricas | Catálogo, Entrenamiento de hoy | Sí | Sí | ChoiceCards | ~2 |
| matrix | Transforma | Lógica | Matriz 3×3 estática + 4 opciones con glifos | Catálogo, Entrenamiento de hoy | Sí | Sí | El "?" no se revela con animación (Pl:71) | ~5 |
| math | Cálculo claro | Matemáticas | 4 opciones numéricas | Catálogo, Entrenamiento de hoy | Sí | Sí | ChoiceCards | ~7 |
| percent | Porcentajes prácticos | Matemáticas | 4 opciones | Catálogo, Entrenamiento de hoy | Sí | Sí | ChoiceCards | ~5 |
| fraction | Fracciones útiles | Matemáticas | 4 opciones | Catálogo, Entrenamiento de hoy | Sí | Sí | ChoiceCards | ~4 |
| equation | Encuentra x | Matemáticas | 4 opciones | Catálogo, Entrenamiento de hoy | Sí | Sí | ChoiceCards | ~3 |
| ratio | Proporciones | Matemáticas | 4 opciones | Catálogo, Entrenamiento de hoy | Sí | Sí | ChoiceCards | ~3 |
| balance | Balanza | Matemáticas | 4 opciones con dibujo de balanza | Catálogo, Entrenamiento de hoy | Sí | Sí | BalanceBoard estático (V:123) | ~15 |
| budget | Presupuesto | Vida cotidiana | 4 opciones | Catálogo, Entrenamiento de hoy | Sí | Sí | ChoiceCards | ~4 |
| unitprice | Compra inteligente | Vida cotidiana | 2 opciones | Catálogo, Entrenamiento de hoy | Sí | Sí | ChoiceCards | ~3 |
| planning | Tiempo y planificación | Vida cotidiana | 4 opciones | Catálogo, Entrenamiento de hoy | Sí | Sí | ChoiceCards | ~3 |
| bits | Bits | Redes | 4 opciones (binario y decimal) | Sección Redes (3 modos), Misión, Práctica 3 min | Sí | Sí | ChoiceCards; sin interruptores de bit | ~20 |
| subnet | Subnet Sprint | Redes | **Propia**: arrastrar la frontera de la máscara sobre 32 bits (Normal); 4 opciones (Relámpago, 3 min) | Redes, Misión, 3 min | Sí | Sí | Tween de color por bit, frontera dibujada, liveRegion (PV:51-150) | ~22+30+100 |
| ports | Puerto Seguro | Redes | **Propia**: panel de firewall con interruptores (Normal); 4 opciones en los demás modos | Redes, Misión, 3 min | Sí | Sí | Paquetes animados en puertos abiertos, tween de color (PV:254) | ~32+53+38 |
| phishing | Anzuelo | Redes | **Propia**: deslizar la tarjeta de correo (Normal); 2 opciones en los demás modos | Redes, Misión, 3 min | Sí | Sí | Inclinación al arrastrar, sellos SEGURO/TRAMPA, pila de cartas, pista resaltada (PV:194) | ~45+10+95 |
| sqlsafe | Consulta Segura | Redes | 2 opciones (código) o 5 opciones (tipo de ataque) | Redes, Misión, 3 min | Sí | Sí | ChoiceCards mono + CodeBox | ~49 |
| logs | Cazador de logs | Redes | 4 líneas mono, tocar la sospechosa | Redes, Misión, 3 min | Sí | Sí | ChoiceCards mono | ~39 |
| pair | Parejas matemáticas | Arcade | Unir operación y resultado; las cartas se reordenan | Inicio ("Jugar ahora", "Parejas", "Arcade") y GameSetup | **No** | No (3 dificultades manuales) | `animateItemPlacement`, sacudida, tween de borde (L:244-281) | ~38+38 |
| pulse | Pulso de neón | Arcade | Topo: tocar la luz en una cuadrícula 3×3 antes de que se mueva | GameSetup | **No** | Ritmo interno | Brillo pulsante, onda circular al acertar (L:286-314) | ~30 |
| meteor | Ruta espacial | Arcade | Esquivar en 3 carriles con Canvas a 120 Hz | GameSetup | Solo récord en prefs (`meteor_best`) | Nivel interno cada 8 | Partículas, temblor, destello, nave inclinada (MeteorGame.kt) | ~250 |
| — modo | Relámpago | Redes | 60 s con combo ×2/×3, siempre 4 opciones (`Generator.make`) | Redes → modo | Solo récord (`flash_best:<id>`) | Usa el nivel guardado, no lo cambia | Escala del multiplicador (NV:111) | ~30 |
| — modo | Sin tiempo / Infinito | Todos | Bucle de 12 rondas sin cronómetro | Catálogo y Redes | **No** | No | Igual que Normal | — |
| — modo | Práctica 3 min | Redes | 6 retos o 3 min, siempre 4 opciones | Pausa de Foco ("Practicar 3 min") y "Hoy" | Solo pref `short_session:last` | **No** | ChoiceCards | ~118 |
| — modo | Entrenamiento de hoy | Atención, Lógica, Mates, Vida | 4 ejercicios encadenados | Inicio (DailyTrainingCard) | Sí | Sí | — | ~25 |
| — modo | Misión de hoy | Redes | Un juego de Redes elegido (el peor y el menos reciente) | Inicio (tarjeta Redes) | Sí | Sí | — | ~5 |

---

## 2. Examen por juego

Notas de 1 a 10: **D** diversión · **A** aprendizaje real (para un estudiante de redes y seguridad) · **M** variedad de mecánica · **V** calidad visual y animación · **F** feedback (sonido, vibración, visual) · **Acc** accesibilidad.

**Órbitas** D6 A3 M6 V6 F6 Acc6. Es Simon en círculo y tiene identidad visual (V:107). Los nodos cambian de color sin transición y no hay pulso ni sonido por nodo durante la observación (Pl:39 solo cambia `flash`). Es la misma mecánica que Ecos.

**Ecos** D4 A3 M3 V3 F5 Acc6. Duplica a Órbitas (`Kind.SEQUENCE` en los dos, G:45 y G:49) con una cuadrícula gris. La casilla iluminada no tiene animación (Pl:65).

**Destellos** D5 A3 M6 V3 F5 Acc6. La mecánica está bien (marcar y comprobar). Las casillas se encienden y apagan de golpe (Pl:65) y no se muestran aciertos y fallos por casilla en el feedback; solo se repintan los objetivos.

**Última huella** D4 A4 M5 V3 F5 Acc6. Error: `options()` genera distractores entre −12−l y +12+l (G:42, G:50). Aparecen opciones como "−7" o "15" para un dígito de 0 a 9, así que la respuesta se adivina por eliminación. El dígito no tiene animación de entrada (Pl:69).

**Punto de enfoque** D3 A1 M3 V5 F5 Acc4. Solo usa 2 o 3 glifos casi iguales (G:51). Con 18 opciones, las tarjetas son pequeñas para personas con baja visión.

**Intruso** D2 A1 M2 V4 F5 Acc7. La regla es siempre "todos pares menos uno" (G:52), así que lo que se aprende se agota en una partida.

**Centro** D3 A1 M2 V4 F5 Acc7. Es un flanker con 6,5 s de plazo (G:53), demasiado para que haya efecto de interferencia. Tiene 2 botones.

**Rumbo inverso** D4 A1 M3 V5 F5 Acc6. En los niveles 1 a 3 la regla es siempre "opuesta" (`opposite=l<4||…`, G:46), así que la descripción "cambia de regla" no se cumple al principio.

**Pulso (reaction)** D3 A1 M5 V3 F5 Acc5. El panel cambia de color de golpe y usa `clickable` sin pressClick (Pl:70). Tiene el mismo nombre que el arcade "Pulso de neón" y eso confunde.

**Decisión** D2 A1 M2 V4 F5 Acc7. Par o impar en 2 botones (G:55).

**Siguiente** D4 A3 M2 V4 F5 Acc7. Series numéricas con 4 opciones; los distractores aleatorios suelen ser obvios.

**Transforma** D5 A3 M5 V5 F5 Acc5. La matriz es estática y se responde con 4 opciones (Pl:71). La casilla "?" no se revela con la respuesta en el feedback. Solo tiene una regla (avanzar por filas, G:89).

**Cálculo claro / Porcentajes / Fracciones / Encuentra x / Proporciones** D3 A4 M1 V4 F5 Acc7. Son cinco pantallas iguales (`numeric()`, G:43). La explicación paso a paso es buena; lo malo es que se repite el formato.

**Balanza** D4 A4 M2 V6 F5 Acc6. Es el mismo ejercicio que "Encuentra x" con un dibujo (G:47). El dibujo no se inclina ni se anima.

**Presupuesto / Compra inteligente / Planificación** D2 A3 M1 V4 F5 Acc7. No tienen relación con redes y repiten el formato.

**Bits** D5 A8 M3 V4 F5 Acc7. Buen contenido: los distractores cambian un bit (N:30) y la explicación muestra las potencias de 2. El plan pedía interruptores de bit que suman en vivo y no está hecho.

**Subnet Sprint** D8 A9 M9 V7 F7 Acc8. Es el mejor del catálogo. La vibración `TAP` salta en cada cambio de prefijo al arrastrar (PV:96) y puede saturar. Hay una pequeña asimetría: el toque usa `col+1` y el arrastre `roundToInt` (PV:107-111). En Relámpago y en 3 min se pierde el tablero y vuelve a 4 opciones (NV:103, SP:79).

**Puerto Seguro** D7 A9 M8 V7 F6 Acc8. El panel de firewall y los paquetes que fluyen están muy bien. Al aplicar reglas solo se colorean las filas y falta un efecto global de "regla aplicada" (por ejemplo, un barrido). Fuera del modo Normal se pierde el panel.

**Anzuelo** D8 A9 M8 V7 F7 Acc8. Error visual: al soltar más allá del umbral, `commit()` devuelve la tarjeta al centro con un muelle (PV:202) en lugar de lanzarla fuera de la pantalla, y parece un rechazo. La tarjeta siguiente siempre entra por la derecha (PV:199). Tiene 35 cebos (N:101+) y se pueden repetir dentro de la misma partida (no se excluyen los ya vistos, P:112).

**Consulta Segura** D5 A9 M3 V5 F5 Acc6. El contenido es excelente (consultas, GRANT, XSS, CSRF, traversal). La mecánica es de 2 opciones (N:189-191). El código mono a 13 sp en botones es difícil de leer en un móvil.

**Cazador de logs** D6 A9 M4 V5 F5 Acc6. Buenos indicios por nivel (N:212+). Siempre hay 4 líneas y siempre de la misma "tienda en Madrid" (N:231). Falta el flujo de terminal que se desplaza.

**Parejas matemáticas** D7 A2 M8 V8 F7 Acc7. La mecánica y las animaciones están muy bien (`animateItemPlacement`, sacudida). Pero el contenido es aritmética, no guarda nada (L:244) y aparece 3 veces en el inicio.

**Pulso de neón** D6 A1 M6 V7 F5 Acc5. Cuando la luz se mueve sin tocarla, la racha se pone a 0 en silencio, sin sonido ni marca visual (L:291). No guarda resultados.

**Ruta espacial** D8 A1 M8 V9 F8 Acc6. Es el mejor arcade: Canvas, partículas (MeteorGame.kt:39,161), temblor y destello. Solo guarda el récord. Para personas que no ven bien, solo describe el carril y las vidas, no dónde están los meteoros.

**Relámpago** D7 A7 M3 V5 F7 Acc6. El combo funciona. Lee el récord una sola vez al componerse (NV:105) y no guarda el historial.

**Sin tiempo** D4 A6 M- V- F5. No guarda resultados ni la lista de errores (`!endless`, VM:128).

**Práctica 3 min** D5 A7 M3 V4 F5 Acc7. Es el punto de entrada más importante (la pausa) y usa la versión más pobre: 4 opciones y sin tableros. No cambia de nivel (está documentado, SP:39).

---

## 3. Veredicto

| Juego | Veredicto | Motivo |
|---|---|---|
| Subnet Sprint | **MANTENER** | Mecánica propia, núcleo de la carrera. Llevar su tablero a todos los modos. |
| Puerto Seguro | **MANTENER** | Panel de firewall único; solo pulir el efecto de "aplicar". |
| Anzuelo | **MEJORAR** | Animación de lanzar la tarjeta y no repetir cebos en la misma partida. |
| Bits | **MEJORAR** | Crear la mecánica de interruptores de bit; el contenido ya está bien. |
| Consulta Segura | **MEJORAR** | Cambiar el formato de 2 opciones por armar la consulta con fichas. |
| Cazador de logs | **MEJORAR** | Flujo de terminal desplazable y varios escenarios. |
| Ruta espacial | **MANTENER** | Mejor arcade; guardar sesiones y temática de paquetes y cortafuegos. |
| Parejas matemáticas | **MEJORAR** | Cambiar el contenido a parejas de redes (puerto↔servicio, protocolo↔capa OSI) y guardar el resultado. |
| Pulso de neón + Pulso (reaction) | **FUSIONAR** | Un solo "Pulso": topo con rondas de "espera la señal". Acaba con el nombre duplicado. |
| Órbitas + Ecos | **FUSIONAR** | Misma `Kind.SEQUENCE`; queda Órbitas, que tiene mejor arte. |
| Destellos | **MANTENER** | Mecánica distinta; añadir animación de casillas. |
| Última huella | **MEJORAR** | Corregir los distractores fuera de 0 a 9; opción de flujo de octetos IP o puertos. |
| Transforma | **MEJORAR** | Revelar el "?" y añadir más reglas (columnas, rotación). |
| Cálculo claro, Porcentajes, Fracciones, Encuentra x, Proporciones, Balanza, Siguiente | **FUSIONAR → "Cálculo"** | Un juego con modos; se reutilizan los generadores tal cual. |
| Presupuesto, Compra inteligente, Planificación | **ELIMINAR** (a extras ocultos) | Fuera del eje y con formato duplicado. |
| Punto de enfoque, Intruso, Centro, Decisión, Rumbo inverso | **ELIMINAR** (a extras ocultos) | Formato de 2 a 4 opciones con aprendizaje casi nulo. |
| Relámpago | **MEJORAR** | Usar los tableros piloto en versión rápida y guardar el historial. |
| Sin tiempo | **MEJORAR** → "Estudio" | Guardar errores para repasar y mostrar el álbum. |
| Práctica 3 min | **MEJORAR** → "Misión 3 min" | Usar los tableros piloto; base de las misiones diarias. |
| Entrenamiento de hoy | **FUSIONAR** en Misiones diarias | Hoy elige solo Atención, Lógica, Mates y Vida (VM:90), justo los juegos que se eliminan. |
| Misión de hoy | **MANTENER** (integrada en Misiones) | Su regla de elección es buena (`ShortSession.mission`). |

"Eliminar" significa **ocultar del menú, no borrar el `Game`**. `Games.get(id)` se llama sobre el historial guardado (`VM.areaAccuracy`, `areaPoints`, PG:41) y lanza una excepción con ids desconocidos (`first{}`, G:32). Hay que conservar las entradas con una marca `hidden` o mapear los ids antiguos a "Cálculo".

---

## 4. Problemas transversales

### 4.1 Navegación duplicada
- En el inicio, "Jugar ahora", "Parejas matemáticas" y "Arcade" llaman a la misma `play` → GameSetup (L:84, L:89, L:90). Son tres botones para el mismo destino.
- Hay 6 puertas de entrada a juegos: LiteHome, catálogo "Más ejercicios" (L:122), sección Redes (L:74), Misión de hoy (NV:62), Entrenamiento de hoy (PG:111) y, desde Foco, "Practicar 3 min" y "Juegos" en la pausa (`app/.../FocusOverlay.java:103-104`).
- La jerarquía está invertida: la tarjeta grande "Entrenamiento de hoy" lleva a los juegos de menor valor y Redes queda en 4ª posición.
- GameSetup dice "Sin niveles ni logros" (L:175), pero la Ruta espacial tiene niveles y récord.
- La insignia "NUEVO" está fija para siempre en orbit, compass y balance (V:97).
- El desbloqueo de Redes es lineal con 8/12 (NV:37): Consulta Segura y Logs, que son de los más útiles, quedan detrás de 4 juegos.

### 4.2 Estilos inconsistentes
- Las pantallas Lite usan hex fijos (`0xFF1EDB90`, `0xFF5A3ED4`, degradados `0xFF151B2E`…, L:89-110 y PG:116). Redes usa ámbar fijo (NV:28-63). Player y los tableros piloto usan `MaterialTheme.colorScheme`. El plan (sección 4) pedía tokens únicos en `:lab-design` con base salvia, pero la portada del Lab es azul noche y neón.
- Hay tres resúmenes de fin de partida diferentes: `ResultScreen` (PG:62), `SessionSummary` (L:221) y `ShortSummary` (SP:101). `ResultContent` (Pl:134) es código muerto.
- Los encabezados de sesión también difieren: `SessionHeader` en arcade, Relámpago y 3 min, y una cabecera propia en Player (Pl:46).

### 4.3 Huecos de animación (transiciones concretas que faltan)
1. Al entrar en una partida o volver de ella hay un corte brusco: `if(vm.run!=null){Player(vm);return}` (L:57) se salta el `AnimatedContent`.
2. Los cambios de fase de Player (INTRO → ronda → RESULT) no tienen `AnimatedContent`, es un `when` directo (Pl:47).
3. El panel de feedback sale con `ExitTransition.None` (Pl:80) y desaparece de golpe al pulsar "Siguiente".
4. Las casillas de Destellos y Ecos no tienen tween de color, escala ni "encendido" (Pl:65).
5. El panel de Pulso (reaction) pasa de gris a menta sin transición, sin onda y sin escala (Pl:70).
6. El dígito de Última huella no tiene entrada ni salida (Pl:69).
7. En Transforma, el "?" no se revela con la respuesta (Pl:71).
8. En Anzuelo, la tarjeta vuelve al centro en vez de salir disparada hacia el lado elegido (PV:202).
9. Puerto Seguro no tiene barrido de "regla aplicada" al enviar (PV:282).
10. Subir de rango o desbloquear un juego es solo texto (NV:137-138): no hay partículas ni sonido.
11. El candado de la lista de Redes no se abre con animación cuando se desbloquea un juego (NV:82-90).
12. El récord nuevo (meteor, Relámpago) es solo una línea de texto en `SessionSummary` (L:332, NV:116).
13. Pulso de neón no tiene marca visual cuando se escapa la luz (L:291).

### 4.4 Sonido y vibración
- Solo existen 4 señales: TAP, CORRECT, RETRY y COMPLETE (`Experience.kt:29`). Lo bueno es que el tono de CORRECT sube con la racha (`GameTones.correctRate`, Experience.kt:72).
- No hay señal para subir de nivel, subir de rango, desbloquear, récord ni "luz perdida". La subida de nivel de la Ruta espacial reutiliza COMPLETE (MeteorGame.kt:74).
- Al arrastrar en Subnet se lanza un TAP por cada bit (PV:96). Conviene limitarlo a una vez por octeto o usar `EFFECT_TICK`.
- La observación en Órbitas, Ecos y Destellos no tiene sonido. Un tono por nodo ayudaría a memorizar.

### 4.5 Huecos al guardar resultados
- "Sin tiempo" o "Infinito" no guarda nada; `next()` reinicia los resultados cada 12 rondas (VM:131) y no recoge errores (VM:128).
- Práctica 3 min no sube de nivel ni guarda un `ResultRow`; solo guarda `short_session:last` (SP:106).
- Relámpago, Parejas, Pulso de neón y Ruta espacial no tienen historial. Las dos primeras ni siquiera guardan récord.
- `persist()` está vacío (`= Unit`, VM:103), pero `init` sigue intentando restaurar el borrador (VM:67-72). Si Android cierra la app a mitad de partida, la partida se pierde y ese código no hace nada.
- Comentario duplicado en Pl:141-142 (detalle menor).

---

## 5. Estructura final del menú (14 juegos)

```
Inicio
├── Misiones de hoy (3 × ~3 min)  ← sustituye a "Entrenamiento de hoy" y "Misión de hoy"; aquí aparece el cofre
├── Redes y seguridad   [modos: Normal · Relámpago · Estudio]
│   ├── 1. Bits              (interruptores de bit)
│   ├── 2. Subnet Sprint     (frontera de máscara)
│   ├── 3. Puerto Seguro     (panel de firewall)
│   ├── 4. Anzuelo           (bandeja con deslizamiento)
│   ├── 5. Consulta Segura   (fichas para armar la consulta)
│   └── 6. Cazador de logs   (terminal desplazable)
├── Mente
│   ├── 7. Órbitas           (incluye Ecos)
│   ├── 8. Destellos
│   ├── 9. Última huella     (dígitos u octetos IP)
│   ├── 10. Transforma
│   └── 11. Cálculo          (modos: operaciones · %, fracciones, proporciones · x/balanza · series)
├── Arcade
│   ├── 12. Ruta espacial
│   ├── 13. Pulso            (neón + reacción)
│   └── 14. Parejas de redes (puerto↔servicio, protocolo↔capa)
└── Progreso: álbum de conceptos · jardín · semana · ajustes
```
Candidato para un juego 15, sustituyendo a otro si hace falta: "Siguiente salto" (tabla de rutas), del plan 5C.
Los extras ocultos se pueden reactivar con un interruptor en Ajustes ("Mostrar ejercicios clásicos").

---

## 6. Plan del sistema de enganche ético

Principios: nunca da minutos de ocio (regla actual, SP:40); sin pago, sin pérdidas ni rachas que castiguen; máximo de recompensas al día; sin cuentas atrás para presionar; cada sesión tiene un final claro ("Terminar por hoy"); todo respeta `LocalMotionEnabled` y los ajustes de sonido y vibración.

| Pieza | Dónde se engancha | Dependencias | Esfuerzo | Riesgos |
|---|---|---|---|---|
| **Cofre** (recompensa variable) | Núcleo puro nuevo `lab-core/.../core/Rewards.kt`: `roll(seed=resultId)` determinista y testeable. Se ofrece en `ResultScreen` (PG:62, tras `NetsecResult`), en `SessionSummary` (L:221) y en `ShortSummary` (SP:101). Se guarda en `PreferenceRow` (`chest:pending`, `chest:day:<fecha>`), así no hace falta migrar Room (BD `version=1`, Store.kt:43). | Ninguna | M | Que se vuelva compulsivo: limitar a 3 cofres al día, siempre con contenido (una carta, un aspecto o un dato). Nunca tiempo. |
| **Misiones diarias de 3 min** | Sustituir `VM.dailyPlan()` (VM:89) y `DailyTrainingCard` (PG:111) por `Missions.today(date, results)` en el núcleo, sobre `ShortSession` (ShortSession.kt). Ejemplos: "5 subredes bien", "esquiva 20 meteoros", "1 repaso de un error". El progreso se guarda en la pref `mission:<fecha>`. | Ninguna | M | Elegir misiones al azar que el usuario no puede cumplir: usar solo juegos desbloqueados y su nivel. |
| **Álbum de cartas de conceptos** | El contenido ya existe: `Port.lesson` (18), `Bait.clue` (35), `Attack.lesson` (12), `Clue.why` (13) y las lecciones de GRANT y consultas (16), unas 94 cartas. Id estable = `juego:índice`. Se desbloquea al acertar ese elemento por primera vez (`Outcome.item`, VM:23) o con un cofre. Pantalla nueva `AlbumPage` como página `"album"` en `LiteApp` (L:66). | Ninguna | M | Los índices de los bancos cambian si se reordenan las listas: añadir un `key` estable a cada elemento. |
| **"Otra vez" + fantasma del récord** | Botón "Otra vez" en `ResultScreen` y `SessionSummary` que reinicia el mismo juego con `vm.start(game)`. Mensaje de casi récord: "a 1 de tu récord". El fantasma en Player compara `RoundDots` (Pl:160) con los `trials` JSON del mejor `ResultRow`, que ya se guardan. En Relámpago, curva de puntos por segundo en la pref `flash_ghost:<id>`. En la Ruta espacial, un marcador de la distancia récord en el Canvas. | Ninguna | S–M | El casi récord empuja a seguir jugando: mostrar "Otra vez" solo 2 veces seguidas y después sugerir terminar. |
| **Celebraciones con partículas** | Sacar `Spark` y `burst` de `MeteorGame.kt:39,161` a un `Particles.kt` reutilizable (`Confetti(trigger)`). Se usa al subir de rango (NV:137), al desbloquear (NV:138), con un récord nuevo, al abrir un cofre y al completar una misión. Se añaden las señales `LEVEL_UP`, `UNLOCK` y `CHEST` a `FeedbackCue` y sus `.wav`. | **Compose Canvas puro, recomendado** (ya existe el motor de partículas, no pesa, respeta el modo de movimiento reducido). Konfetti-compose (ISC) es opcional y añade una dependencia. Lottie (Apache-2.0) solo si se quieren animaciones de diseñador; pesa más y necesita JSON. | S | Batería y saturación: menos de 1,5 s, `withFrameNanos` solo mientras dura, nada si el movimiento está desactivado. |
| **Cofre desde la pantalla de pausa** | `FocusOverlay.java:103-104`: añadir la acción "Abrir cofre (n)" solo si hay un cofre **ganado** pendiente. Leerlo con `PracticeSummary` (solo lectura, `PracticeSummary.kt`) añadiendo `chestsPending`. Nuevo `LabLaunch.chest(context)` con `EXTRA_CHEST` y ruta en `LabActivity` (`LabActivity.kt:59`). Al abrirlo, ofrecer la misión de 3 min y no un menú infinito. | Ninguna | M | Que la pausa se convierta en motivo para abrir la app bloqueada: el cofre se gana practicando y no se regala por abrir TikTok. No cambia el bloqueo ni da minutos. Foco solo lee la base de Lab. |

---

## 7. Orden de trabajo (3 lotes, uno por agente)

### Lote A: Poda y orden (sin funciones nuevas)
1. Añadir `hidden` (o `core`) a `Game`; ocultar del catálogo los 17 ejercicios eliminados o fusionados sin borrarlos (`Games.get` debe seguir funcionando con el historial).
2. Crear el juego "Cálculo" (id nuevo `calc`) con modos que llaman a los generadores existentes (math, percent, fraction, ratio, equation/balance, series).
3. Fusionar Ecos en Órbitas y reaction en "Pulso" (o retirar reaction del menú). Renombrar para que no haya dos "Pulso".
4. Inicio: una sola entrada por destino (quitar las tarjetas duplicadas de L:89-90), Redes arriba, "Mente" y "Arcade" como secciones. Quitar "NUEVO" fijo (V:97) y el texto "Sin niveles ni logros".
5. `dailyPlan()` pasa a usar Redes y Mente.
6. Corregir los distractores de Última huella (solo 0 a 9).
7. Transición animada al entrar y salir de Player (L:57) y salida animada del feedback (Pl:80).

**Aceptación:** como máximo 14 juegos visibles; un historial con ids antiguos abre el inicio, el jardín y los resultados sin errores (prueba en `lab-core`); las pruebas existentes (`RulesTest`, `MathGamesTest`, `NetSecTest`…) pasan; Última huella nunca ofrece valores fuera de 0 a 9 (prueba nueva).

### Lote B: Mecánicas y animación
1. Los tableros piloto (Subnet, Inbox, Firewall) también en Relámpago y en 3 min (`Rounds.make`/`Pilot.make` en NV:103 y SP:79).
2. Anzuelo: lanzar la tarjeta hacia el lado elegido y no repetir cebos en la misma partida.
3. Bits: tablero de interruptores (`Kind.BITS`) con suma en vivo.
4. Animaciones de Destellos y Ecos, Pulso (reaction), Última huella y Transforma (4.3, puntos 4 a 7); barrido de Firewall; marca de luz perdida en Pulso.
5. Parejas con contenido de redes (`PairGame.newDeck` acepta un banco de pares); guardar el resultado de Parejas, Pulso y la Ruta espacial (por ejemplo, `ResultRow` con `total` = intentos, o prefs de historial).
6. `Particles.kt` extraído de MeteorGame y señales `LEVEL_UP`/`UNLOCK`; limitar la vibración al arrastrar en Subnet.
7. "Estudio" (sin tiempo) guarda la lista de errores.

**Aceptación:** en Relámpago y 3 min, Subnet, Puertos y Anzuelo usan su tablero; ningún cebo se repite en 12 rondas salvo un repaso marcado; con "Animaciones" desactivado no corre ninguna animación nueva (comprobar con `LocalMotionEnabled=false`); las pruebas de `PilotTest` y `PairGameTest` cubren el banco de redes.

### Lote C: Enganche ético
1. `Rewards.kt` (cofre determinista y límite diario) y `Missions.kt` (3 misiones al día) en `lab-core`, con pruebas.
2. Álbum: `key` estable en los bancos (puertos, cebos, ataques, indicios de logs, consultas) y `AlbumPage`.
3. "Otra vez" y fantasma del récord en `ResultScreen`, `SessionSummary` y Relámpago.
4. Celebraciones en rango, desbloqueo, récord, cofre y misión.
5. Cofre en la pausa: `PracticeSummary.chestsPending`, `LabLaunch.chest`, acción en `FocusOverlay`.

**Aceptación:** un cofre nunca cambia minutos ni bloqueos de Foco (prueba: `Rewards` no expone tiempo); máximo de 3 cofres al día probado; el mismo `resultId` da siempre el mismo cofre; el álbum sobrevive a la copia de seguridad y restauración (`Store.export/restore`); el botón de la pausa solo aparece con un cofre ganado pendiente y abre la misión de 3 min, no el catálogo.
