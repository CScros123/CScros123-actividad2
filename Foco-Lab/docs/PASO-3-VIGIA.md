# Paso 3 — Vigía de alertas (v1.11, sin compilar)

Ficha del paso 3 de `docs/AUDITORIA-JUEGOS-V3.md`. **Puerta técnica pendiente**: este paso se escribió sin compilar ni
ejecutar pruebas, a petición tuya. Lo primero es `./gradlew :lab-core:test :lab:testDebugUnitTest`.

## Qué cambió
- **Partida de 4 tramos seguidos** (antes 12), sin el panel entre tramos. Unos 2 minutos y medio.
- **Todos los ataques, siempre**: si un ataque no cabe en el tramo, el tramo se regenera. Antes, el 18 % de los tramos de
  los niveles 1–2 traía un solo ataque de dos.
- **Señuelo desde el nivel 2**: fallos de `admin` desde varias IP internas distintas. Ya no basta con ver "admin": hay que
  mirar la columna `src` y contar. En los niveles 2–4 son 3 líneas; desde el 5, 5.
- **El primer ataque rota**: solo la mitad de las veces es el patrón más nuevo del nivel.
- **Subir de nivel**: cuenta el porcentaje de ataques detectados en toda la partida (menos 5 puntos por falsa alarma),
  85 % en dos partidas seguidas.
- La fuerza bruta puede repartirse en 7 líneas en vez de 6 (sigue dentro de los 60 s de la regla).

## Por qué debería ser más divertido
Menos minutos y más tensión por minuto: cada tramo trae todos sus ataques, y desde el nivel 2 hay algo que parece un ataque
y no lo es.

## Identidad que no se tocó
El turno de noche, el monitor que sube, los escudos, congelar 2 s (3 por partida), el motivo de cada falsa alarma, el
latido final y el motor de reglas.

## Sin medir todavía
Cuántos tramos del nivel 2 llevan señuelo (el tramo es corto y puede no caber) y el porcentaje del bot "una línea basta".
La prueba nueva es `VigilTest.partidaOfFourTramosWithAllItsAttacks`; si falla, los números a ajustar están en
`Vigil.build` (`place(...)`).

## Puerta humana (tras 3 partidas como mínimo)
1. ¿Es más divertido que antes? (sí / igual / no)
2. ¿Jugarías otra partida ahora mismo? (sí / no)
3. ¿Qué momento te gustó más y cuál te aburrió o frustró?
4. ¿Sigue sintiéndose como "ese" juego, con su personalidad?
