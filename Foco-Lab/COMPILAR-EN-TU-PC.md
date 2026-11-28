# Compilar Foco + Lab en tu PC

Estado: compila y pasan las 565 pruebas (`foco-core` 206, `lab-core` 142, `lab` 80, `app` 137). Para hacer cambios, mira primero `docs/MAPA-DEL-CODIGO.md`.

## 1. Preparar
1. Instala Android Studio. En *Settings → Languages & Frameworks → Android SDK* marca **Android 15 (API 35)** y, en SDK Tools, **Build-Tools 35.0.0**.
2. *File → Open* → carpeta `Foco-Lab`. Espera al *Gradle Sync*.
3. *Settings → Build, Execution, Deployment → Build Tools → Gradle → Gradle JDK*: **17** o **21** (evita fallos de kapt/Room). Java 25 no sirve: el plugin de Kotlin del proyecto no lo reconoce. Desde la terminal: `export JAVA_HOME=/ruta/al/jdk-21`.

## 2. Compilar por partes (en este orden, terminal de Android Studio)
En Windows usa `gradlew.bat`; en Mac/Linux `./gradlew`.
```
gradlew.bat :foco-core:test
gradlew.bat :lab-core:test
gradlew.bat :lab:testDebugUnitTest
gradlew.bat :app:testDebugUnitTest
gradlew.bat :app:lintDebug
gradlew.bat :app:assembleDebug
```
Ir por partes ayuda a localizar el error. APK: `app/build/outputs/apk/debug/app-debug.apk`.

## 3. Si algo falla
Cada fase dejó su lista de riesgos con archivo:línea y arreglo:
- `docs/FASE-1.md` (estructura de módulos, manifest, Gradle)
- `docs/FASE-2-3.md` (práctica corta y DNS)
- `docs/FASE-4.md` (registro de bloqueos)
- `docs/FASE-5-6.md` (catálogo, tarjeta "Tu práctica", sonido)
- `docs/FASE-7-PILOTO.md` (Subredes, Phishing, Puertos)

Arregla el primer error que aparezca y vuelve a ejecutar ese mismo comando. Si no sabes cómo, copia el mensaje de error completo y pásamelo.

## 4. Instalar sin perder tus datos de Foco
El paquete es `es.foco.next`, el mismo que tu Foco actual (firmado con tu clave). Un APK debug tiene otra firma y Android no deja instalarlo encima.
- Para probar al lado: en `app/build.gradle`, dentro de `buildTypes`, añade `debug { applicationIdSuffix '.dev' }`.
  Nota: con el sufijo, el código que trata `es.foco.next` como app esencial no reconocerá la copia `.dev`, así que la pausa podría aparecer sobre la práctica. Úsalo solo para revisar pantallas.
- Para la prueba real: compila **release** firmado con tu clave (variables de entorno `FOCO_KEYSTORE`, `FOCO_STORE_PASSWORD`, `FOCO_KEY_ALIAS`, `FOCO_KEY_PASSWORD`) con `gradlew.bat :app:assembleRelease`. Antes, exporta tu plan y apuntes desde la app actual.

## 5. Probar en el Samsung
Las listas de comprobación de dispositivo están en cada `docs/FASE-*.md`. Lo más importante:
- Practicar 3 min desde la pausa: no aparece la pausa encima y al volver no cambia el presupuesto.
- DNS privado: apagarlo pausa ocio y navegadores tras ~60 s; Ajustes, Teléfono y WhatsApp siguen abiertos; al encenderlo se quita la pausa.
- Registro: cada bloqueo aparece con hora y app; exportación CSV.
- Pausa multimedia: tabla por app (YouTube, Spotify, TikTok, Instagram) en `docs/FASE-5-6.md`.
- Juegos piloto: arrastre de la máscara, deslizar correos, interruptores de puertos, fluidez.

## 6. Lotes 1–5 (bloqueos con nombre)
- Lote 1 · `docs/LOTE-1-BLOQUEOS.md`: bloqueos con nombre, límite, pausas con tope, estricto, apps nuevas.
- Lote 2 · `docs/LOTE-2-HORARIOS.md`: horarios, lista blanca, bloqueo rápido y Pomodoro.
- Lote 3 · `docs/LOTE-3-WEB.md`: sitios, palabras clave y navegadores no compatibles.
- Lote 4 · `docs/LOTE-4-ESTADISTICAS.md`: estadísticas Día / Semana / Tendencia.
- Lote 5 · `docs/LOTE-5.md`: duplicar, copia de bloqueos, compras en Google Play, arreglos.

Comprobación combinada en el teléfono:
- [L1] Crear «Redes» con 2 apps y 1 min: al gastarlo aparece la pausa; usar 1 pausa y ver que no deja una segunda si el tope es 1.
- [L1] Instalar una app con «Añadir apps nuevas» activo: aviso y queda en el bloqueo.
- [L1/L5] Estricto + bloqueo activo: no se puede editar ni restaurar copia; Ajustes peligrosos se cierran.
- [L2] Horario 22:00–07:00 con bloqueo total: fuera de franja la app abre; dentro, no.
- [L2] Lista blanca: solo abren las apps elegidas, Teléfono, Ajustes y Foco.
- [L2] Bloqueo rápido de 15 min y Pomodoro: bloquean todas las apps de ocio sin pausas.
- [L3] Sitio youtube.com en Chrome y Samsung Internet: se cierra; youtube.co no.
- [L3] Palabra clave en una página y navegador no compatible bloqueado.
- [L4] Estadísticas: Día con apps e iconos, Semana con 7 barras, Tendencia con flechas.
- [L5] Duplicar crea «X (copia)» desactivado; guardar y restaurar copia de bloqueos.
- [L5] Compra dentro de un juego: la hoja de Google Play se cierra; la ficha de Play Store sigue abierta.
- [L5] Usar una pausa: Estadísticas muestra «Pausas hoy: 1» e intentos aparte.
- [L5] Regla web con horario + estricto: Ajustes protegido solo dentro de la franja.

## 7. Versión release firmada (para uso diario, más ligera: ~15 MB)
Antes de compilar la release, comprueba que pasan las pruebas de la sección 2.

1. Crea tu clave **una sola vez** (guárdala fuera del proyecto y haz copia; sin ella no podrás actualizar):
   ```bash
   keytool -genkeypair -v -keystore ~/foco-release.jks -alias foco -keyalg RSA -keysize 4096 -validity 10000
   ```
2. Compila firmado:
   ```bash
   export FOCO_KEYSTORE=~/foco-release.jks
   export FOCO_STORE_PASSWORD='tu-contraseña'
   ./gradlew :app:assembleRelease
   ```
   APK: `app/build/outputs/apk/release/app-release.apk`. Guarda también `app/build/outputs/mapping/release/mapping.txt` de cada versión.
3. Cambiar de la versión debug a la release obliga a **desinstalar una vez** (firmas distintas). Antes, en Foco: exporta la copia JSON del plan y la "Copia de los bloqueos". Después instala la release, restaura ambas copias y vuelve a conceder Accesibilidad, Acceso de uso, Administrador y Aparecer encima. Desde ahí, las actualizaciones release se instalan encima sin perder datos.

## 8. Si Maven Central responde 429 (Too Many Requests)
Usa el espejo de Google que trae el proyecto: `./gradlew -I docs/mirror.gradle <tarea>`.
