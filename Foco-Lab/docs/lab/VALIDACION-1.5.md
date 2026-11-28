# Validación de Cognitive 1.5.0

Fecha: 28 de octubre de 2026. Esta validación corresponde al código y APK de esta entrega.

## Resultado

- Compilación limpia de la app y generación de APK debug: correcta.
- 39 pruebas aprobadas: 21 del núcleo Kotlin y 18 de la app; 0 fallos, 0 errores, 0 omitidas.
- Lint release: 0 errores y 5 avisos sobre versiones más recientes de dependencias. Se conservaron las versiones del proyecto para limitar el alcance de esta mejora.
- Firma del APK comprobada con apksigner; paquete `personal.qqq.braintraining.preview`, versión `1.5.0-preview`, código 6, Android 13+.

Comando ejecutado con JDK 17, Gradle 8.9, SDK 35 y Build Tools 35.0.0:

```bash
./gradlew :app:clean :core:test :app:testDebugUnitTest :app:lintRelease :app:assembleDebug
```

## Qué se comprobó

Las pruebas cubren reglas y generación de juegos, evaluación, copias y validación atómica del historial, recordatorios, navegación, retos, soluciones, tableros de memoria/dirección y los controles nuevos. La prueba de ajustes comprueba su persistencia al salir y volver a entrar; la de almacenamiento comprueba exportar/restaurar los tres ajustes y rechazar valores inválidos sin perder datos.

Las cuatro pruebas del controlador de efectos comprueban sonidos distintos sin bucle, volumen moderado, independencia entre sonido y respuesta táctil, silencio/vibración, No molestar y ausencia de efectos cuando la ventana no está visible o no tiene foco. Se usa SoundPool simulado por Robolectric, no reproducción en hardware.

Se generaron e inspeccionaron capturas mediante Compose y Robolectric con gráficos nativos, SDK 33 y una pantalla de 411 × 891 dp. Incluyen inicio claro/oscuro, catálogo, ajustes, eventos, balanza, órbitas, dirección y soluciones. La captura de ajustes muestra los tres efectos desactivados tras comprobar los interruptores.

- [Capturas](capturas-1.5/)
- [Resultados por prueba](pruebas-1.5/resumen.json)
- [Salida de compilación](pruebas-1.5/compilacion.txt)
- [Lint](pruebas-1.5/lint-release.txt)

## Límites

No se ejecutó en teléfono físico ni se midió fluidez en dispositivo. La sensación, intensidad y latencia de la vibración, el volumen percibido y las rutas de audio (altavoz/auriculares/Bluetooth) deben comprobarse en un teléfono. No se verificó actualización directa con la clave original, que no está incluida: el APK de prueba se instala aparte y usa datos independientes.

Los informes y capturas en otras carpetas corresponden a versiones anteriores.

SHA-256 del APK de prueba:

```text
bceb7674b4173680990b9c362412d430bbadb01c31822050b6e98437c38d080a
```
