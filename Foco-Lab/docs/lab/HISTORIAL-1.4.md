# Cognitive · qqq · 1.4.0 (avance de código)

Aplicación Android en español para practicar matemáticas, resolución de problemas, lógica, atención y memoria. Funciona sin conexión, sin cuentas ni anuncios. Android 13 o superior.

## Estado de esta entrega

Código actualizado; aún no hay APK 1.4 compilada ni prueba en teléfono. El núcleo Kotlin se compiló y sus 21 pruebas JUnit pasaron. No se debe interpretar la validación histórica incluida como validación de la interfaz 1.4.

## Mejoras 1.4

- Registro por ronda del modo (con/sin cronómetro) y del tiempo de respuesta, también en respuestas incorrectas cuando existe una respuesta.
- Desglose de errores y sugerencia de estrategia en el resultado y en el historial.
- Comparación de las últimas seis sesiones del mismo juego, nivel y modo: tres anteriores frente a tres recientes. No mezcla sesiones antiguas cuyo modo no se conoce.
- Tiempo activo máximo por ronda ampliado de 2 minutos a 24 horas como protección contra tiempos extremos. Pausar sigue descartando la ronda incompleta; no se cuenta tiempo en segundo plano.
- Copias JSON con las evidencias nuevas y lectura compatible con copias anteriores. Las copias nuevas con rondas de más de dos minutos requieren 1.4; no deben restaurarse en 1.3.
- Se mantienen las reglas adaptativas originales, el identificador y el esquema Room para conservar la coherencia del historial. Una futura modificación del algoritmo requerirá versionarlo y validar la reconstrucción de copias.

## Novedades anteriores

- Nombre Cognitive e icono nuevo con una C y el signo igual.
- 21 juegos en 6 áreas. Matemáticas y vida cotidiana aparecen primero.
- Cálculo con suma, resta, multiplicación, división y paréntesis; porcentajes; fracciones; ecuaciones; proporciones; presupuesto; comparación por unidad; planificación del tiempo.
- Solución explicada después de responder, con respuesta correcta marcada y selección incorrecta destacada.
- Modo de aprendizaje sin cronómetro, activado inicialmente para matemáticas, lógica y vida cotidiana. Puedes desactivarlo en Ajustes para usar retos con tiempo.
- Rutina diaria: dos juegos matemáticos, uno cotidiano y uno de lógica. La selección prioriza los menos practicados.
- Una propuesta diaria para aplicar estrategias a compras, recetas, presupuestos, planificación o decisiones reales. Estos ejercicios son sugerencias de práctica; los resultados de los juegos no prueban mejoras generales de cognición.
- Recordatorio diario local con hora elegible, prueba de notificación y reprogramación tras reiniciar, actualizar o cambiar la hora/zona del teléfono.
- Tipografía más clara, claves estables en listas, cálculo de totales fuera del hilo principal, retos almacenados por ronda y contador aislado del tablero.

## Retos y recompensas

Eventos reúne tres retos diarios y tres metas semanales. Los juegos matemáticos y de situaciones cotidianas alternan por fecha. Registrar una aplicación concreta en tu día añade una nota al historial. Los objetivos semanales cuentan sesiones, días distintos y aplicaciones prácticas; no exigen días consecutivos. Cada recompensa se suma una sola vez, con XP e insignias. El registro conserva las recompensas al renovar los retos y viaja en la copia JSON. Descansar o dejar un reto pendiente no resta puntos.

La primera semana ajusta los objetivos de días a los días restantes. Las sesiones anteriores a esta actualización no generan recompensas retroactivas. Al borrar todo el historial también se reinician notas y recompensas.

## Diseño y nuevos juegos

Interfaz clara con un catálogo de mosaicos, portadas vectoriales originales, un color por área y tableros visuales. El tema claro es el valor inicial; un tema elegido explícitamente se conserva en Ajustes.

- Órbitas: memoriza y repite una secuencia en ocho nodos circulares.
- Rumbo inverso: sigue la dirección o responde a su opuesta según la regla escrita de la ronda.
- Balanza: deduce el peso de cajas iguales a partir de una igualdad y comprueba sustituyendo la respuesta.

Cada juego conserva sesiones de 12 rondas, pausa y dificultad adaptativa. Balanza participa en las sesiones matemáticas y en el aprendizaje sin cronómetro. Las portadas se dibujan con Canvas; no añaden descargas ni conexión al jugar.

## Recordatorios

Abre Ajustes → Aprender cada día. Activa el interruptor y permite notificaciones. La hora inicial es 19:00, hora local del teléfono, y puedes cambiarla. El recordatorio empieza desactivado; no requiere conexión. Android puede retrasarlo para ahorrar batería. Si fuerzas la detención de la app, vuelve a abrirla para reprogramar los avisos. El aviso abre Cognitive, donde puedes iniciar la rutina.

La hora y la activación del recordatorio se conservan en el teléfono; no viajan en la copia JSON del historial. Se requiere permitir también el canal «Aprender cada día» en Android.

## Datos y actualización

Se mantienen el identificador `personal.qqq.braintraining`, la base de datos y el formato de copia anterior. Las copias de Mente siguen siendo válidas. El ajuste de aprendizaje sí se exporta en las copias nuevas. Los nuevos juegos guardan progreso con el mismo sistema adaptativo de niveles 1–10. Subir o bajar requiere dos bloques consistentes de 12 rondas.

El tiempo activo contabilizado admite hasta 24 horas por ronda. El tiempo de respuesta sin cronómetro se describe como tiempo de resolución; no interviene en las reglas de velocidad adaptativa.

Para actualizar una instalación anterior sin perder datos, firma con la misma clave. La firma de prueba original se conserva únicamente en tu ZIP 1.3.0. Esta entrega no redistribuye la clave ni sus credenciales; para una actualización instalable se necesita esa misma firma y verificar la conservación de datos.

## Compilación

Abre la carpeta Cognitive con Android Studio. Instala JDK 17, SDK 35 y Build Tools 35.0.0; sincroniza las dependencias y ejecuta:

```bash
./gradlew :core:test :app:testDebugUnitTest :app:lintRelease :app:assembleDebug
```

Consulta `docs/COMPILAR-Y-FIRMAR.md` para firmar una actualización. El APK debug lleva una firma distinta de la firma de prueba entregada anteriormente.

## Estado de verificación de esta entrega

Se generó el APK release instalable `Cognitive-1.3.0.apk`, entregado por separado, y se verificó su firma con la clave de prueba original. Se mantienen identificador y certificado para actualizar Mente.

Pasaron 25 pruebas del proyecto: lógica, respuestas de los juegos, copias del historial, horarios del recordatorio e inicio/catálogo/solución en Compose mediante Robolectric. Se inspeccionaron inicio, retos, registro, catálogo, soluciones y los tres nuevos tableros. Consulta `docs/VALIDACION.md` para los detalles.

No se realizó prueba en un teléfono físico ni medición de fluidez en dispositivo. Los informes y capturas de la versión 1.0 están archivados en `docs/validacion-1.0`.
