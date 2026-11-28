# Reconstrucción de Cognitive Lab Arcade

El código de esta edición se basa en el proyecto Cognitive 1.5 disponible en la sesión. El APK de Cognitive Lab 1.8.0 sirvió como referencia de identificación y características, pero no se disponía de su proyecto fuente. Esta edición no pretende reproducirlo byte por byte ni incorpora datos de su instalación.

Las parejas se generan con resultados únicos. Tras completar un par, las cartas se eliminan y se calcula una nueva disposición que cambia la fila de cada carta restante, incluida la última. Al completar el tablero, aparece otro. Se puede escoger Suave, Media o Intensa y jugar durante 60 segundos o sin límite.

Dos juegos originales de reflejos completan la sección Arcade. El modo infinito también está disponible en el catálogo anterior. No se guardan recompensas ni progresión por niveles. Las preferencias de apariencia y efectos se guardan localmente; los efectos están sujetos al teléfono y sus ajustes.

Las capturas `capturas-arcade/` se obtuvieron con pruebas Compose en Robolectric y no sustituyen una prueba en teléfono físico. No se ha medido la latencia de sonido, vibración o animaciones en dispositivo.
