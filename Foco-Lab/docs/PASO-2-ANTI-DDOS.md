# Paso 2 — Turno anti-DDoS (v1.11)

Ficha del paso 2 de `docs/AUDITORIA-JUEGOS-V3.md`. Puerta técnica superada; falta la **puerta humana**.

## Qué cambió
- **Partida de 5 oleadas seguidas** (antes 12 sueltas). Sin el panel "Solución paso a paso" entre oleadas: termina una y
  entra la siguiente. Dura entre 2:15 y 2:41 según el nivel (antes unos 7,5 min).
- **El jefe sella una regla nueva en la oleada 3**, como prometía la ayuda. Las oleadas 1 y 2 usan las reglas que ya
  conoces; desde la 3 se suma la nueva. La oleada 5 es el **pico**: 10 BPM más rápida.
- **Cada pareja de niveles añade una regla**: 1–2 `:23` y luego SYN · 3–4 suma "más de 3 por IP" · 5–6 suma el despliegue
  de 8080 · 7–8 suma la excepción del monitor · 9–10 suma la amplificación DNS (y el segundo carril). Los niveles 7–10
  terminan con las mismas reglas que antes; los niveles 1–6 terminan con una más.
- **Combo que cruza oleadas**: ×2 a los 5 aciertos seguidos, ×3 a los 10 y ×4 a los 15. Un fallo lo rompe. El ×4 ya se
  puede alcanzar (antes pedía 24 seguidos en oleadas de 20) y enciende el borde de la cinta.
- **Puntos a la vista**: marcador en la cabecera durante la partida y, al final, puntos, combo máximo, oleadas y tu récord.
  El aro del resultado muestra los paquetes bien de 100 (120 en los niveles 7–8 y 140 en 9–10).
- **Pulso audible**: un clic suave en cada tiempo y un acento cuando entra un paquete. Sale del mismo reloj que mueve la
  cinta. Respeta "sin sonido" y el volumen de efectos; no vibra.
- **Los errores ya no paran la cinta** (solo en el nivel 1, que es donde se aprende): la tarjeta queda marcada, la regla se
  ilumina en la cabecera y la lección sale en el pie.
- **Confeti de oleada perfecta** a todo el ancho de la cinta (salía en una franja de 1 px).
- **Oleadas distintas entre sí**: cada una reparte de otra forma cuántos paquetes trae cada regla, y el prefijo de la IP ya
  no delata nada (antes los hostiles venían siempre de los mismos 4 rangos).
- **Subir de nivel**: cuenta el 85 % de los **paquetes** de la partida, dos partidas seguidas. Ya no depende de pasar
  "11 de 12 oleadas".

## Formato de partida por juego (`GameFormat`)
Nuevo en `lab-core/.../Games.kt`: cada juego declara cuántas unidades tiene su partida, cómo se llaman, si van encadenadas
y qué cuenta para subir de nivel. Anti-DDoS es el primero en usarlo; los otros diez juegos siguen con 12 rondas hasta que
llegue su paso. Los resultados antiguos de 12 oleadas siguen leyéndose y las copias de seguridad se restauran igual.

## Por qué debería ser más divertido
Antes eran 12 oleadas idénticas con una pausa de lectura entre cada una. Ahora la partida tiene arco: calientas, llega una
regla que cambia qué hay que tocar, y cierras con un pico más rápido mientras proteges un combo que has ido subiendo desde
la primera oleada. El pulso da el ritmo que el juego decía tener, y un fallo ya no corta la cinta.

## Identidad que no se tocó
La cinta que sube al ritmo, tocar para bloquear, deslizar hacia arriba para la lista negra, el parte del jefe con su sello,
la barra de carga del servidor y su caída, la prioridad de reglas (excepción > despliegue > bloqueos), el reloj sin deriva,
las 12 ranuras fijas de tarjetas y las cartas del álbum.

## Cifras antes / después
| Medida | Antes (v1.10) | Después (v1.11) |
|---|---|---|
| Duración de una partida | ≈ 7,5 min (nivel 1) | 2:41 (N1–2) · 2:27 (N3–4) · 2:15 (N5–6) · 2:19 (N7–8) · 2:22 (N9–10) |
| Regla nueva dentro de la partida | nunca (12 oleadas iguales) | oleada 3, en los 10 niveles (100 semillas × 5 oleadas por nivel) |
| Combo máximo alcanzable | ×3 (el ×4 pedía 24 de 20) | ×4, desde la primera oleada |
| Pausas obligatorias por partida | 12 paneles + 1 s por error | 0 (1 s por error solo en el nivel 1) |
| Partidas con al menos 3 repartos distintos entre sus 5 oleadas | no aplica (reparto fijo por nivel) | ≥ 90 % de 1 000 partidas |
| Diferencia entre rangos de IP en % de maliciosos | 100 % (4 rangos solo hostiles) | ≤ 8 puntos |
| Jugador al 96 % por paquete: sube en 3 partidas | no medido | ≥ 85 % de 200 simulaciones (niveles 1, 4, 7 y 9) |
| Jugador al 78 % por paquete: sube en 6 partidas | no medido | ≤ 5 % de 200 simulaciones |

## Pruebas
`lab-core`: 130 (antes 122) · `lab`: 80 (antes 69). Nuevas: `DdosPartidaTest` (sello en la oleada 3, combo alcanzable,
pulso sin deriva, duración, nivel por paquetes, curva, variedad, mismo guion = mismo registro), `DdosFormatTest` (guardado
y copia de seguridad), 6 pruebas de tablero en `Fase3BoardsTest`, la partida completa en `LiteGamesTest` y el confeti en
`JuiceTest`. Se ajustaron 5 líneas de `DdosTest` y 1 de `Fase3BoardsTest` porque fijaban la tabla de niveles antigua.

## Lo que las pruebas no pueden decir
- Si el clic del pulso acompaña o cansa. Si molesta, se baja o se quita en una línea (`GameTones.baseVolume`).
- Si el ×4 llega demasiado pronto (15 aciertos seguidos) y deja de emocionar.
- Si los niveles 1–6, que ahora terminan con una regla más, se sienten justos en tu nivel actual.
- Si 900 ms entre oleadas es poco para respirar.

## Puerta humana (para ti, tras 3 partidas como mínimo)
1. ¿Es más divertido que antes? (sí / igual / no)
2. ¿Jugarías otra partida ahora mismo? (sí / no)
3. ¿Qué momento te gustó más y cuál te aburrió o frustró?
4. ¿Sigue sintiéndose como "ese" juego, con su personalidad?
