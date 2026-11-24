# Plan: Foco + Cognitive Lab (una sola app)

Objetivo: que el bloqueo de ocio de Foco empuje a practicar una habilidad (redes y seguridad) dentro de la misma app, con un diseño coherente y un filtro DNS estable.

**Principio rector: estabilidad.** Cada fase termina con la app funcionando y sus pruebas en verde. Nada de VPN, ADB ni permisos nuevos de riesgo. Si una fase no cumple su criterio de aceptación, no se avanza.

---

## 1. Decisiones tomadas

| Tema | Decisión |
|---|---|
| Estructura | Una sola app. `applicationId = es.foco.next` (conserva datos y ruta de actualización). |
| Motor de bloqueo | `FocusEngine` y su presupuesto (5 min de ocio por ventana de 60 min) **no se tocan**. |
| Jugar y minutos de ocio | Practicar **no** da minutos de ocio. La recompensa es la práctica (nivel y progreso). *(Supuesto: confirmar.)* |
| DNS | Un proveedor fijo en DNS privado de Android. Foco lo vigila; si se apaga, bloquea ocio y navegadores hasta reactivarlo. Sin VPN y sin ADB. |
| Elección de apps | Foco preselecciona solo las apps de ocio instaladas según un catálogo. El usuario ajusta excepciones. |
| Ofuscación | R8 ya activo; se refuerza con reglas, pero en la **última** fase para no esconder errores durante el desarrollo. |
| minSdk | Sube de 26 a 33 (el Samsung del usuario lo cumple). |
| Datos | Foco sigue con `focus-lite-v1.json`. Lab sigue con su Room (`brain-training.db`). **No se mezclan formatos** en las fases 1 a 5. |
| Permisos y registro | Se piden los permisos que dan datos reales y estabilidad (Acceso de uso y exclusión de batería). **No** "todos". El registro de bloqueos va en una base propia y es de mejor esfuerzo: si falla, el bloqueo sigue. Ver 5A. |
| Sonido | Se conserva el lo-fi y el foco de audio de 0.58 y se refuerza con pausa por tecla multimedia; sonidos de juego más ricos. Ver 5B. |
| Juegos | Curaduría (de 27 a unos 14), mecánicas distintas al "4 opciones", estética propia. Piloto de 3 juegos antes de seguir. Ver 5C. |

## 2. Punto de partida (verificado en el código entregado)

- **Foco 0.58:** Java 17, Views, módulos `app` y `core` (Java puro, sin Android), AGP 8.7.3, Gradle 8.9, minSdk 26, 89 pruebas. Bloqueo por `FocusAccessibilityService` + `FocusOverlay` (ventana `TYPE_ACCESSIBILITY_OVERLAY`). `es.foco.next` ya cuenta como app esencial.
- **Cognitive Lab Arcade:** Kotlin 1.9.25, Compose (compiler 1.5.15, BOM 2024.09.03), Room, AGP 8.7.3, minSdk 33, 39 pruebas. Módulos `app`, `core`, `data`, `designsystem`. Incluye 6 juegos de redes y seguridad (bits, subredes, puertos, phishing, SQL, logs) con dificultad adaptativa.
- Ambos usan el mismo AGP, así que se pueden combinar en un solo Gradle.
- Conflictos conocidos: `AppTheme` e `ic_launcher` existen en los dos `app`; los dos tienen recordatorio diario; el `core` de Foco y el de Lab se llaman igual.

## 3. Arquitectura destino

```
:foco-core      (Java puro, el core actual de Foco, sin cambios de comportamiento)
:lab-core       (Kotlin, core de Arcade)
:lab-data       (Room de Arcade)
:lab-design     (tema Compose; ver sección 4)
:lab            (biblioteca Android, antes el `app` de Arcade: pantallas Compose + LabActivity)
:app            (app final: Foco actual + dependencia de :lab)
```

