# Mejoras de interfaz (Galaxy S23 Ultra)

- 120 Hz: MainActivity pide el modo de mayor frecuencia a la resolución actual.
- Tema: modo oscuro sigue al sistema si no hay elección guardada; nuevas opciones "Negro puro" (AMOLED) y "Colores del fondo de pantalla" (Material You).
- Navegación con transiciones de deslizamiento (respetan el ajuste de animaciones).
- Contenido limitado a 640 dp de ancho: DeX, horizontal y pantalla dividida.
- Inicio: tarjeta principal con degradado y brillos animados; tarjetas con borde y degradado.
- Selector de modo segmentado (Con tiempo / Infinito) y chips de dificultad con check.
- Ajustes agrupados (Apariencia / Al jugar) con iconos.
- Partidas: barra de tiempo (roja en los últimos 10 s), contador de puntos, aviso de pausa con botón, resumen final con animación y sonido de completado.
- Parejas: verde al acertar, rojo y sacudida al fallar; recolocación con resorte.
- Pulso de neón: la luz palpita con brillo.
- Ruta espacial: fondo estrellado, meteoro con degradado, destello rojo al chocar y control por deslizamiento.

Textos usados por los tests de Robolectric conservados. Pendiente: compilar y ejecutar `./gradlew testDebugUnitTest assembleRelease` en un equipo con Android SDK.
