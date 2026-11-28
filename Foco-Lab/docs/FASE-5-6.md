# Fases 5 y 6 — Catálogo de ocio, "Tu práctica" y sonido

Rama: `fase-5-6` (sobre la Fase 1). `applicationId` sin cambios (`es.foco.next`).

> **Importante:** como en la Fase 1, esto se preparó **sin compilar ni ejecutar Gradle** (a pedido del usuario). Se revisó leyendo el código. La verificación real se hace en el PC con los comandos de la sección "Verificar en el PC". Los posibles fallos están en "Riesgos de compilación".

Reglas respetadas: `FocusEngine`, `FocusState` y sus pruebas sin tocar; **ningún permiso nuevo**; **el manifiesto no cambia**; recursos multimedia existentes sin tocar (no se añaden audios nuevos); ninguna prueba borrada ni debilitada.

---

## Fase 5

### 1. Catálogo de ocio (`AlternativeCatalog`, `:foco-core`)

Lista explícita por **nombre de paquete exacto** (sin prefijos ni categorías de la tienda), comparada en minúsculas:

| Grupo | Paquetes |
|---|---|
| Vídeo y directos | YouTube `com.google.android.youtube`, Netflix `com.netflix.mediaclient`, Twitch `tv.twitch.android.app`, Kick `com.kick.mobile`, Disney+ `com.disney.disneyplus`, Prime Video `com.amazon.avod.thirdpartyclient`, HBO Max `com.hbo.hbonow`, Max `com.wbd.stream`, Crunchyroll `com.crunchyroll.crunchyroid`, Pluto TV `tv.pluto.android` |
| Redes y vídeo corto | TikTok `com.zhiliaoapp.musically`, `com.ss.android.ugc.trill`, TikTok Lite `com.zhiliaoapp.musically.go`, Instagram `com.instagram.android`, Instagram Lite `com.instagram.lite`, Threads `com.instagram.barcelona`, Facebook `com.facebook.katana`, Facebook Lite `com.facebook.lite`, X/Twitter `com.twitter.android`, Snapchat `com.snapchat.android`, Reddit `com.reddit.frontpage`, Pinterest `com.pinterest`, Tumblr `com.tumblr`, 9GAG `com.ninegag.android.app`, Kwai `com.kwai.video`, Likee `video.like`, BeReal `com.bereal.ft` |
| Juegos | Roblox `com.roblox.client`, Free Fire `com.dts.freefireth`, Free Fire MAX `com.dts.freefiremax`, Clash Royale `com.supercell.clashroyale`, Clash of Clans `com.supercell.clashofclans`, Brawl Stars `com.supercell.brawlstars`, Candy Crush Saga `com.king.candycrushsaga`, Candy Crush Soda `com.king.candycrushsodasaga`, PUBG Mobile `com.tencent.ig`, PUBG Lite `com.tencent.iglite`, Genshin Impact `com.miHoYo.GenshinImpact`, Honkai: Star Rail `com.HoYoverse.hkrpgoversea`, Minecraft `com.mojang.minecraftpe`, Subway Surfers `com.kiloo.subwaysurf`, Call of Duty Mobile `com.activision.callofduty.shooter`, Among Us `com.innersloth.spacemafia`, Mobile Legends `com.mobile.legends`, 8 Ball Pool `com.miniclip.eightballpool`, Pokémon GO `com.nianticlabs.pokemongo`, Stumble Guys `com.kitkagames.fallbuddies`, Royal Match `com.dreamgames.royalmatch`, Coin Master `com.moonactive.coinmaster`, EA FC Mobile `com.ea.gp.fifamobile`, Fortnite `com.epicgames.fortnite` |

Un paquete equivocado o que no exista no hace daño: simplemente nunca aparece instalado. **Revisar en el Samsung** que los marcados coinciden con lo esperado (sobre todo Kick, Max y TikTok Lite, cuyos paquetes pueden variar por región).

