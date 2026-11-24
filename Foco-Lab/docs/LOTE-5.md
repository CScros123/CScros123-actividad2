# Lote 5 · Duplicar, copia de bloqueos, compras y arreglos

- **Duplicar**: botón «Duplicar» en cada tarjeta de Bloqueos. Crea una copia con id nuevo, nombre «X (copia)» (recortado a 40 caracteres) y **desactivada** (`BlockRule.duplicate`).
- **Copia de los bloqueos** (tarjeta al final de Bloqueos): «Guardar copia» escribe un JSON (`ACTION_CREATE_DOCUMENT`, `format:1`, `kind:"foco-bloqueos"`) solo con los bloqueos: sin tiempos de uso, pausas ni sesión de foco. «Restaurar copia» (`ACTION_OPEN_DOCUMENT`) valida con el mismo lector que `block-rules-v1.json` (`RuleStore.parseRules`): ignora entradas sin id o repetidas, aplica `clamp()`, máximo 50 bloqueos y 1 MB, quita apps esenciales y pide confirmación. Con modo estricto se niega mientras haya un bloqueo activo (también al confirmar). Test JVM: `app/src/test/.../data/RuleBackupTest`.
- **Compras dentro de apps** (mejor esfuerzo): opción «Bloquear compras dentro de apps (Google Play)» en el editor. Se aplica como el filtro web: bloqueo activado, sin pausa y sin horario o dentro de su franja. Si en primer plano está `com.android.vending` y la ventana parece la hoja de pago (botón Comprar/Suscribirse/Buy/Subscribe/1-tap buy + precio con $, €, MXN, USD… + «Google Play» o clase de facturación), Foco pulsa Atrás y muestra «Compras bloqueadas por «Regla»». Lógica pura en `foco-core/PurchaseGuard` con tests (incluida la ficha de una app con «Instalar», que no se toca).
  - Límites: depende de los textos que Play expone a Accesibilidad y de su idioma; no cubre pagos fuera de Google Play (tarjeta dentro de la app, webs), compras ya confirmadas por huella en el mismo instante ni otras tiendas. Para un bloqueo fuerte usa además la autenticación de compras de Google Play.
- **Arreglos**
  - Estadísticas · Día: «Pausas hoy» suma las pausas reales usadas hoy en todos los bloqueos (`RuleStore.pausesToday`); los intentos de abrir apps bloqueadas salen aparte como «Intentos durante bloqueo».
  - Modo estricto: una regla solo de filtro (web o compras) cuenta como bloqueo activo cuando **tiene horario y está dentro de su franja** (`RuleEngine.strictActive`). Las reglas de filtro sin horario no bloquean Ajustes (si no, Ajustes quedaría bloqueado todo el día).

## Probar en el teléfono
1. Duplicar un bloqueo: aparece «… (copia)» desactivado, con las mismas apps, horario y web.
2. Guardar copia, borrar un bloqueo, restaurar: vuelve. Con estricto y un bloqueo activo, Restaurar se niega.
3. Regla con compras activada: en un juego, abrir una compra → la hoja de Google Play se cierra con el aviso. La ficha de Play Store de una app sigue abriéndose.
4. Estadísticas: usar una pausa y comprobar «Pausas hoy: 1».
5. Regla web con horario y estricto: dentro de la franja Ajustes peligrosos se cierran; fuera, no.
