# Foco: de control a cuidado

Por qué: un sistema que se siente como castigo se abandona, y al abandonarlo el ocio rebota
(5 h 18 min → 10 h 25 min a la semana, 15–21/11). Foco pasa de vigilar a acompañar.

## Modos (`core/care/CareMode`, guardado en `runtime/CareStore`)

| | Cuidado | Normal | Enfoque |
|---|---|---|---|
| ¿Cuenta el ocio? | No | Sí, 5 min por hora | Sí, 5 min por hora |
| Al pasar de 5 min | — | Recordatorio con propuesta (máx. 3/h, uno cada 5 min) | Se cierra (como antes) |
| Banco y Regla 4 | No se gasta | No se gasta | Sí |
| Bloqueos con nombre, webs, secciones, tope diario, franja estricta, pausa por DNS, Aduana que cierra, protección de Ajustes | No | No | Sí |
| Noche, Telegram, web de redes (Regla 1) | Sí | Sí | Sí |
| Avisos | Sugerencia suave cada 2 h como mucho (tras 30 min de ocio) | Recordatorios | Los de siempre |
| Números en «Hoy» | Ocultos | Visibles (salvo «Ocultar números») | Visibles |

Cambios: a Enfoque (1 h, 2 h o hasta la noche) y a Cuidado, siempre e inmediato. A Normal,
inmediato salvo durante un Enfoque. Al vencer el Enfoque se vuelve al modo anterior. Todo cambio
queda en el log: `modo_cambiado` con `de=…;a=…;motivo=…`.

SharedPreferences en vez de DataStore: la Accesibilidad lee el modo en cada evaluación de forma
síncrona y el resto de Foco ya guarda así; DataStore sería una dependencia nueva sin ventaja.

Protección de Ajustes: solo en Enfoque o con el candado del modo estricto (los dos los elige el
usuario). En el resto solo se registra. El modo estricto (administrador de dispositivo) no cambia.

## Noche ajustable (`core/care/NightHours`)

Empieza entre las 19 y las 23 y acaba entre las 5 y las 9. Si el horario nuevo cubre al menos las
mismas horas, se aplica ya; si suelta alguna, desde mañana. `AbsoluteRules.useNight()` deja el
horario vigente para todo Foco.

## Ánimo (`core/care/Mood`)

«¿Cómo estás?» del 1 al 5 y una palabra opcional: la primera vez que se abre Foco en el día
(inicio y Foco completo) y al elegir Cuidado. Con 1 o 2 se ofrece Cuidado con un botón; nunca se
cambia solo. Evento `animo` solo en el teléfono: el CSV y el resumen exportados no lo incluyen salvo
«Incluir mi ánimo al exportar».

## Lo bueno primero

- «Hoy» (inicio) y el informe diario: invertido, retos y apps que sumaron; el ocio al final. El
  banco solo se muestra en Enfoque.
- Resumen semanal: «Lo que salió bien» → «Para conocerte» → «Al final, el ocio». Sin
  comparaciones que hagan sentir mal: frente a la semana anterior solo se dice lo que mejoró.
- «Ocultar números»: solo frases («Hoy leíste y practicaste idiomas»).
- Sin contadores de segundos: `Design.duration` redondea a minutos y la Aduana ya no cuenta 5-4-3.
- Sin rojo: el aumento de ocio en Estadísticas usa un tono neutro.

## Auditoría del resumen 15–21/11

- «Invertido: 0 min»: solo contaban las apps aprobadas como productivas. Duolingo, Biblia o
  Claude salían como `sin_clasificar`. Ahora `GoodApps` las cuenta en los informes (solo medir: no
  aprueban nada ni dan minutos, y una app en ocio sigue siendo ocio).
- «Retos: 0»: `drainLab` escribía cada reto como una tarea suelta en la cola del log (256 huecos,
  descarta en silencio si está llena) y borraba la cola de Lab sin esperar a que se guardara. Un
  reto podía abonar el banco y no quedar nunca en el registro. Además, solo se leía la cola al abrir
  Foco completo. Ahora se escribe todo en una tarea, la cola se vacía al terminar y también se lee
  desde el inicio y antes del resumen semanal.

## Mensajes (`Companion`)

Categoría nueva CUIDADO (10 mensajes) y disparadores LEISURE_OVER (Normal) y CARE_SUGGESTION
(Cuidado). Rotación sin repetir en 3 días. Nada en llamadas, No molestar ni de noche. En Cuidado no
hay recordatorios ni propuestas.

## Pruebas (escritas, sin ejecutar)

`foco-core/.../care/CareModeTest`, `app/.../log/CareReportTest`, `app/.../ui/CareTextTest`.

## Versión 7: el búho como voz de Foco, temas fotográficos y fluidez

**Búho.** Notificaciones con su icono y su cara («🦉 Búho · …»). Su último mensaje (30 min) aparece
como banner arriba del inicio («Ahora no» lo cierra). En la pausa de Enfoque, el búho en lugar de un
aviso frío. Con mucho ocio: preocupado y tierno (cejas arriba, ojos grandes, cabeza ladeada, alas
abiertas despacio, tonos cálidos), con frases que preguntan y proponen.

**Temas fotográficos** (`core/home/PhotoTheme`): Café del Alba, Horizonte del Alba, Cascada Serena,
Dunas de Enfoque, Ruta de Lectura, Luna de Ocaso, Cumbre Nocturna y Fricción Ígnea. Con foto:
café, cascada, dunas, ruta y lava (`res/drawable-nodpi/theme_*.jpg`, reducidas). Sin foto aún:
horizonte, luna y cumbre, que se dibujan con su paleta. Automático por hora y por lo que haces
(racha productiva → Ruta; Enfoque o banco ≥ 15 min a mediodía → Dunas), nunca por el ocio: la lava
y la «alerta roja» del prototipo no se aplican solas (Fricción Ígnea solo si la eliges). Opcional:
la misma foto en la pantalla de bloqueo (permiso SET_WALLPAPER, solo al cambiar de tema) e iconos
del dock suavizados.

**Fluidez.**
- `HomeColors` ahora es inmutable con equals: antes, todo lo que leía la paleta se recomponía siempre.
- Los 9 colores ya no se animan 1,5 s: antes eso recomponía el inicio en cada fotograma.
- Los paneles vecinos se componen antes de deslizar y cada uno se dibuja en su propia capa.
- La foto se decodifica una vez, a tamaño de pantalla y en RGB_565.
- El centro es más limpio: el reto rápido queda en «Hacer», y la burbuja no se repite si hay banner.

## Versión 8: lo que enseñó el registro del 21/11

Datos: a las 06:12 Duolingo 10 s y después 1 h 45 min de vídeo corto (Facebook y TikTok); Foco
apagado hasta las 10:07; Instagram 11 veces en 90 s; el inicio de Samsung de vuelta.
Reglas puras en `core/home/OwlCare` y la parte Android en `runtime/OwlWatch`.

1. **Despertar con el búho.** El primer desbloqueo de la mañana (5:00–12:00, una vez al día) abre
   el ritual en el inicio: luz, agua y algo tuyo (Duolingo, Biblia, lectura). «Empezar el día» lo
   cierra. Se puede apagar (Ajustes › Cuidado). Si abres ocio antes del fin de la noche, la pausa
   saluda: «Buenos días. Aún es pronto…».
2. **Inquietud.** 6 aperturas de ocio en 2 minutos (cuentan también los reintentos) → «¿Todo bien?»
   con «Respirar 30 s»: un círculo que respira con el búho, sin contador de segundos. Como mucho un
   aviso cada 30 min; nada de noche, en llamadas ni en No molestar. Evento `inquietud` en el log.
3. **Foco apagado.**
   - El inicio lo dice con una tarjeta del búho y un botón directo a Accesibilidad.
   - Una alarma inexacta cada ~2 h (y al arrancar el teléfono) avisa con una notificación del búho,
     como mucho cada 6 h y nunca de noche.
   - Al volver, si estuvo apagado 10 min o más: «Estuve apagado de 5:00 a 10:07…». El tiempo con el
     teléfono apagado no cuenta. Queda en el log como incidente `foco_apagado_min`.
4. **Inicio perdido.** Si Foco Home lo fue alguna vez y deja de serlo, una notificación al día:
   tocarla vuelve a pedir el inicio predeterminado.

