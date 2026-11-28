# Auditoría de Foco + Cognitive Lab

Fecha: 2026-11-06 · Rama `main` · Commits `Auditoría 1` a `Auditoría 5`.

## Resumen

Revisión de los caminos calientes (servicio de Accesibilidad y respaldo `GuardService`), de la batería, de la seguridad del
modo estricto y de la estructura del código. Lo más importante que se ha corregido:

1. **El hilo principal ya no escribe en disco en cada cambio de app.** `Repository.sample()` guardaba `focus-lite-v1.json`
   (con fsync) en cada `evaluate(true)`, es decir, en cada cambio de ventana y cada 500 ms en una web bloqueada.
   `RuleStore.decide()` hacía lo mismo con `block-rules-v1.json`. Ahora los puntos de control del servicio se escriben en un
   hilo de E/S propio, fundidos y numerados (nunca pisa una escritura más nueva). El formato de los archivos no cambia.
2. **El modo estricto tenía atajos dentro de la propia app.** Con el candado puesto se podía quitar apps de ocio del plan
   clásico, añadir la app bloqueada a «Otras apps esenciales» (quedaba libre de todo bloqueo), restaurar una copia antigua
   del plan, elegir Spotify/Apple Music como música de la pausa (la app elegida nunca se bloquea), apagar la vigilancia del
   DNS o, sin candado, apagar el modo estricto durante un bloqueo con nombre. Todo cerrado; el plan clásico sigue la regla
   «solo más estricto» con `PlanChange` (foco-core, con test), igual que los bloqueos con `RuleChange`.
3. **El respaldo sin Accesibilidad podía tapar el teléfono.** `GuardService` solo eximía `FocusEngine.essential` e Inicio;
   con una lista blanca bloqueaba el marcador, las llamadas en curso, los bancos y la app de música elegida. Ahora usa las
   mismas esenciales que la Accesibilidad.
4. **Batería del respaldo:** consultaba Acceso de uso cada segundo también con la pantalla apagada, y el candado se
   guardaba en preferencias en cada lectura (hasta 1 escritura por segundo).
5. **Copia entre teléfonos:** desde Android 12, `allowBackup="false"` no evita la transferencia entre dispositivos
   (Smart Switch). Se añaden `dataExtractionRules` que excluyen todo.

Recuento: **25 hallazgos** · Altas 5 (5 corregidas) · Medias 11 (10 corregidas, 1 pendiente mitigada) · Bajas 9 (6 corregidas, 3 pendientes).
Comprobado sin cambios (correcto): componentes exportados, `PendingIntent` inmutables, SQL parametrizado, receptores
dinámicos `RECEIVER_NOT_EXPORTED`, reciclado de nodos, tickers de animación que se paran al ocultarse, reglas R8.

## Tabla de hallazgos

