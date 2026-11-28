# Modo estricto

El modo estricto tiene dos niveles:
- **Sin candado** (como antes): protege solo durante un bloqueo y se puede apagar fuera de un bloqueo.
- **Reforzado, con candado de tiempo**: al activarlo eliges cuánto tiempo no podrás apagarlo. Mientras el candado dure, la protección de las pantallas propias de Foco es permanente (no solo durante bloqueos), los bloqueos solo pueden hacerse más estrictos y un respaldo sigue bloqueando aunque apagues la Accesibilidad.

## El candado de tiempo
Al pulsar "Activar modo estricto" eliges: **1 h**, **4 h**, **8 h**, **hasta mañana a las 07:00**, **hasta una fecha y hora**, o **Siempre (24 h de espera para desactivar)**.
- Con una duración: "Desactivar modo estricto" se rechaza hasta que pase, haya o no un bloqueo.
- Con "Siempre": pulsa **Solicitar desactivar**; empieza una cuenta atrás de 24 h. Al terminar puedes apagarlo. Puedes **cancelar la solicitud** (vuelve a "siempre").
- Si el modo estricto ya estaba activado sin candado, "Poner candado de tiempo" lo añade. Un candado nuevo nunca acorta uno más largo.
- **Cambiar la fecha y hora no sirve**: el tiempo del candado avanza con el reloj monótono del sistema (`elapsedRealtime`) mientras el teléfono no se reinicia. Tras un reinicio avanza con el reloj de pared, pero nunca hacia atrás: si la hora se retrasa, el candado sigue activo. (Lógica pura en `foco-core` `StrictLock`, con tests.)
- No se puede activar sin la **Accesibilidad de Foco** y el **administrador de dispositivo**. Muy recomendables: **Aparecer encima** y **Acceso de uso** (sin ellos el respaldo no puede bloquear; Foco avisa). Y **Batería sin restricciones**. La tarjeta muestra el estado de cada uno con su botón.

## Qué protege y cuándo
| Pantalla | Con candado (siempre) | Durante un bloqueo |
|---|---|---|
| Ficha de Foco (Desinstalar / Forzar detención) | ✓ | ✓ |
| Accesibilidad cuando aparece Foco | ✓ | ✓ |
| Apps de administración del dispositivo | ✓ | ✓ |
| Diálogo "¿Quieres desinstalar?" de Foco | ✓ | ✓ |
| DNS privado, Fecha y hora, VPN (con Foco) | — | ✓ |

DNS privado, fecha y hora y VPN quedan "solo durante bloqueos" para no dejarte sin Ajustes normales. Foco cierra la pantalla (Atrás + Inicio) con el aviso "Modo estricto: disponible al terminar el candado/bloqueo". Además, mientras Foco sea administrador del dispositivo, Android no permite desinstalarlo directamente (el administrador no usa ninguna política). Un "bloqueo" es: presupuesto agotado, pausa por DNS privado apagado o un bloqueo con nombre activo.

## Bloqueos: solo más estricto
Con el candado activo, el editor de bloqueos (y "Desactivar mi plan") rechaza: eliminar o desactivar bloqueos, quitar apps (en una lista blanca: permitir apps nuevas), subir el límite, añadir pausas, acortar la ventana, cambiar el periodo o el tipo, recortar horarios o días, quitar sitios, palabras o protecciones, y restaurar copias. Se permite: añadir bloqueos, añadir apps/sitios/palabras, bajar límites, ampliar horarios y renombrar. Un bloqueo desactivado se puede editar libremente. (Lógica pura en `foco-core` `RuleChange.isStricter`, con tests.) Las sesiones de foco no se pueden terminar antes de tiempo.

## El respaldo sin Accesibilidad (GuardService)
Servicio en primer plano (`specialUse`) que solo corre mientras el candado está activo. Arranca al activar el candado, al abrir Foco, al reiniciar el teléfono y cuando la Accesibilidad de Foco se desconecta.
- Con la Accesibilidad conectada, espera (una mirada cada 30 s) con la notificación "Modo estricto activo".
- Sin ella, muestra la notificación **"Foco necesita Accesibilidad para protegerte"** (con botón a Accesibilidad) y cada segundo consulta Acceso de uso (`queryEvents`, último `ACTIVITY_RESUMED`). Si la app delante está bloqueada (presupuesto clásico ya agotado, sesión de foco o bloqueo con nombre, con la misma decisión `RuleStore.decide` que usa la Accesibilidad; el tiempo delante cuenta para los límites), la tapa con una pantalla completa (`TYPE_APPLICATION_OVERLAY`, requiere "Aparecer encima") con **Ir a Inicio**.
- Límites: no puede leer textos, así que no protege Ajustes ni la ficha de Foco (por eso el administrador sigue siendo clave); el presupuesto del Foco clásico no se consume sin Accesibilidad (solo se respeta si ya estaba agotado); filtro web y compras no funcionan; reacciona con hasta ~1 s de retraso.

## Cómo salir
1. Lo normal: espera a que termine el candado (o solicita desactivar y espera 24 h), apaga el modo estricto en Foco fuera de un bloqueo.
2. Para desinstalar: después del paso 1, Ajustes › Seguridad y privacidad › Más ajustes de seguridad › Apps de administración del dispositivo › quita Foco → desinstala.
3. Último recurso (emergencia): **modo seguro** de Android (mantén pulsado Apagar › mantén pulsado Apagar otra vez › Modo seguro). En modo seguro las apps de terceros no se ejecutan: quita el administrador de Foco y desinstálalo. Reinicia para salir del modo seguro.

## Qué no puede impedir
Modo seguro, ADB (puede quitar el administrador y desinstalar), restablecimiento de fábrica, y "forzar detención" desde el panel de apps en ejecución de One UI. Tras un reinicio, adelantar el reloj de pared antes de que Foco vuelva a mirar sí acorta el candado (no hay otra referencia de tiempo sin Internet).

## Prueba en un Samsung (One UI)
- [ ] Activar sin Accesibilidad o administrador → se rechaza. Sin Aparecer encima / Acceso de uso → aviso.
- [ ] Candado 1 h, fuera de un bloqueo: Ajustes › Aplicaciones › Foco → se cierra. Accesibilidad › Apps instaladas › Foco → se cierra. Wi-Fi, DNS privado, Fecha y hora → no se cierran.
- [ ] Durante un bloqueo: DNS privado y Fecha y hora → se cierran.
- [ ] "Desactivar modo estricto" con el candado → rechazado. Retrasar la hora del teléfono → sigue activo.
- [ ] Siempre → Solicitar desactivar → cuenta atrás 24 h; cancelar funciona.
- [ ] Editor: quitar una app o subir el límite → rechazado; añadir una app o bajar el límite → se guarda.
- [ ] Apagar la Accesibilidad (vía ADB o modo seguro) con un bloqueo activo → notificación "Foco necesita Accesibilidad…" y la app bloqueada queda tapada con "Ir a Inicio".
- [ ] Reiniciar con el candado activo → el respaldo vuelve a aparecer (notificación).

Los textos cambian según la versión e idioma de One UI. Si una pantalla no se cierra, añade su título (en minúsculas) a `foco-core/src/main/java/es/foco/next/core/StrictGuard.java` (`DANGEROUS_SCREENS`, `FOCO_SCREENS` o `APP_ACTIONS`; las de red y reloj, también en `BLOCK_ONLY`; paquetes en `GUARDED_PACKAGES`) y un caso en `StrictGuardTest`.
