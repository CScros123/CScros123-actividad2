# Arquitectura · Cognitive 1.1.0

- `core`: catálogo de 18 juegos, generadores deterministas por semilla, soluciones, dificultad adaptativa y sugerencias para situaciones reales. No depende de Android.
- `data`: Room y transacciones para resultados, progreso, sesión interrumpida, preferencias y objetivos. Mantiene schema 1 y el identificador de exportación anterior para aceptar copias de Mente.
- `designsystem`: tema claro/oscuro con tipografía sin serif y colores por área.
- `app`: navegación Compose, rutina diaria, panel de progreso, ajustes, resolución de rondas y recordatorios locales.

Los nuevos juegos usan opciones enteras y explicaciones paso a paso. El modo de aprendizaje evita el plazo de respuesta en matemáticas, vida cotidiana y lógica. Memoria, atención y velocidad mantienen su dinámica temporal. La selección errónea se conserva para resaltarla junto a la correcta.

Los retos se generan de manera perezosa una vez por instancia de ronda. El estado del contador pertenece a RoundProgress para que sus actualizaciones no recompongan todo Player. Los totales por fecha se calculan mediante Flow en Dispatchers.Default y Home los observa con lifecycle. Las listas del catálogo y áreas usan claves estables.

El recordatorio usa SharedPreferences y AlarmManager con una alarma inexacta diaria; al dispararse se programa la siguiente fecha local. NotificationChannel y POST_NOTIFICATIONS controlan la entrega. Receptores del sistema reprograman tras reiniciar, actualizar y cambiar hora o zona. No hay sondeo continuo ni servicio permanente. Los ajustes del recordatorio no forman parte de las copias JSON.

Se conservan applicationId, nombre de base de datos y certificado de prueba original para poder construir una actualización. No se afirma una mejora medida de rendimiento: se compiló el APK y se verificó la interfaz con Robolectric; falta medir en dispositivo físico. Consulta VALIDACION.md.