Pruebas: `OwlCareTest`.

## Versión 9: pulido visual, ergonomía y micro-interacciones

- **Inicio**
  - Pie del búho con métricas compactas («3 hábitos hoy · 5 min/h disponibles», `CareMode.owlLine`); la frase de lo bueno de hoy solo en «Hoy».
  - Chevron ⌄/⌃ con muelle en «Protección activa» y «Esta semana».
  - Gráfica semanal con escala √ y días tocables (detalle real del día; tocar otra vez vuelve al resumen).
  - Resplandor del 7 % y luna creciente tenue detrás del búho en temas oscuros.
  - La guía del dock solo aparece si faltan apps o de noche.
- **Pausa consciente**
  - Velo negro al 84 % y hoja flotante de 28 dp sobre la barra de navegación.
  - Arriba solo el dato objetivo («Modo Normal · Límite: 5 min/hora») y una frase bajo los botones.
  - Los botones se habilitan tras una respiración completa (`Aduana.UNLOCK_MS` = 2,5 s), con un toque háptico.
  - «1 min» destacado como recomendado.
- **Ajustes**
  - Contenido recortado bajo la barra de estado.
  - Permisos plegados en «7/7 activos ✓» (se despliegan solos si falta alguno).
  - «›» en las filas que abren un selector.
  - El astro baja al 20 % al deslizar hacia Ajustes o Hacer.
- **Hacer**
  - Recorte bajo la barra de estado.
  - La Regla 4 está siempre plegada hasta tocar (i).
  - Estado vacío con una sola guía y atajos «+ Leer 5 min», «+ Repasar Anki» y «+ Estirar 2 min».
- **Pie de navegación:** enlaces según la pestaña («Inicio ›» en Ajustes, «‹ Inicio» en Hacer), con fundido y toque háptico.
- **Propias**
  - Los botones se hunden un 3 % con muelle al pulsar (`Modifier.pressScale`).
  - Etiquetas de sección con espaciado de letra.

## Versión 10: modos con tiempo, cronómetro y enfriamiento

- **Cuidado con tiempo** (`CareMode.toCare`)
  - Al elegirlo se escoge 15 min, 1 h o 3 h, y al acabar vuelve solo al modo de antes (`fin_cuidado`).
  - Si se entró desde un Enfoque que aún dura, vuelve al Enfoque; mientras tanto no se puede saltar a Normal.
  - Tope de 2 h de Cuidado al día, salvo que el ánimo de hoy sea 1 o 2. Si queda menos que lo pedido, se concede lo que queda.
  - La píldora del modo muestra «Cuidado · quedan X».
- **Cronómetro** (`LeisureTimer`, `ui/LeisurePill`)
  - Píldora arrastrable sobre la app vigilada: «🦉 4 min», «🦉 último minuto» (ámbar) y, pasado el límite en Normal, «🦉 otros 5 en 30 min».
  - Solo minutos. No aparece en Cuidado, de noche ni con una pausa delante.
- **Enfriamiento en Normal** (`FocusEngine.Input.cooldownMs`)
  - Al gastar los 5 min, la espera es de 30 min en lugar de lo que quede de la hora. Solo acorta.
  - Como afloja, empieza el día siguiente a instalar la versión.
  - Enfoque sigue con 5 min por hora y banco.

Pruebas: `CareModeTest` (Cuidado con tiempo, vuelta a Enfoque, tope diario), `FocusEngineTest` (enfriamiento), `LeisureTimerTest`.

## Versión 11: ideas del prototipo HTML

- **Pie del inicio:** solo los tres puntos, y cada uno lleva a su panel (32 dp de zona táctil). Las apps se abren con el buscador o deslizando hacia arriba.
- **Cajón**
  - Una manija arriba en lugar de «Cerrar»: se toca o se arrastra hacia abajo.
  - Mantener pulsada una app abre Información de la app y Desinstalar, con confirmación propia y luego la de Android (permiso REQUEST_DELETE_PACKAGES). Foco no se puede desinstalar desde aquí.
- **Búho**
  - Calma, Lectura (LIBRO) y Dormido (NOCHE) ya existían.
  - Nuevo, Música: cascos y balanceo cuando suena algo (`AudioManager.isMusicActive`, sin permisos, consulta cada 4 s).
  - Nuevo, De espaldas (~165°): en la v12 pasa a depender de volver de ocio pasado del límite (ver Versión 12). Sin ceño; al tocarlo se gira 5 s.
- **Comillas tipográficas:** fuera de los textos del inicio, el cajón y la frase bíblica del búho.
- **No portado**
  - El búho en WebGL: el de Canvas ya tiene volumen y giro, y una librería 3D sería una dependencia nueva.
  - El filtro de gestos sobre botones: en Compose, el pager ya cancela el toque si el dedo se arrastra, y el búho consume su propio arrastre.

## Versión 12: dock nocturno y posturas del búho

- **Dock de noche:** las esenciales van centradas con tamaño fijo (columna de 64 dp e icono en una caja de 54 dp, blanco al 6 % con borde al 8 %) y sin texto de guía.
- **Posturas** (`core/home/OwlState`, por prioridad):
  1. Noche (horario configurado) → SLEEPING: párpados cerrados, z Z z y respiración a 15 fps. Sin azar, sin música, sin lectura y sin giro.
     - Al desbloquear, tocar al búho o abrir ocio llega un aviso de buenas noches. Como mucho uno cada 45 min, con 3 textos que rotan; con No molestar no se envía.
  2. Música en vivo → LISTENING_MUSIC: cascos y notas. Usa `AudioManager.AudioPlaybackCallback`, que avisa al instante al empezar, pausar o parar.
  3. App reciente, con memoria de 4 min. La Accesibilidad guarda la categoría al salir de la app (`OwlWatch.foreground`).
     - Lectura → READING: anteojos carey/dorado con destello y libro abierto.
     - Ocio pasado del límite → ANGRY_BACK: de espaldas.
  4. Azar de día: CALM (40 %), STRETCH, CURIOUS o READING_IDLE, al encender la pantalla y cada 6 min.
- **Por qué no `MediaSessionManager`:** `getActiveSessions` exige un NotificationListenerService con acceso a las notificaciones (un permiso delicado y un servicio nuevo). La otra vía, `MEDIA_CONTENT_CONTROL`, es solo para apps del sistema. El callback de audio da lo mismo, sin permisos y sin retraso.

Pruebas: `OwlStateTest`.

## Versión 13: auditoría, puntos de fuga y ajustes mínimos

**Puntos de fuga cerrados**
1. **PiP y pantalla dividida.** Antes solo contaba la ventana con el foco: YouTube o Facebook en imagen en imagen, o en la otra mitad de la pantalla, sumaban 0 min. Ahora, si la app enfocada no está vigilada y otra ventana de app visible sí, se mide esa (`ForegroundWindows.current(…, watched)`, en el evento y en la evaluación). Los filtros de webs y secciones siguen leyendo solo la ventana enfocada.
2. **Ánimo repetido.** Volver a contestar 1 o 2 en «¿Cómo estás hoy?» abría Cuidado sin tope. Ahora la exención la decide la primera respuesta del día (`CareStore.moodLowForCap`). La regla elegida no cambia.
3. **Menú del cajón sobre Foco.** La pantalla de información de Foco permite Forzar detención y Borrar datos (reiniciar el banco). Las apps de Foco ya no tienen menú.

**Revisado y sin cambios (por diseño o ya cubierto)**
- Accesibilidad apagada: aviso del búho y hueco en el registro.
- Inicio perdido: aviso diario.
- Hora manual: noche más incidente.
- Ajustes protegidos: en Enfoque o con el candado.
- Normal no bloquea al pasar los 5 min: solo recuerda, con enfriamiento de 30 min. Es la decisión de cuidado. Si se quiere cerrar, la opción es Enfoque.
- Cuidado sin Aduana: hasta 2 h al día.

**Ajustes mínimos**
- **Inicio › Ajustes:** solo Modo y Noche a la vista.
  - Los permisos solo aparecen si falta alguno.
  - Todo lo demás (tema, búho, cuidado, gestos, energía y redes) va dentro de «Personalizar inicio», un solo botón que se despliega.
  - Las filas sin descripción repetida.
