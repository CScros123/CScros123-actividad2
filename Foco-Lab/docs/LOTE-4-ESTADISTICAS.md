# Lote 4 · Estadísticas

Pantalla `StatsActivity` con pestañas **Día / Semana / Tendencia**. Se abre desde Hoy (tarjeta «Tus estadísticas») y desde Registro.

- **Datos**: historial de uso de Android (`UsageStatsManager.queryEvents`, ACTIVITY_RESUMED/PAUSED, más pantalla apagada/bloqueo como cierre). Se consulta solo al abrir la pantalla, en el hilo del registro (`BlockLog.query`). Sin Acceso de uso se muestra una explicación y el botón para concederlo (`StabilityPermissions.openUsageAccess`). No hay permisos nuevos.
- **Caché**: `foco-log.db` versión 2 añade `stats_day(day,pkg,ms)` y `stats_done(day,updated)`. Un día cuya actualización es posterior a su medianoche final ya no se vuelve a pedir a Android; hoy se recalcula siempre. Se poda y se borra con el resto del registro.
- **Día**: tiempo total de hoy, apps más usadas con icono, tiempo, barra y etiqueta «Distractivo» (apps de ocio del Foco clásico o de cualquier bloqueo que no sea lista blanca), bloqueos y pausas de hoy según `block_event`.
- **Semana**: gráfico de 7 barras dibujado con Canvas (`WeekBars`, hoy resaltado), media diaria y apps más usadas de la semana.
- **Tendencia**: últimos 7 días frente a los 7 anteriores; hasta 3 subidas (↑ en rojo) y 3 bajadas (↓ en verde), solo cambios de 5 min o más.
- **Lógica pura** en `foco-core` (`ScreenTime`, con tests en `ScreenTimeTest`): se unen los intervalos por app y actividad, si falta una pausa el intervalo se cierra en el siguiente cambio de app, los resumes solapados no cuentan dos veces, se reparte el tiempo en la medianoche local, más sumas semanales, totales, top y tendencia con umbral.
- Foco, los launchers (intent HOME), la interfaz del sistema y los teclados no salen en las listas.
- Límite: Android guarda pocos días de eventos detallados; la Tendencia se completa a medida que Foco guarda días en caché.
