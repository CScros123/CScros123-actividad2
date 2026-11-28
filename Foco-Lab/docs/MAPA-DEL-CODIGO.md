# Mapa del código: dónde cambiar qué

Guía para hacer cambios en Foco + Lab sin tener que leer todo el proyecto. Busca en la tabla lo que quieres cambiar, abre ese archivo y busca el nombre indicado (Ctrl+F). Los nombres no cambian aunque se muevan las líneas.

Rutas abreviadas:

| Abreviatura | Carpeta real |
|---|---|
| `foco-core/` | `foco-core/src/main/java/es/foco/next/core/` |
| `app/` | `app/src/main/java/es/foco/next/` |
| `lab-core/` | `lab-core/src/main/kotlin/personal/qqq/braintraining/core/` |
| `lab/` | `lab/src/main/java/personal/qqq/braintraining/` |
| `lab-design/` | `lab-design/src/main/java/personal/qqq/braintraining/design/` |

## 1. Cómo está organizado

```
Foco-Lab
├── foco-core   Reglas de Foco en Java puro: presupuesto, ventanas, reglas absolutas, bloqueos. Sin Android.
├── app         La app Android de Foco (Java): servicio de Accesibilidad, pausa, pantallas, registro.
│   ├── data      Guardar y leer el plan y el estado (Repository).
│   ├── runtime   Lo que corre en segundo plano: servicio, audio, DNS, banco, Weekend Lock.
│   ├── log       Registro de bloqueos y del Inspector (base de datos, CSV).
│   └── ui        Pantallas y pausa.
├── lab-core    Reglas de los juegos de Lab en Kotlin puro: generan rondas y deciden aciertos. Sin Android.
├── lab-data    Base de datos de Lab (Room): resultados, progreso, preferencias.
├── lab-design  Tema visual de Lab (colores, tipografía).
└── lab         Pantallas de Lab en Compose: un tablero por juego, partida, resultados, sonido.
```

**Regla de oro:** las reglas (qué pasa) están en los módulos `-core`, y lo que se ve o se oye está en `app` y `lab`. Si cambias una regla, cámbiala en el `-core` y no en la pantalla. Así la prueban los tests sin necesidad de teléfono.

## 2. Juegos de Lab

Cada juego tiene dos archivos:
- **`lab-core/<Juego>.kt`**: las reglas y **todos sus números** (velocidad, niveles, puntos).
- **`lab/<Juego>Board.kt`**: el dibujo y la interacción.