Reglas:
- Convertir el `app` de Arcade en **biblioteca** (`com.android.library`) con prefijo de recursos `lab_` para evitar choques. Sin `AppTheme`, ni icono, ni launcher propios.
- Desactivar el recordatorio de Lab (queda solo el de Foco).
- `LabActivity` (Compose) no exportada. Se abre desde `MainActivity` y desde la pausa con `startActivity` + `FLAG_ACTIVITY_NEW_TASK`.
- Permisos nuevos aceptables: `VIBRATE`, `PACKAGE_USAGE_STATS` (Acceso de uso, se concede en Ajustes) y `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS`. **Prohibido** añadir INTERNET, VPN, escucha de notificaciones, ubicación, micrófono, cámara, contactos, SMS o administrador de dispositivo. Actualizar la lista de permisos y los textos de la app (ver 5A).
- Foco no lee los datos de Lab en fases 1 a 3. En la fase 4 solo **lee** un resumen de Room para la tarjeta "Tu práctica".

## 4. Diseño unificado (para que no quede desajustado)

Una sola identidad con dos "modos":
- **Calma (Foco):** "Hoy", "Mi plan", "Ajustes" y la pausa. Base salvia, terraza, hojas, titulares serif.
- **Juego (Lab):** pantallas Compose. Mismo fondo salvia, tarjetas redondeadas, colores de área más suaves.

Tokens (fuente única en `:lab-design` y `colors.xml`):
- Fondo `#F2F5ED`, superficie `#FCFDF8`, texto `#243C31`, texto secundario `#53685B`, acento principal `#38674D`.
- Áreas de juego: azul, violeta, naranja, menta (menta de Arcade como puente con el verde de Foco), todas con saturación reducida.
- Modo oscuro: grafito/verde muy oscuro, con AMOLED opcional (ya existe en Lab).
- Titulares serif; contenido de juego en sans (legibilidad).
- Esquinas: 20 dp en tarjetas, 18 dp en botones. Escala de espaciado de 4 dp.
- Movimiento: entrada de tarjetas (ya existe en `UiMotion.reveal`), transición entre pestañas (nueva), anillo de tiempo de ocio que se vacía, indicador de estado que "respira" lento.
- Transición pausa → práctica: el fondo se desenfoca, las hojas bajan de intensidad y entran las tarjetas de juego. Al terminar vuelven las hojas.
- Todo respeta "reducir movimiento" y "ahorro de energía" de Foco, y la desactivación global de animaciones de Android.
- Menos párrafos: las explicaciones largas pasan a un "¿Cómo funciona?" plegable.

## 5. DNS estable

- Proveedor por defecto: **CleanBrowsing Family** (`family-filter-dns.cleanbrowsing.org`; bloquea adultos, sitios mixtos, proxies/VPN y fuerza SafeSearch). Alternativa seleccionable: AdGuard Family (`family.adguard-dns.com`).
- Lógica pura en `:foco-core` (testeable): `DnsGuard.decide(estado, ahora, últimoOk)`.
- Estado (de `LinkProperties`, API 28+): DNS privado activo, y nombre del servidor igual al elegido.
- Se evalúa con `registerDefaultNetworkCallback` (ACCESS_NETWORK_STATE ya declarado) y en cada `evaluate()` del servicio.
- **Anti falsos positivos:** sin red activa se considera OK; margen de 60 s antes de bloquear; nunca bloquear Ajustes, Teléfono ni apps esenciales (para poder arreglarlo).
- Si el DNS está mal: bloquear apps de ocio **y navegadores conocidos** (Chrome, Samsung Internet, Firefox, Brave, Edge, Opera, DuckDuckGo) con un mensaje claro y un botón "Abrir DNS privado".
- Chrome puede tener su propio DNS seguro que pisa el del sistema (reporte de usuario). Se añade un paso en el asistente: dejar "DNS seguro" de Chrome en el del sistema.
- Límites que se documentan: el contenido dentro de apps y las redes sociales abiertas en navegador no se filtran por DNS; el usuario puede desactivar el DNS (Foco lo detecta y bloquea).

## 5A. Permisos y registro de bloqueos

