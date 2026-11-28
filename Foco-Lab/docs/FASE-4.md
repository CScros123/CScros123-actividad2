# Fase 4 — Permisos y registro de bloqueos

Plan: `docs/PLAN-FOCO-LAB.md`, secciones 5A y "Fase 4". Rama `fase-4`, sobre la Fase 1.

> **Importante:** igual que la Fase 1, esta fase se preparó **sin compilar ni ejecutar Gradle**. Todo se revisó leyendo el código y con `grep`. La sección "Riesgos de compilación" lista lo que podría fallar, con archivo y línea.

## Resumen

- Base propia **`foco-log.db`** (no toca `focus-lite-v1.json`) con las tablas `block_event` y `usage_day` de la sección 5A.
- Escritura en **un solo hilo** de fondo (`foco-log`, cola acotada de 256; si se llena, se descarta el evento, nunca se espera). Todo con `try/catch`: **si el registro falla, el bloqueo sigue**. Nunca se escribe ni se lee la base en el hilo principal.
- **Poda de 90 días** (hoy + 89 días anteriores) una vez al día y al abrir el Registro.
- Eventos enganchados: `BUDGET_LOCK`, `ATTEMPT_WHILE_LOCKED` (servicio), `ALT_OPENED` y `HOME` (pausa). `DNS_OFF_LOCK`, `PRACTICE_STARTED` y `PRACTICE_FINISHED` ya existen en el modelo; las fases 2-3 solo tienen que llamar a la API estática (abajo).
- **UsageStats bajo demanda:** al abrir el Registro y una vez al día (en la primera decisión del servicio de cada día). Sin sondeo periódico. Contrasta los ms observados por Accesibilidad (los 7 días que guarda el motor) con los de Android y los guarda en `usage_day`. El motor de bloqueo **no** lo usa.
- Pantalla **Registro** (Views, mismos helpers `Design`): por día, bloqueos e intentos, detalle por app y contraste de tiempos. Exportación **CSV de 30 o 90 días** con `ACTION_CREATE_DOCUMENT` (el mismo mecanismo SAF que el CSV semanal). Botón para borrar el registro.
- Permisos nuevos: solo `PACKAGE_USAGE_STATS` y `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS`. Tarjeta "Permisos de estabilidad" con estado, botón a Acceso de uso (`Settings.ACTION_USAGE_ACCESS_SETTINGS`), petición de exclusión de batería y guía a "Sin restricciones" de One UI.
- Textos corregidos: README, descripción del servicio de Accesibilidad y Ajustes ("consultas de uso periódicas"). `FocusEngine`, `FocusState` y sus pruebas **no se tocaron**. `applicationId` sin cambios. `app/build.gradle` sin cambios (ninguna dependencia nueva).

### API para otras fases

```java
// Desde cualquier hilo; vuelve al instante y nunca lanza.
es.foco.next.log.BlockLog.record(context, BlockEvent.Type.PRACTICE_STARTED, "", "pausa");
es.foco.next.log.BlockLog.record(context, BlockEvent.Type.PRACTICE_FINISHED, "", "6_retos");
es.foco.next.log.BlockLog.record(context, BlockEvent.Type.DNS_OFF_LOCK, paqueteBloqueado, "dns_apagado");
// Con datos extra:
BlockLog.record(context, BlockEvent.now(BlockEvent.Type.DNS_OFF_LOCK).pkg(p).windowLeft(ms).action("dns"));
```

`pkg` y `action` solo admiten `[A-Za-z0-9._-]` (≤256 y ≤48 caracteres); otro texto se guarda vacío. Los nombres del enum se guardan en la base y en el CSV: no renombrarlos.

## Decisión: SQLiteOpenHelper en vez de Room

El plan dice "Room en `:app`". Se usó **`SQLiteOpenHelper`** del framework porque:
1. `:app` es Java y no tiene procesador de anotaciones. Room exigiría `annotationProcessor 'androidx.room:room-compiler:2.6.1'`, `room-runtime`, configurar `room.schemaLocation` (o `exportSchema=false`) y generar `*_Impl`. Sin compilar aquí, cualquier error de DAO/entidad/esquema solo aparecería en el PC.
2. Dos tablas, cuatro consultas: no hay beneficio real de Room. Sin dependencias nuevas, `app/build.gradle` no cambia (menos conflictos con las otras ramas).
3. El objetivo del plan se cumple igual: base propia `foco-log.db`, sin mezclar formatos con `focus-lite-v1.json` ni con la Room de Lab (`brain-training.db`).

