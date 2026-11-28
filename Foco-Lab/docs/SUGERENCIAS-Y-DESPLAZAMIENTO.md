# Sugerencias, desplazamiento y Estados de WhatsApp (versión 1.9)

## Estados y canales de WhatsApp
- Secciones nuevas `whatsapp_status` («Estados de WhatsApp») y `whatsapp_channels` («Canales de WhatsApp») en
  `InAppFilter`, para `com.whatsapp` y `com.whatsapp.w4b`. Atajo «Bloquear Estados y canales de WhatsApp» → bloqueo
  «Sin Estados ni canales» (sin apps ni horario; con el candado no se apaga).
- WhatsApp sigue siendo **esencial**: el bloqueo normal, el DNS y el Foco clásico lo saltan. Solo el camino de
  secciones lo revisa (`InAppFilter.ESSENTIAL_WITH_SECTIONS`) y nunca durante una llamada.
- Prudente: cuenta un identificador del **visor** (`status_playback*`, `status_viewer*`, `newsletter_header`,
  `newsletter_directory`…) o la pestaña **«Novedades»/«Updates» seleccionada junto a** un identificador débil de lista
  (`status_list`, `my_status`, `progress_bar`, `newsletter_row`…). Un identificador débil solo nunca basta
  (`progress_bar` también sale en las descargas de un chat). Chats, Llamadas y Comunidades tienen tests negativos
  (`WhatsAppSectionsTest`).
- **Sin confirmar en dispositivo**: todos los identificadores de WhatsApp. Para informar de uno: con el estado (o el
  canal) abierto, `adb shell uiautomator dump /sdcard/v.xml && adb pull /sdcard/v.xml`, luego
  `grep -o 'resource-id="com.whatsapp[^"]*"' v.xml | sort -u`; repite en la lista de Chats y envía solo el id que
  aparece en el visor y no en Chats, con la versión de WhatsApp. **No envíes el volcado**: contiene nombres y mensajes.

## Desplazamiento (`UsageShift`)
Últimos 7 días frente a los 7 anteriores. Una app de ocio (vigilada o de un bloqueo) baja ≥ 30 % y ≥ 30 min y otra
(cualquiera, también WhatsApp) sube ≥ 50 % y ≥ 20 min → «Desplazamiento: bajaste TikTok (−43 %) pero Instagram subió
(+120 %)». Tarjeta al principio de Tendencia y una línea en Día.

## Sugerencias para ti (`BlockSuggestions`)
Con ≥ 14 días de datos de los últimos 28 (caché `stats_day` + nueva `stats_hour`, migración 2→3 que solo añade tablas):
- **Noche**: las 2–3 horas seguidas con más ocio (sin datos por horas: 20:00–23:00) → bloqueo total en franja cada día
  para tus 5 apps de ocio con más tiempo (por defecto TikTok, Instagram, Facebook, Call of Duty si están instaladas).
- **Día más largo** (el día de la semana con más media, si supera la media en un 20 % y 1 h): límite de 15 min ese día.
- **Estados**: WhatsApp ≥ 30 min/día de media → el atajo de Estados y canales.
«Crear bloqueo» lo crea activado (crear siempre es más estricto, vale con el candado); se edita en Bloqueos.
«Descartar» la oculta 14 días (preferencias `foco-sugerencias`). Una sugerencia desaparece cuando su bloqueo existe.
Tarjetas en Bloqueos y en Estadísticas › Tendencia.

## Opciones quitadas o fusionadas
- Bloqueos: las tarjetas «Shorts y Reels» y «TikTok» se juntan en una sola tarjeta **«Atajos»** con tres interruptores
  (Shorts y Reels, TikTok por completo, Estados y canales de WhatsApp). Mismos bloqueos y nombres: nada se borra.
- Tendencia: las tarjetas «Estas apps están creciendo» y «Estas apps van a menos» se juntan en **«Cambios por app»**
  (mismos datos: hasta 3 subidas y 3 bajadas, ≥ 5 min), debajo del desplazamiento.
