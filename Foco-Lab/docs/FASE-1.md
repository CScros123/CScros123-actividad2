# Fase 1 — Unir Foco y Lab sin cambiar comportamiento

Versión: `0.59-fase1` (versionCode 59). `applicationId` y namespace de `:app`: `es.foco.next` (sin cambios).

> **Importante:** esta fase se preparó **sin compilar ni ejecutar Gradle** (a pedido del usuario). Todo se revisó leyendo el código y comprobando referencias con `grep`. La verificación real se hace en el PC con los comandos de abajo. La sección "Puntos no comprobados" lista lo que podría fallar.

## Estructura nueva

```
Foco (raíz, Gradle 8.9, un solo settings.gradle)
├── foco-core/   :foco-core  Java puro. Era `core/` de Foco 0.58; movido con git mv, sin cambios de código.
├── lab-core/    :lab-core   Kotlin JVM. Era `core/` de Cognitive Lab Arcade 2.1.
├── lab-data/    :lab-data   Room (kapt). Era `data/` de Lab. Esquemas en lab-data/schemas.
├── lab-design/  :lab-design Tema Compose. Era `designsystem/` de Lab.
├── lab/         :lab        Biblioteca Android (com.android.library). Era `app/` de Lab: pantallas Compose + LabActivity.
└── app/         :app        App final (Foco 0.58, Java + Views). Depende de :foco-core y :lab.
```

- Versiones de plugins declaradas **una sola vez** en `build.gradle` (raíz): AGP 8.7.3 (`application` y `library`), Kotlin 1.9.25 (`android`, `jvm`, `kapt`).
- La raíz y `:app`/`:foco-core` siguen en Groovy; los módulos de Lab siguen en Kotlin DSL (`*.gradle.kts`). Gradle admite la mezcla.
- Un solo wrapper (Gradle 8.9, el de Foco; el de Lab era idéntico byte a byte).
- La documentación original de Lab está en `docs/lab/` (README de Arcade 2.1 en `docs/lab/README-ARCADE-2.1.md`). Licencias y avisos de Lab en `lab/` (`LICENSE-PERSONAL.txt`, `THIRD-PARTY-NOTICES.md`, `licenses/`).
- `_lab-import/` se eliminó (todo su contenido se movió con `git mv`, salvo los archivos Gradle raíz duplicados).

## Qué cambió

### Gradle
- `settings.gradle`: `include ':foco-core', ':lab-core', ':lab-data', ':lab-design', ':lab', ':app'`.
- `build.gradle` (raíz): plugins de Foco y Lab con `apply false`.
- `gradle.properties`: fusionado. `-Xmx3072m` (antes 2048m; Compose + kapt + R8 piden más), `org.gradle.parallel=false`, `org.gradle.caching=true`, `org.gradle.workers.max=2`, `android.useAndroidX=true`, `kotlin.code.style=official`.
- `.gitignore`: fusionado (se añaden `keystore.properties` e `.idea/` de Lab).
- `gradlew`: ahora con bit de ejecución en Git (antes no lo tenía en Foco).
- `app/build.gradle`: `minSdk 26 → 33`, `versionCode 59`, `versionName '0.59-fase1'`, `implementation project(':foco-core')` (antes `:core`) e `implementation project(':lab')`; exclusión de `/META-INF/{AL2.0,LGPL2.1}` al empaquetar (licencias duplicadas de Kotlin/coroutines). `:app` sigue siendo solo Java: no aplica el plugin de Kotlin ni Compose.
- `lab/build.gradle.kts`: `com.android.library`, namespace `personal.qqq.braintraining` (se conservan los paquetes de Lab), `resourcePrefix = "lab_"`. Se quitan `applicationId`, `targetSdk`, `versionCode/Name`, `applicationIdSuffix ".preview"`, `resValue app_name`, minify y firma. `proguard-rules.pro` pasa a `consumerProguardFiles` (solo contiene un comentario). Dependencias: `:lab-core`, `:lab-data`, `:lab-design` y las mismas de Lab 2.1. `ui-test-manifest` pasa de `debugImplementation` a `testImplementation` para que la actividad exportada `androidx.activity.ComponentActivity` no se cuele en el APK debug de Foco.
- `lab-data/build.gradle.kts`: `project(":core")` → `project(":lab-core")`. kapt + Room 2.6.1 sin cambios; `room.schemaLocation = "$projectDir/schemas"` sigue apuntando a `lab-data/schemas` (la carpeta se movió con el módulo).
- `lab-core/build.gradle.kts`: `kotlin { jvmToolchain(17) }` se sustituye por `sourceCompatibility/targetCompatibility 17` + `jvmTarget = JVM_17` (igual que `:foco-core`). Así compila con el JDK que use Gradle (17, o el JBR 21 de Android Studio) sin exigir un JDK 17 instalado aparte.
- `lab-design/build.gradle.kts`: sin cambios.

