---
name: foco
description: Reglas y forma de trabajo del proyecto Foco (app Android es.foco.next, con Foco Core, el búho, Lab y NextDNS). Úsala siempre que se toque, se audite o se entregue código de Foco, o que el usuario hable de compilar una versión, del zip, de BORRAR.txt, del búho, de Foco Core, de cupones, del Weekend Lock o de los modos Cuidado/Normal/Enfoque.
---

# Foco: reglas del proyecto

Foco es una app Android (paquete `es.foco.next`) que protege del ocio compulsivo y acompaña con
tareas reales (Foco Core). **El búho es la voz de Foco.** El usuario habla español: responde en
español, claro y sin tecnicismos innecesarios.

## Reglas absolutas (del usuario)

1. **No ejecutes Gradle, no compiles y no generes APK: compila el usuario.** Tampoco `javac`.
   Revisa a mano (ver «Comprobar sin compilar»). Si hay analizadores de Java/Kotlin (plugins
   `jdtls-lsp`, `kotlin-lsp`) úsalos para diagnosticar; no son compilar.
2. **No elimines funciones** salvo las que el usuario pida.
3. **Usa el mismo lenguaje y sistema de interfaz** del proyecto (Java en `foco-core`, Java y
   Compose en `:app`, diálogos `AlertDialog` donde ya se usan).
4. **No agregues dependencias nuevas** sin justificarlas.
5. **No declares terminado algo que no lo esté.** Entrega siempre: archivos modificados, qué
   quedó conectado, qué quedó pendiente y riesgos.
6. **Privacidad:** el correo del usuario es privado. La clave de NextDNS solo se escribe dentro de
   Foco: nunca la pidas por el chat.
7. **Antes de proponer cambios grandes, pregunta.** Si el usuario dice «termina todo», decide con
   criterio y explica tus decisiones al final.
8. Dirección actual (v32): **simplificar, reglas coherentes y medir lo que realmente hace**. No
   añadir bloqueos ni funciones nuevas salvo que se pidan.

## Dónde está el código

El código está en **`Foco-Lab/`** de este repositorio (subido en la v32). El usuario compila su
propia copia en `~/Foco-Lab`: si él compiló cambios que no están aquí, pídele el zip antes de
empezar. Antes de cada versión guarda una copia intacta (`Foco-Lab-before-vNN` en el scratchpad)
para comparar y armar el zip acumulado, y al terminar haz commit de `Foco-Lab/` en la rama de
trabajo.

Módulos: `:foco-core` (Java puro, reglas y pruebas), `:app` (Java + Compose), `:lab` /
`:lab-core` / `:lab-data` (juegos de entrenamiento; se conservan, el usuario los mejorará).
AGP 8.7.3, Kotlin 1.9.25, Compose BOM 2024.09.03, minSdk 33, `enableEdgeToEdge` (las hojas de
Compose necesitan `imePadding()`).

## Arquitectura que hay que respetar

- **Decisión del ocio:** `FocusAccessibilityService.evaluate()`. Las excepciones salen de
  `core/LeisurePolicy` (una sola tabla, la usa también `GuardService`):
  | Pase | Salta | Sigue |
  |---|---|---|
  | Hora libre | límite de ocio, Aduana | Pomodoro, bloqueos con nombre, webs, DNS, tope, rutina |
  | Cupón normal | lo anterior + tope, rutina, bloqueos con nombre | Pomodoro, webs, DNS |
  | Cupón semana perfecta | todo | la noche |
  | Cuidado | todo | la rutina |
  La noche no la salta nada. No crees otra tabla: cambia esa y su prueba.
- **Puerta de Foco Core:** `in.gated` (`LeisurePolicy.gated`): Enfoque, y Weekend Lock en Normal y
  Enfoque (no en Cuidado). Sin hora libre ni cupón → `noBalance`.
- **Tareas:** `RoutineStore` (prefs `foco-rutina`). Una sola lista de hoy: `blockTasks(hoy)` con la
  copia fijada `fijo_`; `tasks()` la usa también. Quitar una tarea vale desde mañana.
- **Día de Foco = de 04:00 a 04:00** (`Routine.logicalDay`, `RoutineStore.today()`) para tareas,
  premios, evasiones e informes. El informe diario se escribe a las 04:05.
- **Premios:** `core/care/Rewards` (puro) y `runtime/RewardStore` (prefs `foco-premios`).
- **Medición del ocio en cuatro clases:** `ocio` (motor / sistema), `ocio_premio`, `ocio_cuidado`,
  `ocio_fuera`. Con Acceso de uso el total sale del sistema (`InspectorLog.rows`).
- **Registro:** `InspectorLog` → `LogDatabase`. Las evasiones cuentan una vez por hecho (solo si se
  insertó). No añadas más efectos de negocio al registro.
- **Avisos no críticos:** pasan por `OwlBudget.take` (8 al día). Los críticos no.
- **«¿Me aporta?» (v33):** reglas en `core/care/Awareness`, datos en `runtime/AwarenessStore`,
  ventana `ui/AwarenessActivity` (actividad propia, no capa: así se pausa TikTok y el teclado va
  bien), y la sesión la lleva `FocusAccessibilityService.awareness()`. v34: los tramos por ocio de hoy
  (15/10/5 min) están en `Awareness.dueIn(..., todayBeforeMs)`. v35: corte entre videos (espera al
  deslizar, `awScroll` + `Awareness.cutNow`). El texto de las notas nunca
  va al registro exportable; solo al informe del teléfono.
- **Horario de la noche:** siempre `AbsoluteRules.nightStart()/esNocheAbsoluta()` (leen
  preferencias vía `CareStore.install`), nunca un valor guardado aparte.
- Documentación por versión: `docs/FOCO-CUIDADO.md` (añade una sección por versión).

## Comprobar sin compilar

1. Releer cada zona editada y sus llamadas (`grep` de cada método cambiado).
2. Equilibrio de llaves y paréntesis frente a la copia anterior:
   `python3 -I .claude/skills/foco/scripts/balance.py <archivos>` (compara con el mismo archivo de
   la copia: una diferencia que ya existía no es un error nuevo).
3. Java: variables usadas en lambdas efectivamente finales; parámetros de lambda que no choquen con
   variables locales; imports. Kotlin: `when` como sentencia, tipos nulos.
4. Si cambias una firma pública, busca todos los usos (también en `src/test`).
5. Añade o actualiza pruebas en `foco-core/src/test` para la lógica pura (JUnit 4). Di siempre que
   no se ejecutaron.

## Entrega

1. Zip acumulado de todo lo cambiado desde la base que el usuario tiene compilada (hoy:
   `Foco-Lab-before-v21`), más `BORRAR.txt` con las rutas eliminadas:
   `python3 -I .claude/skills/foco/scripts/zip_acumulado.py <base> <actual> <salida.zip> [BORRAR.txt]`
2. Envía el zip y da las instrucciones, cada comando en su bloque:
   ```bash
   cp -a ~/Foco-Lab ~/Foco-Lab-copia
   ```
   ```bash
   unzip -o ~/Descargas/<zip> -d ~/Foco-Lab
   ```
   ```bash
   cd ~/Foco-Lab && xargs rm -f < BORRAR.txt && rm BORRAR.txt
   ```
   ```bash
   bash ~/Foco-Lab/scripts/compilar.sh
   ```
3. Pide las líneas `e:` / `error:` si sale «BUILD FAILED» y corrígelas sin cambiar nada más.
4. Resumen final: archivos modificados, qué quedó conectado, qué quedó pendiente, riesgos y qué
   probar en el teléfono.
