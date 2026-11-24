# Fases 8 y 9 (versión mínima)

## Cambios
- **Tokens únicos:** `FocoTokens` en `lab-design/.../design/Theme.kt` (mismos valores que `app/src/main/res/values{,-night}/colors.xml`, que ya estaban alineados). El esquema claro de Lab usa fondo, superficie, texto, secundario y acento de Foco; el oscuro usa fondo/superficie grafito-verde de Foco. `Shapes`: 18 dp (medium, botones) y 20 dp (large, tarjetas).
- **Áreas más suaves:** `areaColor` en `lab/.../LabActivity.kt:77` con saturación reducida.
- **Reducir movimiento:** `HostMotion` (en `lab/.../HostSound.kt`) lee `foco-ambient` → `reduce`/`saving`; `ExperienceHost` (`Experience.kt:88`) apaga animaciones decorativas si alguna está activa (además de la pref de Lab y la escala del sistema).
- **R8:** `app/proguard-rules.pro` con `-repackageclasses ''`, `-allowaccessmodification`, `-renamesourcefileattribute`, `-keepattributes SourceFile,LineNumberTable` y eliminación de `Log.v/d/i`.
- **mapping.txt:** guardar `app/build/outputs/mapping/release/mapping.txt` de cada versión publicada junto al APK (p. ej. `releases/<versión>/mapping.txt`).

## Pendiente
- Transición pausa → práctica (desenfoque, hojas atenuadas) y transición entre pestañas.
- Radios 20/18 dp sólo vía `MaterialTheme.shapes`; usos con `RoundedCornerShape` literal no se tocaron.
- `HostMotion` se lee al recomponer; cambiar la pref en Foco con Lab abierto no se refleja hasta recomponer/reabrir.
- Capturas claro/oscuro de "Hoy", pausa y una ronda.

## Checklist del APK release
- [ ] Servicio de Accesibilidad se activa y bloquea; pausa aparece.
- [ ] Vídeo de pausa y audio de calma; silencio/volumen de efectos respetados en Lab.
- [ ] Juegos de Lab abren, puntúan y guardan (Room); progreso persiste tras reiniciar.
- [ ] Exportaciones (registro de bloqueos, datos) generan archivo válido.
- [ ] "Reducir movimiento" / ahorro de energía en Foco quitan animaciones en Lab.
- [ ] Recordatorio diario y DNS siguen funcionando; sin crash en `adb logcat`.
- [ ] Guardado `mapping.txt` de la versión.

## Riesgos de compilación / R8
- `Theme.kt`: `Shapes(...)`/`RoundedCornerShape` requieren foundation (ya es `api` en `lab-design/build.gradle:14`).
- `Experience.kt:88`: `LocalContext.current` dentro de expresión composable; si el linter se queja, extraer a `val`.
- `app/proguard-rules.pro`: `-repackageclasses ''` + `-allowaccessmodification` puede romper código que use reflexión por nombre de clase (no detectada; revisar crash en release). Si Room falla, añadir `-keep class * extends androidx.room.RoomDatabase`.
