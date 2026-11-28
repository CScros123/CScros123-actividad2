# Fases 2 y 3 — Puente a la práctica y DNS estable

Rama `fase-2-3`, sobre la Fase 1. `applicationId es.foco.next` sin cambios. **Sin permisos nuevos** (el DNS usa `ACCESS_NETWORK_STATE`, ya declarado en `app/src/main/AndroidManifest.xml:2`). `FocusEngine`, `FocusState` y sus pruebas **no se tocaron**.

> Igual que la Fase 1, esto se preparó **sin compilar ni ejecutar Gradle** (a pedido del usuario). Se revisó leyendo el código y con `grep`. Al final hay una lista de riesgos de compilación con archivo y línea.

Decisiones confirmadas por el usuario y aplicadas:
1. Practicar **no** da minutos de ocio.
2. Si el DNS está mal, se pausan **apps de ocio y navegadores conocidos**.
3. Práctica corta: **hasta 6 retos o 3 minutos**, lo primero que ocurra.
4. Proveedor por defecto **CleanBrowsing Family** (`family-filter-dns.cleanbrowsing.org`); alternativa **AdGuard Family** (`family.adguard-dns.com`).

## Fase 2 — El puente

**Qué hace.** Botón **"Practicar 3 min"** en la pausa (`FocusOverlay`) y una tarjeta "Practica 3 minutos" en "Hoy". Abre `LabActivity` en modo práctica corta (`LabLaunch.EXTRA_SHORT_SESSION`):
- Hasta 6 retos o 3 minutos **activos** (el reloj se para en pausa y al salir de la pantalla). Lógica pura en `ShortSession` (`:lab-core`).
- Empieza por Redes y seguridad con la misma regla que la "Misión de hoy" (`BrainViewModel.mission()`): entre los juegos desbloqueados, el de menor último acierto (nunca jugado primero) y luego el menos reciente. Después rota por el resto de juegos de redes desbloqueados. Cada reto usa el **nivel adaptativo guardado** de su juego (tabla `progress` de Room, leída con `allProgress()`; sin datos, nivel 1 y Bits).
- Reutiliza `Generator`, `ChoiceCards`, `CodeBox`, `SessionHeader`, `PausedBanner`, `StatChip` y el sonido/vibración de Lab. Tras cada respuesta muestra la explicación y "Siguiente reto".
- Resumen final ("x de y", juego, segundos activos, aviso de que no suma ocio) y botón **"Volver"**.
- No guarda un resultado de 12 rondas ni cambia el nivel (`Adaptive.next` exige 12). Solo escribe, de mejor esfuerzo, la preferencia de Room `short_session:last` (JSON con fecha, aciertos, tiempo, juego y nivel). La Fase 4/5 puede leerla para "Tu práctica".

**Volver.** Desde la pausa se abre con `FLAG_ACTIVITY_NEW_TASK | MULTIPLE_TASK | EXCLUDE_FROM_RECENTS` (tarea propia, oculta de Recientes). "Volver" (o Atrás) llama a `finishAndRemoveTask()`: Android vuelve a la app de debajo y, si sigue bloqueada, el servicio vuelve a mostrar la pausa. Desde "Hoy" se abre en la tarea de Foco y "Volver" hace `finish()` (regresa a "Hoy").

**El bloqueo no se afecta (verificado leyendo el código):**
- `FocusEngine.essential()` incluye `es.foco.next` (`foco-core/.../FocusEngine.java:25`); `LabActivity` vive en ese paquete y proceso. Al enfocarse su ventana, `FocusAccessibilityService.onAccessibilityEvent` → `observe()` → `evaluate()` ve `in.essential=true` y llama a `overlay.dismiss()`: no hay pausa encima mientras se practica.
- `ForegroundWindows.current()` solo mira ventanas `TYPE_APPLICATION` enfocadas, así que la propia pausa (`TYPE_ACCESSIBILITY_OVERLAY`) no se confunde con Lab.
- El puente no llama a `Repository`, `FocusEngine` ni cambia el presupuesto o la ventana. El tiempo en Foco/Lab no cuenta como ocio (app esencial). La prueba `BridgeAndDnsTest.pauseOffersShortPracticeInItsOwnTask` comprueba que pulsar el botón no cambia `usedMillis`.

## Fase 3 — DNS estable