- **Foco completo › Ajustes:** los textos largos (privacidad, apps esenciales, protección del plan y créditos) van a una sola hoja, «Cómo funciona y privacidad». La tarjeta «Protección del plan», que solo tenía texto, desaparece.

## Versión 14: NextDNS automático

- **Clave:** Foco completo › Ajustes › Filtro DNS › «Ver estadísticas automáticas».
  - Se pega una vez la clave de API (my.nextdns.io › Cuenta › API).
  - Se guarda en las preferencias privadas de Foco. No se exporta ni se registra.
  - Solo aparece si el DNS vigilado es un perfil de NextDNS.
- **Lectura** (`runtime/NextDnsSync`):
  - `GET api.nextdns.io/profiles/{id}/analytics/status` y `…/analytics/domains?status=blocked`, desde el inicio del día local, con la cabecera `X-Api-Key`.
  - Como mucho una lectura cada 30 min, al volver al inicio. Sin librerías: `HttpURLConnection`.
  - Si la clave no vale o el perfil no existe, el error se muestra en la tarjeta del DNS.
- **Inicio:** si hubo bloqueos hoy, el búho lo cuenta en una tarjeta (dominio principal y tres más). Con «Ocultar números», solo los nombres.
- **Permiso nuevo: INTERNET.** Foco era 100 % local. Ahora solo sale a api.nextdns.io, y solo con clave guardada. Sin clave, no hay ninguna conexión.

Pruebas: `NextDnsStatsTest`.

## Versión 15: cambiar de DNS con el modo estricto

- **Con el candado puesto,** cambiar de proveedor (adoptar el NextDNS actual, escribir un perfil o pasar a AdGuard o CleanBrowsing) ya no se prohíbe: se aplaza 24 h, como todo lo que afloja (`DnsMonitor.requestProvider`).
  - Hasta entonces Foco sigue vigilando el proveedor anterior.
  - Al cumplirse, cambia solo y lo anota en el log (`dns_proveedor_cambiado`).
- **Antes, «Cambiar a AdGuard o CleanBrowsing» no miraba el candado.** Ahora también espera 24 h.
- **La tarjeta del DNS** muestra el cambio pedido, con un botón para anularlo.
- **Estadísticas de NextDNS:** con un cambio a NextDNS pedido, la clave de API ya se puede guardar y leer.

## Versión 16: aviso del DNS mientras se espera el cambio

Si el teléfono ya usa el proveedor pedido con el candado, el inicio deja de mostrar el aviso amarillo «Filtro DNS privado inactivo» y pone una nota: «DNS en cambio: Foco adopta tu nuevo proveedor el domingo 22:38». La píldora dice «DNS: en cambio». No afloja nada: hasta esa hora, Foco sigue tratando el DNS como no conforme para pausar ocio y navegadores.

## Versión 17: dock de noche sin quedarse sin salida

- **De noche**, si hay un espacio libre, aparece un «+» compacto. Al tocarlo se ofrecen solo las apps esenciales que pueden ir en el dock.
- **Quitar una app manteniéndola pulsada** ya no es definitivo: durante 6 s aparece «X quitada · Deshacer» bajo el dock.

## Versión 18: rutinas obligatorias de mañana y de noche

- **Tareas por defecto** (`core/care/Routine`), editables en Inicio › Ajustes › Personalizar › Rutinas, con una tarea por línea y sus minutos:
  - Mañana (5:00 a 12:00): bañarte 10, ordenar el cuarto 5, revisar que llevas todo para el trabajo 3, moverte o hacer ejercicio 10.
  - Noche (desde 2 h antes de la noche): lonche de mañana 10, leer un poco 5, ordenar el cuarto 5, bañarte 10, arreglar la barba 5.
- **Mientras falte algo:**
  - Las apps de ocio muestran la pausa «Primero tu rutina…: faltan N (~M min)», en cualquier modo, Cuidado incluido.
  - Esenciales, llamadas y WhatsApp siguen libres.
  - Hay una notificación fija con lo que falta. Si se aparta, vuelve en la siguiente comprobación.
- **Contra los atajos:**
  - Marcar todo de golpe no abre el ocio. Se abre cuando pasa la mitad del tiempo estimado (máximo 15 min) desde que empezó la rutina.
  - Mientras una rutina está en marcha, no se puede editar.
  - Desactivar las rutinas vale desde mañana; activarlas, al momento.
- **Inicio:** la tarjeta del búho con lo que falta y el tiempo estimado abre la lista para marcar. El primer desbloqueo de la mañana abre la lista en lugar del ritual de luz y agua.
- **Registro:** evento `rutina` al completar cada una.

Pruebas: `RoutineTest`.

## Versión 19: planificar la semana

- **Qué lista vale cada día:** la de esa fecha; si no hay, la de ese día de la semana; si no, la de siempre (`Routine.resolve`).
- **Planificar la semana** (Ajustes › Personalizar › Rutinas, o la tarjeta del domingo):
  - Cada día muestra su mañana y su noche con los minutos estimados. ✎ marca los días con lista propia.
  - Al tocar un día se edita su lista. «Usar la de siempre» quita la lista propia.
  - «Un día concreto…» planifica una fecha de los próximos 7 días, por ejemplo una cita médica.
- **Domingo desde las 12:00:** tarjeta del búho «Planifica tu semana», una vez.
- **Contra los atajos:** no se puede cambiar nada que afecte a la rutina de hoy mientras está en marcha con algo pendiente.

Pruebas: `RoutineTest` (orden de prioridad de las listas y la tarjeta del domingo).

## Versión 20: gasto del día

- **Antes de entrar al banco** (bancos reconocidos, abiertos desde Foco Home):
  - Una hoja pregunta el monto y para qué. «Anotar $X y abrir» guarda y abre el banco; «Solo abrir» abre sin anotar.
  - Si el banco se abre desde otro sitio, no se pregunta.
- **Presupuesto semanal y lo que debes:** se ponen en «Planificar la semana» o tocando la tarjeta 💵.
- **Cálculo** (`core/care/Money`): lo que queda de la semana se reparte entre hoy y los días que faltan.
  - Si hoy te pasas, «se reparte solo en los días que quedan, sin drama». Nunca regaña.
  - Con deuda, la idea es apartar cerca del 10 % de lo diario, mínimo $1: «Si apartas $2 al día, saldas $60 en 30 días».
- **Dónde se ve:** la tarjeta 💵 del inicio y la rutina de la mañana, al despertar.
- **Privacidad:** los montos solo se guardan en el teléfono (`foco-dinero`, 8 semanas). No van al registro ni a lo que se exporta.

Pruebas: `MoneyTest`.

## Versión 21: pausa del DNS con el cambio pedido

- **Con un cambio de proveedor pedido,** la pausa ya no dice «Tu filtro DNS está apagado». Dice «Cambio de DNS en camino»: qué pediste, cuándo lo adopta Foco y qué DNS poner para seguir ya.
- **Pedir otra vez el mismo proveedor** ya no reinicia las 24 h. Antes, cada toque en «Usar mi NextDNS actual» volvía a empezar la espera.

## Versión 22: Foco Core (2+2, dinero, evidencias y reportes)

Fusiona las rutinas (v18 y v19) y el gasto (v20) en un solo sistema.
- **Lógica pura:** `core/care/FocoCore` y `Routine`.
- **Datos:** `runtime/RoutineStore` y `MoneyStore`.
- **Pantalla:** `ui/FocoCoreScreen`. Se abre sobre el inicio con el tema fotográfico de fondo, igual que Cognitive Lab vive aparte y se abre desde Hacer.

**Formato de tareas:** `nombre · minutos · hora límite · dificultad`, por ejemplo `Estudiar · 60 · 10:30 · difícil`. Las líneas de siempre siguen valiendo. El día empieza a las 4:00.

**Widget de Inicio** (sustituye las tarjetas de rutina y de dinero). Tiene 4 estados:
1. **Sin plan** (menos de 2 tareas hoy): «Anota tus pendientes de hoy».
2. **En marcha:** siguiente tarea con su hora límite, hechas/total, hora a la que terminas (ahora + minutos pendientes) y dinero de hoy, en terracota si te pasas. El dinero nunca bloquea nada.
3. **Penalización:**
   - Al llegar la hora límite, aviso.
   - Con 10 min de margen sin hacerla, queda incumplida y el ocio se pausa 1 h, con la cuenta atrás en el widget y en la pausa.
   - No se acumula (máximo una activa) y nunca empieza de noche.