| Quiero cambiar… | Archivo | Busca |
|---|---|---|
| **Órbitas**: velocidad de las luces | `lab-core/Orbit.kt` | `STEP_MS` (bloque «AJUSTES DE ÓRBITAS») |
| Órbitas: tiempo para responder, giro, pausas, destellos, bono | `lab-core/Orbit.kt` | el resto del bloque «AJUSTES DE ÓRBITAS» |
| Órbitas: cuántas luces por ronda, qué ronda trae cada novedad | `lab-core/Orbit.kt` | `fun make(` |
| Órbitas: colores, cometa, núcleo, barra superior, mapa de atención | `lab/OrbitBoard.kt` | `OrbitCyan`… al principio; `drawOrbit`, `OrbitHeader`, `OrbitAttention` |
| **Ruta de paquetes**: velocidad del paquete, routers por nivel | `lab-core/Route.kt` | `HOP_MS`, `fun level(` |
| **Vigía de alertas**: velocidad, alertas por tramo | `lab-core/Vigil.kt` | `fun level(` (tabla `VigilLevel`) |
| **Turno anti-DDoS**: ritmo, oleadas, combo | `lab-core/Ddos.kt` | `fun level(`, `WAVES`, `PEAK_BPM`, `MAX_TIER`, `BRIEF_MS` |
| **La maleta del técnico**: objetos por nivel, tiempo a la vista | `lab-core/Suitcase.kt` | `fun level(`, `SHOW_MS`, `MAX_TRAY` |
| **Parejas de incidentes**: cartas y tríos | `lab-core/PairGame.kt` | `fun make(` |
| Juegos de Redes (subredes, phishing, puertos…) | `lab-core/Pilot.kt`, `lab-core/NetSec.kt`, `lab-core/Firewall.kt` | `fun make(` |
| Nombre, descripción o instrucciones de un juego | `lab-core/Games.kt` | `Game("<id>",` |
| Retirar o mostrar un juego en los menús | `lab-core/Games.kt` | `retiredLoteA`, `retiredFase2`, `retiredFase3` |
| Rondas por partida y si van seguidas | `lab-core/Games.kt` | `object Formats` |
| Cuándo se sube o baja de nivel (85 % / 60 %) | `lab-core/Games.kt` | `object Adaptive` |
| Misiones diarias, cofres, álbum | `lab-core/Missions.kt`, `lab-core/Rewards.kt`, `lab-core/Album.kt` | `object Missions`; cofres por día: `DAILY_MAX` en `Rewards.kt` |
| Práctica corta de 3 minutos | `lab-core/ShortSession.kt` | `MAX_CHALLENGES`, `MAX_ACTIVE_MS` |
| Pantalla de partida (cabecera, pausa, panel entre rondas) | `lab/Player.kt` | `fun Player(` |
| Pantalla de resultado | `lab/ProgressViews.kt` | `fun ResultScreen(` |
| Inicio y menús de Lab | `lab/LiteApp.kt` (navegación), `lab/LabActivity.kt` | `fun LiteApp(`, `fun Home(` |
| Colores y tipografía de Lab | `lab-design/Theme.kt` | `object FocoTokens` |
| Color de cada área (Mente, Atención…) | `lab/LabActivity.kt` | `fun areaColor(` |
| Sonidos: volumen, tonos, notas pentatónicas | `lab/HostSound.kt` (`object GameTones`), `lab/Experience.kt` | `baseVolume`, `note(` |
| Lógica de la partida (empezar, pausar, guardar) | `lab/BrainViewModel.kt` | `fun start(`, `fun pause(`, `fun finish(` |

## 3. Reglas de Foco

| Quiero cambiar… | Archivo | Busca |
|---|---|---|
| Los 5 minutos de ocio por hora, el aviso de los 4 minutos | `foco-core/FocusEngine.java` | `BUDGET`, `HOUR`, `WARNING` |
| Noche absoluta (21:00–07:00) | `foco-core/AbsoluteRules.java` | `NIGHT_START`, `NIGHT_END` |
| Redes sociales que se bloquean siempre | `foco-core/AbsoluteRules.java` | `SOCIAL_PACKAGES`, `SOCIAL_DOMAINS` |
| Banco de minutos: máximo por día, máximo total, premio por subir de nivel | `foco-core/AbsoluteRules.java` | `EARN_DAY_MAX_MS`, `BANK_MAX_MS`, `LEVEL_UP_MS` |
| Inicio del Weekend Lock (viernes 18:00) | `foco-core/WeekendLock.java` | `START_HOUR` |
| Bloqueos con nombre, pausas permitidas | `foco-core/RuleEngine.java`, `foco-core/BlockRule.java` | `MAX_PAUSES` |
| Filtro web (sitios y palabras) | `foco-core/WebFilter.java` | `MAX_SITES`, `MAX_KEYWORDS` |
| Shorts, Reels, Spotlight | `foco-core/InAppFilter.java` | — |
| Espera antes de pausar si el DNS se apaga | `foco-core/DnsGuard.java` | `GRACE` |
| Modo estricto | `foco-core/StrictLock.java`, `foco-core/StrictGuard.java` | — |
| Tope diario de TikTok | `app/runtime/Supervisor.java` | `TIKTOK_LIMIT` |
| Qué apps son esenciales, de ocio o productivas | `foco-core/Categories.java`, `app/runtime/AppKinds.java` | — |
| Cómo decide el servicio en cada cambio de app | `app/runtime/FocusAccessibilityService.java` | el método con `FocusEngine.Input in` |
| La pausa que se ve encima de la app | `app/ui/FocusOverlay.java` | `public void block(` |
| Pantalla principal de Foco | `app/ui/MainActivity.java` | — |
| Pantalla de bloqueos, estadísticas, registro | `app/ui/BlocksScreen.java`, `app/ui/StatsActivity.java`, `app/ui/RegistroActivity.java` | — |
| Colores de Foco | `app/src/main/res/values/colors.xml` (y `values-night/`) | — |
| Días que se guarda el registro de bloqueos | `app/log/LogRetention.java` | `DAYS` |
| Hora que usan el servicio y el Weekend Lock (para pruebas) | `app/runtime/WallClock.java` | `now()` |