**Permisos que sí aportan:**
- `PACKAGE_USAGE_STATS` (Acceso de uso): historial real por app con `UsageStatsManager`. Sirve para contrastar el tiempo cuando el servicio estuvo suspendido (el README actual admite que se pierde parte del último intervalo). El motor de bloqueo **sigue usando Accesibilidad**; UsageStats solo contrasta y alimenta el registro. Se consulta bajo demanda (al abrir el Registro y una vez al día), sin sondeo periódico.
- `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` + guía a "Apps sin restricciones" de One UI: One UI puede dormir servicios y eso rompe la estabilidad.
- Ya declarados: red, notificaciones, reinicio, servicio de primer plano multimedia, vibración.

**Permisos que no se piden aunque se pueda:** escucha de notificaciones (leería mensajes), ubicación, micrófono, cámara, contactos, SMS, Internet, VPN, administrador. No añaden datos útiles para el registro de bloqueos, aumentan la superficie de riesgo y rompen la promesa de que los datos no salen del teléfono. Sin INTERNET, nada sale salvo exportación manual.

**Registro (base propia `foco-log.db`, Room en `:app`; no se toca `focus-lite-v1.json`):**
- Tabla `block_event`: id, marca de tiempo, zona horaria, tipo (`BUDGET_LOCK`, `DNS_OFF_LOCK`, `ATTEMPT_WHILE_LOCKED`, `PRACTICE_STARTED`, `PRACTICE_FINISHED`, `ALT_OPENED`, `HOME`), paquete, presupuesto usado, ventana restante, duración del bloqueo, acción elegida.
- Tabla `usage_day`: día, paquete, ms observados por Accesibilidad, ms según UsageStats.
- Escritura en un executor de un hilo, con `try/catch`: **si falla el registro, el bloqueo sigue**. Nunca en el hilo principal. Retención de 90 días con poda.
- Pantalla "Registro": por día, intentos y bloqueos. Exportación CSV de 30 o 90 días. Muestra números observados, sin prometer ahorro.
- Actualizar README, descripción del servicio de Accesibilidad y textos de Ajustes (hoy dicen "sin consultas de uso" y "no se solicita...").

## 5B. Sonido

**Lo que ya hay en 0.58:** música local (Ghostrifter, Tokyo Music Walker), `sendero.ogg` procedural (`scripts/make_soundscape.py`, original, sin muestras) y foco de audio `TRANSIENT_EXCLUSIVE` durante el bloqueo. Límite conocido: apps que ignoran el foco pueden seguir sonando. **No se ha medido en el Samsung.**

**Mejoras:**
1. Al bloquear, además del foco de audio, enviar `KEYCODE_MEDIA_PAUSE` (DOWN + UP) con `AudioManager.dispatchMediaKeyEvent`. Pausa apps con sesión multimedia. Probar en el Samsung con YouTube, Spotify, TikTok e Instagram y anotar el resultado por app (pausa / baja volumen / ignora). No prometer lo que no se mida.
2. Ambientes propios con el script: pausa de día, pausa de noche y práctica. Más variantes, siempre originales.
3. Sonidos de juego: escala pentatónica que sube con la racha, acierto y error suaves, cierre de sesión. Mezcla baja; respeta silencio y No molestar.
4. Ajustes: volumen de música y de efectos por separado, y un interruptor de "sin sonido".
5. Revisar licencias de Ghostrifter y Tokyo Music Walker antes de distribuir.

## 5C. Juegos: curaduría y rediseño

**Diagnóstico en el código:** de los 27 ejercicios del catálogo, 21 usan la misma pantalla "pregunta + 4 opciones" (`Kind.CHOICE`): compass, balance, search, pattern, flanker, choice, series, math, percent, fraction, equation, ratio, budget, unitprice, planning y los 6 de redes y seguridad. Solo 6 tienen mecánica distinta (orbit, sequence, grid, working, reaction, matrix). Además hay 3 arcade (parejas, pulso de neón, ruta espacial). Esto explica lo repetitivo.