Si se quiere migrar a Room más adelante, el esquema es compatible (mismos nombres de tablas y columnas); bastaría con `version 2` y una migración vacía.

## Esquema (`foco-log.db`, versión 1)

`block_event`: `id` (autoincremental), `ts` (ms UTC), `zone` (zona horaria), `day` (fecha local `yyyy-MM-dd`, índice), `type`, `pkg`, `budget_used_ms`, `window_left_ms`, `lock_ms`, `action`. Desconocido = `-1`.

`usage_day`: `day`, `pkg` (clave primaria compuesta), `observed_ms` (Accesibilidad), `usage_stats_ms` (Android; `-1` sin permiso), `updated`.

Cómo se derivan los eventos del servicio (`LockTracker`, sin tocar el motor):
- `BUDGET_LOCK`: el presupuesto restante pasa de >0 a 0 con la ventana en curso. App = la que consumió los últimos minutos. `lock_ms` = `window_left_ms` (el bloqueo dura hasta el fin de la ventana).
- `ATTEMPT_WHILE_LOCKED`: la pausa se muestra para una app bloqueada y no estaba ya mostrada para esa app (los refrescos de minuto no cuentan). Tras reiniciar el proceso, el primer bloqueo visible cuenta como intento, no como bloqueo nuevo.
- `ALT_OPENED`: alternativa abierta desde la órbita (`pausa_orbita`) o la galería (`pausa_galeria`) de la pausa. `pkg` = la alternativa.
- `HOME`: botón "Descansar · ir a Inicio" de la pausa (`pausa_inicio`).

## Archivos

### Nuevos
- `app/src/main/java/es/foco/next/log/BlockEvent.java` — modelo y tipos (Java puro).
- `app/src/main/java/es/foco/next/log/SafeLogger.java` — envoltorio que nunca lanza, con executor y destino inyectables (Java puro).
- `app/src/main/java/es/foco/next/log/LockTracker.java` — decisiones del motor → eventos (Java puro).
- `app/src/main/java/es/foco/next/log/LogRetention.java` — fechas de poda y rangos de exportación (Java puro).
- `app/src/main/java/es/foco/next/log/LogCsv.java` — formato CSV con protección contra fórmulas (Java puro).
- `app/src/main/java/es/foco/next/log/LogSummary.java` — modelo por día de la pantalla (Java puro).
- `app/src/main/java/es/foco/next/log/UsageMath.java` — tiempo en primer plano desde eventos de uso (Java puro).
- `app/src/main/java/es/foco/next/log/ObservedSnapshot.java` — copia de lo observado (lee `FocusState`, no lo modifica).
- `app/src/main/java/es/foco/next/log/LogDatabase.java` — `SQLiteOpenHelper` de `foco-log.db`.
- `app/src/main/java/es/foco/next/log/UsageContrast.java` — consulta a `UsageStatsManager` bajo demanda.
- `app/src/main/java/es/foco/next/log/BlockLog.java` — API estática, hilo, mantenimiento diario.
- `app/src/main/java/es/foco/next/ui/RegistroActivity.java` — pantalla Registro (no exportada).
- `app/src/main/java/es/foco/next/ui/StabilityPermissions.java` — tarjeta de Acceso de uso y batería.
- Pruebas: `app/src/test/java/es/foco/next/log/{BlockEventTest,LogRetentionTest,LogCsvTest,SafeLoggerTest,LockTrackerTest,UsageMathTest,LogSummaryTest,ObservedSnapshotTest,LogDatabaseTest}.java` y `app/src/test/java/es/foco/next/runtime/BlockLogServiceTest.java`.
- `docs/FASE-4.md` (este archivo).

