# Auditoría de Foco como producto (v1.11, sin compilar)

Hecha por lectura de código; **nada de esto se ha compilado ni probado** (restricción de la etapa). Antes de instalar:
`./gradlew test`.

## Revisado
Manifiesto, `Repository`, `AtomicJsonFile`, `RuleStore` (apertura y guardado), `MainActivity` completa, inicio de
`BlocksScreen`, manejo de fallos de `FocusAccessibilityService` y `GuardService`, temporizadores y consultas al sistema de
toda la app, confirmaciones de acciones destructivas.
No revisado a fondo: lógica de `FocusAccessibilityService` (432 líneas), `GuardService`, `StatsActivity`,
`RegistroActivity`, `LogDatabase`, modo estricto, DNS y todo `foco-core`.

## Problemas encontrados y cambios
| Problema | Cambio | Archivo |
|---|---|---|
| Bloqueos, función central, no estaba en la barra: solo se llegaba por una tarjeta al final de Hoy | Cuarta pestaña "Bloqueos" (icono nuevo); se quitan la tarjeta de Hoy y el botón "Volver a Hoy" | `MainActivity`, `BlocksScreen`, `tab_blocks.xml` |
| Si falta Accesibilidad, el plan está desactivado o no hay apps, el aviso estaba dentro de la quinta tarjeta | Tarjeta "Foco todavía no te protege" arriba del todo, con el botón que lo arregla | `MainActivity` |
| "Tu tiempo de ocio" (el dato principal) era la quinta tarjeta | Sube a primera posición | `MainActivity` |
| Estadísticas, registro y exportación repartidos en tres tarjetas y dos pantallas | Una tarjeta "Tu actividad"; se quita el botón duplicado de Registro en Ajustes | `MainActivity` |
| Galería de alternativas duplicada en Hoy y en Mi plan | Se queda solo en Hoy | `MainActivity` |
| "Desactivar mi plan" quitaba la protección con un toque | Pide confirmación (activar no) | `MainActivity` |
| Atrás cerraba Foco desde Mi plan o Ajustes | Atrás vuelve a Hoy; solo desde Hoy sale | `MainActivity` |
| Si el archivo del plan no se podía leer, Foco quedaba inutilizable para siempre, sin ninguna salida | Botón "Empezar con un plan nuevo": aparta el archivo (no lo borra) y arranca limpio | `MainActivity`, `Repository.quarantine` |
| Si el archivo de bloqueos no se podía leer, la lista aparecía vacía sin explicación | Aviso en la pantalla de Bloqueos | `RuleStore`, `BlocksScreen` |
| El reloj de Hoy copiaba el plan entero cada segundo | Consulta directa `Repository.enabled()` | `Repository`, `MainActivity` |
| Cada nombre de paquete compilaba su expresión regular al validar | Patrón compilado una vez | `Repository` |
| Lista de oyentes del plan sin protección entre hilos | `CopyOnWriteArrayList` | `Repository` |
| Créditos de música dentro de "Protección del plan" | Pasan a "Tus datos" | `MainActivity` |

## Funcionalidades eliminadas
Ninguna función. Solo accesos duplicados: tarjeta "Bloqueos" y tarjeta "Tus estadísticas" de Hoy, "Tus alternativas
instaladas" de Mi plan, botón de Registro en Ajustes y "Volver a Hoy" en Bloqueos.

## Lo que ya estaba bien (no se tocó)
Escritura atómica con instantáneas numeradas, puntos de control fuera del hilo principal, validación estricta al
restaurar copias, protecciones del modo estricto en plan y restauración, y confirmación en borrados.

## Pendiente
- Compilar y pasar todas las pruebas. Una prueba cambiada: `CalmVisualTest` espera ahora 4 pestañas.
- Hueco del candado (adelantar la hora y reiniciar): sigue abierto; exige cambiar 2 pruebas y poder ejecutarlas.
- Hoy sigue siendo larga (inicio, alternativas, práctica, cuaderno, actividad). Decidir si práctica y cuaderno merecen
  pestaña propia requiere verlo en el teléfono.
- Ajustes mezcla permisos, ahorro de energía, modo estricto, sonido y DNS en una sola lista larga: agrupar por secciones.
- Revisar a fondo lo no revisado (lista de arriba), sobre todo `FocusAccessibilityService`.
- Identificadores de Estados de WhatsApp y Samsung Internet, y versículos: siguen como estaban.

## Añadido después: Foco Inspector (sin compilar)
Resumen diario opcional, apagado por defecto. En Ajustes → "Foco Inspector" se elige una carpeta (por ejemplo, de Google
Drive) y Foco escribe ahí `Foco-Inspector.txt` (estado del plan y los 7 días de tiempos), como mucho cada 2 horas: al
abrir Foco, al encender o apagar la pantalla con la Accesibilidad activa y con el recordatorio diario. Foco sigue sin
permiso de internet. Código: `runtime/Inspector.java`. Una tarea programada de Claude lee ese archivo cada tarde.
