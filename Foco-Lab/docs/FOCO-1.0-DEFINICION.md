# Foco 1.0 — Definición de versión final

Nov 14, 2026 · @Santos

## 1. Qué es Foco 1.0

Foco 1.0 es la versión que se termina, se instala y se usa sin tocarla durante al menos **tres semanas seguidas**. No se le agregan juegos ni se rediseña la interfaz. Solo se corrigen errores que rompan algo.

Tiene cinco piezas y nada más:

1. El bloqueo actual (apps por AccessibilityService y páginas por DNS).
2. La regla de los 5 minutos de ocio, pagada con minutos ganados.
3. Weekend Lock, donde el único desbloqueo es gastar minutos ganados.
4. Cognitive Lab tal como existe hoy, como fuente principal de minutos.
5. Un informe diario (y un resumen el domingo) que termina en una sugerencia concreta.

**Regla de congelamiento:** cualquier idea nueva va a la lista 1.1 (sección 7). Si no está en este documento, no entra en la 1.0.

**Punto de partida (semana del 8 al 14 de noviembre, según Bienestar digital de Samsung):** 4 h 2 min de pantalla al día en promedio. TikTok 3 h 55 min a la semana, juegos 2 h 8 min (IQDungeon2 1 h 46 min), Firefox 2 h 31 min y hora pico a las 19:00. En ese mismo período Foco registró solo unos 88 minutos de TikTok, así que **la medición actual de Foco se queda corta** (ver sección 5). El tiempo invertido que vio Foco fue casi cero, aunque el 15 de noviembre hubo 44 min de Claude y 19 de Duolingo que Foco no contó porque no había apps productivas aprobadas.

La meta de la 1.0 no es solo bajar el ocio: es que el tiempo invertido se mida bien y deje de ser casi cero.

### Esta semana, sin tocar código

1. Aprobar Duolingo y Google Play Libros como productivas (Mi plan → Categorías).
2. Ponerle a TikTok un límite diario propio de 10 minutos.
3. Sacar Telegram de la lista de ocio vigilado y dejarlo solo en bloqueo total.

## 2. Categorías de apps

| Categoría | Límite | ¿Gana minutos? | Ejemplos |
| --- | --- | --- | --- |
| **Bloqueo total** | Siempre bloqueada. Ni los minutos ganados la abren | No | Telegram y todos sus clientes |
| **Esencial** | Sin límite | No | Teléfono, SMS, banco, mapas, cámara, Foco |
| **Productiva aprobada** | Sin límite | Sí, con uso real | Google Play Libros, Duolingo, Cognitive Lab |
| **En observación** | Bloques de 5 min, como el ocio | No, hasta que se apruebe | Toda app nueva o sin clasificar |
| **Ocio 5 min** | Bloques de 5 min pagados con minutos ganados | No | TikTok, Instagram, Facebook, YouTube, Reddit, Pinterest, juegos |

Una app nueva no se marca como ocio de forma automática: entra **en observación**. Mientras está ahí tiene el mismo límite que el ocio, pero el Inspector la vigila para decidir dónde va.

**TikTok** tiene además un límite diario propio de 10 minutos. Aunque haya saldo en el banco, pasado ese tope no se abre hasta el día siguiente.

**Pendiente de decidir:** IQDungeon2 y Almanac no están en ninguna lista de Foco, así que hoy Foco no los ve. Hay que definir si cuentan como aprendizaje o como ocio. Mientras no se decida, entran en observación como cualquier app nueva.

### Telegram: bloqueo total

Esta regla no se negocia y no tiene excepciones, ni en descansos ni con minutos ganados.

- **App** (bloqueo por accesibilidad): `org.telegram.messenger`, `org.telegram.messenger.web`, `org.telegram.messenger.beta`, `org.thunderdog.challegram` (Telegram X), `org.telegram.plus` (Plus Messenger), `tw.nekomimi.nekogram` (Nekogram). Antes de cerrar la lista, verificar los nombres de paquete en el teléfono.
- **Dominios** (bloqueo por DNS, incluyendo subdominios): `telegram.org`, `web.telegram.org`, `t.me`, `telegram.me`, `telegram.dog`, `telegra.ph`, `telesco.pe`, `tg.dev`, `cdn-telegram.org`, `telegram-cdn.org`.
- **Nueva instalación:** si se instala cualquier app cuyo paquete contenga `telegram` o esté en la lista, entra directo a bloqueo total, sin pasar por observación.