| ID | Área | Sev. | Archivo:línea | Problema | Estado | Nota |
|----|------|------|---------------|----------|--------|------|
| A1 | A/B | Alta | `data/Repository.java:57` | `sample()` escribía el JSON completo con fsync en el hilo principal en cada `evaluate(true)` (cada cambio de app, cada eco del filtro web). Riesgo de tirones y ANR en el servicio de Accesibilidad. | Corregido | `AtomicJsonFile.writeLater()`: hilo `foco-io`, instantáneas fundidas y numeradas. Los cambios del usuario (`saveConfig`, `importBackup`) siguen síncronos para poder deshacer si fallan. `resetForTests()` vacía lo pendiente. |
| A2 | A/B | Media | `data/RuleStore.java:69` | `decide()` (cada minuto o al cambiar el estado) y el fin de sesión escribían en el hilo principal. | Corregido | Igual que A1. `flush()` (cierre del servicio) sigue síncrono y gana a lo pendiente. |
| A3 | A/B | Media | `data/Repository.java:40` | `config()` hacía ida y vuelta por JSON (codificar + decodificar todas las listas) 2–3 veces por evento. | Corregido | Copia directa de los conjuntos; mismo resultado (lo guardado ya está saneado). |
| A4 | A/B | Media | `runtime/FocusAccessibilityService.java:234` | Cada cambio de app consultaba `getWindows()` dos veces (evento + `evaluate`). | Corregido | `verifiedHint`: `evaluate` reutiliza la consulta del mismo evento; se consume al entrar, nunca queda obsoleta. |
| A5 | A | Media | `runtime/FocusAccessibilityService.java:358` | Al desactivar la Accesibilidad (`onUnbind`) la pausa seguía puesta hasta `onDestroy` mientras `GuardService` ya ponía la suya: dos pantallas a la vez. | Corregido | `onUnbind` quita la pausa, cancela temporizadores y guarda los bloqueos antes de despertar al respaldo. |
| A6 | A/E | Baja | `runtime/FocusAccessibilityService.java:49` | Modo estricto y compras: un Toast por cada cierre (cada 300 ms mientras Ajustes insistía); el HOME diferido se acumulaba. | Corregido | `toast()` con enfriamiento de 3 s por mensaje; un solo `Runnable` HOME que se reprograma. |
| A7 | A/B | Baja | `runtime/FocusAccessibilityService.java:88` | `purchaseCheck` recorría los bloqueos antes del filtro de 300 ms. | Corregido | Filtro barato primero. |
| A8 | A | Baja | `runtime/FocusAccessibilityService.java:330` | La lista de lanzadores (`homes`) solo se leía al conectar: un lanzador nuevo podía quedar bloqueado. | Corregido | Se recalcula al instalar una app. |
| A9 | A | Alta | `runtime/GuardService.java:116` | El respaldo no eximía marcador, llamada en curso, bancos ni música elegida: con una lista blanca tapaba el teléfono. | Corregido | Mismas esenciales que `FocusAccessibilityService.essential()` (`CallMonitor`, `BankAppGuard`, `PauseMusic`) y `calls.active()`. |
| B1 | B | Alta | `runtime/GuardService.java:96` | Sin Accesibilidad, consulta de Acceso de uso cada 1 s también con la pantalla apagada o bloqueada. | Corregido | Pantalla apagada: ninguna consulta; se despierta con `SCREEN_ON`/`USER_PRESENT` (receptor dinámico) y, por si acaso, cada 30 s. |
| B2 | B/C | Media | `runtime/StrictAdmin.java:39` | `lock()` guardaba el candado en preferencias en cada lectura (1/s con el respaldo, cada comprobación en Ajustes). | Corregido | `mustPersist`: solo con reinicio, salto del reloj > 5 s o cada minuto. Sin guardar, la siguiente lectura parte de la última observación guardada: el contador avanza igual. |
| C1 | C | Alta | `ui/MainActivity.java:194` | Con el candado del modo estricto se podía quitar apps de ocio o añadir esenciales en el plan clásico. | Corregido | `PlanChange.reason()` (foco-core, `PlanChangeTest`). |
| C2 | C | Alta | `ui/MainActivity.java:183` | «Restaurar copia de Foco Ligero» con candado sustituía el plan y los tiempos. | Corregido | Rechazado con candado (al pulsar y al confirmar). |
| C3 | C | Media | `ui/SoundSettingsCard.java:45` | Elegir Spotify/Apple Music como música de la pausa los volvía esenciales (nunca bloqueados). | Corregido | Con candado no se puede elegir una app de música nueva; aviso visible. |
| C4 | C | Media | `ui/DnsSettings.java:49` | Con candado se podía desactivar la vigilancia del DNS. | Corregido | Solo puede encenderse. |
| C5 | C | Media | `ui/StrictModeCard.java:50` | Sin candado, «Desactivar modo estricto» solo miraba el bloqueo clásico, no los bloqueos con nombre ni la sesión. | Corregido | También `RuleStore.anyActive()`. |
| C6 | C | Media | `AndroidManifest.xml` | `allowBackup="false"` no detiene la transferencia entre dispositivos desde Android 12: se podría restaurar un estado antiguo (sin candado). | Corregido | `res/xml/data_extraction_rules.xml` excluye todo en nube y transferencia; `fullBackupContent="false"`. |
| C7 | C | Media | `core/StrictLock.java:98` + `core/StrictGuard.java:45` | Adelantar la hora (Fecha y hora no se cierra sin bloqueo) y reiniciar antes de la siguiente observación acortaba el candado. | Pendiente (mitigado) | `GuardService` anota el reloj al instante con `TIME_SET`/`TIMEZONE_CHANGED`/`SHUTDOWN` (commit síncrono) y `mustPersist` guarda cualquier salto. Queda una ventana mínima si el teléfono se apaga a la fuerza. Cerrarla del todo exige cambiar `rebootCountsWallTimeForward` y `alwaysLeavesNetworkAndClock` (tests existentes: no se tocan sin tu decisión). |
| D1 | D | Baja | `runtime/DeviceState.java` | `awake()`, lista de lanzadores y caché de «tiene icono» duplicadas en los dos servicios. | Corregido | `DeviceState` compartido. |
| D2 | D | Baja | `ui/Documents.java` | Bucle de lectura/escritura de documentos copiado en 5 sitios con límites distintos (1 000 000 / 1 048 576). | Corregido | `Documents.read/write`, un solo `MAX_IMPORT`. |
| D3 | D | Baja | `lab/.../ReminderSettings.kt` | Composable sin ningún uso. | Corregido | Borrado. `LearningReminder` se conserva (lo usa `ReminderTimeTest`). |
| E1 | E | Media | `ui/MainActivity.java:162`, `res/values/strings.xml:7` | Textos de privacidad desfasados: «no lee direcciones web», «Acceso de uso solo al abrir el Registro», «esta instalación no tiene ese permiso», versión «0.58» fija. | Corregido | Textos al día (filtro web, compras, modo estricto, respaldo); versión real desde `PackageManager`. La descripción de Accesibilidad conserva su inicio (lo usa `StrictGuard.FOCO_PAGE`). |
| D4 | D | Baja | `runtime/FocusAccessibilityService.java` | Clase de 360 líneas con cinco responsabilidades (medición, web, estricto, compras, DNS). | Pendiente | Extraer `WebGuard`, `StrictScreenGuard`, `PurchaseCheck` (ver Pendientes). |
| D5 | D/E | Baja | `ui/FocusOverlay.span`, `ui/Design.duration` | Dos formatos de duración distintos. | Pendiente | Unificar en foco-core con test. |
| E2 | E | Baja | `runtime/GuardService.java:show`, `ui/StatsActivity.java:144` | Pantalla del respaldo sin los helpers de `Design`; rojo/verde fijos en Estadísticas. | Pendiente | Funciona en claro/oscuro (usa `R.color`), solo es estética. |