**Curaduría propuesta (a confirmar por el usuario):**
- Conservar: orbit, sequence, grid, working, reaction, matrix (mecánicas propias) y los arcade si pasan la checklist.
- Fusionar: math, percent, fraction, equation y ratio en un solo juego "Cálculo" con modos.
- Sacar del núcleo (módulo "extras"): budget, unitprice, planning, choice, search, pattern, flanker, series, balance, compass. Se vuelven a admitir si ganan una mecánica distinta.
- Redes y seguridad pasan a ser el eje, con mecánicas propias:
  - Bits: interruptores de bit que se encienden y suman en vivo.
  - Subredes: arrastrar la máscara sobre una cuadrícula de octetos.
  - Puertos: panel de firewall con interruptores; arrastrar servicios a puertos.
  - Phishing: bandeja de correos que se deslizan, con deslizar a un lado u otro (seguro/trampa).
  - Logs: flujo de terminal que se desplaza y se toca la línea sospechosa.
  - SQL: armar la consulta parametrizada con fichas.
  - Nuevos ligados a Packet Tracer: tipo de cable según dispositivos, siguiente salto en una tabla de rutas, VLAN y ACL.

**Variedad:** cada sesión mezcla 3 mecánicas y nunca repite el mismo formato dos rondas seguidas. Bancos de escenarios reales en vez de solo números aleatorios. Los errores reaparecen en rondas posteriores (repaso espaciado).

**Estética:** cambiar glifos de texto por ilustraciones vectoriales propias, con la identidad de la sección 4. Animación de acierto y error, racha visible, partículas suaves, sonido. Nada de claims de mejora cognitiva (se mantiene el aviso de Lab).

**Checklist por juego (para aprobarlo):** mecánica distinta a las demás, feedback visible y sonoro, explicación de una línea, 60 fps en el Samsung, usable en claro y oscuro, aprende algo concreto.

**Método:** 3 juegos por tarea de Codex. Piloto: Subredes, Phishing y Puertos, para fijar el estilo antes de seguir con el resto.

## 6. Fases y criterios de aceptación

**Fase 0. Preparación**
- `git init`, etiquetas `foco-0.58` y `lab-arcade-2.1`, copia de ambos zips.
- Clave de firma propia (nunca en Git). Exportar plan y apuntes desde la app actual antes de instalar.
- ✔ Se puede reconstruir el estado actual desde Git.

**Fase 1. Unir proyectos sin cambiar comportamiento**
- Estructura de la sección 3, minSdk 33, Compose habilitado.
- ✔ `:foco-core:test`, `:lab-core:test`, `:app:testDebugUnitTest`, `:lab:testDebugUnitTest`, `:app:lintDebug` y `:app:assembleDebug` en verde (≥ 89 + 39 pruebas).
- ✔ La app se comporta exactamente como Foco 0.58.

**Fase 2. El puente**
- Botón "Practicar 3 min" en `FocusOverlay` y en "Hoy". Abre `LabActivity` en una sesión corta: hasta 6 retos o 3 minutos, lo primero que ocurra. Comienza por Redes y seguridad en el nivel adaptativo guardado.
- Al salir, vuelve a la pausa o a "Hoy". El bloqueo no se afecta.
- ✔ Durante la práctica no aparece el overlay encima (la app es esencial). ✔ Al abrir una app bloqueada después, la pausa vuelve. ✔ No cambian presupuesto ni ventanas.

**Fase 3. DNS estable** (sección 5)
- ✔ Pruebas de `DnsGuard` (sin red, margen, cambio de proveedor, falsos positivos). ✔ Probado en el Samsung: apagar y encender DNS privado bloquea y desbloquea sin quedarse atascado.

**Fase 4. Permisos y registro de bloqueos** (sección 5A)
- Base `foco-log.db`, eventos de bloqueo, pantalla "Registro", exportación CSV, UsageStats bajo demanda y guía de batería.
- ✔ Con el registro forzado a fallar, el bloqueo sigue funcionando (prueba). ✔ Cada bloqueo, intento y práctica aparece con hora y app. ✔ Textos y README corregidos.