4. **100 %:** botón «Activar 1 h de ocio libre», una vez al día.
   - Quita el límite de 5 min/h y la Aduana.
   - Las reglas fijas siguen: redes en el navegador y Telegram.
   - Se corta al empezar la noche.

**Pantalla Foco Core:**
- **Hoy:** cabecera con tiempo y dinero, y los bloques Mañana, Tarde y noche y Eventos. Cada tarea muestra minutos, hora límite, dificultad, cronómetro (▶/❚❚, en minutos) y la marca de completar.
  - Al completar: «Hecha, con foto» o «Hecha, sin foto».
  - La foto va a `files/evidencias/` como WebP de 480 px al 60 %, se guarda 8 semanas y no entra en la galería.
  - La cámara escribe una foto temporal en la caché mediante un FileProvider, y Foco la borra al comprimirla. No hace falta el permiso de cámara.
- **Plan:** presupuesto y deuda, las listas de siempre, cada día de la semana y eventos puntuales (hoy y los próximos 7 días).
- **Reportes:**
  - Por día (últimos 7): ✓ / ✗ / tarde por tarea, bloqueos de 1 h, dinero frente a lo que tocaba (terracota si te pasas) y mosaico de fotos.
  - Sábado y domingo: análisis del búho con la franja más difícil, el balance del dinero y recomendaciones concretas.

**Contra los atajos:** hoy solo se pueden añadir tareas (si al guardar bajan, se deshace), y la rutina en marcha sigue sin poder editarse.

**Hacer:** nueva tarjeta «Foco Core», junto a Cognitive Lab.

Pruebas: `FocoCoreTest` y el formato nuevo en `RoutineTest`.

## Versión 23: auditoría de Foco Core

**Corregido:**
1. **Avisos a su hora aunque no uses el teléfono.** Antes, la hora límite solo se revisaba cuando la Accesibilidad veía algún cambio de app. Ahora una alarma inexacta (±5 min) despierta a Foco en la próxima hora límite y al fin de su margen (`RoutineStore.scheduleNext`, `OwlWatch.ACTION_CORE`). Se reprograma al editar, al reiniciar el teléfono y al actualizar Foco.
2. **Días anteriores a Foco Core.** Antes salían en Reportes con todo en ✗ y contaban como 0 % en el análisis. Ahora muestran «Sin registro» y no cuentan (`RoutineStore.hasData`).
3. **Dinero y tareas cambian de día a la vez,** a las 4:00. Antes el dinero cambiaba a medianoche.
4. **Avisos de Foco Core:** nada de noche ni con No molestar, como el resto del búho.
5. **Privacidad:** el registro exportable ya no guarda el nombre de la tarea incumplida, solo la franja.
6. **Guía 2+2:** si una franja pasa de 2 tareas o de 2 h, se avisa en Hoy. No se impide.

## Versión 24: decisiones tras la auditoría

- **Cuidado sin pausa:** en modo Cuidado, pasarse de la hora límite marca la tarea como incumplida (sale en Reportes), pero no pausa el ocio. Si estabas en una pausa y pasas a Cuidado, se levanta (`RoutineStore.activePenaltyUntil`).
- **Sin plan = lo propio de hoy:** cuentan los eventos y las listas propias de esa fecha o de ese día de la semana. Las rutinas de siempre no cuentan. Con menos de 2, el widget pide anotar los pendientes del día y se abre en Plan (`FocoCore.state(…, planned, …)`).
- **Copia de seguridad:** «Guardar copia» de Foco completo incluye ahora Foco Core (listas, plan semanal, eventos, lo marcado, presupuesto, deuda y gastos) bajo la clave `focoCore`.
  - Las fotos no van en la copia, ni lo pasajero: cronómetros, pausa activa y hora libre.
  - Restaurar sigue bloqueado con el candado del modo estricto.
  - Las versiones anteriores de Foco ignoran esa clave.

## Versión 25: limpieza, etapa 1 (lo que ya no sirve)

Sale de la auditoría completa del 23/11. Solo quita; nada nuevo.

- **Código muerto:** `LeafMark`, las tarjetas `RoutineCard` y `MoneyCard` (las sustituyó Foco Core) y el recordatorio propio de Lab (`LearningReminder`, ya apagado) con su prueba.
- **Aviso de la VPN anterior** y la nota de la migración de Foco Ligero, en Ajustes de Foco completo.
- **Búho, gestos y exportar, fijos:** tono cercano, palabra del día y burbuja de ideas siempre encendidos; los tres gestos siempre activos; los números siempre visibles (salvo en Cuidado) y el ánimo nunca se exporta. Se van sus seis interruptores.
- **Despertar con el búho:** se va su lista propia (luz, agua y reto). En el primer desbloqueo de la mañana el búho abre la rutina de la mañana de Foco Core o, si no toca, Foco Core en Hoy. El modo Despertar del inicio termina al completar la rutina de la mañana (o a las 7:00).
- **Perfiles por Wi-Fi:** se van con su diálogo y el permiso de ubicación.
- **Brillo y Bluetooth** en la barra de protección: se van, con los permisos de Bluetooth, Modificar ajustes y Cambiar Wi-Fi.
- `scripts/compilar.sh` borra los archivos que trae `BORRAR.txt` dentro del zip.

## Versión 26: limpieza, etapa 2 (sin banco y solo NextDNS)

**Sin banco de minutos.** Los retos de Lab y el uso productivo ya no dan minutos.
- En **Enfoque**, y en el **Weekend Lock** (salvo en Cuidado), el ocio solo se abre con la **hora libre de Foco Core** (100 % de las tareas de hoy). Sin ella, la pausa dice cuántas tareas llevas.
- Se van el bloque gratis del día, el saldo y sus textos (inicio, cajón, Foco completo, búho, notificaciones). Un saldo guardado de antes no se lee.
- Los retos de Lab y el tiempo productivo **se siguen midiendo** en el registro (`InspectorLog.drainLab`), para los reportes. El búho recuerda agilizar la mente sin prometer minutos.
- Borrados: `runtime/Bank.java`, la Regla 4 de `AbsoluteRules` (pagos por reto) y `LeisureStatus.rulesText`, con sus pruebas.

**Solo NextDNS.** CleanBrowsing y AdGuard salen.
- Sin perfil todavía, vale cualquier perfil de NextDNS y Foco adopta el primero que ve; si había un cambio a NextDNS pedido, se adopta ya. Con perfil, solo vale ese perfil exacto.
- Si NextDNS falla, Foco lo pausa 10 min y lo reintenta (igual que antes). Además el búho avisa (como mucho cada 6 h, nunca de noche) y tú decides: **seguir esperando** o **pasar a automático en 24 h**. En automático Foco deja de exigir y de reintentar; **volver a NextDNS es al momento**.

## Versión 27: limpieza, etapa 3 (un solo sistema de tareas y ajustes en Foco)

- **Foco Core es el único sistema de tareas.** Se van las tareas del panel Hacer (`TaskStore`, `TaskList`), el «Próximo paso» editable (Foco completo e inicio) y la nota del día con su recordatorio (`DailySpace`, `NotebookStore`; el aviso diario se apaga solo).
- **El próximo paso es la siguiente tarea de Foco Core** (`RoutineStore.nextTaskLabel`): lo usan la pausa, el búho y sus mensajes.
- **Ajustes del inicio dentro de Foco** (Ajustes › Inicio): temas, dock, movimiento (un solo interruptor), rutinas obligatorias, y botones que abren en el inicio el plan, las rutinas, el horario de la noche, el tema y la pregunta de ánimo. La foto del tema va también a la pantalla de bloqueo, siempre.
- **El inicio usa ese hueco:** panel izquierdo **Análisis** (permisos que falten, tu día y tu semana, Foco Core, reportes y el botón Ajustes de Foco) y panel derecho **Practicar** (retos, juegos, Foco Core y enfoque).
- **Foco completo:** la pestaña Hoy pasa a **Estado** (modo, protección, tiempo de ocio, música y reportes); se van la escena, la práctica, lo observado y la galería de alternativas.

## Versión 28: limpieza, etapa 4 (pausa simple, sin escenas y un solo informe)