Notas de revisión sin hallazgo:
- **Manifiesto:** solo `MainActivity` (lanzador), el servicio de Accesibilidad (`BIND_ACCESSIBILITY_SERVICE`) y `StrictAdmin`
  (`BIND_DEVICE_ADMIN`) están exportados; todo lo demás `exported="false"`. Todos los `PendingIntent` llevan `FLAG_IMMUTABLE`.
- **Enlace de playlist:** `PauseMusic.validLink` exige `spotify:`, `https://open.spotify.com/` o `https://music.apple.com/` y el
  intent lleva `setPackage()`: no puede abrir otra app.
- **SQL:** todas las consultas de `LogDatabase` usan `?`; no hay concatenación de datos.
- **Nodos de Accesibilidad:** todos los caminos reciclan; además, con minSdk 33 `recycle()` ya no hace nada.
- **Animación:** `PauseVideoView`, `AmbientView`, `PauseMessageCard` y `FloatingLeaves` se paran al ocultarse, con pantalla
  apagada y al desacoplarse.
- **R8:** las clases nuevas no usan reflexión; `org.json` tampoco. No hacen falta reglas.

## Optimización (antes / después)

| Medida | Antes | Después |
|--------|-------|---------|
| Escrituras a disco en el hilo principal por cambio de app | 1 (`focus-lite-v1.json` completo + fsync) | 0 (hilo `foco-io`, varias seguidas = 1 escritura) |
| Escrituras en hilo principal con una web bloqueada delante | hasta 2/s | 0 |
| `getWindows()` por cambio de app | 2 | 1 |
| Copia de configuración por evento | codificar + decodificar JSON, 2–3 veces | copia de conjuntos |
| Consultas de Acceso de uso con pantalla apagada (respaldo) | 3 600/h | 0 (despertar cada 30 s solo para mirar el candado) |
| Escrituras del candado en preferencias | 1 por lectura (≈1/s con respaldo) | ≤ 1/min + reinicios, saltos de reloj, cambio de hora, apagado |
| Avisos del modo estricto | hasta ~3/s | 1 cada 3 s por mensaje |