* **Configuración:** Telegram sale de `watchedApps` (la lista de ocio vigilado) y queda solo en bloqueo total, para que no ensucie el conteo de ocio.

**Limitación:** la app de Telegram se conecta directo por IP, así que el DNS solo no basta para bloquearla. Lo que la frena es el bloqueo por accesibilidad. El DNS cubre la versión web y los enlaces `t.me`.

**VPN:** mientras Proton VPN (o cualquier otra VPN) está encendida, el filtro DNS de Foco probablemente no se aplica; el bloqueo de apps sí sigue funcionando. La VPN se usa por razones reales, así que **no se bloquea**: Foco solo registra cuándo hay una VPN activa (evento `vpn_activa`) para que el informe lo muestre.

### Quién mueve las apps de categoría

La regla es asimétrica: **endurecer puede ser automático; aflojar siempre lo confirmas tú.** Si el Inspector se equivoca endureciendo, lo peor que pasa es que una app queda limitada de más. Si se equivoca aflojando, se abre un hueco.

**Endurecer (automático, con aviso):** una app en observación o productiva pasa a ocio 5 min si en los últimos 7 días cumple cualquiera de estas condiciones:

- Más de 45 minutos en un mismo día, dos días o más.
- Más de 15 aperturas en un mismo día.
- Es un juego (categoría de Android `CATEGORY_GAME`) con sesiones de más de 20 minutos.

**Aflojar (lo propone el Inspector, lo confirmas tú):** una app en observación puede pasar a productiva aprobada si en 7 días tuvo uso real en al menos 3 días, con sesiones promedio de 3 minutos o más, y no cumple ninguna condición de endurecer. El Inspector muestra la propuesta y tú aceptas o rechazas. No se puede aceptar durante Weekend Lock.

**Nunca se pueden aprobar como productivas:** navegadores, YouTube, redes sociales, cualquier app con feed infinito y todo lo que esté en bloqueo total.

### Supervisor automático

El supervisor vive dentro de Foco. Son reglas fijas que cuentan y comparan, sin IA, sin internet y sin depender de ningún plan de pago. Todas endurecen, así que pueden actuar solas, siempre con un aviso que dice qué regla se cumplió:

- **Hora pico:** si en los últimos 7 días una misma hora concentra más del 30 % del ocio o de los bloqueos, Foco activa una franja estricta que empieza una hora antes y dura dos horas. Durante la franja no se pueden gastar minutos del banco en ocio. Si esa hora baja del 15 % durante 7 días seguidos, la franja se quita sola. Con los datos actuales, esto pondría una franja de 18:00 a 20:00.
- **App dominante:** si una app supera el 40 % del ocio en 3 de los últimos 7 días, su límite diario propio baja 2 minutos, con un mínimo de 5. Si en 7 días queda por debajo del 25 %, recupera el límite anterior.
- **Endurecer apps:** las condiciones de arriba (45 minutos, 15 aperturas, juegos con sesiones largas).

## 3. Sistema de minutos ganados

El ocio ya no es gratis: cada bloque de 5 minutos se paga con minutos del banco. El banco se llena aprendiendo.

### Cómo se ganan

**Cognitive Lab** (la fuente más confiable, porque Foco sabe si de verdad terminaste):

| Acción | Minutos |
| --- | --- |
| Completar un reto | +5 |
| Subir de nivel en un juego | +10 extra |
| Misiones diarias | No cuentan como logro; solo cuentan si completan un reto |
| Repetir el mismo juego el mismo día | 5, luego 3, luego 1, luego 0 |
| Fallar o abandonar el reto | 0 |

**Pendiente:** la lista completa de juegos de Cognitive Lab y qué significa "completar" en cada uno, para que la tabla sea justa con todos.

La caída por repetición empuja a variar y evita sacar minutos del juego más fácil.

**Apps productivas aprobadas:** 20 minutos de uso activo dan 5 minutos de ocio. Es un punto medio entre 10 y 30 minutos; se ajusta después de dos semanas de uso.

Uso real significa:

- La app está en primer plano y la pantalla está encendida.
- Hay actividad (scroll, toque, cambio de página) al menos cada 2 minutos. Si pasan más de 2 minutos sin eventos, el tramo se corta.
- Solo cuentan tramos de 60 segundos seguidos o más. Abrir Libros 7 segundos no suma nada.