**Música (Spotify, YouTube Music, Deezer…) no es ocio por defecto.** Motivos: suele sonar de fondo mientras se estudia o se trabaja (no captura la atención como un feed); Foco mide tiempo en primer plano, así que bloquearla solo castigaría el momento de elegir una canción; y la pausa multimedia de la Fase 6 ya la para cuando se bloquea el ocio. El usuario puede añadirla a mano en "Mi plan → Apps de ocio".

API nueva:
- `leisure(pkg)`, `leisurePackages()`.
- `neverMark(config, pkg)`: guardia. Devuelve `true` para todo lo que `FocusEngine.essential` ya protege (Foco, Ajustes, Teléfono/marcador, WhatsApp y WhatsApp Business, bancos por paquete con `SensitiveApps`, la lista "Otras apps esenciales" del usuario) **y** para mensajería (Messenger, Telegram, Signal, Mensajes de Google y Samsung, Gmail, Teams, Zoom).
- `preselect(config, instalados, protegidoExtra)`: instalados ∩ catálogo, menos `neverMark`, menos la app alternativa (`returnApp`), menos lo que diga `protegidoExtra` (en Android: bancos detectados **por etiqueta** con `BankAppGuard`). Conserva el nombre exacto del paquete (p. ej. `com.miHoYo.GenshinImpact`).
- `social(pkg)` ahora también es `true` para todo el catálogo: ninguna app de ocio se ofrece como alternativa ni como "Mi app" (si una copia antigua tenía un juego del catálogo como alternativa, se vacía al leerla, igual que ya pasaba con Facebook).

### 2. Preselección en una instalación nueva (`Repository`, `:app`)

- Solo cuando **no existe** ni `focus-lite-v1.json` ni el archivo antiguo `focus-state-v1.json` (instalación nueva). Una instalación existente o migrada **no se reescribe nunca**; el usuario sigue quitando o añadiendo en "Mi plan → Apps de ocio".
- Consulta `queryIntentActivities(MAIN/LAUNCHER)` una vez. **Visibilidad de paquetes (Android 11+):** el `<queries>` con `MAIN/LAUNCHER` ya estaba declarado en el manifiesto (0.58), así que todas las apps con icono son visibles. **No** se usa `QUERY_ALL_PACKAGES` ni se añade nada al manifiesto.
- Si algo falla, se empieza con la lista vacía (como en 0.58) y "Hoy" muestra "Elegir mis apps de ocio".

### 3. Tarjeta "Tu práctica" en "Hoy"

- `:lab` expone `personal.qqq.braintraining.PracticeSummary.load(context)` (Kotlin, `@JvmStatic`, campos `@JvmField` para usarla desde Java): sesiones de hoy, racha (días seguidos con al menos una sesión, terminando hoy o ayer, igual que `BrainViewModel.streakDays`), rango y puntos de Redes y seguridad (mismos umbrales de `netsecRank`).
- **Solo lectura.** Si `brain-training.db` no existe (Lab nunca abierto), devuelve un resumen vacío **sin crear la base**. Si existe, abre Room (misma clase `Store` de Lab), lee y cierra. Juegos desconocidos (p. ej. nuevos de la Fase 7) no rompen el cálculo.
- `es.foco.next.ui.PracticeCard` (Java) la ejecuta en un hilo propio (`foco-practice`, un solo hilo), nunca en el principal. Muestra "Cargando…", luego el resumen; si falla, "Sin datos de práctica por ahora". Guarda el último resumen 15 s para no releer en cada redibujado.
- No da minutos de ocio (lo dice la tarjeta). No abre Lab: el botón "Practicar 3 min" es de la Fase 2.

---

## Fase 6

### 1. Pausa multimedia al bloquear

