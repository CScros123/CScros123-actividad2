# Pausa viva

## Qué cambió
- **La escena ya no se congela.** Antes, `PauseVideoView.animationAllowed()` apagaba el video con el ahorro de batería del sistema, el botón «Ahorro de energía» de la pausa (que se queda guardado), las animaciones del sistema en 0 o «Reducir movimiento», y quedaba solo la imagen fija. Además, hojas y órbita avanzaban *solo* con los fotogramas del video, así que todo se quedaba quieto. Un fallo del decodificador (la app bloqueada suele tenerlo ocupado) también dejaba la escena quieta para siempre.
- **Modo ligero** (`SanctuaryLight`): cuando no hay video, la imagen hace un Ken Burns muy lento (1.00→1.06, 20 s de ida y 20 s de vuelta), un halo «respira» detrás de la cabeza y el corazón (opacidad 0.15↔0.35, 6 s), unos rayos translúcidos giran (una vuelta cada 90 s) y aparecen destellos de vez en cuando. Con video, se dibuja solo la capa de luz encima.
- **Un solo reloj** en `PauseVideoView` (`postOnAnimationDelayed`): 30 fps con video y 20 fps en modo ligero. Mueve la luz, las hojas y la órbita.
- **Hojas** (`FloatingLeaves`): 3 capas de profundidad (tamaño, velocidad, opacidad y un halo suave en las lejanas), balanceo con fase propia por hoja, giro y volteo 3D (`scaleX = cos`), ráfagas cada 8–15 s que empujan a todas las hojas y vuelven a aparecer fuera de la pantalla. Como máximo 18 hojas (10/14/18 según «Intensidad del viento»). Siguen sin tapar textos ni botones.
- **Frases de apoyo** (`foco-core` `PauseMessages`, tarjeta `PauseMessageCard`): categorías Ánimo, Aprender, Cuerpo y Fe (versículos de la Reina-Valera 1909 con la ortografía actualizada y reflexiones originales). Cambian con un fundido cada 9 s y al tocarlas. Las categorías se eligen en Ajustes › Ambiente visual › «Frases en la pausa» (todas activadas por defecto).
- El video se reintenta solo dos veces (cada 3 s) si falla. Si toca el texto de estado, también se reintenta.

## Batería
| Situación | Escena |
|---|---|
| Pausa visible, pantalla encendida y sin ahorro de energía | Video, luz, hojas y órbita (≤ 30 fps) |
| Ahorro de batería (sistema o Foco), animaciones del sistema en 0 o error de video | Modo ligero sin decodificador (≤ 20 fps) |
| «Reducir movimiento» | Imagen fija con halo estático; las frases cambian sin fundido |
| Pausa oculta, pantalla apagada o ventana retirada | Nada corre: sin reloj, sin video y sin rotación de frases |

## Cómo añadir frases
Edite el arreglo de la categoría en `foco-core/src/main/java/es/foco/next/core/PauseMessages.java`. Las frases deben ser breves (≤ 140 caracteres), no pueden repetirse entre categorías y deben estar escritas en español neutro. `PauseMessagesTest` comprueba todo esto. Para añadir una categoría, añádala al `enum` y agregue su clave a `PauseMessageCard.KEYS` y a `PauseMessageCard.LABELS`, en el mismo orden.