Pendiente de verificar en el teléfono: qué eventos de accesibilidad genera Google Play Libros al pasar página. Si no genera ninguno, para Libros se usa solo el tiempo en primer plano con pantalla encendida, con un tope de 20 minutos ganables por día.

### Límites

- **Base gratis:** 1 bloque de 5 minutos al día de lunes a viernes. El fin de semana no hay base gratis.
- **Máximo ganado por día:** 20 minutos (antes 30; con 30 el banco no apretaba).
- **Banco máximo:** 60 minutos. Lo que pase de ahí no se acumula.
- **Sin saldo:** la app de ocio queda bloqueada y Foco redirige a una actividad que da minutos (ver sección 6).

### Reglas técnicas

- **El tiempo de uso se mide con `UsageStatsManager`**, la misma fuente que usa Bienestar digital. La accesibilidad queda solo para detectar la app en primer plano y bloquear. Así el banco y el informe usan números reales.
- Los tramos y topes se cuentan con `SystemClock.elapsedRealtime()`, no con la hora del reloj, para que cambiar la hora no regale minutos.
- Cada ganancia y cada gasto queda en el log del Inspector (sección 5).
- El saldo se guarda en la base de datos que ya usa Foco, no en un archivo nuevo.

## 4. Weekend Lock

**Horario:** viernes 18:00 a domingo 23:59, hora local del teléfono.

### Qué pasa durante el Weekend Lock

| Categoría | Comportamiento |
| --- | --- |
| Bloqueo total (Telegram) | Bloqueada, igual que siempre |
| Esencial | Libre |
| Productiva aprobada | Libre, y sigue ganando minutos |
| En observación y ocio 5 min | Solo se abren gastando minutos del banco, en bloques de 5 |

**No hay desbloqueo manual libre.** La única forma de usar ocio el fin de semana es gastar lo que ganaste. Llamadas, SMS, mapas y demás esenciales nunca se bloquean, así que las emergencias quedan cubiertas.

**Configuración protegida:** durante el Weekend Lock no se puede apagar el modo, cambiar el horario ni mover apps a una categoría más permisiva. Endurecer sí se puede.

### Cómo funciona por dentro

- **El estado se calcula, no se guarda.** Hay una función `modoActual(ahora, configuración)` que devuelve NORMAL o WEEKEND\_LOCK. El servicio de accesibilidad la consulta cada vez que decide si bloquear. Así, después de un reinicio o si el servicio se cae, al volver el resultado es el mismo y no hay estados contradictorios.
- **Las alarmas solo avisan, no deciden.** AlarmManager (`setExactAndAllowWhileIdle`) dispara el viernes a las 18:00 y el domingo a las 23:59 para mostrar la notificación de inicio y fin. Si Android no da permiso de alarma exacta (`SCHEDULE_EXACT_ALARM`), el bloqueo sigue funcionando igual, porque depende del cálculo, no de la alarma.
- **Recuperación:** en `BOOT_COMPLETED`, `TIME_CHANGED`, `TIMEZONE_CHANGED` y al reconectarse el servicio de accesibilidad, se recalcula el estado y se reprograman las alarmas.
- **Manipulación de hora:** Foco guarda la última hora conocida junto con `elapsedRealtime`. Si durante el Weekend Lock la hora del reloj salta y ese salto no se explica por el tiempo transcurrido ni por un cambio de zona horaria, se registra un incidente y el Weekend Lock sigue activo hasta el final calculado con `elapsedRealtime`. Después de un reinicio esto ya no se puede comprobar del todo; en ese caso se registra el incidente si la hora es anterior a la última guardada.
- **Al terminar:** se registra el evento de fin y todo vuelve a NORMAL. Como el Weekend Lock es una regla que se suma encima de la configuración y nunca la modifica, no hay nada que restaurar.

## 5. Cambios al log del Inspector

El log actual sirve, pero le faltan datos para tomar decisiones. Estos son los cambios:

