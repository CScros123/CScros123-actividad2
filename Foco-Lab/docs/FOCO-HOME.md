# Foco Home (etapas 1 a 6)

Sin compilar ni ejecutar (por indicación del usuario). Pruebas escritas, no ejecutadas.
Lógica pura en `foco-core/.../core/home/` (con pruebas). Android en `app/.../runtime` y `app/.../ui`.

## Decisiones (y por qué)
| Tema | Decisión |
|---|---|
| DataStore | **SharedPreferences** (`foco-home-state`). DataStore sería una dependencia nueva para 7 campos; Weekend, DNS y el ambiente ya usan SharedPreferences. |
| `racha_dias` | **Días cumplidos en total** (20 min invertidos o 1 reto superado). Nunca vuelve a 0, igual que en Lab. Se celebran 3, 7 y 14. |
| `proximo_paso` | Es el mismo dato que antes (`AppConfig.intention`): no hay nada que migrar. |
| Recalcular el estado | Cuando se escribe en el log (`InspectorLog.usage/event`, `BlockLog.record`) y al mostrarse el inicio. Sin temporizador. |
| DNS: cómo se prueba | Sin abrir conexiones (Foco no tiene INTERNET): usa `VALIDATED` y `LinkProperties.isPrivateDnsActive()`, que ya calcula Android (`isPrivateDnsBroken()` es API oculta). |
| Mensajes | Notificación silenciosa con un botón. Al tocarla se abre HomeActivity directamente (Android 12+ prohíbe el trampolín). |
| Arranque | No existe una clase Application. Lo pesado de `MainActivity.onResume` (Bank.sync, Inspector, categorías) se hace tras el primer dibujado, en el hilo principal (Bank.sync avisa al servicio, que toca la pausa). |

## Lo que la guía pedía quitar y NO existía
La **escala de grises, la deuda acumulada y la pulsación larga** (etapa 6) no están en el código: no había nada que eliminar. Los únicos grises del inicio son los iconos de ocio del cajón, que la etapa 2 pide expresamente.

## Contradicción a decidir: «seguir en ocio sin saldo»
Hoy, sin saldo, el ocio **se bloquea** (Regla 4). Quitar ese bloqueo aflojaría una regla, y Foco nunca afloja, así que **no lo he quitado**. Por eso `minutos_extra` solo cuenta el ocio medido con el ocio ya bloqueado (fugas: latencia, pausa que no llegó a mostrarse) y en la práctica será casi 0. El recordatorio de «ocio sin saldo» y la consecuencia de más de 15 min solo se activan con esas fugas. Si quieres que el ocio sin saldo deje de bloquearse y pase a contarse como extra, dímelo: es un cambio de regla.

## Estados (etapa 1.2)
NOCHE de 21:00 a 07:00 (o con la hora automática apagada). DESPERTAR desde el primer desbloqueo después de las 05:00 hasta las 07:00 o hasta completar los 3 pasos; las reglas siguen siendo las de noche. DISTRAIDO con 5 min de ocio o 3 intentos en 30 min. PRODUCTIVO con 10 min productivos o de retos (un reto cuenta 3 min) y 0 de ocio. NEUTRO, todo lo demás. Histéresis de 5 min. PRODUCTIVO se mantiene 15 min frente a NEUTRO; DISTRAIDO no espera ese margen.

## Vigilancia de Ajustes (etapa 5.8)
Solo en `com.android.settings`. Lee la clase y hasta 200 textos visibles y **no guarda nada leído**, solo el tipo de pantalla.
| Pantalla | Detección | Depende de One UI | Acción |
|---|---|---|---|
| Wi-Fi | clase `WifiSettings`/`NetworkProviderSettings`, o texto «Wi-Fi» | texto sí | registro |
| Bluetooth | clase `Bluetooth*`, o texto | texto sí | registro |
| Batería | clase `PowerUsage*`/`BatterySaver*`, o texto «Batería» | texto sí | registro |
| Sonido | clase `SoundSettings`, o «Sonidos y vibración» | texto sí | registro |
| Fecha y hora | clase `DateTimeSettings`, o «Fecha y hora» | texto sí | aviso + registro |
| DNS privado | clase `PrivateDns*`, o «DNS privado» + «Automático»/«Nombre de host» (el diálogo, no la fila) | **sí** | inicio + incidente, **solo si el DNS ya está bien puesto** (si está roto, hay que poder arreglarlo) |
| Accesibilidad > Foco | texto «Usar Foco» | **sí** | inicio + incidente |
| Info de la app Foco | «Foco» + «Forzar detención»/«Desinstalar» | **sí** | inicio + incidente |
| VPN | clase `VpnSettings`, o «VPN» | texto sí | registro |