### Modificados (**compartidos** con otras ramas marcados con ★; cada cambio lleva el comentario `Fase 4`)
- ★ `app/src/main/AndroidManifest.xml` — 2 permisos (+ comentario) y `RegistroActivity` (`exported="false"`). `PACKAGE_USAGE_STATS` con `tools:ignore="ProtectedPermissions"`.
- ★ `app/src/main/java/es/foco/next/runtime/FocusAccessibilityService.java` — 3 imports, campo `lockLog`, línea `pauseBefore` antes de mostrar/ocultar la pausa y una llamada `BlockLog.afterDecision(...)` al final del `try` de `evaluate()`, **después** de mostrar la pausa y programar el siguiente límite.
- ★ `app/src/main/java/es/foco/next/ui/FocusOverlay.java` — 2 líneas: `ALT_OPENED` en la órbita y `HOME` en "Descansar · ir a Inicio".
- ★ `app/src/main/java/es/foco/next/ui/MainActivity.java` — 4 líneas: botón "Ver registro de bloqueos" en "Tu semana" (Hoy); texto de Ajustes corregido; tarjeta `StabilityPermissions.card(...)` en Ajustes; botón "Registro de bloqueos · ver y exportar" en "Tus datos".
- ★ `app/src/main/res/values/strings.xml` — `accessibility_description` menciona el registro local.
- ★ `README.md` — permisos, registro, Acceso de uso, batería/One UI.
- `app/src/main/java/es/foco/next/ui/Alternatives.java` — 1 línea: `ALT_OPENED` en la galería cuando `compact` (solo la usa la pausa). Compartido si la Fase 5 toca la galería.
- **No** modificados: `app/build.gradle`, `FocusEngine`, `FocusState`, `Repository`, pruebas existentes.

## Pruebas nuevas (45 `@Test`; `:app` pasa de 57 a 102)

| Clase | Tipo | Qué prueba |
|---|---|---|
| `LogRetentionTest` (5) | JVM | 90 días = hoy + 89; rangos 30/90 incluyen hoy; año bisiesto y cambio de año; rangos inválidos. |
| `BlockEventTest` (5) | JVM | Día según zona horaria; nombres de tipo estables; categorías; desconocido = -1; paquetes saneados. |
| `LogCsvTest` (5) | JVM | 14 columnas en cabecera y filas; hora en la zona guardada; celdas vacías para desconocidos; inyección de fórmulas y comillas; fallo de etiqueta → paquete. |
| `SafeLoggerTest` (6) | JVM | Destino que lanza `RuntimeException` o `Error`, executor que rechaza, informador de errores que lanza: el llamador (el bloqueo) sigue. |
| `LockTrackerTest` (6) | JVM | Bloqueo dentro de la app = 1 `BUDGET_LOCK` y no intento; refresco de minuto no cuenta; reabrir/cambiar de app bloqueada = intento; tras reinicio = intento; nueva ventana. |
| `UsageMathTest` (6) | JVM | Reanudar→pausar, cambio de app, pantalla apagada, recorte al rango, filtro de paquetes. |
| `LogSummaryTest` (3) | JVM | Agregado por día (nuevo primero), por app, tipos futuros ignorados, contraste. |
| `ObservedSnapshotTest` (2) | JVM | Suma ocio + alternativa por día; paquetes de interés; no modifica `FocusState`. |
| `LogDatabaseTest` (4) | Robolectric | Archivo separado, ida y vuelta, poda exacta, reemplazo de `usage_day`, borrado. |
| `BlockLogServiceTest` (3) | Robolectric | **Con el registro forzado a fallar (destino que lanza, o hilo que rechaza), la pausa aparece y vuelve al reabrir la app.** Con registro funcional: un `BUDGET_LOCK` con app y hora, sin intento falso; reabrir = `ATTEMPT_WHILE_LOCKED` con app. |

Comando de la fase (sin cambios):
```sh
./gradlew :foco-core:test :lab-core:test :lab:testDebugUnitTest :app:testDebugUnitTest :app:lintDebug :app:assembleDebug
```

## Comprobar en el teléfono (Samsung, One UI)

1. Instalar encima de la Fase 1/0.58: plan, tiempos y apuntes siguen ahí. En "Merged Manifest" solo aparecen como nuevos `PACKAGE_USAGE_STATS` y `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` (y `VIBRATE` de la Fase 1). Nada de `INTERNET`.
2. Ajustes → "Permisos de estabilidad": sin Acceso de uso dice "sin conceder"; "Abrir Acceso de uso" abre la lista (o la página de Foco); concederlo y volver: dice "concedido".
3. "Permitir sin restricciones de batería": aparece el diálogo del sistema; aceptar y volver: "Batería · sin restricciones". Seguir la guía de One UI (Sin restricciones; fuera de apps en suspensión).
4. Agotar los 5 min en una app de ocio: aparece la pausa. Abrir Registro: hoy tiene "Bloqueos: 1" con esa app.
5. Ir a Inicio desde la pausa, reabrir la app bloqueada dos veces: "Intentos: 2" (y "Ir a Inicio: 1"). Abrir una alternativa desde la órbita y desde "Otras alternativas": "Alternativas: 2".
6. Comprobar que la hora de cada evento en el CSV coincide con el reloj del teléfono.
7. Exportar 30 y 90 días: el selector propone `Foco-registro-30d-AAAA-MM-DD.csv`; abrirlo en una hoja de cálculo (14 columnas; filas `evento` y `uso_diario`).
8. Con Acceso de uso: el Registro muestra "Observado por Foco … · Android …" por app. Sin él: "Android: sin Acceso de uso".
9. "Borrar el registro": vacía el Registro; el plan y "Tu semana" no cambian.
10. Estabilidad del bloqueo: si se borra a mano `foco-log.db` o el almacenamiento está lleno, la pausa sigue apareciendo (el registro solo deja de anotar).
11. Batería/fluidez: no debe haber actividad nueva en segundo plano (sin trabajos programados; solo escrituras puntuales al bloquear o al abrir el Registro).

