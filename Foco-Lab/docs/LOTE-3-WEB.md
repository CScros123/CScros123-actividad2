# Lote 3 · Filtro web

## Qué añade
- **Sitios web y palabras clave por bloqueo** (`BlockRule.sites`, `keywords`; motor puro `WebFilter`).
  - Sitio: dominio y sus subdominios (`youtube.com` bloquea `m.youtube.com`, `music.youtube.com`; nunca `notyoutube.com`).
    Con ruta (`youtube.com/shorts`) solo bloquea esa ruta y sus subrutas, por segmentos (`/shortsfan` no).
    Se normaliza: sin `https://`, `www.`, `m.`, puerto, `?consulta` ni `#ancla`, en minúsculas.
  - Palabra clave: sin mayúsculas ni tildes y como palabra completa («tel» no salta en «hotel» ni «teléfono»).
    Se busca en la dirección y en los textos visibles de la página (máximo 300 nodos, como mucho cada 500 ms).
- **Cuándo se aplican**: si el bloqueo está activado y (no tiene horario, o la hora está dentro de su franja), sea cual sea
  el modo de horario. Los minutos permitidos y el periodo **no** cuentan: un sitio de la lista está bloqueado aunque queden
  minutos. Una **pausa** del bloqueo sí lo levanta mientras dura. Un bloqueo con solo filtro web no bloquea apps.
- **Navegadores compatibles**: Chrome, Samsung Internet, Firefox, Brave, Edge, Opera y DuckDuckGo. Foco lee su barra de
  direcciones por ID de vista (lista en `WebFilter.BROWSERS`); si no existe ese ID, usa el primer campo de texto que parezca
  una dirección.
- **Al coincidir**: pausa "Sitio bloqueado por «Regla»: dominio" y un solo gesto Atrás. La pausa sigue mientras la dirección
  coincida. No gasta el presupuesto del Foco clásico ni los minutos de los bloqueos. Registro: `RULE_LOCK`, acción `web`
  (sin la dirección).
- **Bloquear navegadores no compatibles** (`blockUnsupportedBrowsers`): con el filtro aplicándose, cualquier app que abra
  enlaces http y no esté en la lista se bloquea ("Navegador no compatible bloqueado por «Regla»"). Nunca Foco, esenciales,
  Inicio ni Ajustes. Usa una entrada `<queries>` (visibilidad de apps), no un permiso nuevo.
- **Persistencia**: `block-rules-v1.json` gana `sites`, `keywords` y `blockUnsupportedBrowsers`, opcionales. Archivos de
  los lotes 1–2 se leen igual. Tests: `WebFilterTest`.

## Límites conocidos
- Chrome puede mostrar solo el dominio en la barra (sin ruta) cuando no está enfocada: `youtube.com/shorts` puede no
  detectarse ahí; `youtube.com` sí. Las palabras clave también miran el texto de la página.
- Mientras escribes en la barra, lo escrito cuenta como dirección: escribir `reddit.com` ya puede mostrar la pausa.
- Pestañas de incógnito, apps con navegador integrado (WebView) y pestañas personalizadas de otras apps no se filtran.

## Cómo probar en el teléfono
1. Bloqueos › Nuevo bloqueo "Web", sin apps, Sitios web: `youtube.com/shorts` y `reddit.com`. Guardar.
2. En Chrome abre `reddit.com`: aparece "Sitio bloqueado por «Web»: reddit.com" y el navegador vuelve atrás. `old.reddit.com`
   también; `notreddit.com` (o cualquier otro) no.
3. Abre `m.youtube.com/shorts/…`: bloqueado. `youtube.com/watch?v=…`: libre.
4. Añade la palabra clave `apuestas` y busca "apuestas deportivas": la página de resultados se bloquea; "hotel" con `tel` no.
5. Pausa 5 min desde la pantalla de bloqueo: el sitio vuelve; al terminar la pausa, se bloquea otra vez.
6. Pon horario 22:00–07:00: fuera de la franja los sitios abren; dentro, se bloquean.
7. Activa "Bloquear navegadores no compatibles" e instala otro navegador (p. ej. Vivaldi): queda bloqueado mientras el filtro se aplica.
8. Repite 2–3 en Samsung Internet, Firefox, Brave, Edge, Opera y DuckDuckGo.

## Si un navegador no se filtra (One UI / Samsung Internet)
El ID de la barra puede cambiar entre versiones (sobre todo Samsung Internet). Para reportarlo: indica navegador, versión
(Ajustes › Apps › navegador) y versión de One UI/Android, y qué ves (nada / pausa tardía). Si puedes, con
`adb shell uiautomator dump /sdcard/u.xml && adb pull /sdcard/u.xml` y la página abierta, busca el `resource-id` del
campo de dirección en el XML y añádelo a `WebFilter.BROWSERS`.