Antes de tocar una regla de Foco, lee `docs/REGLAS-ABSOLUTAS.md`: explica por qué cada regla es como es.

## 4. Cómo hacer un cambio sin romper nada

1. **Cambia** el número o el código (con la tabla de arriba).
2. **Formatea**: `./gradlew spotlessApply`. Deja el código con el estilo del proyecto; nunca cambia la lógica.
3. **Prueba solo lo que tocaste:**

   | Si tocaste… | Ejecuta |
   |---|---|
   | `foco-core` | `./gradlew :foco-core:test` |
   | `lab-core` | `./gradlew :lab-core:test` |
   | `lab` | `./gradlew :lab:testDebugUnitTest` |
   | `app` | `./gradlew :app:testDebugUnitTest` |

4. **Compila e instala:** `./gradlew :app:assembleDebug`, y después `adb install -r app/build/outputs/apk/debug/app-debug.apk`.
5. Si una prueba falla después de un cambio **intencional** (por ejemplo, cambiaste `STEP_MS` y la prueba esperaba el valor anterior), actualiza la prueba al nuevo valor. Si no tocaste nada de lo que prueba, el fallo es real: no lo ocultes.

Las pruebas de cada juego están en `lab-core/src/test/.../core/<Juego>Test.kt`; las de Foco, en `foco-core/src/test` y `app/src/test`.

## 5. Estilo del código

- Kotlin: ktfmt, estilo kotlinlang (4 espacios). Java: google-java-format, estilo AOSP (4 espacios). Configurado en `build.gradle` (Spotless) y en `.editorconfig`.
- `./gradlew spotlessCheck` avisa si algo quedó sin formatear.
- Los números que alguien querrá ajustar van como constante con nombre y un comentario de una línea, nunca sueltos en medio del código.
- Si Maven Central responde 429 al descargar dependencias: `./gradlew -I docs/mirror.gradle <tarea>`.

## 6. Documentación

- `README.md`: qué hace la app.
- `COMPILAR-EN-TU-PC.md`: preparar el PC y compilar.
- `docs/README.md`: índice de todos los documentos de diseño.
- `docs/historial/`: notas de versiones antiguas (0.51 a 0.58).

## Foco Home (inicio)
- Reglas puras: `foco-core/.../core/home/` (`HomeState`, `HomeSections`, `HomePalette`, `SettingsWatch`, `DnsFallback`, `Companion`). Pruebas en `foco-core/src/test/.../core/home/`.
- Estado actual y log: `app/.../runtime/HomeHub.java`. Mensajes: `CompanionNotifier.java`. DNS de respaldo: `DnsAuto.java`. Rol de inicio: `HomeRole.java`.
- Pantalla: `app/.../ui/HomeActivity.java`, `HomeSceneView.java`, `HomeApps.java`, `SystemPanel.java`, `CompanionSlot.java` (hueco del compañero, vacío).
- Lab: `QuickChallenge.kt` y `LabLaunch.game(...)`.
- Detalle y decisiones: `docs/FOCO-HOME.md`.
