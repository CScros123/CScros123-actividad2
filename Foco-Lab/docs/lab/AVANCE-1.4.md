# Cognitive 1.4 — evaluación y progreso

Esta entrega contiene código, no una APK instalable.

## Cambios y compatibilidad

El núcleo Evaluation agrupa errores observables y compara precisión entre dos grupos de tres sesiones del mismo juego, dificultad y modo. Los consejos corresponden al tipo de error y área practicada. No diagnostican capacidades cognitivas ni estiman CI.

BrainViewModel añade responseMs y mode al JSON de cada ronda. La mediana histórica usada por Adaptive no cambia. Las rondas antiguas sin estos campos se leen con modo desconocido; si un bloque restaurado mezcla modos tampoco se compara. En resultados e historial se muestra un resumen con sugerencias.

El límite de tiempo activo por ronda pasa de 120000 a 86400000 ms. Los validadores de copias permiten esa duración y validan los campos nuevos. Room permanece en versión 1 y la copia en formato 1: no se cambian columnas. VersionCode 5 y versionName 1.4.0. Las copias 1.3 siguen siendo legibles; 1.3 no puede leer una copia nueva con rondas superiores a dos minutos.

Se conservan Adaptive y sus criterios por juego porque la restauración reconstruye el nivel usando ese algoritmo. Cambiarlo silenciosamente invalidaría el historial antiguo. La siguiente fase necesita un algoritmo versionado y migración comprobada si se desea cambiar los criterios.

## Verificación realizada

- Compilación JVM del núcleo completo y sus pruebas con Kotlin 1.9.23 y Java 17.
- JUnit: 21 pruebas superadas, 15 existentes y 6 nuevas de evaluación.
- Comprobación aislada de compilación de EvaluationPresentation contra las APIs JSON Android, con un sustituto mínimo de ResultRow. Esto no verifica Room ni Compose.
- Se añadieron dos pruebas Robolectric: copia de sesión sin cronómetro de una hora y rechazo de tiempo de respuesta negativo sin perder datos. No se ejecutaron: faltan las dependencias Android/Room/Robolectric.
- Gradle offline no pudo resolver Android Gradle Plugin 8.7.3. No hay compilación integral, APK firmada, verificación visual ni prueba en Samsung S23 Ultra de esta versión.

## Para completar la versión instalable

En Android Studio con SDK 35, JDK 17 y las dependencias del proyecto, ejecutar `./gradlew :core:test :app:testDebugUnitTest :app:assembleDebug` (en Linux dar permiso de ejecución a gradlew). Ejecutar además lint y compilar release antes de distribuir.

Probar el resultado y el historial en pantalla pequeña y con texto ampliado; una ronda de cinco minutos; pausa y vuelta desde segundo plano; copia 1.3; copia 1.4; rechazo de copia corrupta; actualización firmada con la misma clave sin pérdida de historial. Mantener la firma privada. La firma de prueba permanece en el ZIP original y no en esta entrega.