## Seguridad

- **Modelo «solo más estricto» completo:** con el candado puesto, los bloqueos (`RuleChange`) y ahora también el plan clásico
  (`PlanChange`), la música de la pausa y la vigilancia DNS solo pueden endurecerse; restaurar copias (plan o bloqueos) está
  prohibido. Sin candado pero con el modo estricto activo, nada se relaja durante un bloqueo, ni siquiera apagar el modo.
- **Reloj:** `StrictLock` cuenta con el reloj monótono; tras un reinicio, con la pared. El respaldo anota el reloj al cambiar
  la hora o al apagar, y cualquier salto se guarda en el momento (C7).
- **Datos fuera del teléfono:** ni copia en la nube ni transferencia entre dispositivos; las copias manuales (JSON/CSV) las
  decide el usuario. Las copias importadas están limitadas a 1 MB, 50 bloqueos y paquetes con formato válido.
- **Registros:** `Log.w/e` solo con mensajes fijos y excepciones; ningún paquete, URL ni texto leído. En release, R8 quita
  `Log.v/d/i`.
- **Límites honestos (sin cambio):** modo seguro, ADB o restablecimiento de fábrica siguen saltándose el modo estricto.

## Arquitectura

```
                    ┌─────────────────────────── :app (Java, Views) ───────────────────────────┐
 Android ──eventos──▶ runtime/FocusAccessibilityService ──▶ ui/FocusOverlay (pausa, escena, música)
   │                 │   ├─ ForegroundWindows, DeviceState, CallMonitor, BankAppGuard, DnsMonitor
   │                 │   ├─ filtro web · modo estricto (Ajustes) · compras (Google Play)
   │                 │   └─ log/BlockLog ──(hilo foco-log)──▶ foco-log.db
   │                 ├─ runtime/GuardService (respaldo con candado, Acceso de uso + «Aparecer encima»)
   │                 ├─ runtime/StrictAdmin (administrador + candado en prefs) · DailyReminder (arranque)
   │                 ├─ data/Repository ─┐                                 (hilo foco-io)
   │                 ├─ data/RuleStore ──┴─ AtomicJsonFile ──▶ focus-lite-v1.json · block-rules-v1.json
   │                 └─ ui/MainActivity · BlocksScreen · StatsActivity · RegistroActivity · StrictModeCard
   │                                   │ usa
   │                 ┌──────── :foco-core (Java puro, con tests) ────────┐
   │                 │ FocusEngine/FocusState (Foco clásico, sin cambios) │
   │                 │ RuleEngine/RuleState/BlockRule/Schedule/FocusSession│
   │                 │ WebFilter · PurchaseGuard · DnsGuard · ScreenTime   │
   │                 │ StrictGuard · StrictLock · RuleChange · PlanChange  │
   │                 └─────────────────────────────────────────────────────┘
   └─ :lab (Kotlin/Compose, LabActivity no exportada) ─▶ :lab-core (lógica) · :lab-data (Room) · :lab-design (tokens)
```