- **Pausa simple:** la foto del tema que muestra el inicio, con un velo; el búho; el motivo y cuándo vuelves; **una frase** (cambia en cada pausa, sin rotar sola); lo siguiente de Foco Core; música tranquila; cofre, práctica, juegos, descansar y llamadas. Se van el vídeo, la luz, las hojas, la órbita de apps, la galería de alternativas y el botón de ahorro.
- **Foco completo sin escena animada** (paisaje, hojas, profundidad, viento) ni su tarjeta «Ambiente visual». La música de la interfaz se queda.
- Borrados: `AmbientView`, `TerraceView`, `FloatingLeaves`, `OrbitApps`, `PauseVideoView`, `SanctuaryLight`, `PauseMessageCard`, `InspectorReport` y sus pruebas, el vídeo `sanctuary_motion.mp4` y las imágenes `sanctuary.jpg`, `coast_day.png` y `coast_night.png` (unos 5,7 MB menos).
- **Informe diario único** (`Inspector`, ya sin carpeta): cada día de 5:00 a 24:00 se cierra solo (alarma de las 00:05, o al abrir Foco o reiniciar si se perdió) y se guarda en `files/informes/AAAA-MM-DD.txt`, 90 días. Incluye lo de fuera (tareas de Foco Core con su estado, pausas, fotos, gasto, permitido, semana y deuda) y lo de dentro (modo, plan, bloqueos, candado, Weekend Lock, DNS y NextDNS, y el registro del día con tiempos, bloqueos y retos de Lab). Sin el ánimo. En **Estado › Informe diario** se exportan los últimos 30 en un archivo.
- **Reportes: dos.** Estadísticas (con el Registro de bloqueos dentro) y Reportes de Foco Core. Se van la tarjeta «Hoy, lo bueno primero» y Foco Inspector.
- **Una sola copia de seguridad:** «Guardar copia de todo» incluye ahora también los informes; al restaurar se añaden los que falten.

## Versión 29: limpieza, etapa 5 (fondos digitales automáticos)

- **Fondos digitales:** los 8 temas tienen ahora una ilustración plana por capas (cielo, sol o luna, montañas, agua o camino), sintetizada con los colores de tus fotos (`scripts/fondos_gen.py`, 1080×2340, WebP de unos 40 KB cada uno, frente a 1 MB de las fotos).
- **Arreglado: de tarde y de noche no cambiaba el fondo.** Horizonte, Luna y Cumbre no tenían imagen y el inicio se quedaba sin foto. Además el tema solo se recalculaba al volver al inicio; ahora también cada minuto con el inicio a la vista.
- **Automático:** mañana Café o Horizonte, día Cascada, Enfoque Dunas, racha productiva Ruta, de 17:30 a 20:30 Luna, de noche Cumbre. La misma imagen va a la pantalla de bloqueo y a la pausa, sin interruptor.

## Versión 30: Foco Core rediseñado y adaptativo

- **Colores adaptativos:** toda la pantalla sale de la paleta del tema del inicio (`LocalHome`). De día (Café) es clara y cálida; de noche (Cumbre) azul y oscura; cambia sola cuando cambia el tema. Verde, ámbar y terracota se mezclan con el acento del tema y cambian con una transición de 0,7 s (`coreColors`).
- **Hoy:** anillo de progreso animado con lo que queda, la hora a la que terminas, el dinero y una píldora de estado (hora libre a N tareas, pausa, sin plan). Tarjeta **Lo siguiente** con ▶ Empezar (cronómetro), ✓ Hecha y 📷. Franjas compactas: pendientes primero, las hechas plegadas («+4 hechas»), hora límite, minutos y un punto de color por dificultad. Botón flotante **＋ Nueva tarea**. Mantener pulsada una tarea la edita.
- **Nueva tarea sin formatos:** solo se escribe el nombre (con sugerencias); lo demás se toca: cuándo (hoy, mañana, siempre o días L–D), franja, duración, hora límite (± 15 min), dificultad y si pide foto. Hoy/Mañana van como tareas de ese día; Siempre a la lista de todos los días; Días a la lista de cada día elegido.
- **Quitar vale desde mañana:** si la tarea es de la lista de hoy, hoy queda fijada tal cual (`fijo_…`) y desde mañana ya no sale.
- **Semana** (antes Plan): dinero de la semana en una hoja del tema (sin diálogos grises) y tus listas, tocables para cambiar o quitar. Las rutinas, el plan del domingo y los eventos se abren aquí: se acabó el editor de texto con formato.
- **Reportes:** siete anillos (uno por día, tocables), porcentaje, días seguidos al 100 % y pausas; barras animadas de minutos en tareas reales; barra del dinero frente al presupuesto con el día más caro; el día elegido con sus fotos; y el análisis del búho todos los días.
- **Arreglado:** el «•» que algunos teclados ponen en vez de «·» se quedaba en el nombre («Hacer el desayuno •»). Ahora separa y los nombres guardados se limpian solos. Nuevo campo `foto` en la tarea (`Routine.Task.photo`): «Hecha» abre la cámara solo si la tarea la pide.
- Pruebas nuevas en `RoutineTest` (sin ejecutar).

## Versión 31: recompensas (cupones, semana perfecta, constancia y ánimo)

Lógica pura en `core/care/Rewards.java` (pruebas en `RewardsTest`, sin ejecutar); el resto en `runtime/RewardStore.java` (preferencias `foco-premios`).

- **Cupón:** 1 por cada día con todas las tareas hechas. Abre **una** app de ocio que eliges al canjearlo: 30 min (15 si es una red social; solo la app, su web sigue bloqueada). Como mucho 3 guardados, caducan a los 7 días, 1 al día. No se puede usar de noche, con una pausa por tarea vencida ni con la rutina de la mañana pendiente. Cada 7 días seguidos, uno extra.
- **Semana perfecta (de lunes a domingo, fin de semana incluido):** todos los días al 100 % con al menos 2 tareas propias, las tareas que piden foto con su foto, sin pausas por tareas y sin intentos de evasión (Accesibilidad apagada, hora cambiada o automática apagada, otro inicio, VPN o DNS apagado a mano; el respaldo de 10 min de NextDNS no cuenta). Premio: **2 cupones especiales** que valen hasta el domingo siguiente, se pueden usar seguidos (1 h) y **saltan las reglas** (límite diario, Weekend Lock, rutina pendiente, pausa, bloqueos con nombre y Aduana). **La noche sigue siendo la única excepción.**
- **Durante el cupón:** el servicio trata esa app como en la hora libre (`FocusAccessibilityService`, `coupon`), un aviso fijo con cuenta atrás dice cuándo termina, y al acabar la app vuelve a sus reglas al momento. Cada canje queda en el registro y en el informe diario.
- **Constancia:** días seguidos al 100 % que desbloquean, para siempre: 3 días bufanda del búho, 7 días fondo Aurora, 14 días fondo Mar al alba, 30 días dock dorado. Los fondos nuevos solo aparecen en «Tema del inicio» cuando se ganan.
- **Ánimo:** el búho avisa al completar el día (cupón y racha), al desbloquear algo, a media tarde si faltan 3 tareas o menos y al ganar una semana perfecta. Como mucho 3 al día, nunca de noche ni con No molestar.
- **Foco Core:** tarjeta «Tus cupones» en Hoy (dorada con cupones de semana) con su hoja para elegir la app; tarjeta «Tu constancia» en Reportes con la racha, el próximo desbloqueo y los días perfectos de la semana.
- La copia de seguridad no incluye los cupones (no se pueden recuperar restaurando).
- **Arreglado (v31.1):** con el teclado abierto, las hojas inferiores (antes de entrar al banco, ánimo, rutina, nueva tarea, dinero) quedaban tapadas y no se veía lo que escribías. Ahora suben encima del teclado (`imePadding`) y se pueden desplazar.

## Versión 32: reglas coherentes y medir lo que haces (tras la auditoría sistémica)

Sin bloqueos nuevos ni funciones nuevas: corrige y unifica. Hecho sin compilar.

- **Una sola tabla de excepciones** (`core/LeisurePolicy.java`, pruebas en `LeisurePolicyTest`). La usan la Accesibilidad y el respaldo (`GuardService`).
  - **Hora libre:** solo quita el límite de ocio (y la Aduana). El Pomodoro, los bloqueos con nombre, las webs, el DNS, el tope y la rutina siguen.
  - **Cupón normal:** además salta el tope, la rutina, la Aduana y los bloqueos con nombre de esa app. El Pomodoro, las webs y el DNS siguen.
  - **Cupón de semana perfecta:** todo salvo la noche.
  - **Cuidado:** todo salvo la rutina.
  - La noche no la salta nada.