1. **Medir el tiempo con `UsageStatsManager`.** Es el cambio más importante. En la semana del 8 al 14, Bienestar digital marcó 3 h 55 min de TikTok y Foco solo unos 88 minutos. La accesibilidad deja huecos (por ejemplo, cuando se desactivaba). Desde ahora, `segundos` sale de `UsageStatsManager` y la accesibilidad solo decide qué bloquear.
2. **Agregar la hora** (0 a 23). Sin ella no se puede calcular el peor horario ni aplicar la regla de hora pico. `UsageStatsManager.queryEvents` da las horas exactas.
3. **Formato uniforme.** Las filas de `bloqueo_por_limite` no llevan comillas como las demás; todas las filas deben usar el mismo formato para que el CSV se lea bien.
4. **Separar tiempo de intentos.** `segundos` es solo tiempo de uso real; `veces` es solo número de aperturas o intentos. Un intento de apertura nunca suma tiempo. Hoy las filas de ocio tienen `veces` en 0: hay que llenarlo.
5. **Medir lo que pasa después de elegir una alternativa.** Hoy solo se registra que se abrió Duolingo; hay que registrar también cuántos segundos de uso real tuvo.
6. **Registrar Cognitive Lab, los minutos y las apps que hoy no se ven** (por ejemplo, IQDungeon2), que hoy no aparecen en el log.

### Encabezado nuevo

`fecha,hora,tipo,paquete,app,segundos,veces,valor,detalle`

### Tipos de evento

| Tipo | Qué registra |
| --- | --- |
| `ocio` | Uso real de una app de ocio (ya existe) |
| `uso_productivo` | Uso real de una app productiva aprobada |
| `alternativa_elegida` | Ya existe; ahora con segundos de uso real |
| `bloqueo_por_limite` | Ya existe; ahora con hora y paquete |
| `intento_bloqueo_total` | Intentos de abrir Telegram o sus dominios |
| `reto_completado` | Juego, dificultad y resultado en `detalle` |
| `logro_desbloqueado` | Nombre del logro en `detalle` |
| `minutos_ganados` | Cantidad en `valor`, fuente en `detalle` |
| `minutos_gastados` | Cantidad en `valor`, app en `paquete` |
| `saldo_fin_dia` | Saldo del banco al cerrar el día |
| `cambio_categoria` | App, categoría anterior y nueva, si fue automático o confirmado |
| `weekend_inicio` / `weekend_fin` | Inicio y fin del Weekend Lock |
| `incidente` | Accesibilidad desactivada, permiso revocado, hora manipulada, VPN o DNS privado, reinicio |
| vpn\_activa | Inicio y fin de una VPN activa (solo registro, no bloquea) |
| franja\_estricta | Inicio y fin de una franja creada por la regla de hora pico, con el motivo |
| redireccion | Foco propuso una actividad en lugar de ocio; en detalle: aceptada, rechazada o ignorada |

### Reglas del log

- Un evento se guarda una sola vez. Si ya existe un registro equivalente, se reutiliza.
- El detalle se guarda 30 días; después solo queda el resumen diario, para no llenar la base de datos.
- El log se puede exportar como texto, igual que ahora, para analizarlo fuera de la app.

## 6. Informe diario y sugerencias

El Inspector no sirve si solo junta datos. Cada día tiene que terminar en algo que puedas hacer.

### Informe diario (en la pantalla del Inspector)

- **Tiempo invertido:** Cognitive Lab + uso productivo real.
- **Tiempo de ocio:** solo uso real, sin contar intentos.
- **Peor horario:** la hora del día con más ocio y más bloqueos.
- **Saldo del banco** al final del día.
- **Una sola sugerencia**, la de mayor prioridad de la tabla de abajo.

### Reglas de sugerencias (en orden de prioridad)

| Si el log muestra… | El Inspector sugiere… |
| --- | --- |
| Un incidente de protección | Revisar qué pasó y reactivar lo que se desactivó |
| Una app pasó a ocio por la regla de endurecer | Avisar del cambio y por qué |
| Una app supera el 40 % del ocio del día | Ponerle un límite propio más bajo (ejemplo: TikTok) |
| Se eligió una alternativa y se usó menos de 1 minuto | "Abriste Libros 7 s. 10 minutos mañana te dan 5 de ocio" |
| Más bloqueos que el promedio de la semana | Activar el modo estricto antes de la hora más débil |
| 3 días sin Cognitive Lab | Un reto de un juego distinto al último jugado |
| Una app en observación cumple las condiciones para aprobarse | Proponer pasarla a productiva (lo confirmas tú) |
| Ninguna de las anteriores | Reconocer el avance con un dato concreto, sin frases motivacionales |