**Lógica pura** `DnsGuard` (`:foco-core`, Java, sin Android):
- `decide(state, now, lastOk)` → `OK`, `GRACE` (con `recheck` en ms) o `BLOCK`.
- Sin vigilancia activada = OK. **Sin red activa = OK.** **60 s de margen** antes de bloquear.
- Cumple si el DNS privado está activo **y** el nombre del servidor es el del proveedor elegido (sin distinguir mayúsculas, espacios ni punto final). El modo automático (sin nombre) no cumple: no filtra.
- `Tracker`: el margen empieza cuando se **ve** el primer estado incorrecto (no en el último OK, que puede ser antiguo: evita bloqueos instantáneos al cambiar de Wi-Fi a datos mientras Android valida el DNS). Se reinicia al activar la vigilancia o cambiar de proveedor. Si el reloj retrocede, nunca bloquea al instante.
- Lista de navegadores: Chrome `com.android.chrome`, Samsung Internet `com.sec.android.app.sbrowser`, Firefox `org.mozilla.firefox`, Brave `com.brave.browser`, Edge `com.microsoft.emmx`, Opera `com.opera.browser`, DuckDuckGo `com.duckduckgo.mobile.android`.

**Android** (`DnsMonitor`, `:app/runtime`):
- Lee `LinkProperties.isPrivateDnsActive()` y `getPrivateDnsServerName()` de la red por defecto con `ConnectivityManager.registerDefaultNetworkCallback` (en el hilo principal) y al iniciar el servicio. Cada cambio de red llama a `evaluate()`; además se decide en **cada** `evaluate()` del servicio.
- Cualquier error al leer la red se trata como "sin dato" = no bloquear.
- Qué se pausa: apps de ocio del plan (`FocusEngine.watched`) y navegadores conocidos, solo si el plan está activado. **Nunca** apps esenciales: Ajustes, Teléfono/marcador, llamadas, Inicio, WhatsApp, bancos, Foco y las "Otras apps esenciales" del usuario.
- Bajo la pausa DNS la app se envía al motor como "no visible" (`in.interactive=false`): **no gasta presupuesto ni abre ventana**. Si esa app ya tiene el bloqueo de presupuesto, se muestra la pausa normal (manda el motor).
- Durante el margen se programa una reevaluación (`dnsRecheck`) para que el bloqueo llegue a los 60 s aunque no haya eventos.
- Preferencias propias en `SharedPreferences "foco-dns"` (`armed`, `provider`, `chrome_checked`). No se toca `focus-lite-v1.json`.

**Pausa DNS** (`DnsPause`, ventana de `FocusOverlay.dnsBlock`): mensaje claro, el nombre del proveedor, botón **"Abrir DNS privado"** (copia el nombre al portapapeles y abre Ajustes) y "Descansar · ir a Inicio". Se quita sola cuando el DNS vuelve a estar bien.

**No existe un intent público al DNS privado.** Se prueba `android.settings.PRIVATE_DNS_SETTINGS` (acción oculta que algunos teléfonos aceptan), luego `Settings.ACTION_WIRELESS_SETTINGS` y por último `Settings.ACTION_SETTINGS`. La interfaz lo explica y da la ruta de One UI (Ajustes › Conexiones › Más ajustes de conexión › DNS privado).

**Ajustes** (`DnsSettings`, sustituye la tarjeta "Adultos, anuncios y rastreadores" que recomendaba AdGuard):
- Estado actual, proveedor elegido y botón para cambiar entre CleanBrowsing Family y AdGuard Family (avisa: 60 s para cambiarlo también en Android).
- Asistente de 3 pasos con ✓: (1) DNS privado de Android con "Copiar y abrir DNS privado"; (2) **Chrome: "Usar DNS seguro" con el proveedor actual/del sistema** (o apagado), con botón "Ya lo revisé en Chrome"; (3) vigilancia de Foco.
- La vigilancia **se activa sola** la primera vez que Foco ve el DNS privado con el proveedor elegido (si el usuario no la apagó antes). También se activa a mano.
- Apagar la vigilancia tiene fricción: no se permite durante el bloqueo de presupuesto ni con el DNS mal (habría que arreglarlo primero).

**Límites (documentados en la app y el README):**
- El DNS no filtra el contenido dentro de apps ni las redes sociales abiertas en el navegador.
- Chrome y otros navegadores pueden usar su propio DNS seguro y saltarse el del sistema (paso 2 del asistente). Foco no puede leer ese ajuste de Chrome.
- Una VPN activa cambia la red por defecto: Foco la ve como DNS apagado y pausa ocio y navegadores.
- Android permite apagar el DNS privado o desactivar Accesibilidad; Foco solo lo detecta y pausa, no lo impide.
- Un navegador marcado por el usuario como "app esencial" no se pausa (elección explícita).
- Usuarios de 0.58 con AdGuard: el proveedor por defecto pasa a CleanBrowsing; como no coincide, la vigilancia **no** se autoactiva. En Ajustes verán "DNS privado con otro proveedor" y pueden elegir AdGuard Family.