Responsabilidades:
- **foco-core:** todas las decisiones (cuándo bloquear, qué cambio es más estricto, qué pantalla es peligrosa). Sin Android, con tests.
- **app/runtime:** observar el sistema y ejecutar decisiones (ventanas, red, audio, alarmas, servicios).
- **app/data:** persistencia atómica y validación de lo importado.
- **app/log:** registro de mejor esfuerzo en su propio hilo; nunca bloquea ni lanza.
- **app/ui:** pantallas Views con `Design`; la pausa vive en una ventana de Accesibilidad.
- **lab\*:** juegos y práctica, aislados; Foco solo abre `LabActivity` y lee su resumen fuera del hilo principal.

## Pendientes recomendados (por prioridad)

1. **C7 — cerrar del todo el reloj:** decidir si con candado se cierra también «Fecha y hora» (cambia `alwaysLeavesNetworkAndClock`)
   o si tras un reinicio el tiempo de pared acreditado se limita (cambia `rebootCountsWallTimeForward`). Ambos requieren tu permiso
   para cambiar un test existente.
2. **Tests de la app (Robolectric):** no se pudieron ejecutar aquí (Maven devolvió 429 al descargar `nativeruntime-dist-compat`).
   Ejecutar en el PC: `./gradlew :app:testDebugUnitTest` (cubre `RepositoryTest` con la escritura en segundo plano).
3. **Lint:** `:app:lintDebug` falla por la misma descarga (necesita el modelo de tests). Ejecutarlo en el PC y revisar.
4. **D4 — partir `FocusAccessibilityService`:** extraer `WebGuard`, `StrictScreenGuard` y `PurchaseCheck` con interfaz pequeña
   (`check(pkg, stateChange)`); el servicio quedaría en orquestación.
5. **D5 — un solo formato de duraciones** en foco-core con test.
6. **E2 — respaldo con `Design`** (mismo estilo que la pausa) y colores de Estadísticas en `colors.xml`.

## Cómo verificar en el teléfono

1. **Fluidez:** activa *Opciones de desarrollo › Modo estricto activado* (la pantalla parpadea cuando el hilo principal toca
   el disco). Abre y cierra varias apps de ocio seguidas y una web bloqueada en Chrome: antes parpadeaba en cada cambio de
   app; ahora no (solo al guardar un cambio tuyo en Foco).
2. **Plan clásico con candado:** activa el modo estricto con candado de 1 h. En *Mi plan* intenta quitar una app de ocio →
   «Modo estricto (solo más estricto): no se pueden quitar apps de ocio». En *Ajustes › Otras apps esenciales* añade una →
   rechazado. *Restaurar copia de Foco Ligero* → rechazado. Añadir una app de ocio → permitido.
3. **Música y DNS con candado:** en *Sonido*, Spotify/Apple Music aparecen desactivados (salvo el ya elegido); *Desactivar
   vigilancia* del DNS → rechazado.
4. **Respaldo:** con candado, desactiva la Accesibilidad de Foco (desde ADB o al terminar el candado de prueba). Abre una app
   bloqueada → pantalla del respaldo. Abre el Teléfono o tu banco con una lista blanca activa → **no** se tapa. Apaga la
   pantalla 10 min y mira *Ajustes › Batería › Foco*: sin actividad en ese tiempo.
5. **Relevo:** vuelve a activar la Accesibilidad → la pantalla del respaldo desaparece y nunca se ven dos pausas a la vez.
6. **Hora:** con candado de 1 h, cambia la hora +1 día en *Fecha y hora* y reinicia: el candado sigue (faltan ~60 min).
   Efecto conocido y aceptado (más estricto): si después de reiniciar devuelves la hora atrás, el candado no avanza hasta que
   el reloj real alcance la hora adelantada.
7. **Copia entre teléfonos:** en Smart Switch, Foco aparece sin datos que transferir.