## Riesgos de compilación y de pruebas (no comprobados aquí)

1. `app/src/main/java/es/foco/next/log/UsageContrast.java:33` — `AppOpsManager.unsafeCheckOpNoThrow` (API 29). En Robolectric, si el shadow no lo implementa, lanza y se captura → "sin conceder" (no rompe pruebas).
2. `app/src/main/java/es/foco/next/log/UsageContrast.java:70-75` — `switch` sobre constantes de `UsageEvents.Event` (`ACTIVITY_RESUMED/PAUSED/STOPPED`, `SCREEN_NON_INTERACTIVE`, `KEYGUARD_SHOWN`, `DEVICE_SHUTDOWN`): son `static final int` distintas en SDK 35; si el compilador marcara un `case` duplicado, cambiar a `if/else`.
3. `app/src/main/java/es/foco/next/ui/RegistroActivity.java:79`, `:126` y `:138` — inferencia genérica de `BlockLog.query(...)` con lambdas implícitas (`T` se deduce del retorno de `Work`). Si javac no la deduce, escribir `BlockLog.<List<LogSummary.Day>>query(...)` y `BlockLog.<Boolean>query(...)`.
4. `app/src/main/java/es/foco/next/ui/RegistroActivity.java:51` — `new ScrollView.LayoutParams(-1,-2)` (clase heredada de `FrameLayout`). Si no resuelve, usar `new android.widget.FrameLayout.LayoutParams(-1,-2)`.
5. `app/src/main/java/es/foco/next/log/BlockLog.java:107` — variable `final T result` dentro de la lambda del executor (asignación definida tras `try/catch` con `return`).
6. `app/src/main/AndroidManifest.xml:8` — `tools:ignore="ProtectedPermissions"` es necesario porque lint marca `PACKAGE_USAGE_STATS` como **error** y `abortOnError true`. El espacio de nombres `tools` ya estaba declarado (Fase 1).
7. `app/src/main/java/es/foco/next/ui/StabilityPermissions.java:47` — `@SuppressLint("BatteryLife")` en la petición de exclusión de batería (advertencia de política de Play; esta app no se publica en Play). Si lint lo marcara también en el manifiesto, añadir `tools:ignore="BatteryLife"` a esa línea.
8. Lint puede avisar `StaticFieldLeak` en `BlockLog.app` y `LogDatabase.owner` (guardan el contexto de **aplicación**, no de actividad) y `SetTextI18n` en Registro. Son advertencias, no errores.
9. `app/src/test/java/es/foco/next/runtime/BlockLogServiceTest.java` — usa el mismo montaje que `LiteRuntimeTest`. Con el registro por defecto, `LiteRuntimeTest` y demás pruebas existentes ahora escriben en `foco-log.db` desde un hilo de fondo dentro de Robolectric; cualquier error ahí se captura y solo se registra en `Log.w`. Si apareciera inestabilidad, el remedio es llamar `BlockLog.installForTests(Runnable::run, e -> {})` en esas pruebas (no se hizo para no tocarlas).
10. `UsageContrast` cuenta como uso de una app el tiempo que la pausa de Foco (overlay de Accesibilidad) la cubre: Android no ve el overlay como actividad. Se explica en la pantalla; no es un error.

## Sin probar en dispositivo

Todo lo de la lista anterior: esta fase no se compiló ni se instaló. En especial: comportamiento real de `ACTION_USAGE_ACCESS_SETTINGS` con `package:` en One UI, el diálogo de exclusión de batería, la precisión del contraste UsageStats frente a Accesibilidad y el coste de batería del hilo del registro.