El Inspector solo sugiere. Lo único que hace por su cuenta es la regla de endurecer de la sección 2.

### Redirigir en vez de solo bloquear

Cuando intentas abrir ocio sin saldo, o con TikTok ya en su tope, Foco no se limita a bloquear. Muestra una propuesta concreta, por ejemplo:

> "En vez de esto, 20 min activos en Duolingo te dan 5 min. ¿Empiezas?"

- Tiene un botón que abre la app propuesta. La app nunca se abre sola: hace falta tu toque.
- La propuesta rota entre las apps productivas aprobadas y Cognitive Lab, empezando por la que lleva más días sin usarse.
- Si la rechazas, no se repite la misma propuesta ese día.
- Si no respondes, cuenta como ignorada, no como aceptada.
- Durante la franja estricta de la hora pico, la propuesta aparece igual, porque es justo el momento más útil.

### Revisión semanal con Claude

Foco funciona solo con las reglas de arriba; no depende de Claude ni de ningún plan de pago. Aparte, el domingo se exporta el log y se analiza en una conversación con Claude, como revisión humana de la semana. Claude no puede recibir ni leer el log automáticamente.

### Resumen del domingo

Compara esta semana con la anterior: tiempo invertido, ocio, la relación entre los dos, la app que más tiempo tomó, el peor día y el peor horario, y los juegos jugados. Incluye un botón para exportar el resumen y analizarlo con Claude, sin meter ninguna IA dentro de la app.

## 7. Fuera de la 1.0 (lista 1.1)

Nada de esto se toca hasta haber usado la 1.0 al menos tres semanas seguidas:

- Smart Activities completo (recomendaciones espontáneas por contexto, más allá de la redirección).
- Descansos programados.
- Device Owner y Lock Task Mode (probar solo en un teléfono secundario).
- Juegos nuevos en Cognitive Lab.
- Rediseños de la interfaz.
- Registro de libros pendientes y metas personalizadas.
- Subir el log automáticamente a Google Drive y revisarlo con una tarea programada semanal (depende del plan de Claude).
- Análisis avanzado de patrones dentro de la app.

## 8. Pruebas de aceptación

La 1.0 está lista cuando pasan todas estas pruebas en el teléfono:

| # | Prueba | Cómo se hace | Resultado esperado |
| --- | --- | --- | --- |
| 1 | Telegram bloqueado | Abrir la app y entrar a `t.me` y `web.telegram.org` en el navegador | Bloqueado siempre, incluso con saldo |
| 2 | App nueva | Instalar una app cualquiera | Queda en observación, con límite de 5 min |
| 3 | Regla de endurecer | Abrir una app en observación más de 15 veces en un día | Pasa a ocio 5 min y aparece el aviso |
| 4 | Minutos por reto | Completar un reto de Cognitive Lab | +5 en el banco y evento en el log |
| 5 | Repetición | Repetir el mismo juego 4 veces | 5, 3, 1, 0 |
| 6 | Uso real | Abrir Libros 10 s y salir; luego leer 10 min | Lo primero no suma; lo segundo suma 5 |
| 7 | Sin saldo | Gastar todo el banco e intentar abrir TikTok | Bloqueado y mensaje con lo que falta |
| 8 | Inicio del Weekend Lock | Poner la hora en viernes 17:59 y esperar | A las 18:00 se activa |
| 9 | Fin del Weekend Lock | Poner la hora en domingo 23:58 y esperar | Después de las 23:59 vuelve a NORMAL |
| 10 | Reinicio | Reiniciar el teléfono en pleno Weekend Lock | Sigue activo al encender |
| 11 | Manipular la hora | Durante el Weekend Lock, adelantar la hora al lunes | Se registra el incidente y el bloqueo sigue |
| 12 | Configuración protegida | Intentar mover TikTok a productiva durante el Weekend Lock | No lo permite |
| 13 | Accesibilidad | Desactivar el servicio y volver a activarlo | Incidente registrado y aviso |
| 14 | Log | Exportar el Inspector | Todas las filas con hora, mismo formato, sin intentos contados como tiempo |
| 15 | Sin regresiones | Usar Foco un día normal | El bloqueo y el DNS funcionan igual que en la versión 1.11 |
| 16 | Medición real | Comparar el tiempo de TikTok de un día en Foco y en Bienestar digital | Diferencia menor al 10 % |
| 17 | Límite de TikTok | Usar TikTok 10 min con saldo en el banco | Se bloquea hasta el día siguiente aunque quede saldo |
| 18 | Hora pico | Revisar el aviso después de 7 días con el log nuevo | Franja estricta creada con el motivo escrito; no se gastan minutos dentro de ella |
| 19 | Redirección | Quedarse sin saldo e intentar abrir Instagram | Aparece una propuesta con botón; la app no se abre sola; queda registrada |
| 20 | Tope diario | Completar retos hasta pasar los 20 min ganados | El banco deja de subir en 20 |