`es.foco.next.runtime.MediaPause.pauseOthers(context)` se llama en `FocusOverlay.block()` cada vez que la pausa **se muestra** (no en el refresco de cada minuto), antes de pedir el foco de audio de la música:
- Envía `KEYCODE_MEDIA_PAUSE` DOWN + UP con `AudioManager.dispatchMediaKeyEvent`.
- Solo si `isMusicActive()` y el modo de audio es normal (sin llamada). Así no despierta una app de música parada (Android entrega la tecla a la última sesión multimedia). `PAUSE` nunca inicia reproducción (no se usa `PLAY_PAUSE`).
- Interruptor en Ajustes → Sonido → "Pausar otras apps al bloquear" (activado por defecto).
- Nunca lanza: si falla, la pausa y el foco de audio siguen igual. Sin permisos nuevos.

### 2. Ajustes de sonido (tarjeta "Sonido" en Ajustes)

`es.foco.next.runtime.SoundSettings` (preferencias `foco-soundscape`, las mismas de la música de 0.58):

| Clave | Tipo | Por defecto | Efecto |
|---|---|---|---|
| `silent` | booleano | `false` | "Sin sonido": apaga la música al instante, impide que arranque sola y calla los efectos de Lab. |
| `music_volume` | 0–100 | 100 | Escala la música: 100 % = 0,28 (el volumen de 0.58). Se aplica en caliente. |
| `effects_volume` | 0–100 | 100 | Escala los efectos de los juegos de Lab. |
| `pause_others` | booleano | `true` | Pausa multimedia al bloquear. |

Con los valores por defecto todo suena exactamente como en 0.58 / Arcade 2.1. Pulsar "Música tranquila" es una petición explícita: reproduce y quita "sin sonido".

### 3. Puente Foco → Lab (efectos)

`personal.qqq.braintraining.HostSound` (en `:lab`) lee `silent` y `effects_volume` del mismo archivo `foco-soundscape` (misma app y proceso; sin dependencia de `:app` ni intents). El contrato está documentado en `SoundSettings` y en `HostSound`. Lab sigue respetando su propio ajuste "Sonido" y además el silencio/vibración del teléfono y No molestar (ya lo hacía).

### 4. Sonidos de juego

Procedurales a partir de las 4 muestras **originales de Lab** (`lab_touch/correct/retry/complete.wav`, sin archivos nuevos ni licencias nuevas), con `SoundPool` (velocidad 0,5–2,0):
- **Acierto:** escala pentatónica mayor que sube con la racha de aciertos seguidos de la sesión: −5, −3, 0, +2, +4, +7 semitonos (sol-la-do-re-mi-sol) y se queda en la nota más alta.
- **Error:** suave, un tono por debajo (velocidad 0,89) y más bajo de volumen. La racha vuelve a empezar.
- **Cierre de sesión:** `lab_complete` (ya sonaba al terminar). Reinicia la racha.
- **Mezcla baja:** toque 0,20, acierto 0,36, error 0,26, cierre 0,40 (antes 0,22 / 0,42), multiplicado por el volumen de efectos de Foco.
- Respeta "sin sonido", el modo silencio/vibración y No molestar.

No se añadieron ambientes nuevos (pausa de día/noche/práctica): `scripts/make_soundscape.py` necesita numpy y ffmpeg, así que no se ejecutó aquí. Queda pendiente para una fase de diseño de sonido. Para regenerar `sendero.ogg` en el PC: `pip install numpy`, instalar ffmpeg y ejecutar `python scripts/make_soundscape.py`.

### 5. El lo-fi no se queda sonando

Ya garantizado en 0.58 (y cubierto por `CalmVisualTest.musicStopsWhenActivityLeavesForeground` y `QuietMusicTest`): la música solo suena con una superficie visible de Foco (Hoy en `onPause`, la pausa al quitarse la ventana), el servicio no es "sticky" y suelta el foco de audio. Se añade:
- `CalmAudioService.onTaskRemoved`: si se cierra Foco desde Recientes, se para y se libera.
- "Sin sonido" lo para al instante.