## Archivos

**Nuevos**
- `foco-core/src/main/java/es/foco/next/core/DnsGuard.java`
- `foco-core/src/test/java/es/foco/next/core/DnsGuardTest.java`
- `app/src/main/java/es/foco/next/runtime/DnsMonitor.java`
- `app/src/main/java/es/foco/next/ui/DnsPause.java`
- `app/src/main/java/es/foco/next/ui/DnsSettings.java`
- `app/src/main/java/es/foco/next/ui/PracticeEntry.java`
- `app/src/test/java/es/foco/next/ui/BridgeAndDnsTest.java`
- `lab-core/src/main/kotlin/personal/qqq/braintraining/core/ShortSession.kt`
- `lab-core/src/test/kotlin/personal/qqq/braintraining/core/ShortSessionTest.kt`
- `lab/src/main/java/personal/qqq/braintraining/LabLaunch.kt`
- `lab/src/main/java/personal/qqq/braintraining/ShortPractice.kt`
- `lab/src/test/java/personal/qqq/braintraining/ShortPracticeTest.kt`
- `docs/FASE-2-3.md`

**Compartidos modificados** (cambios cortos y marcados con "Fase 2"/"Fase 3" para facilitar la fusión):
- `app/src/main/java/es/foco/next/runtime/FocusAccessibilityService.java` — campo `dns` y `dnsRecheck` (l. 29-31), arranque del monitor (l. 46), `removeCallbacks(dnsRecheck)` (l. 74), bloque DNS en `evaluate()` (l. 84-92 y 99), parada en `onDestroy()` (l. 110).
- `app/src/main/java/es/foco/next/ui/FocusOverlay.java` — campo `dns` (l. 23), `!dns` en la condición de `block()` (l. 35), botón "Practicar 3 min" (l. 57), métodos `dnsBlock()`/`dnsVisible()` (l. 81-89), `dns=false` en `dismiss()` (última línea).
- `app/src/main/java/es/foco/next/ui/MainActivity.java` — una línea en `today()` (l. 105, tarjeta de práctica) y la tarjeta DNS de Ajustes sustituida por `DnsSettings.render` (l. 153); se eliminó el método privado `privateDnsStatus()`, ahora sin uso.
- `lab/src/main/java/personal/qqq/braintraining/LabActivity.kt` — lee el extra (l. 51), elige `ShortPracticeScreen` o `LiteApp` (l. 59) y `closeShort()` (l. 62-63).
- `README.md`.

**No modificados:** `AndroidManifest.xml` (app y lab), `strings.xml`, `build.gradle`, `FocusEngine`, `FocusState`, pruebas existentes. Nota: la descripción del servicio de Accesibilidad (`strings.xml`) no menciona todavía la pausa por DNS; se deja para la Fase 4, que reescribe esos textos (5A), para no chocar.

## Pruebas añadidas (35 `@Test`)

| Módulo | Archivo | Pruebas | Qué cubren |
|---|---|---|---|
| `:foco-core` | `DnsGuardTest` | 16 | sin red, sin vigilancia, margen exacto de 60 s, `lastOk` desconocido/futuro, modo automático y otros proveedores, falsos positivos de nombre (mayúsculas, espacios, punto final, sufijo malicioso), proveedor desconocido → predeterminado, cambio de proveedor con margen, margen desde el primer fallo visto, bloquear y desbloquear sin atascarse, pérdida de red, activar vigilancia, reloj hacia atrás, lista de navegadores, Ajustes/Teléfono/Foco esenciales |
| `:lab-core` | `ShortSessionTest` | 8 | fin a los 6 retos, fin a los 3 min, saltos de reloj, límites, regla de misión, plan dentro de Redes y seguridad, retos válidos |
| `:lab` | `ShortPracticeTest` (Robolectric) | 6 | plan con misión y nivel guardado, usuario nuevo, nivel acotado a 1..10, flags del intent desde la pausa y desde "Hoy" |
| `:app` | `BridgeAndDnsTest` (Robolectric) | 5 | qué apps pausa el DNS (y cuáles nunca), proveedor por defecto y validación, monitor sin vigilancia nunca bloquea, pausa DNS única y sustituida por la de presupuesto, botón de práctica abre `LabActivity` en tarea propia sin cambiar el presupuesto |

Total esperado: 137 de la Fase 1 + 35 = **172**.