### Manifiestos
- `lab/src/main/AndroidManifest.xml`: solo `VIBRATE` (permitido por la regla 2; Lab lo usa con `VibratorManager`) y `LabActivity` (`exported="false"`, tema `@style/lab_Theme`). Se quitan: intent-filter de launcher, atributos de `<application>` (icono, tema, etiqueta, backup, reglas de extracción), los receptores del recordatorio de Lab y los permisos `POST_NOTIFICATIONS` y `RECEIVE_BOOT_COMPLETED` (Foco ya los declara; Lab no los necesita sin recordatorio).
- `app/src/main/AndroidManifest.xml`: se añade `xmlns:tools` y se **elimina** el permiso propio `${applicationId}.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION` que inyecta `androidx.core` (traído por Compose). Con minSdk 33 androidx.core usa `RECEIVER_NOT_EXPORTED` nativo y no lo necesita. Resultado: la lista de permisos de la app queda igual que en 0.58 + `VIBRATE`.
- Entradas que sí añaden las bibliotecas AndroidX al manifiesto final (normales en cualquier app con Compose, sin permisos nuevos): `androidx.startup.InitializationProvider` (no exportado) y `androidx.profileinstaller.ProfileInstallReceiver` (exportado pero protegido por el permiso de sistema `android.permission.DUMP`).

### Código y recursos de Lab
- `MainActivity.kt` → `LabActivity.kt`; clase `MainActivity` → `LabActivity`. Se quita la llamada `LearningReminder.schedule(this)` de `onCreate`.
- Recordatorio de Lab desactivado: sin receptores en el manifiesto, sin programación al abrir y sin el bloque `ReminderSettings()` en Ajustes de Lab (`Settings.kt`). Las clases `LearningReminder`, `LearningReminderReceiver`, `ReminderRescheduleReceiver` y el composable `ReminderSettings` se conservan sin uso para no romper `ReminderTimeTest` (regla 4). Solo queda el recordatorio de Foco (`DailyReminder`).
- Recursos renombrados con prefijo `lab_`: `raw/touch|correct|retry|complete.wav` → `raw/lab_*.wav`; `drawable/ic_notification.xml` → `drawable/lab_ic_notification.xml`. Referencias actualizadas en `Experience.kt`, `LearningReminder.kt` y `ExperienceFeedbackTest.kt`.
- Recursos eliminados (los aporta Foco o ya no aplican): `AppTheme` (`values/styles.xml`), `ic_launcher*`, `mipmap-anydpi/ic_launcher.xml`, `values/launcher.xml` (`app_name`, `launcher_background`), `xml/data_extraction_rules.xml`.
- Nuevo `values/lab_themes.xml`: `lab_Theme` (padre `Theme.Material.Light.NoActionBar`, fondo `#171A21`, acento `#3667FF`, como el AppTheme de Lab pero sin splash ni icono).
- Pruebas de Lab movidas a `lab/src/test`. `LiteGamesTest` lanza `LabActivity` en lugar de `MainActivity`. Ninguna prueba se borró ni se debilitó.

### Foco
- Ningún cambio de código Java ni de recursos en `:app` ni en `:foco-core`. **No hay acceso desde Foco a Lab** (llega en la Fase 2): `LabActivity` queda registrada pero inaccesible. El texto de la app sigue diciendo "Foco 0.58 · Brisa viva" a propósito (comportamiento idéntico).
- Efecto de `minSdk 33`: la app ya no se instala en Android 8 a 12. Las comprobaciones `SDK_INT >= 28/31/33` y las carpetas `values-v27` siguen funcionando (lint puede marcarlas como `ObsoleteSdkInt`, solo advertencia).

## Pruebas (conteo de `@Test` en el código)

| Módulo | Pruebas |
|---|---|
| `:foco-core` | 32 |
| `:app` | 57 |
| `:lab-core` | 29 |
| `:lab` | 19 (Experience 4, LiteGames 3, ReminderTime 2, Storage 9, VisualScenes 1) |
| `:lab-data`, `:lab-design` | 0 |
| **Total** | **137** (89 de Foco + 48 de Lab) |

