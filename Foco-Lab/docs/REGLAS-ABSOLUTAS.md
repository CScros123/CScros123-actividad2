# Reglas absolutas (log real 14–17 nov)

Sin compilar ni ejecutar tests, por indicación del usuario.

## Paso 0 — Auditoría (antes de este cambio)

**Formas de sacar una app de ocio, aprobarla o cambiar su categoría**
1. Mi plan → «Apps de ocio»: desmarcar. Efecto inmediato. Solo lo frenaban la ventana bloqueada o el candado del modo estricto.
2. Categorías → «En observación»: quitar apps (salían sin límite).
3. Categorías → «Productivas aprobadas»: aprobar directamente. Solo filtraba navegadores, redes sociales y las apps que estaban en ocio en ese momento. Bastaba con sacarla de ocio y aprobarla un minuto después.
4. Categorías → «Aprobar» de una propuesta.
5. «Otras apps esenciales»: marcar como esencial una app de ocio la eximía al instante, porque esencial gana sobre ocio.
6. «Desactivar mi plan»: nada de ocio se medía.
7. Restaurar copia JSON: sustituía el plan **y el estado**, banco incluido.
8. Bancos reconocidos por etiqueta (BankAppGuard) y la música de la pausa, también exentos.

**Importación JSON** (`format: 51`). En `config` acepta `enabled`, `watchedApps`, `sensitiveApps`, `intention`, `returnApp`, `observedApps` y `productiveApps`. En `state` acepta `window`, `usedMillis`, `warningSent`, `lockedApps`, `day`, `dailyPackageMillis`, `weekly*`, `bankMs`, `earnedTodayMs`, `productiveCarryMs`, `earnedDay` y `freeDay`. Es decir, una copia editada podía llenar el banco a 60 min.

**Ventana de 5 minutos.** La decide `FocusEngine.step`. La primera apertura de ocio abre una ventana de una hora (`Deadline` guarda el reloj, el tiempo desde el arranque y el número de arranque). El tiempo se suma con `elapsedRealtime`, que no se puede manipular. Lo que sí sale del reloj de pared en zona local es el día, el bloque gratis y el Weekend Lock, y eso se podía cambiar a mano.

**weekend_inicio el sábado 14 a las 21:00.** No se calculó mal. El evento lo escribe `Weekend.active()` la **primera vez que se llama** dentro del Weekend Lock: en cada evento de Accesibilidad, al abrir Foco o en el modo estricto. No hay alarma. Que salga a esa hora indica que fue la primera vez que esa versión se ejecutó con el servicio activo, casi seguro porque la instalaste entonces. Puedes comprobarlo en Ajustes → Apps → Foco (fecha de actualización). El bloqueo en sí era correcto desde ese momento, porque se calcula con la hora.

**com.brute.launcher y Apple Music como uso_productivo.** Solo se registra así lo que está en `productiveApps`, así que las dos estaban aprobadas. El selector no excluía los lanzadores ni las apps de audio, y las propuestas tampoco, así que un lanzador con uso diario cumplía «3 días, 3 min por apertura».

**dns_apagado.** Es la pausa DNS (`DnsGuard`): con la vigilancia activa, el DNS privado no está activo o apunta a otro servidor durante más de 60 s. No distinguía una VPN activa de un DNS privado cambiado o apagado.

**Bug encontrado:** en `Repository.validate`, `if(Categories.totalBlock(c.returnApp))c.returnApp="";` estaba dentro de un comentario y nunca se ejecutaba. Corregido.

## Implementado

Todas las reglas están en `foco-core/.../core/AbsoluteRules.java` como constantes y funciones puras, sin preferencias, base de datos ni JSON.