```sh
./gradlew :foco-core:test :lab-core:test :lab:testDebugUnitTest :app:testDebugUnitTest :app:lintDebug :app:assembleDebug
```

## Probar en el Samsung

1. **Puente desde la pausa:** agotar los 5 min → pausa → "Practicar 3 min". Debe abrirse la práctica **sin** la pausa encima. Terminar o pulsar "Volver"/Atrás → vuelve la app de ocio con la pausa. Comprobar en "Hoy" que el tiempo disponible y la ventana no cambiaron.
2. **Puente desde "Hoy":** "Practicar 3 min" → práctica → "Volver" regresa a "Hoy".
3. Que la práctica corta no aparezca en Recientes después de "Volver", y qué pasa si se sale con Inicio en mitad de la práctica (la tarea queda oculta; al volver a la app de ocio debe salir la pausa).
4. Reloj de 3 min: pausar, apagar pantalla y volver; el tiempo no debe avanzar en segundo plano.
5. **DNS:** con vigilancia activa, apagar el DNS privado → durante ~60 s Chrome y el ocio funcionan → luego aparece la pausa DNS. Ajustes, Teléfono y WhatsApp deben seguir abriéndose. Encender el DNS → la pausa se quita sola (sin quedarse atascada). Repetir 3-4 veces.
6. "Abrir DNS privado": anotar qué pantalla abre en One UI (DNS privado directo o Conexiones).
7. Cambiar Wi-Fi ↔ datos y modo avión con el DNS bien: no debe aparecer la pausa DNS (falso positivo).
8. Con una VPN activa: confirmar que se pausa (comportamiento esperado) y anotarlo.
9. Chrome con "DNS seguro" en otro proveedor: Foco no lo detecta; comprobar si el filtro se salta.
10. Con el DNS mal, abrir una app de ocio durante varios minutos con la pausa DNS: el presupuesto de "Hoy" no debe bajar.

## Riesgos de compilación (no compilado aquí)

1. `app/src/main/java/es/foco/next/runtime/DnsMonitor.java:44` — `registerDefaultNetworkCallback(callback, Handler)` (API 26). Real en Android. En Robolectric, si la versión con `Handler` no tiene sombra, la excepción se captura (`RuntimeException`) y el monitor queda sin vigilancia, sin afectar a las pruebas.
2. `app/src/main/java/es/foco/next/ui/PracticeEntry.java:7,27` y `BridgeAndDnsTest.java:51` — Java llama a Kotlin: `LabLaunch.shortSession/isShort/fromPause` son `@JvmStatic` en un `object`, así que se ven como métodos estáticos. Si fallara, usar `LabLaunch.INSTANCE.shortSession(...)`.
3. `app/src/test/java/es/foco/next/ui/BridgeAndDnsTest.java:49` — `Shadows.shadowOf(Application).getNextStartedActivity()` para un `startActivity` lanzado desde el contexto de un servicio. Robolectric guarda los intents en una instrumentación global; si devolviera `null`, usar `Shadows.shadowOf(s.get()).getNextStartedActivity()`.
4. `lab/src/main/java/personal/qqq/braintraining/ShortPractice.kt:71` — construye `SessionClock` (clase `internal` de `LiteApp.kt`) con fracción propia de 3 min. Mismo módulo, debería compilar.
5. `lab/src/main/java/personal/qqq/braintraining/ShortPractice.kt:52` — llamadas `suspend` de Room (`allResults()`, `allProgress()`) dentro de `LaunchedEffect`; Room las ejecuta en su propio hilo.
6. `lab/src/main/java/personal/qqq/braintraining/ShortPractice.kt:87` — `return@ChoiceCards` dentro de la lambda final de `ChoiceCards`.
7. `lab-core/src/main/kotlin/personal/qqq/braintraining/core/ShortSession.kt:25` — `minWith` no nulo (Kotlin ≥ 1.7; el proyecto usa 1.9.25 y `BrainViewModel` ya lo usa).
8. `lab/src/test/java/personal/qqq/braintraining/ShortPracticeTest.kt` — accede a `ShortRound` y `shortPlan` (`internal`) desde las pruebas del mismo módulo; Kotlin lo permite.
9. Lint de `:app`: textos fijos en Java (advertencias, como en el resto de Foco). No se esperan errores nuevos; `ACCESS_NETWORK_STATE` ya está declarado para `getActiveNetwork`/`getLinkProperties`/`registerDefaultNetworkCallback`.
10. La captura `foco-calm-pausa.png` de `CalmVisualTest` cambiará (aparece el botón "Practicar 3 min"); la prueba no compara píxeles, solo exporta.
