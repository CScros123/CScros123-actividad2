# Cognitive 1.5 · Color, sonido y movimiento

El diseño toma como referencia la organización en tarjetas redondeadas y el contraste de los paneles aportados por el usuario. Las ilustraciones siguen siendo vectores originales; no se necesitan imágenes ni conexión al jugar.

## Cambios

- Temas claro y oscuro con fondos gris azulado/grafito y tarjetas de alto contraste. Azul, verde, violeta, naranja y rosa distinguen las áreas y los juegos.
- Inicio con una tarjeta de rutina en grafito, un pequeño mosaico de juegos, fecha y botón verde. Estadísticas en tarjetas de color y navegación redondeada.
- Marca C con signo igual, iconos de interfaz Material Rounded y nuevo icono adaptativo con variante monocroma para Android 13+.
- Pulsación con escala y efecto de tinta en tarjetas, respuestas y botones principales. Transiciones entre pantallas, aparición de soluciones, colores de respuesta y medalla de final de sesión.
- Cuatro sonidos breves originales en WAV: toque, acierto, volver a intentar y sesión completa. Se cargan con SoundPool y funcionan sin conexión.
- Respuesta táctil nativa para selección, acierto, error y final. Android decide la intensidad y disponibilidad según el dispositivo y su ajuste de respuesta táctil. No se solicita permiso de vibración ni se fuerza el ajuste del sistema.
- Ajustes → Sonido y movimiento permite desactivar sonidos, vibraciones y animaciones de forma independiente y probar la respuesta. Los ajustes se guardan y viajan en las copias JSON. Las copias antiguas siguen siendo compatibles.

Los tres ajustes empiezan activados si todavía no se han elegido. El sonido usa el volumen multimedia, respeta el modo silencio/vibración y No molestar. Los efectos se detienen al pasar a segundo plano y los resultados antiguos no vuelven a producirlos al restaurar una pantalla. Las animaciones respetan también la desactivación global de animaciones de Android.

Las secuencias de memoria y la espera del juego de reacción mantienen su temporización. Los efectos de resultado ocurren después de registrar la respuesta; no se añaden pistas sonoras durante la observación. Desactivar animaciones elimina el movimiento decorativo, pero conserva las secuencias necesarias para jugar.

## APK de prueba y actualización

El APK debug se llama **Cognitive · Prueba** y usa `personal.qqq.braintraining.preview`. Se instala junto a la aplicación anterior, con almacenamiento independiente. Para probar con tu historial puedes exportar una copia en la aplicación anterior y restaurarla en la de prueba. Conserva tu copia original.

El código de producción conserva `personal.qqq.braintraining`, Room versión 1 y los identificadores de los juegos. Para actualizar la aplicación anterior directamente se debe compilar release y firmar con su misma clave. Esta entrega no incluye ni necesita la clave original para generar el APK de prueba.

## Verificación

Consulta `VALIDACION-1.5.md` para el resultado real de compilación, pruebas y capturas de esta entrega. Las capturas e informes anteriores corresponden a versiones anteriores.