---

## Archivos tocados

**Compartidos** (cambios mínimos y marcados con `// Fase 5` o `// Fase 6`):

| Archivo | Cambio |
|---|---|
| `app/src/main/java/es/foco/next/ui/MainActivity.java` | +2 líneas: `PracticeCard.add(this,body)` en `today()` (después de "Tu tiempo de ocio") y `SoundSettingsCard.add(this,body)` en `settings()` (después de "Ambiente visual"). |
| `app/src/main/java/es/foco/next/ui/FocusOverlay.java` | +1 línea en `block()`: `MediaPause.pauseOthers(service)` tras `dismiss()`. |
| `lab/src/main/java/personal/qqq/braintraining/Experience.kt` | Solo `ExperienceFeedback`: campos `host` y `streak`, actualización de la racha al inicio de `play()` y volumen/velocidad en la llamada a `pool.play`. |

No se tocaron: `FocusAccessibilityService`, `AndroidManifest.xml`, `strings.xml`, `FocusEngine`, `FocusState`, recursos multimedia.

**Propios de estas fases:**

| Archivo | Tipo |
|---|---|
| `foco-core/src/main/java/es/foco/next/core/AlternativeCatalog.java` | modificado (bloque "Fase 5" + `social()`) |
| `app/src/main/java/es/foco/next/data/Repository.java` | modificado (`preselectLeisure`, solo instalación nueva) |
| `app/src/main/java/es/foco/next/runtime/CalmAudio.java` | modificado (`enabled` respeta "sin sonido", `toggle` lo quita, `soundSettingsChanged`) |
| `app/src/main/java/es/foco/next/runtime/CalmAudioService.java` | modificado (volumen de música, `applyVolume`, `onTaskRemoved`) |
| `app/src/main/java/es/foco/next/runtime/SoundSettings.java` | nuevo |
| `app/src/main/java/es/foco/next/runtime/MediaPause.java` | nuevo |
| `app/src/main/java/es/foco/next/ui/PracticeCard.java` | nuevo |
| `app/src/main/java/es/foco/next/ui/SoundSettingsCard.java` | nuevo |
| `lab/src/main/java/personal/qqq/braintraining/PracticeSummary.kt` | nuevo |
| `lab/src/main/java/personal/qqq/braintraining/HostSound.kt` | nuevo (`HostSound` + `GameTones`) |
| `docs/FASE-5-6.md` | nuevo |

## Pruebas nuevas (30)

| Módulo | Archivo | Pruebas |
|---|---|---|
| `:foco-core` | `LeisureCatalogTest` | 9: catálogo acordado, música fuera, solo instaladas, nada instalado, **esenciales/bancos/WhatsApp nunca**, catálogo sin esenciales ni bancos, lista del usuario y bancos por etiqueta ganan, alternativa nunca marcada, ocio nunca es alternativa. |
| `:app` | `LeisurePreselectionTest` | 2: instalación nueva marca YouTube y Roblox, no WhatsApp, banco, Spotify ni ReadEra; una instalación existente no se reescribe. |
| `:app` | `SoundSettingsTest` | 9: valores por defecto = 0.58, volúmenes separados y acotados, "sin sonido" silencia y evita autoarranque, "sin sonido" para la música en curso, el botón de música quita "sin sonido", `onTaskRemoved` para el lo-fi, condiciones de la pausa multimedia, DOWN+UP de `MEDIA_PAUSE`, nunca lanza. |
| `:lab` | `PracticeSummaryTest` | 5: vacío, hoy/racha/puntos, racha sin sesión hoy, juego desconocido, `load` no crea la base. |
| `:lab` | `HostSoundTest` | 5: "sin sonido" de Foco calla Lab, volumen de efectos escala, volumen 0 no suena, valores por defecto, pentatónica creciente dentro de 0,5–2,0. |

