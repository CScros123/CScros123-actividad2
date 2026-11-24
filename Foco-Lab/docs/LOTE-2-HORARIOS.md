# Lote 2 · Horarios, sesiones y lista blanca

## Qué añade
- **Horario por bloqueo** (`Schedule`): días de la semana + franja desde/hasta en hora local. Si «Hasta» < «Desde» cruza la
  medianoche (22:00–07:00; cuenta el día en que empieza). Desde == hasta = día entero. Tres modos (`scheduleMode`):
  - *Sin horario* (`ALWAYS_LIMIT`): igual que el Lote 1.
  - *Bloqueo total en la franja* (`BLOCK_IN_WINDOW`): dentro, las apps del bloqueo se bloquean ("Termina en X").
  - *El límite solo cuenta en la franja* (`LIMIT_IN_WINDOW`): fuera ni se mide ni se bloquea.
  Las pausas siguen valiendo por bloqueo. El servicio programa su propia reevaluación en el próximo borde de franja
  (sin alarmas exactas ni permisos nuevos); al encender la pantalla también se recalcula.
- **Lista blanca** (`allowlist`): bloquea todas las apps con icono de lanzador salvo las elegidas, las esenciales,
  Inicio, teléfono, Ajustes/instalador y Foco. Pensada para combinar con horario (por ejemplo, noche).
- **Sesión de foco** (`FocusSession`, en Bloqueos arriba): *Bloqueo rápido* 15/30/60/120 min o *Pomodoro* (25/5 ×4 por
  defecto, editable). En enfoque bloquea las apps de todos los bloqueos (salvo listas blancas) y las del Foco clásico,
  **sin pausas**; los descansos desbloquean. Pausa: "Bloqueo rápido · termina en X" / "Pomodoro 2/4 · enfoque · termina en X".
- **Modo estricto**: sesión en enfoque y lista blanca/horario activo cuentan como bloqueo (Ajustes peligrosos se cierran);
  no se puede editar ningún bloqueo durante una sesión en enfoque ni terminar la sesión con estricto activado.
- **Persistencia**: mismos `block-rules-v1.json`; campos nuevos opcionales (`scheduleMode`, `schedules`, `allowlist`, `session`).
  Un archivo del Lote 1 se lee igual. Tests: `ScheduleTest`, `RuleScheduleTest`, `FocusSessionTest`.

## Cómo probar en el teléfono
1. Bloqueos › Nuevo bloqueo "Noche", una app de ocio, Horario: bloqueo total en la franja, desde ahora+2 min hasta ahora+10 min.
   Deja la app abierta: a la hora de inicio aparece la pausa "«Noche» · horario de bloqueo" y "Termina en 8 min".
2. Con la franja activa, pulsa la pausa de 5 min: la app vuelve y a los 5 min se bloquea otra vez. Al terminar la franja, se libera sola.
3. Prueba una franja 23:00–07:00 de lun–vie y comprueba en la tarjeta "Fuera de horario" / "En horario · termina en …".
4. Activa "Lista blanca" en otro bloqueo con solo una app (por ejemplo Kindle) y franja ahora: cualquier otra app de lanzador se
   bloquea; teléfono, Ajustes, Inicio y Foco siguen abriéndose.
5. Bloqueo rápido 15 min: abre una app de un bloqueo o del Foco clásico → "Bloqueo rápido · termina en 15 min", sin botón de pausa.
6. Pomodoro 1/1/2: enfoque bloquea, al minuto el descanso desbloquea, luego "Pomodoro 2/2 · enfoque".
7. Con modo estricto: "Terminar" y "Editar" se rechazan durante la sesión; Ajustes › Apps › Foco se cierra.
