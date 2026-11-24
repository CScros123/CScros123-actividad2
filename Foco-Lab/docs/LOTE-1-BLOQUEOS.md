# Lote 1 · Bloqueos con nombre

## Qué añade
- **Varios bloqueos con nombre** (Hoy › Bloqueos › Nuevo bloqueo): apps propias, minutos permitidos (0 = bloqueo total),
  reinicio cada 60 min o cada día, pausas al día (0–5) de 5/10/15 min, interruptor de activado y opción
  "Añadir apps nuevas automáticamente".
- **Foco clásico** (5 min cada 60 min) sigue igual en `FocusEngine` y `focus-lite-v1.json`; aparece como tarjeta fija no eliminable.
- **Motor puro** `foco-core`: `BlockRule`, `RuleState`, `RuleEngine` (decide, pause, roll, addInstalled). Tests: `RuleEngineTest`.
- **Persistencia**: `files/block-rules-v1.json` (`RuleStore`, escritura atómica). Si el archivo no se puede leer, se aparta como `block-rules-v1.corrupt.json`.
- **Servicio**: tras la decisión del Foco clásico (y solo si no bloquea él), se consulta `RuleEngine` para la app en primer plano.
  No cuentan esenciales, llamadas, pantalla apagada/bloqueada ni la pausa DNS. Si una app está en varios bloqueos, basta con uno.
- **Pausa**: la misma pantalla de pausa muestra el nombre del bloqueo, "Se reinicia en X" y "Pausar N min (quedan K hoy)".
- **Modo estricto**: un bloqueo con nombre activo cuenta como bloqueo (Ajustes peligrosos se cierran) y su edición se rechaza.
- **Registro**: evento `RULE_LOCK` ("Bloqueo con nombre"), una vez por periodo y bloqueo.
- **Apps nuevas**: receptor de `PACKAGE_ADDED` registrado dentro del servicio de Accesibilidad (Android 8+ no lo entrega al manifiesto).
  Ignora actualizaciones, apps sin icono de lanzador, esenciales, bancos y mensajería.

## Cómo probar en el teléfono
1. Activa Accesibilidad de Foco. Hoy › Bloqueos › Nuevo bloqueo: "Prueba", una app de ocio, 1 min, cada 60 min, 1 pausa de 5 min.
2. Abre la app 1 minuto: aparece la pausa con "Límite de «Prueba» alcanzado" y "Se reinicia en 59 min".
3. Pulsa "Pausar 5 min (quedan 1 hoy)": la app vuelve; a los 5 min se bloquea otra vez y ya no hay pausas.
4. Con modo estricto activado, intenta editar "Prueba": se rechaza. Abre Ajustes › Apps › Foco: se cierra.
5. Activa "Añadir apps nuevas automáticamente" e instala una app cualquiera: aparece un aviso y la app queda en el bloqueo.
6. Registro de bloqueos: aparece "Bloqueo con nombre".