- **Respaldo sin Accesibilidad:** `GuardService` ahora también cierra el ocio de noche, en la puerta de Foco Core (Enfoque y Weekend Lock) y con la rutina o la pausa por tareas pendientes. No puede ver webs, secciones ni DNS, y no lo finge.
- **La rutina y Foco Core usan la misma lista de hoy**, con la copia fijada `fijo_` (`RoutineStore.tasks`). Quitar una tarea ya no mueve las marcas.
- **El día completado durante una pausa cuenta** para el cupón diario y la racha (`RoutineStore.dayComplete`). También se revisa al acabar la pausa. La pausa sigue anulando la semana perfecta.
- **Evasiones justas:**
  - Cada hecho cuenta una sola vez (respeta `onceKey`).
  - Cuando el sistema detiene la Accesibilidad (memoria, actualización) se apunta `accesibilidad_detenida`, sin castigo.
  - `accesibilidad_desactivada` solo se apunta si Foco aparece desactivado en los Ajustes de Android.
- **Horario de la noche:** siempre leído de las preferencias (`AbsoluteRules.setNightSource`, instalado por `CareStore.install`), no de un valor que podía quedarse viejo.
- **«Siempre» cuenta como plan** si guardaste esa lista. Editar la predeterminada es adoptarla. La que nunca tocaste no cuenta.
- **Weekend Lock:** cierra de verdad en Normal y Enfoque (hora libre o cupón). En Cuidado queda abierto y se registra. El anuncio lo dice así.
- **Cuidado durante Enfoque o el Weekend Lock:** se puede, con una hoja que dice qué se abre y pide un motivo (cansancio, ánimo bajo, imprevisto). No es evasión. Tope diario de 2 h, y 3 h con el ánimo en 1–2 (antes no tenía tope).
- **El ocio se mide siempre, en cuatro clases** (`ocio_premio`, `ocio_cuidado`, `ocio_fuera` más `ocio`).
  - Con Acceso de uso, el total sale del sistema (ya incluía los premios).
  - Sin Acceso de uso, se suman las clases.
  - El informe trae la sección «OCIO (cuatro clases)».
  - El tiempo de premio y de Cuidado no cuenta para el tope diario de una app.
- **Día de Foco = de 04:00 a 04:00** para tareas, premios, evasiones e informes.
  - El informe se genera a las 04:05 y su CSV lleva las horas naturales de ese día lógico.
  - Guarda los modos usados y si hubo Weekend Lock ese día (`modos_del_dia`, `weekend_lock_del_dia`).
  - Las horas límite después de medianoche ya cuentan sobre el día lógico.
- **Avisos:** un presupuesto común (`OwlBudget`, 8 al día) para los avisos no críticos: el búho, los logros y el ánimo, y el aviso de la noche. Los importantes quedan fuera (rutina, horas límite, DNS, Accesibilidad apagada, inicio perdido, cupón en uso, Weekend Lock).
- **Limpieza:** fuera los restos del banco del motor (`bank`, `freeAllowed`, `paid`, el registro de minutos gastados). `in.gated` es la puerta de Foco Core. `bankMs` se sigue leyendo y escribiendo para las copias antiguas.
- **Cupones:** Telegram (bloqueo total) ya no sale en la lista del cupón, porque nunca se abría.
- **Pendiente a propósito:** R12 (rendimiento): medir antes de tocar nada.

## Versión 33: «¿Lo que veo ahora me aporta?»

Semana 1 del plan «ser consciente de lo que pierdo y lo que gano». No añade bloqueos: pregunta y mide. Hecho sin compilar.

- **Reglas puras:** `core/care/Awareness.java`, con su prueba `AwarenessTest`.
  - La primera pregunta llega a los **15 min seguidos** en una app de ocio. Salir y volver en menos de 10 min no reinicia la cuenta.
  - Tras un «sí» hay **5 min** más y luego otra pregunta.
  - La nota pide **15 palabras distintas** («asdf asdf» cuenta 1).
  - Tras un «no», la música sigue **3 min**. Si vuelves a esa app antes de **20 min**, la pregunta sale al abrirla.
- **Ventana aparte** (`ui/AwarenessActivity.kt`, Compose, tema oscuro `AwarenessTheme`). No es una capa encima: al abrirse, la app de ocio pasa a segundo plano y se pausa, aunque no obedezca la tecla de pausa (TikTok). Además se envía esa tecla (`MediaPause`) y suena la música tranquila (`CalmAudio`, que toma el sonido).
  - **«No, ya está»:** es el botón grande. Lleva a «Buena decisión» con tres alternativas (caminar con música, leer, cocinar), **una app productiva al azar** (tus apps productivas y las conocidas de `GoodApps`, por ejemplo Duolingo) o tus tareas. A los 30 s sin tocar nada, va al inicio.
  - **«Sí, me aporta»:** pide la nota con tres atajos (Aprendo, Me relaja, Hablo con alguien) y una barra de palabras. El botón «Seguir 5 min» no se enciende hasta llegar a 15 palabras. Al seguir, vuelves a la app.
  - **Atrás:** en la pregunta cuenta como «no».
- **«¿Valió la pena?»:** al salir de una sesión de 10 min o más sin pregunta, llega un aviso con tres botones (Sí / Más o menos / No). Como mucho 6 al día; nunca de noche ni con No molestar. Lo recibe `OwlWatch`.
- **Datos:** `runtime/AwarenessStore.java`, preferencias `foco-conciencia`, últimas 300 notas.
  - El registro exportable guarda solo el tipo (`decision_consciente`, `valio_la_pena`), nunca el texto de la nota.
  - El informe diario trae la sección «CONCIENCIA»: decisiones, «valió la pena» por app y tus notas.
  - El domingo desde las 18:00, el búho resume la semana con tu última nota. Pasa por `OwlBudget`.
- **Quién pregunta:** `FocusAccessibilityService.awareness()` lleva la sesión seguida con el reloj de arranque. Vale en Normal, hora libre, cupón y Cuidado; de noche no, porque el ocio ya está cerrado.

## v34: la pregunta se adelanta según el ocio de hoy

«¿Me aporta?» ya no espera siempre 15 min. Cuenta el ocio del día de Foco (desde las 04:00) en las
apps vigiladas, con el Acceso de uso:

| Ocio hoy | Primera pregunta | Tras un «sí» |
|---|---|---|
| menos de 1 h | 15 min seguidos | 5 min |
| de 1 h a 2 h | 10 min seguidos | 5 min |
| 2 h o más | 5 min seguidos | 3 min |

Si cruzas 1 h o 2 h a mitad de una sesión, la pregunta llega en ese momento (con el mínimo de 10 o
5 min seguidos). La ventana lo dice: «Hoy llevas 2 h 08 de ocio: te pregunto a los 5 min». Sin
Acceso de uso se queda en 15 min. Números en `core/care/Awareness` (`TODAY_1H_MS`, `TODAY_2H_MS`,
`FIRST_CHECK_1H_MS`, `FIRST_CHECK_2H_MS`, `EXTEND_2H_MS`); el total, `AwarenessStore.leisureTodayMs`
(una consulta por sesión).

## v35: corte preciso entre videos

Cuando ya toca «¿Me aporta?» (los tramos de la v34), Foco no corta a mitad de un video: espera a
que deslices al siguiente y abre la ventana justo ahí. La Accesibilidad escucha ahora también
`typeViewScrolled`, pero solo lo usa en la app de la sesión de ocio en curso (sale enseguida en las
demás).

- Un video = cambio de posición en la lista que se desliza; varios cambios en menos de 3 s cuentan
  como uno.
- Espera máxima = dos videos, entre 30 s y 3 min. Video típico: TikTok 45 s, Reels (Instagram,
  Facebook) 30 s, Shorts 40 s, otras 60 s. Tras 5 videos se usa tu ritmo real de la sesión.
- Volver tras un «no» sigue preguntando al abrir, sin esperar.
- La ventana muestra «≈ 38 videos · unos 28 s cada uno».