Las pruebas existentes no cambian. Efecto esperado en ellas: en `CalmVisualTest` el Robolectric instala Facebook antes de crear el repositorio, así que ahora Facebook queda marcado como ocio en esa prueba (no afecta a ninguna aserción); `ExperienceFeedbackTest` sigue viendo un volumen dentro de 0,01–0,5.

## Verificar en el PC

```sh
./gradlew :foco-core:test :lab-core:test :lab:testDebugUnitTest :app:testDebugUnitTest :app:lintDebug :app:assembleDebug
```

## Riesgos de compilación (no comprobados aquí)

1. `app/src/main/java/es/foco/next/ui/PracticeCard.java:11` y `:41` — Java de `:app` usa la clase Kotlin `PracticeSummary` de `:lab` (`implementation`). Solo usa `load` (`@JvmStatic`), campos `@JvmField` y `isEmpty()`; `from` es `internal` para no exponer `ResultRow` de `:lab-data`. Si javac pide clases de Kotlin (`kotlin.Metadata`, `org.jetbrains.annotations.*`) serán avisos, no errores. Si aparece "cannot access ResultRow", añadir `implementation project(':lab-data')` a `app/build.gradle` (no debería hacer falta).
2. `lab/src/main/java/personal/qqq/braintraining/PracticeSummary.kt:28` — `EMPTY` usa `netsecRank` de `NetSecViews.kt` (archivo con Compose). Si la Fase 7 cambia o mueve `netsecRank`, ajustar aquí.
3. `lab/src/main/java/personal/qqq/braintraining/PracticeSummary.kt:36` — `runBlocking` (kotlinx-coroutines, ya dependencia de `:lab`) en el hilo de `PracticeCard`, nunca en el principal.
4. `lab/src/test/java/personal/qqq/braintraining/HostSoundTest.kt:48` — `ShadowSoundPool.getResourcePlaybacks(...).single().leftVolume` (misma API que usa `ExperienceFeedbackTest`).
5. `app/src/test/java/es/foco/next/runtime/SoundSettingsTest.java:78` — `AudioManager.dispatchMediaKeyEvent` dentro de Robolectric: `MediaPause` atrapa `RuntimeException`; si Robolectric lanzara un `Error`, cambiar el `catch` de `MediaPause.java:27` a `Throwable` (la prueba no comprueba el envío, solo que no rompa).
6. `app/src/main/java/es/foco/next/ui/PracticeCard.java:18-19` — `Handler` estático del hilo principal y ejecutor de un hilo. En pruebas Robolectric de `MainActivity` (p. ej. `CalmVisualTest`) la base de Lab no existe, así que `load` vuelve enseguida sin Room.
7. `app/src/main/java/es/foco/next/runtime/CalmAudioService.java:57` — `onTaskRemoved` llama a `CalmAudio.interrupted()`: la música queda "suspendida" hasta que se cierren todas las superficies de Foco (mismo comportamiento que una pérdida de foco de audio).
8. `foco-core/src/main/java/es/foco/next/core/AlternativeCatalog.java:90` — `social()` ahora incluye todo el catálogo; `Repository.decodeConfig` vacía `returnApp` si era una app de ocio del catálogo (cambio intencionado).
9. Lint: `SoundSettingsCard` usa `android.widget.Switch` y `SeekBar` como `AmbientView.settings` (0.58 ya usa `Switch`). Textos en Java, sin `strings.xml` (como el resto de Foco).

## Lista de comprobación en el Samsung

Fase 5:
- [ ] Desinstalar Foco (o borrar datos), instalar y abrir: "Mi plan → Apps de ocio · N" muestra marcadas solo las apps de ocio instaladas.
- [ ] WhatsApp, apps del banco, Teléfono, Ajustes, Mensajes y Spotify **no** aparecen marcadas.
- [ ] Quitar y añadir apps desde "Apps de ocio" funciona y sobrevive a reiniciar Foco.
- [ ] Instalar encima de una versión anterior: la lista de apps **no** cambia.
- [ ] "Hoy" muestra "Tu práctica": "Aún sin sesiones" sin haber usado Lab; tras una sesión en Lab y volver, el número sube (en ≤ 15 s o al volver a entrar).
- [ ] "Hoy" no se traba al abrir (la lectura es en segundo plano).