| Regla | Dónde actúa |
|---|---|
| 1. Redes sociales | `enforce()` en `Repository.validate`, que se aplica al leer, guardar, endurecer e importar. `FocusEngine.watched/essential`: siempre ocio, nunca esencial, y cuentan aunque el plan esté desactivado. Web: `socialDomain()` en la barra de los navegadores compatibles (atrás + Inicio, log `bloqueo_web_red_social`). Selector con 🔒. Al instalar una, se avisa. Si una importación la toca: incidente `import_reglas_absolutas`. |
| 2. Noche 21–07 | `esNocheAbsoluta()`/`night()` en el servicio: bloqueo con su motivo, la app no cuenta ni paga, y log `bloqueo_noche_absoluta` una vez por app y noche. Bloque gratis solo con `freeBlockAvailable()`. `Repository.earn` devuelve 0 de noche. Hora automática apagada = noche, con incidente `hora_automatica_apagada`. |
| 3. Hueco de configuración | Memoria del ocio (30 días) en las preferencias `foco-absolutas`, que la copia no toca. Aprobar como productiva una app con ocio reciente se rechaza en el guardado, la importación y las propuestas. Sacar de ocio: queda pendiente 24 h (`applyPending`, que mide el tiempo real transcurrido) y se rechaza de noche y en el Weekend Lock. Las apps de ocio ya no pueden marcarse como esenciales. Cada cambio de categoría va al log con su origen (`manual`, `automatico;…`, `copia_importada`, `salida_24h_cumplida`, `version_nueva`). |
| 4. Banco | Lab envía el nivel del reto. `Bank` paga 2/4/5 con caída de 100/60/20/0 %. +10 solo si el nivel supera el máximo guardado para ese juego (preferencias `foco-banco-max`, que no se borran cada día). Las misiones no pagan. Topes de 20 y 60. **El banco ya no se restaura desde una copia.** |
| A. Observación | Una app nueva solo se limita si Android la declara juego, vídeo o social. El resto pasa a observación libre (registrada, sin límite) y se endurece con «45 min dos días» o «más de 15 aperturas». Docs, Files, LocalSend y CleanX salen de observación. |
| B. Neutrales | `AppKinds`: lanzadores (CATEGORY_HOME), systemui, teclados y Foco quedan fuera de todo. El audio es neutral. Se limpian de las listas y del registro. |
| Protección | Incidentes `accesibilidad_desactivada` (ya existía), `hora_automatica_apagada`, `cambio_manual_hora`, `import_reglas_absolutas`, `import_quita_ocio` y `dns_apagado;causa=vpn_activa / dns_privado_cambiado / dns_privado_apagado / dns_privado_automatico / otra`. La VPN solo se registra. |
| Interfaz | Tarjeta «Reglas absolutas» de solo lectura en Mi plan. Lista «Saliendo de ocio». Las redes sociales no aparecen en los selectores de observación ni de productivas, y tampoco lo que nunca puede ser productivo ni lo que tuvo ocio en los últimos 30 días. |

Tests: `foco-core/src/test/.../AbsoluteRulesTest.java` cubre las pruebas pedidas salvo la del lanzador. `LeisurePreselectionTest` está adaptado a la Regla 1.

## Pendiente / huecos abiertos

- **Nada compilado ni ejecutado.** Espera errores en la primera compilación.
- **Prueba del lanzador sin test unitario**: depende de PackageManager (Robolectric). Hay que comprobarla en el teléfono.
- **Los tests de Robolectric que guardan el plan dependen de la hora**: de 21:00 a 07:00, quitar una app de ocio lanza una excepción. Los tests actuales no lo hacen, pero los nuevos deben tenerlo en cuenta.
- **«Bloqueo DNS» de dominios**: Foco no controla las listas del proveedor de DNS privado. La Regla 1 web solo se aplica leyendo la barra en los navegadores compatibles (`WebFilter`). Los navegadores no compatibles solo se bloquean si un bloqueo con nombre lo pide, y las apps con webview propio no se cubren.
- **Sin Accesibilidad** (respaldo `GuardService`) no hay noche absoluta, Regla 1 web ni banco.
- **La noche se mide solo con la hora automática.** Con ella encendida, Foco se fía del reloj; cambiar la zona horaria sigue moviendo la noche (no se registra como incidente).
- **Salidas pendientes**: no se pueden cancelar. Si quieres que la app siga en ocio, vuelve a marcarla cuando salga.
- **Retos a medias**: los de la cola anterior a esta versión se pagan como nivel 1. El máximo de nivel por juego empieza sin dato: la primera subida que se ve después de instalar paga +10.
- **Observación libre**: las aperturas se cuentan desde ahora; el historial anterior solo tiene tiempo.
- **Rechazos de la noche sin rastro en el banco**: un reto hecho de noche no paga, y tampoco queda registrado como «rechazado».
- `weeklyOcio` del JSON sigue con los números de Accesibilidad (como antes).