En One UI casi todo es `SubSettings`, así que manda el texto. Tras una actualización de One UI hay que volver a probar las tres filas marcadas en **sí**.

## Comandos (los ejecutas tú)
```bash
./gradlew :foco-core:test :lab-core:test :app:testDebugUnitTest :lab:testDebugUnitTest
./gradlew :app:installDebug
adb shell pm grant es.foco.next android.permission.WRITE_SECURE_SETTINGS
adb shell am start -a android.settings.HOME_SETTINGS
```
El tercero activa el DNS automático; sin él, el DNS automático no hace nada y lo registra. El cuarto vuelve a tu inicio de siempre.

## Diseño del prototipo aprobado (Prototipo_Launcher_Antidistracción)
- Paleta NEUTRO = la del prototipo (#DCE4DC salvia, colina #667B66, tarjetas #F6F8F4, texto #1C2920, acento #355C40, sol #F1D7A5). Las demás paletas añaden colina y texto secundario.
- Arriba: reloj (54 sp), fecha y estado con punto que «respira» (solo con animaciones permitidas). Atajo a Foco: mantener pulsado el reloj.
- Tarjetas: **Banco de tiempo** (saldo en insignia; «Próximo paso … Iniciar →»: tocar = empezar o editar, mantener = editar) y **Reto rápido** (nivel, ⓘ con la Regla 4 real, «Jugar ahora»).
- **Protección activa**: barra plegable con la píldora del DNS (ámbar solo si pide acción). Mantener pulsado = Ajustes.
- Colina (zona del pulgar): dock de 5 ranuras («+» fija una app; mantener pulsado quita), buscador y «Todas las apps». El cajón tiene el buscador abajo y una etiqueta por app: Libre / Usa banco / Bloqueada.
- **No se copió la lógica de simulación del prototipo** porque contradice las reglas: abrir ocio «−2 min» (en realidad son bloques de 5 min, gratis o pagados con el banco, y los cobra el servicio); «repetir +1 min» (en realidad, 60 % y luego 20 %); «leer 5 min = +3 al banco» (no existe ese pago).

## Foco Home en Jetpack Compose (versión 2)
`:app` ya compila Kotlin + Compose, con las mismas versiones que `:lab`. El resto de Foco sigue en Java.

| Archivo (app/.../ui) | Qué hace |
|---|---|
| `HomeActivity.kt` | Actividad Compose: edge-to-edge, permisos, Aduana, perfiles, gestos y batería |
| `HomeViewModel.kt` | Estado: EstadoActual, apps, uso del día (UsageStats), semana (registro), reto, dock y sistema |
| `HomeScreen.kt` | Estructura estable (`statusBarsPadding` + scroll propio), gestos, pie sobre la colina y compañero |
| `HomeLandscape.kt` | Paisaje Canvas y paleta animada en 1,5 s (`LocalHome`, `LocalMotion`) |
| `Companions.kt` | Compañero vivo: Bonsái zen, Fogata de campamento, Piedras en equilibrio |
| `HomeCards.kt` | Banco (con resumen semanal desplegable), reto rápido, despertar, noche y rol |
| `ProtectionBar.kt` | «Protección activa» plegable |
| `AppDrawer.kt` | Cajón limpio con índice A–Z |
| `AduanaSheet.kt` | Peaje de intención |
| `WifiProfilesDialog.kt` | Perfiles por red |
| `HomeCrashGuard.kt` | Modo mínimo tras 2 cierres en 10 min |

- **Lógica pura** en `foco-core/.../home`: `CompanionMood` y `Aduana`, probadas en `CompanionMoodTest`.
- **Servicio:** `AduanaStore` (el servicio cierra la app al cumplirse el tiempo elegido), `WifiProfiles` (No molestar y ocio apartado por red) y el descanso real en `HomeHub.screenOff/restCheck`.

**Decisiones del usuario:**
- **Aduana = salir antes.** El bloque se paga igual; elegir 1 o 3 min solo acorta.
- **El descanso real da un premio visual, no minutos.** Regla 4 intacta.
- **Perfiles por Wi‑Fi con permiso de ubicación.**

**Gestos:**
- Tirar hacia abajo con el contenido arriba del todo: notificaciones.
- Tirar hacia arriba al final del contenido o sobre el pie: todas las apps.
- Doble toque en el cielo (reloj y fecha): bloquear pantalla.
- Mantener el reloj: abrir Foco.

Notificaciones y bloqueo usan `performGlobalAction` del servicio de Accesibilidad.

## Paneles laterales y fluidez (versión 3)
- **Tres paneles** (HorizontalPager): ‹ **Ajustes** · **Inicio** · **Hacer** ›. El paisaje, la colina, el compañero y el dock no se mueven.
  - Inicio y «atrás» vuelven al centro. Hay puntos de página y etiquetas en la colina.
- **Ajustes** (`SidePanels.kt`):
  - Compañero, con vista previa en vivo.
  - Reducir movimiento y ahorro de Foco.
  - Interruptores de los tres gestos.
  - Permisos con su porqué: Accesibilidad, inicio, acceso de uso, notificaciones, No molestar, modificar ajustes, ubicación.
  - Perfiles por red y Foco completo.
- **Hacer**:
  - Tareas pequeñas (`TaskList` puro + `TaskStore`). La chincheta convierte una en el próximo paso.
  - Las hechas se tachan y desaparecen a las 24 h. Sin puntos ni minutos (Regla 4 intacta).
  - Practicar: reto rápido, Práctica 3 min, todos los juegos.
  - Enfoque: próximo paso, y Bloqueos y Pomodoro en Foco.
- **Fluidez:**
  - Las animaciones infinitas se leen en la fase de dibujo (Canvas o graphicsLayer), así que redibujan sin recomponer.
  - El plan, los perfiles y el bloque gratis de mañana se cachean en el ViewModel; ya no se leen en cada fotograma.
  - Los iconos se convierten una vez, fuera del hilo principal, y la lista los reutiliza (`contentType` en el LazyColumn).
  - La actividad pide el modo de pantalla más rápido (120 Hz).
- **Medir la fluidez:** la compilación debug de Compose es mucho más lenta. Usa `./gradlew :app:assemblePerf`, que es como release (R8, sin depuración) pero firmada con la clave de depuración, así que se instala encima.

## Búho de Foco (versión 4, sustituye al compañero de la colina)
- **Dibujo** (`OwlCompanion.kt`): Canvas «2,5D», con volumen por degradados radiales y giro simulado (la cara se desplaza y se estrecha). Sin dependencias 3D.
  - Arrástralo para girarlo; los ojos siguen el dedo. Tócalo para otra idea.
  - Parpadea, mueve el pico al hablar, y alas, cejas y penachos según el modo. Aureola en la palabra del día, partículas suaves.
  - Valores leídos en el dibujo: 120 Hz sin recomponer. Sin movimiento permitido queda quieto.
- **Cerebro** (`foco-core/.../home/OwlBrain.java`, con `OwlBrainTest`):
  - Modos con datos reales: calma, cambio de chip (DISTRAÍDO; ocre, sin rojo ni culpa), momento de leer (si hay app de lectura), destreza mental (reto rápido con su pago real), palabra del día (RV1909), pausa activa (≥45 min productivos seguidos), descanso (noche) y buenos días.
  - De noche solo calma o palabra del día.
- **No se copió del prototipo:** «+3 min por leer», «te sumo +2 al levantarte», «+1 min de calma» y la «vitalidad 92 %» inventada. La burbuja muestra los minutos invertidos de hoy y el banco reales.
- **Ajustes** (panel izquierdo): tono cercano («bro») o neutro, palabra del día, mostrar la burbuja.

## Auditoría y pulido (versión 5)
- **Sin animaciones infinitas en reposo.** El paisaje queda estático (antes redibujaba la pantalla entera en cada fotograma). El punto de estado respira 3 veces al cambiar de estado y se para.
- **Búho:** a 120 fps solo cuando está despierto (al tocarlo, arrastrarlo, hablar o cambiar de modo, unos segundos). Dormido no redibuja nada salvo un parpadeo cada 3 a 5 s. Menos partículas.
- **Apps de lectura y Biblia:** se detectan una vez, al cargar las apps, y no en cada dibujado. El modo automático del búho se recalcula fuera de la composición.
- **Modo mínimo:** solo cuenta los fallos del inicio, no los de Cognitive Lab.
- **De noche** el cajón muestra «Cerrado hasta las 7:00» en el ocio, en vez de «Usa banco».