Fase 6:
- [ ] Rellenar la tabla de pausa multimedia (abajo).
- [ ] Ajustes → Sonido: mover "Música" cambia el volumen en caliente; "Efectos" cambia el volumen en Lab.
- [ ] "Sin sonido" para la música al instante, la pausa ya no la arranca y los juegos de Lab quedan en silencio (la vibración sigue si está activada en Lab).
- [ ] En Lab: aciertos seguidos suben de tono; un error suena suave y reinicia; al terminar suena el cierre.
- [ ] Con el teléfono en silencio/vibración o No molestar, Lab no suena.
- [ ] El lo-fi para al: salir de Foco con Inicio, abrir otra app desde la pausa, cerrar Foco desde Recientes, apagar la pantalla.

### Tabla de la pausa multimedia (rellenar en el Samsung)

Procedimiento: reproducir contenido en la app, agotar los 5 min de ocio (o tener el ocio bloqueado) y abrir una app bloqueada para que salga la pausa. Repetir con "Pausar otras apps al bloquear" activado y desactivado. Anotar: **pausa** / **baja volumen** / **sigue sonando** / **otro**.

| App (versión) | En primer plano al bloquear | En segundo plano (otra app de ocio delante) | Con el ajuste desactivado (solo foco de audio) | Notas |
|---|---|---|---|---|
| YouTube (   ) |  |  |  |  |
| Spotify (   ) |  |  |  |  |
| TikTok (   ) |  |  |  |  |
| Instagram — Reels (   ) |  |  |  |  |

Modelo y versión de One UI: ________ · Fecha: ________

No prometer en textos ni README lo que la tabla no confirme.

## Revisión de licencias (pendiente antes de distribuir)

- **Morning Routine — Ghostrifter Official** (`res/raw/morning_routine.m4a`) y **Colorful Flowers — Tokyo Music Walker** (`res/raw/colorful_flowers.m4a`): los proporcionó el usuario. Ambos autores publican música "libre para usar" bajo condiciones propias (normalmente con atribución obligatoria y, según la pista, licencia Creative Commons o la de una plataforma como Chosic, Pixabay o el canal del autor). **No se verificó la licencia concreta de estas pistas.** Antes de distribuir el APK fuera del uso personal:
  1. Localizar la página oficial de cada pista y guardar el texto de la licencia (captura o PDF con fecha).
  2. Confirmar que permite redistribuir el archivo **dentro de una app** (no solo usarlo en vídeos) y si exige atribución con formato concreto.
  3. Si exige atribución, mantener el crédito visible (ya está en Ajustes → "Protección del plan") y añadirlo a un aviso de terceros de `:app`.
  4. Si no permite redistribución en apps, sustituir las pistas por música original (p. ej. ampliar `scripts/make_soundscape.py`).
- `sendero.ogg` es original (generado por script, sin muestras). Los sonidos `lab_*.wav` son de Cognitive Lab Arcade; los tonos de esta fase son transformaciones de esos mismos archivos (sin material nuevo).
- `ambiente_lluvia_suave.ogg`, `ambiente_noche.ogg` y `ambiente_piano.ogg` son originales de Foco, generados por `scripts/make_ambient.py` (síntesis con numpy, sin muestras ni descargas). Libres de derechos de terceros.

## Sin probar en dispositivo

Todo: no se compiló ni se instaló. En especial, la pausa multimedia por app, los paquetes del catálogo por región y la lectura de Room mientras Lab está abierto (dos instancias de Room en el mismo proceso, solo lectura).
