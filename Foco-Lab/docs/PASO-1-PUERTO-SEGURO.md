# Paso 1 — Puerto Seguro (v1.10)

Ficha del paso 1 de `docs/AUDITORIA-JUEGOS-V3.md`. Puerta técnica superada; falta la **puerta humana**.

## Qué cambió
- **Enunciados con escenarios** (banco de 52 en `lab-core/.../Firewall.kt`): describen un síntoma o una necesidad
  ("El auditor capturó la contraseña del administrador mientras tecleaba en la consola remota del router") y nunca
  nombran el servicio ni el puerto. Hay que traducir el caso a puertos.
- **Filas con solo `:puerto`** (más grande). El servicio aparece solo en L1–2; el servicio y su uso, al aplicar.
- **Distractores con trampa**: el panel enseña primero el vecino tentador (22 frente a 23, 110 frente a 143…).
- **Orden de reglas (L8–10)**, lo que en V2 era "Firewall con bloques": una lista de 3–4 reglas PERMITIR/DENEGAR con
  primera coincidencia y denegación implícita. Hay que encender solo lo que pasa de verdad. En L8 el panel empieza con el
  error clásico (leer solo los PERMITIR); en L9–10 empieza vacío y hay excepciones y rangos.
- **Variedad**: sin escenario repetido en una partida (también en Relámpago y Práctica 3 min); mezcla de cerrar, abrir,
  auditar y ordenar.
- **Tras cada ronda**, una línea que enseña ("Telnet (23) envía todo en claro…") y un recuento "4 de 6 puertos bien".
- **Botón "Método"**: una pista que enseña a razonar ("el correo usa tres puertos…"), nunca la fila.
- **Juice**: al aplicar, un paquete de prueba cruza cada fila: verde pasa los abiertos y rojo se deshace contra los
  cerrados (`Juice.Reveal`; nada con Animaciones desactivadas).

## Por qué debería ser más divertido
Antes bastaba con buscar la frase. Ahora cada ronda es un pequeño caso que hay que deducir ("¿qué servicio delata
esto?"), y en los niveles altos se añade el reto del orden de reglas, que hasta un administrador experto falla.

## Identidad que no se tocó
El panel de firewall con un interruptor por puerto, el tráfico que fluye por las filas abiertas, el barrido ámbar
"regla aplicada", el botón "Aplicar reglas", AUDIT con estado inicial mixto y los objetivos múltiples desde L7.

## Cifras antes / después (1 000 semillas por nivel)
| Medida | Antes (v1.9) | Después (v1.10) |
|---|---|---|
| Bot "buscar el texto del enunciado" | 100 % L1–4 · 91 % L5–6 · 63 % L7–10 | 0 % en todos los niveles |
| Enunciado que contiene el texto de la fila correcta | 100 % (L1–4) | 0 % |
| Escenarios distintos en una partida de 12 | sin control | 12 de 12 (100 sesiones × 10 niveles) |
| Tipos de tarea | 2 (L1–3) · 3 (L4–10) | 2 (L1–3) · 3 (L4–7) · 4 (L8) · 3 con 50 % de orden (L9–10) |

## Puerta humana (para ti, tras 3 partidas como mínimo)
1. ¿Es más divertido que antes? (sí / igual / no)
2. ¿Jugarías otra partida ahora mismo? (sí / no)
3. ¿Qué momento te gustó más y cuál te aburrió o frustró?
4. ¿Sigue sintiéndose como "ese" juego, con su personalidad?