**Fase 5. Catálogo de ocio y tarjeta "Tu práctica"**
- Ampliar `AlternativeCatalog.social` (YouTube, Netflix, Twitch, juegos, etc.) y preseleccionar las instaladas. El usuario puede quitar o añadir.
- Tarjeta "Tu práctica" en "Hoy" (solo lectura de Room).
- ✔ Una instalación nueva queda con las apps de ocio marcadas sin elegir nada. ✔ Esenciales, bancos y WhatsApp nunca se marcan.

**Fase 6. Sonido** (sección 5B)
- ✔ Tabla app × resultado de la pausa multimedia, probada en el Samsung. ✔ Volúmenes separados y silencio funcionan. ✔ El lo-fi no se queda sonando al salir.

**Fase 7. Juegos (por lotes)** (sección 5C)
- Lote 1 piloto: Subredes, Phishing, Puertos. Lotes siguientes de 3 juegos.
- ✔ Cada juego cumple la checklist. ✔ El usuario aprueba el piloto antes de continuar.

**Fase 8. Diseño y movimiento** (sección 4)
- ✔ Mismos tokens en Views y Compose. ✔ Con "reducir movimiento" no hay animaciones decorativas. ✔ Capturas claro/oscuro de "Hoy", pausa y una ronda de juego.

**Fase 9. Ofuscación y release**
- Añadir a `proguard-rules.pro`: `-repackageclasses ''`, `-allowaccessmodification`, `-renamesourcefileattribute SourceFile`, y quitar `Log` en release. Conservar `mapping.txt` de cada versión.
- ✔ Probar el APK **release** completo (Accesibilidad, pausa, vídeo, audio, juegos, Room, exportaciones).
- Nota: la ofuscación no protege de desactivar la app; la fricción es parte de las fases 2 y 3.

**Fase 10. Verificación real**
- Una semana en el Samsung: batería, fluidez, audio, comportamiento al suspender la app y falsos bloqueos del DNS.
- No prometer porcentajes de ahorro antes de medir.

## 7. Reglas para Codex (copiar al inicio de cada tarea)

1. No modificar `FocusEngine`, `FocusState` ni sus pruebas, salvo que la fase lo pida explícitamente.
2. Solo se pueden añadir `VIBRATE`, `PACKAGE_USAGE_STATS` y `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS`. Nada de VPN, Internet, escucha de notificaciones ni telemetría. El registro nunca puede impedir un bloqueo.
3. Mantener `applicationId es.foco.next`. No tocar recursos multimedia existentes.
4. Si una prueba existente falla, **arreglar el código nuevo**, no borrar ni debilitar la prueba.
5. Comandos antes de cerrar cada fase:
   `./gradlew :foco-core:test :lab-core:test :lab:testDebugUnitTest :app:testDebugUnitTest :app:lintDebug :app:assembleDebug`
6. Entregar el proyecto completo y corregido, no parches sueltos.
7. Una fase por tarea. Al terminar, listar archivos tocados y qué quedó sin probar en dispositivo.

## 8. Riesgos

- Choque de recursos entre módulos (temas, iconos): mitigado con prefijo `lab_` y biblioteca.
- Dos recordatorios diarios: se deja solo el de Foco.
- Accesibilidad puede ser revocada por el usuario; Foco no puede impedirlo sin permisos de dueño del dispositivo.
- Música y vídeo de Foco: revisar licencias antes de distribuir.
- Foco aún no se ha medido en batería ni fluidez; Lab tampoco en dispositivo físico.

## 9. Pendiente de confirmar

1. ¿Practicar **no** da minutos de ocio? (supuesto actual: no).
2. Si el DNS se apaga, ¿bloquear **ocio y navegadores**? (supuesto actual: sí).
3. Sesión de práctica: ¿6 retos o 3 minutos te parece bien?
4. Permisos: ¿Acceso de uso y exclusión de batería te bastan, o hay alguno más que quieras con un motivo concreto?
5. Juegos: ¿de acuerdo con la curaduría de 5C? Marca cuáles quieres conservar sí o sí.