Reglas en `core/care/Awareness` (`typicalVideoMs`, `cutGraceMs`, `cutNow`, `videosText`); el
deslizamiento, `FocusAccessibilityService.awScroll`.

## v36: «¿Cómo lo hiciste?» y aviso cada 5 min antes de la hora límite

**Al tocar una tarea** (en la lista o «✓ Hecha» de «Lo siguiente») ya no se marca sin más: se abre
una hoja con «¿Cómo lo hiciste?» y dos botones, «📷 Foto y hecha» y «✓ Hecha». Nota y foto son
opcionales y van al informe del día (`cómo: …` y `con foto` en cada tarea). Si ya escribiste algo
para esa tarea otro día, «↺ Igual que la última vez» lo pone con un toque. La nota se queda en el
teléfono (`RoutineStore.saveNote`, prefs `nota_*` y `ultimaNota|*`): nunca va al registro exportable.
Tocar una tarea hecha la desmarca, como antes.

**Hora límite de cada franja:** Mañana hasta las 9:00 y Tarde y noche hasta las 23:00, cambiables
tocando «⏰ hasta …» junto al nombre de la franja («Sin hora» la quita). Desde 30 min antes, si
queda algo, el búho avisa **cada 5 min** (canal «Hora límite (cada 5 min)», con vibración) hasta que
la terminas o vence el margen de 10 min. Lo mismo para cada tarea con su propia hora. Al marcar la
última tarea el aviso se calla. Este aviso lo pidió el usuario: no gasta el presupuesto del búho y
suena también de noche (No molestar lo silencia). La hora de la franja **no** pone pausa de ocio:
la pausa sigue siendo solo para las tareas con hora propia.

Reglas en `FocoCore` (`nagAt`, `nagAgain`, `nagText`, `logicalDeadline`, `MORNING_DEADLINE`,
`NIGHT_DEADLINE`); aviso y alarma en `RoutineStore.nag` / `scheduleNext`.

## v37: NextDNS se cambia solo desde otro dispositivo

En este teléfono, la app de NextDNS (cualquier paquete con «nextdns») y su web (`nextdns.io` y
subdominios: my.nextdns.io, help…) se cierran siempre, como Telegram: sin modo, cupón ni hora libre
que lo salte. Registro: `intento_panel_nextdns`. Foco sigue leyendo las estadísticas con la clave de
la API (no pasa por el navegador). Refuerzo recomendado, hecho desde otro dispositivo: añadir
`my.nextdns.io` a la Denylist del perfil. Reglas en `Categories.dnsPanelApp/dnsPanelDomain`.

## v38: fondos solo por la hora (nada de luna a mediodía)

El fondo automático ahora sigue **solo la hora**, para que el cielo cuadre con el momento del día.
Lo que haces (productivo, Enfoque) ya no cambia el fondo: eso lo expresa el búho.

| Hora | Fondo |
|---|---|
| 05:00–08:00 | Café del Alba (Horizonte si ya hiciste un reto) |
| 08:00–11:00 | Dunas o Cascada (días alternos) |
| 11:00–15:30 | **Pradera de Mediodía** (nuevo) |
| 15:30–18:00 | **Tarde Dorada** (nuevo) |
| 18:00–19:30 | Ruta (o Mar en días alternos, si lo ganaste) |
| 19:30 hasta la noche | Luna de Ocaso |
| Noche | Cumbre (o Aurora en días alternos, si la ganaste) |

Los dos fondos nuevos se generaron con el mismo estilo de capas (`theme_pradera.webp`,
`theme_tarde.webp`, ~15 KB). Quien tenía un tema fijado a mano pasa una vez a automático
(`theme_auto_v38`). La luna creciente que el búho dibujaba con cualquier tema oscuro (también a
mediodía) ahora solo sale con cielo de noche (`PhotoTheme.nightSky()`) o cuando duerme. Prueba:
`nuncaUnCieloDeNocheDeDia`.

## v39: modo juego (base) y Foco Core

Primera parte del diseño aprobado («modo juego»). El fondo (foto o paisaje del tema) no cambia.

- **Base, `ui/GameStyle.kt`:** letras Rubik (títulos, números, botones) y Lexend (texto), en
  `res/font` como versiones fijas recortadas a caracteres latinos (~290 KB, licencia OFL en
  `docs/LICENCIA-LETRAS-OFL.txt`, sin dependencias nuevas). Colores por acción (`Act`): verde hacer,
  azul completar, amarillo foto, morado premio, naranja tiempo, coral parar, turquesa cuidarte. Tres
  redondeos (10/16/26). `GameButton` con relieve que se hunde y vibra; `GameCheck` cuadrada que
  salta; `GameBar`, `GameStat`, `GameChip`.
- **Toda la app:** `HomeText` usa la letra nueva (Rubik desde Bold, Lexend el resto) y
  `PrimaryButton` es el botón verde con relieve. «Sí, modo Cuidado» va en turquesa.
- **Foco Core:** arriba un marcador (hechas/total grande, racha y cupones, barra del día y una sola
  etiqueta de estado). «Siguiente» con Empezar (verde, naranja mientras corre), ✓ (azul) y cámara
  (amarillo). Tareas como fichas con casilla cuadrada; hora límite en chip naranja; minutos con «′».
  Textos más cortos: sin la línea «Más que 2+2», cupones «Todo hecho → 30′…», el consejo de
  mantener pulsado solo hasta la primera edición, hoja «¿Cómo? (opcional)» con Foto/Hecha.

## v40: ajustes tras ver la v39 en el teléfono

- Con foto de fondo, el búho ya no dibuja su luna: la foto (Cumbre, Luna, Aurora) trae la suya y
  salían dos.
- Cupones: «Caduca mar 1 · redes 15′» y «TikTok · quedan 12′» en vez de frases largas.
- Semana: ayuda más corta y con aire abajo para que el botón «Tarea» no la tape.

## v41: modo juego en toda la app (inicio, búho, «¿Me aporta?», Reportes, pantallas Java)

**Inicio**
- Reloj en Rubik Black. Debajo, el marcador: racha 🔥, barra del día, cupones 🎟 y la **campana**
  (con número) para lo que no corre prisa: plan del domingo, cuánto estuvo apagado Foco, «Usar Foco
  como inicio», resumen de la noche y NextDNS. Se abren al tocarla. «Foco apagado» y la pregunta de
  ánimo siguen a la vista.
- La tarjeta de Foco Core es una **misión**: etiqueta (SIGUIENTE, PLAN, OCIO EN PAUSA, TODO
  HECHO), la tarea en grande, el aviso naranja si la hora límite está cerca y un solo botón
  (Vamos / Anotar / Activar 1 h libre / Ver tareas).
- Dock en fichas con relieve; tres puntos → **pestañas con icono** (Análisis, Inicio, Practicar).
- Cajón: grupos con su color (ocio naranja, bloqueadas coral, el resto verde) y chips cortos.

**Búho**
- **Festeja** (salto con confeti) cada vez que se marca una tarea (`HomeViewModel.celebrate`).
- **Te apunta**: reloj naranja que late a su lado y dice la tarea cuando la hora límite está cerca
  (`RoutineStore.urgentNow`).
- **Corona** con 7 días seguidos al 100 % (la bufanda sigue a los 3).
- **Mantener pulsado** → menú: Respirar, Siguiente tarea, ¿Cómo estás?
- Globo en modo juego y **frases de unas cinco palabras** (`OwlBrain.say` y `cta` acortados).

**Otras pantallas**
- «¿Me aporta?»: Lexend/Rubik, «¿Te aporta?», botones con relieve (No coral, Sí neutro, Seguir y
  Abrir verdes, Mis tareas azul) y menos texto.
- Foco Core (Reportes y resto): los colores de estado son los de acción (verde, naranja, coral).
- Pantallas Java (Estadísticas, Ajustes, Registro…) vía `Design`: letra Rubik/Lexend, botón
  principal verde con relieve, campos de 16 dp. Temas XML con Lexend por defecto. Píldora del
  cronómetro en Rubik.

## v42: menú del búho legible

El menú de mantener pulsado el búho usaba el desplegable del sistema (fondo claro) con el texto
claro del tema: no se leía. Ahora es una ventanita propia con el color de la tarjeta y tres botones
del modo juego: Respirar (turquesa), Siguiente tarea (verde) y ¿Cómo estás? (neutro).