## 9. Orden de trabajo

Una etapa por sesión de Codex. Después de cada una: compilas tú, pruebas uno o dos días y recién ahí pasas a la siguiente. Las sesiones cortas también gastan menos tokens.

1. **Medición y log** (sección 5). Va primero porque, si los números están mal, todo lo demás se calcula mal.
2. **Categorías, Telegram y límite propio de TikTok** (sección 2).
3. **Minutos ganados** con los topes nuevos (sección 3).
4. **Weekend Lock** (sección 4).
5. **Supervisor automático, redirección e informe diario** (secciones 2 y 6).

### Prompt para la etapa 1

```
Trabaja sobre el proyecto Foco existente. No crees una app nueva.
No ejecutes Gradle, no compiles y no generes APK: yo compilo.
No elimines funciones existentes.

PASO 1 — Auditoría corta (solo lectura). Antes de cambiar nada, dime:
- Dónde se genera hoy el log de Foco Inspector y en qué se guarda
  (Room, SharedPreferences, archivo). El export JSON actual usa
  dailyPackageMillis, weeklyOcio, weeklyAlternative, weeklyChoices,
  weeklyBlocks y bankMs.
- Qué clase mide hoy el tiempo de uso y por qué puede quedarse corta.
  Dato real: en la semana del 8 al 14 de noviembre, Bienestar digital
  marcó 3 h 55 min de TikTok y Foco unos 88 minutos.
- Cómo se registran alternativa_elegida, alternativa_abierta_desde_foco
  y bloqueo_por_limite.
- Si Cognitive Lab guarda progreso, retos, niveles y misiones, y dónde.

PASO 2 — Medición:
1. El tiempo de uso por app se obtiene de UsageStatsManager
   (queryEvents con ACTIVITY_RESUMED / ACTIVITY_PAUSED), no de lo
   que observa el AccessibilityService. La accesibilidad queda solo
   para detectar la app en primer plano y bloquear.
2. Verificar y pedir el permiso PACKAGE_USAGE_STATS si falta.
3. Recalcular dailyPackageMillis y weeklyOcio desde esa fuente.
4. Consultas eficientes: solo el rango nuevo desde la última lectura,
   nada de recorrer la semana completa en cada evento.

PASO 3 — Log:
1. Nuevo formato: fecha,hora,tipo,paquete,app,segundos,veces,valor,detalle
   (hora = 0 a 23). Todas las filas con el mismo formato y comillas.
2. "segundos" es solo uso real; "veces" es solo aperturas o intentos.
   Un intento nunca suma segundos. Llenar "veces" en las filas de ocio.
3. Después de abrir una alternativa desde Foco, registrar los segundos
   de uso real que tuvo.
4. Registrar también las apps que no están en ninguna lista
   (por ejemplo IQDungeon2) como tipo "sin_clasificar".
5. Agregar los tipos: uso_productivo, intento_bloqueo_total,
   reto_completado, logro_desbloqueado, minutos_ganados,
   minutos_gastados, saldo_fin_dia, cambio_categoria,
   weekend_inicio, weekend_fin, incidente, vpn_activa,
   franja_estricta, redireccion.
   En esta etapa solo se crean los tipos y la función para registrarlos;
   conecta ya reto_completado y logro_desbloqueado a Cognitive Lab.
   El resto se conecta en etapas siguientes.
6. Un evento se guarda una sola vez. Detalle por 30 días y luego
   solo resumen diario.
7. Mantener la exportación de texto actual con el nuevo formato.

Al terminar, entrega: archivos modificados, qué quedó conectado
y qué quedó pendiente. No declares terminado algo que no lo esté.
```
