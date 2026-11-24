# Secciones dentro de apps (versión 1.2)

Un bloqueo puede marcar **secciones** en lugar de (o además de) apps: Shorts de YouTube, Reels de Instagram,
Reels y Watch de Facebook, Spotlight de Snapchat y, de forma experimental, Comentarios de YouTube. La app sigue
abriéndose; Foco solo sale de la sección. TikTok no está: toda la app es el feed, usa un bloqueo normal de apps.

- **Cuándo se aplica**: como el filtro web. Bloqueo activado y (sin horario o dentro de su franja). Una pausa lo
  levanta. Los minutos permitidos no cuentan ni se gastan.
- **Qué hace**: al detectar la sección pulsa Atrás (una vez por entrada). Si a los 1,5 s sigue ahí, repite hasta 3
  veces; después muestra la pausa «Regla»: Shorts de YouTube bloqueados con el botón **Volver**. Aviso breve:
  «Shorts bloqueados por «Regla»». El registro apunta `RULE_LOCK` con `section:<id>`.
- **Interruptor rápido** en Bloqueos: «Bloquear Shorts y Reels» crea o completa el bloqueo «Sin Shorts ni Reels»
  (youtube_shorts, instagram_reels, facebook_reels; sin apps ni horario). Con el candado del modo estricto no se
  puede apagar, y quitar secciones de cualquier bloqueo cuenta como aflojarlo.
- **Privacidad**: solo en esas cuatro apps y solo con un bloqueo de secciones activo, lee los identificadores de vista
  y las descripciones de hasta 400 elementos visibles. No guarda nada.

## Cómo se detecta (prudente a propósito)

La lógica está en `foco-core/.../InAppFilter.java` (pura, con tests en `InAppFilterTest`). Una sección cuenta solo si
aparece un identificador del **reproductor o visor** (por ejemplo `reel_recycler`, `clips_viewer_view_pager`) o si la
**pestaña** de la sección está **seleccionada**. La pestaña «Shorts» del inicio de YouTube o la estantería de Shorts
no bastan por sí solas.

## Si una actualización rompe la detección: cómo informar de un identificador nuevo

Las apps cambian sus identificadores con frecuencia. Con el teléfono conectado por USB (depuración activada):

```sh
# 1. Abre la sección (por ejemplo un Short) y vuelca la pantalla
adb shell uiautomator dump /sdcard/ventana.xml
adb pull /sdcard/ventana.xml
# 2. Busca identificadores y descripciones de esa app
grep -o 'resource-id="[^"]*"' ventana.xml | sort -u
grep -o 'content-desc="[^"]*"' ventana.xml | sort -u
```

Repite el volcado en una pantalla normal de la app (inicio) y quédate con un `resource-id` que **solo** aparezca
en la sección. Indica la app, su versión (Ajustes > Apps > la app) y ese identificador. Para añadirlo, súmalo a la
lista `viewIds` de la sección en `InAppFilter.CATALOG` con un test positivo y otro negativo (pantalla de inicio).
No incluyas en el informe textos personales del volcado (nombres, mensajes).

Identificadores aún sin confirmar en dispositivo: `reel_watch_fragment_root`, `shorts_container`,
`clips_viewer_container`, `engagement_panel` (comentarios) y las etiquetas de pestaña de Facebook y Snapchat.

**1.9**: Estados y canales de WhatsApp (`whatsapp_status`, `whatsapp_channels`) y la tarjeta «Atajos», en
[SUGERENCIAS-Y-DESPLAZAMIENTO.md](SUGERENCIAS-Y-DESPLAZAMIENTO.md). WhatsApp es la única app esencial con secciones.