## v45: base del PC (formato Spotless) y arreglos de la auditoría

La base es ahora la versión del PC del usuario (v43 con `spotlessApply`, rama `version-pc`). Sobre
ella, otra vez los arreglos verificados de la auditoría (la v44 se escribió sobre el formato
anterior y se descarta):

- Sin rojo (`CareTextTest`): parar `0xFFFF8170`/`0xFFC2725F`, relieve naranja `0xFFC27A1E`,
  Terracotta y bufanda `0xFFC0735A`, coral de «¿Me aporta?» `0xFFE8806F`.
- `OwlBrainTest.neutralToneHasNoBro` busca «, bro» (fallaba con «El Libro Total»).
- `HomeViewModel.onCleared` suelta `RoutineStore.listen`; `NextDnsSync.running` es `AtomicBoolean`;
  los `catch` vacíos de `BrainViewModel` relanzan la cancelación.

Pendiente: 11 pruebas de `LiteRuntimeTest`/`BlockLogServiceTest` comprueban el bloqueo por los 5 min
con capa, que ya no existe en ningún modo (Normal recuerda; Enfoque cierra antes en la puerta de Foco
Core; la hora libre salta el límite). Hay que reescribirlas viéndolas correr en el PC.

## v46: en Cuidado no sale «¿Te aporta?»

La ventana de conciencia salía también en Cuidado y, al ser una pantalla propia, sacaba de un juego
en plena partida. Cuidado es relajarse sin que te vigilen: ahí ya no se abre (se sigue midiendo el
ocio como `ocio_cuidado`). Lo que sí cierra el ocio en Cuidado, a propósito: la noche y la rutina
obligatoria de mañana o de noche si falta.

## v47: copia nocturna en Drive, hoja de historial y NextDNS por modo

Motivo: al cambiar la llave de firma se perdieron todos los datos. Ahora:

- **Llave estable** (`app/build.gradle`, firma `foco`): ruta, alias y contraseñas en
  `local.properties` (fuera de git) o en las variables `FOCO_*`; sin ellas, llave de depuración.
  Vale para debug, release y perf. Cómo respaldarla y sacar la SHA-1: README.
- **Conexiones** (Ajustes): Google Drive con `AuthorizationClient` y solo `drive.file`
  (`GoogleDrive`); NextDNS con clave cifrada en el Android Keystore (`SecretBox`, la clave plana
  anterior se migra y se borra) e ID de perfil (vacío = el del DNS privado). «Probar conexión».
- **NextDNS por modo** (`core/backup/NextDnsPlan`, `NextDnsControl`, `NextDnsWorker`): redes por
  servicio del control parental (tiktok, instagram, facebook, youtube, reddit, pinterest, snapchat,
  twitter) en Enfoque y de noche (horario de la noche de Foco, 21–7 por defecto); Telegram siempre
  en la lista de bloqueo. Sin «desbloquear ahora». Cada 30 min, al cambiar de modo y en cada borde
  (fin de Enfoque, inicio y fin de la noche) se compara y corrige; si el cambio vino de fuera se
  apunta `nextdns_cambiado`. Sin PATCH: activar = POST, quitar = DELETE.
- **Reporte del día** (`core/backup/DayReport`, `DayCollector`, `ReportChart`): JSON con versión
  de formato y PNG de 1080 px (verdes). El ánimo solo si se activa en Ajustes.
- **Drive y hoja** (`DriveBackup`, `core/backup/SheetLayout`, `SheetRows`): `Foco/Reportes/AAAA-MM`
  con `AAAA-MM-DD.json` y `.png` (se reemplazan); hoja «Foco · Historial» con Panel (tarjetas,
  semana vs anterior y 4 gráficos), Diario, Por hora (escala de verdes), Apps, Rastreadores,
  Eventos (30 días) y Ánimo (opcional). Reenviar un día borra antes sus filas: nada se duplica.
- **Cada noche** (`BackupWorker`, `core/backup/ReportQueue`): WorkManager cada 24 h desde las
  23:30, con red y batería no baja, reintentos con espera creciente; sube en orden los días que
  falten (7 por vuelta). El día de hoy se vuelve a subir cerrado la noche siguiente.
- **El búho** (`core/backup/BackupLines`, `BackupNotifier`): 10 frases sin repetir en 3 días, con
  «Ver hoja» y «Ver gráfico»; un solo aviso suave si falla; «Vuelve a conectar Drive…» si caduca el
  permiso. En llamada o No molestar llega en silencio.
- **Recuperar** (`DriveRestore`): con Drive conectado y el registro vacío, Ajustes ofrece
  «Recuperar historial desde Drive» (resúmenes diarios: tiempo por app, retos y minutos).
- **Privacidad**: todo lo exportado pasa por `Secrets.redact`; la clave y los tokens nunca van al
  log, al JSON ni a la hoja.

Dependencias nuevas (justificadas): `androidx.work:work-runtime` (trabajo nocturno con red,
batería y reintentos que sobreviven a reinicios) y `play-services-auth` (permiso de Drive sin
secretos en el APK). Drive y Sheets van por REST con `HttpURLConnection`, sin sus librerías.

## v47b: la hoja no se rompe al añadir filas

Las filas nuevas se escriben con `OVERWRITE` (con `INSERT_ROWS` Google movía las fórmulas del Panel),
las fórmulas usan columnas enteras y el top 5 usa `FILTER` + `QUERY`. Una fecha sin formato (número
de serie, 46353) también cuenta al quitar duplicados. Las hojas del diseño 1 se reparan solas y los
gráficos que Google dejó sin series se rehacen tras cada copia.

## v48: todos los ajustes en Drive, fuera las copias manuales y Apple Music directo

- **Copia de ajustes** (`SettingsSnapshot`): cada noche `Foco/Ajustes/foco-ajustes.json` con el
  plan, Foco Core, informes, bloqueos con nombre y preferencias permitidas (noche, racha y cupones,
  categorías, inicio y tema, ambiente, sonido, perfil de NextDNS, hoja). Nunca: candado, esperas,
  modo en curso, enfriamientos, DNS pendiente, clave de NextDNS, ánimo ni notas.
- **Recuperar todo** (`DriveRestore.all`): al conectar Drive en un teléfono nuevo, si hay copia,
  Ajustes ofrece «Recuperar todo desde Drive» o «Empezar de cero». Mientras no decidas, Foco no sube
  sus ajustes vacíos encima de tu copia buena. Con el candado del modo estricto no se restaura.
- **Quitado** (lo pidió el usuario): «Guardar copia de todo» / «Restaurar copia» (JSON), «Exportar
  mis 7 días · CSV», «Exportar los últimos 30 días» (TXT) y «Copia de los bloqueos». El código que
  usaban para armar la copia sigue y ahora lo usa `SettingsSnapshot`.
- **Apple Music** (`AppleMusic`, `MediaListener`): abre tu playlist exacta (music.apple.com) y la
  pone a sonar; con «Foco · música» en Acceso a notificaciones ve qué suena y la controla (con
  `playFromUri` si Apple Music lo admite). Suena al entrar en Enfoque (si lo activas), en la pausa
  (si eliges Apple Music) y desde «Mi música» en el menú del búho. Sin MusicKit: no hacen falta
  cuenta de desarrollador ni claves.

## v49: auditoría de batería y la copia a las 21:00

- **Accesibilidad con los eventos justos** (`FocusAccessibilityService.updateEventMask`): los
  cambios de contenido (decenas por segundo en vídeos y juegos) solo se piden con delante un
  navegador, una app con secciones, Google Play, Ajustes o una app productiva; el deslizar, solo en
  apps de ocio. El cambio de ventana se pide siempre.
- **Bucles del inicio solo con el inicio a la vista** (`WhileVisible`): el búho dormido redibujaba a
  15 fps y seguía despertando el procesador con Foco Home en segundo plano; también el reloj, el
  widget de Foco Core y las frases del búho.
- **NextDNS cada 30 min** solo con batería no baja (los cambios de modo y de horario, al momento).
- **Copia en Drive una vez por noche a las 21:00** (antes 23:30); el día se vuelve a subir cerrado
  la noche siguiente. El trabajo ya programado se reprograma solo una vez.
- **Quitado**: la exportación CSV de Registro (30/90 días) y el segundo sitio donde se pedía la clave
  de NextDNS (queda en «NextDNS: control por modo»).
