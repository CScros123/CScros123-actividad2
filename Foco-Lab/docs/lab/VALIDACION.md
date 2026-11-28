# Validación de Cognitive 1.3.0

Fecha: 2026-10-06 UTC.

## Compilación y firma

assembleRelease, core:test, app:testDebugUnitTest y lintRelease completados. APK alineado y firmado con la clave de prueba original. Verificación de apksigner y zipalign aprobada. Certificado idéntico al APK 1.2.0.

Paquete: personal.qqq.braintraining. Versión 1.3.0, código 4. Mínimo Android 13/API 33; objetivo API 35. SHA-256 del APK: 232f1d06de93a560120ef745c034167d5802d5aec948cf591d7761a9274b0bba

## Pruebas

25 pruebas JUnit aprobadas, sin fallos ni errores. Lint: sin incidencias.

- RulesTest (4), MathGamesTest (3), EventsTest (5): generadores, adaptación, operaciones y recompensas.
- VisualGamesTest (3): resuelve 3.000 balanzas desde la igualdad mostrada, verifica ambas reglas y las cuatro orientaciones de Rumbo inverso, y verifica los ocho nodos y niveles de Órbitas.
- StorageTest (6): guardado, copias, restauración y notas reales, incluidos los tres nuevos juegos.
- ReminderTimeTest (2): cálculo del aviso diario.
- LaunchTest (1): inicio, eventos, registro, catálogo, solución matemática y Balanza desde el catálogo hasta una respuesta correcta y su explicación.
- VisualScenesTest (1): comprueba que cada uno de los ocho nodos es visible y responde a su propia zona de toque. Renderiza también el tablero de Rumbo inverso.

Se inspeccionaron las capturas de inicio, catálogo, detalle y tablero de Balanza, sus soluciones y los componentes de Órbitas y Rumbo inverso. Los tableros de estos dos últimos se capturan como componentes, sin el cronómetro ni la cabecera del reproductor. Informes XML y capturas en docs/pruebas y docs/capturas.

## Límites

No instalado en un teléfono físico ni medido el rendimiento en dispositivo. La escritura en el diálogo de aplicación real conserva la limitación de la validación anterior con Robolectric; el guardado y su aparición en el registro sí se verifican. Las pruebas del recordatorio verifican fechas; su entrega real depende de permisos y ahorro de batería de Android.

En el teléfono: instalar encima de la versión anterior, probar Órbitas completo, ambas reglas de Rumbo inverso, la pausa y recuperación, Balanza, cambio de tema y exportación/importación. Mantener el paquete y la firma permite actualizar sin desinstalar.
