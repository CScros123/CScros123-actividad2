# Etapa 0 — Correcciones pendientes (Foco Home)

Sin compilar ni ejecutar (por indicación del usuario). Pruebas escritas, no ejecutadas.

## 1. Tarjeta "Tu tiempo de ocio" de noche
- **Causa:** `MainActivity.updateTime()` mostraba `BUDGET - usado`. De noche el motor no cuenta
  uso (`in.interactive = false`, Regla 2), así que `usado = 0` y salían "5 min 0 s disponibles".
- **Arreglo:** `foco-core/.../LeisureStatus.java` (pura). La noche se decide primero:
  0 disponibles, barra llena y motivo "Ocio cerrado hasta las 7:00". Con la hora automática
  apagada, también 0, con su propio motivo. Usa `WallClock.now()` para que las pruebas fijen la hora.

## 2. Regla 4
- **Verificado:** `Bank.java` paga con `AbsoluteRules.retoMs`/`levelUpMs` (+2/+4/+5, +10 al subir
  de nivel, repetición 100/60/20 %) y `Repository.earn` aplica los topes de 20 al día y 60 en el banco, y no paga de noche.
- **Fallo encontrado y corregido:** Práctica 3 min (`ShortPractice.kt`) y Relámpago
  (`NetSecViews.kt`) creaban `Play(...)` sin `level`, y el valor por defecto es 1, así que siempre
  pagaban +2. Ahora mandan el nivel guardado. En Práctica 3 min va el nivel más bajo de las rondas
  jugadas (ante la duda, se paga menos).
- **Texto:** ahora lo genera `LeisureStatus.rulesText(...)` desde las constantes, así que no
  puede volver a quedarse desfasado. "Un bloque por hora" pasa a decir con exactitud que la
  ventana dura 60 minutos desde que se abre una app de ocio. El motor sigue igual.

## 3. "Peor horario"
- **Causa:** cada bloqueo o intento suma 1 minuto a su hora, así que con 0 minutos de ocio
  aparecía un peor horario.
- **Arreglo:** `LeisureStatus.worstHourLine(ocioMs, hour)` devuelve "Sin datos todavía" si
  no hay ocio. Se aplica en el informe diario y en el semanal.

## Pruebas: `foco-core/src/test/.../LeisureStatusTest.java`
Comprueban las 6:26, los bordes de las 21:00 y las 7:00, la hora automática apagada, el texto de la Regla 4, el peor horario y los pagos.