El plan citaba 39 pruebas de Lab; el código entregado contiene 48 anotaciones `@Test`.

## Verificar en el PC

Requisitos: Android Studio con JDK 17 (o el JBR incluido), SDK 35 y Build Tools 35.0.0. La primera sincronización descarga Kotlin, Compose, Room y Robolectric.

```sh
./gradlew :foco-core:test :lab-core:test :lab:testDebugUnitTest :app:testDebugUnitTest :app:lintDebug :app:assembleDebug
./gradlew :app:assembleRelease
```

En Windows: `gradlew.bat` con los mismos argumentos.

Comprobaciones extra recomendadas:
1. Permisos del APK: `aapt2 dump permissions app/build/outputs/apk/debug/app-debug.apk` (o "Merged Manifest" en Android Studio, pestaña de `app/src/main/AndroidManifest.xml`). Deben aparecer solo los de 0.58 + `android.permission.VIBRATE`, y **no** `DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION`.
2. En el manifiesto fusionado: una sola actividad launcher (`es.foco.next.ui.MainActivity`), `personal.qqq.braintraining.LabActivity` con `exported=false`, y ninguna `androidx.activity.ComponentActivity` exportada.
3. En el teléfono: instalar encima de 0.58 (misma firma). El plan, tiempos y apuntes deben seguir ahí y la app debe comportarse igual (Hoy, Mi plan, Ajustes, pausa, vídeo, música, recordatorio de Foco).

## Puntos no comprobados (posibles fallos de compilación o prueba)

1. `lab/build.gradle.kts:34` — `testImplementation("androidx.compose.ui:ui-test-manifest")`. En una biblioteca, AGP debe fusionar el manifiesto de las dependencias de prueba en el manifiesto de pruebas unitarias para que `VisualScenesTest` encuentre `ComponentActivity`. Si esa prueba falla con "Unable to resolve activity", cambiar a `debugImplementation(...)` y, para no exportar esa actividad en el APK debug de Foco, añadir en `app/src/debug/AndroidManifest.xml` `<activity android:name="androidx.activity.ComponentActivity" tools:node="remove"/>`.
2. `lab-core/build.gradle.kts:4-6` — sustitución de `jvmToolchain(17)` por `java {}` + `compilerOptions.jvmTarget`. Sintaxis válida en KGP 1.9.25 según la API, pero no compilada aquí. Si falla, volver a `kotlin { jvmToolchain(17) }` (requiere JDK 17 instalado).
3. `app/build.gradle:24` — `packaging { resources { excludes.add('/META-INF/{AL2.0,LGPL2.1}') } }` (DSL Groovy de AGP 8). Si Gradle no reconoce `packaging`, usar `packagingOptions { resources { excludes += [...] } }`. Si aparece otro error "2 files found with path 'META-INF/…'", añadir esa ruta a la misma lista.
4. `app/src/main/AndroidManifest.xml:8-9` — eliminación con `tools:node="remove"` del permiso de androidx.core usando el marcador `${applicationId}`. Si el merger no lo empareja, el permiso seguiría apareciendo (no rompe la compilación; solo revisar el manifiesto fusionado).
5. `lab/src/main/res/values/lab_themes.xml` — nombre de estilo `lab_Theme`. Cumple `resourcePrefix` literalmente; lint de `:lab` no se ejecuta en el comando de la fase (`:app:lintDebug` no revisa dependencias por defecto).
6. `:app:testDebugUnitTest` ahora carga en Robolectric el manifiesto fusionado con `InitializationProvider` de AndroidX (emoji2, lifecycle, profileinstaller). Lab ya funcionaba así en sus pruebas; no se espera impacto en las 57 pruebas de Foco, pero es lo único nuevo en su entorno.
7. kapt (Room) con JDK 21: Kotlin 1.9.25 lo soporta; si Android Studio usa JBR 21 y kapt falla, poner el JDK de Gradle en 17 (Settings → Build Tools → Gradle → Gradle JDK).
8. `:app:lintDebug` con `abortOnError true`: subir minSdk a 33 genera advertencias `ObsoleteSdkInt` (carpetas `values-v27`, `SDK_INT >= 28/31/33`). Son advertencias, no errores; no se tocó el código de Foco para no cambiar comportamiento.
9. `lab/src/main/java/personal/qqq/braintraining/ReminderSettings.kt` queda sin uso (solo advertencia del compilador de Kotlin).

## Sin probar en dispositivo

Todo: esta fase no se compiló. Pendiente la instalación sobre 0.58 en el Samsung y la comprobación de que nada cambia para el usuario.
