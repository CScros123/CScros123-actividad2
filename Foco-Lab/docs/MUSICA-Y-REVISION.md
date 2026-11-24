# Música y revisión

## Qué se añadió
- **Lista de reproducción lo-fi** (`runtime/LofiPlaylist.java`, usada por `CalmAudioService`): dos turnos que alternan (día/noche) y cada turno rota sus pistas; nunca repite la misma pista seguida.
  - Turno 0: Morning Routine, Lluvia suave, Piano ambiental. Turno 1: Colorful Flowers, Noche, Sendero.
- **Pistas originales nuevas** (`scripts/make_ambient.py`, numpy + ffmpeg, ogg, bucle continuo):
  `ambiente_lluvia_suave.ogg` (2:30, ~830 KB), `ambiente_noche.ogg` (2:30, ~330 KB), `ambiente_piano.ogg` (2:24, ~320 KB). `sendero.ogg` (ya existía, sin usar) entra en la lista.
- **Música durante la pausa** (Ajustes › Sonido): Lo-fi de Foco (por defecto) / Spotify / Apple Music / Ninguna, y "Enlace de tu playlist".
  Lógica pura en `PauseMusic.java` (tests en `PauseMusicTest`), lanzamiento en `PauseMusicLauncher.java`, llamada desde `FocusOverlay.block`.
  - Lo-fi: como antes. Ninguna: sin música, sigue pausando a otras apps.
  - Spotify/Apple Music: no pausa a otras apps; abre el enlace (ACTION_VIEW + paquete) o busca "lofi" (MEDIA_PLAY_FROM_SEARCH). Una vez por bloqueo. Si la app no está o falla → lo-fi de Foco.
  - La app elegida cuenta como esencial en `FocusAccessibilityService.essential()` y está en `NEVER_LEISURE` de `AlternativeCatalog`. `<queries>` declara ambos paquetes.

## Añadir tus pistas
1. Copia el archivo libre de derechos a `app/src/main/res/raw/` con nombre en minúsculas, sin espacios: `mi_pista.ogg` (ogg/m4a/mp3; ideal ≤ 1,5 MB).
2. En `CalmAudioService`: añade `R.raw.mi_pista` a `RAW`, su título a `TITLES` y su índice a uno de los dos turnos de `PLAYLIST`.
3. Anota autor y licencia en `docs/FASE-5-6.md` (Revisión de licencias).

## Comprobar en el móvil (Spotify / Apple Music)
- Elegir Spotify sin enlace → al bloquear se abre Spotify con una búsqueda/lista lofi (puede requerir sesión iniciada y Premium para reproducir solo).
- Con enlace `https://open.spotify.com/playlist/…` o `spotify:playlist:…` → abre esa lista. Apple Music: `https://music.apple.com/…`.
- Volver a la app bloqueada en el mismo bloqueo no vuelve a lanzar la app. Spotify en primer plano no se bloquea.
- Desinstalar la app elegida → suena el lo-fi de Foco.

## Revisión (lectura de código)
| Función | Estado | Nota |
|---|---|---|
| Sesión corta desde la pausa | OK | `FocusOverlay` "Practicar 3 min" → `PracticeEntry.open` → `LabLaunch.shortSession` |
| "Todos los juegos" en Hoy | OK | `PracticeEntry.today/openFull` → `LabLaunch.full` |
| Redes y seguridad | OK | `NetSecViews.kt` (Lab) |
| Pilotos Fase 7 en modo normal | OK | `Pilot.ids` (subnet, phishing, ports) ⊂ `NetSec.ids` |
| Arcade, catálogo, jardín | OK | `LiteApp.kt`: tarjeta Arcade, página `catalog`, `GardenCard` |
| Registro | OK | `RegistroActivity` en manifiesto; dos botones en `MainActivity` |
| DNS, Tu práctica, Sonido | OK | `MainActivity` 114/156/157 |
| Recursos `lab_` y tema | OK | sin referencias `R.*` sin prefijo en `:lab`; `LabActivity` usa `@style/lab_Theme` |
| Navegación atrás en Lab | OK (sin probar) | 7 `BackHandler` |
| Pendientes en docs | Abierto | FASE-1 instalación sobre 0.58; FASE-5-6 licencias Ghostrifter/Tokyo Music Walker; FASE-7 aprobación del piloto; FASE-8-9 transiciones, radios literales, `HostMotion` en caliente, capturas |
