# Cognitive Lab · Arcade (edición reconstruida de prueba)

Proyecto Android reconstruido a partir del código de Cognitive 1.5, con los cambios descritos para Cognitive Lab 1.8. El código fuente original de Lab 1.8 no estaba disponible. La reconstrucción conserva los 21 ejercicios del proyecto anterior y añade juegos originales de parejas y arcade.

## Jugar

- **Parejas matemáticas:** una columna muestra operaciones y la otra sus resultados. Al acertar se eliminan las dos cartas y el resto cambia de posición; cada resultado del tablero es único. Se generan nuevos tableros al terminar.
- **Dificultad de parejas:** Suave (sumas y restas), Media e Intensa (también multiplicaciones y cantidades mayores).
- **Pulso de neón:** toca la luz que cambia de posición.
- **Ruta espacial:** muévete entre tres carriles para evitar meteoros.
- **Modo infinito:** sin cronómetro; continúa hasta que salgas. Disponible en los tres juegos nuevos y en los ejercicios existentes. No genera logros, niveles ni resultados guardados.
- **Con tiempo:** 60 segundos en los juegos nuevos; los ejercicios existentes usan sus tiempos por ronda y terminan tras 12 rondas.
- Los juegos se pausan al ir a segundo plano. Sonidos, respuesta táctil, animaciones y modo oscuro se configuran en Ajustes.
- Se juega sin conexión. Android 13 o superior.

## Instalación y datos

La edición de prueba se instala junto a Cognitive Lab 1.8.0 con identificador `personal.qqq.braintraining.labreconstruida`. Usa almacenamiento separado y no traslada automáticamente los datos de Lab 1.8.0. No es una actualización directa del APK original y no se ha reconstruido a partir de su fuente. Mantén instalada la app anterior para conservar sus datos.

El APK debug se firma con una clave de prueba local. Para actualizar una app existente hay que usar su mismo identificador, código fuente compatible y clave de firma. Nunca compartas la clave privada.

## Compilar

Android Studio, JDK 17, SDK 35 y Build Tools 35.0.0. La primera sincronización requiere conexión.

```bash
./gradlew :core:test :app:testDebugUnitTest :app:assembleDebug
```

APK: `app/build/outputs/apk/debug/app-debug.apk`. Las pruebas comprueban reglas y operaciones, movimiento de cartas, partidas infinitas, pausa, sonido, datos y horarios. Los efectos de sonido y vibración aún necesitan comprobarse en un teléfono físico.

Proyecto de uso personal; consulta los avisos y licencias incluidos.
