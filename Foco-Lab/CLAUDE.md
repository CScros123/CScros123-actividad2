# Foco + Lab: guía para hacer cambios

App Android (paquete `es.foco.next`) con dos partes: **Foco** (bienestar digital, Java) y **Lab** (juegos de entrenamiento, Kotlin + Compose). Textos de la app y documentación en español.

- **Dónde está cada cosa:** `docs/MAPA-DEL-CODIGO.md`. Por qué se diseñó así: índice en `docs/README.md`.
- **Reglas en los módulos `-core`, pantallas en `app` y `lab`.** Una regla nueva o un número ajustable va en `foco-core` o `lab-core`, como constante con nombre, y se prueba allí.
- **No relajar las reglas de Foco** (presupuesto, noche, redes sociales, banco, modo estricto) sin que el usuario lo pida. Están explicadas en `docs/REGLAS-ABSOLUTAS.md`.

## Comprobar un cambio
- JDK 17 o 21 (no 25: el plugin de Kotlin 1.9.25 no lo reconoce).
- Formato: `./gradlew spotlessApply` (ktfmt kotlinlang y google-java-format AOSP). Solo formato.
- Pruebas del módulo tocado: `:foco-core:test`, `:lab-core:test`, `:lab:testDebugUnitTest`, `:app:testDebugUnitTest`.
- APK: `./gradlew :app:assembleDebug`.
- Si Maven Central responde 429: añadir `-I docs/mirror.gradle`.
- Las pruebas de `app` que dependen de la hora fijan el reloj con `WallClock.fixForTests` (Regla 2: de noche todo el ocio se cierra).
