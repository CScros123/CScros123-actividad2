# Foco 0.59 (Fase 1: Foco + Lab en un solo proyecto)

> **Para hacer cambios:** [docs/MAPA-DEL-CODIGO.md](docs/MAPA-DEL-CODIGO.md) dice dónde está cada cosa y cómo probarla. Para compilar: [COMPILAR-EN-TU-PC.md](COMPILAR-EN-TU-PC.md).

Aplicación Android nativa de bienestar digital. Paquete `es.foco.next`, versión 59 (`0.59-fase1`). Java 17 en `:app` y `:foco-core`; Kotlin 1.9.25 + Compose en los módulos de Lab. Android 13 o posterior (API 33), SDK de compilación y destino 35.

> **Fase 1 del plan `docs/PLAN-FOCO-LAB.md`:** el proyecto incluye Cognitive Lab Arcade 2.1 como bibliotecas (`:lab-core`, `:lab-data`, `:lab-design`, `:lab`). La app se comporta igual que Foco 0.58: `LabActivity` está registrada pero no hay ningún acceso a ella todavía (llega en la Fase 2). Detalles, cambios y comandos de verificación en `docs/FASE-1.md`.

> **Fases 2 y 3:** botón **Practicar 3 min** en la pausa y en "Hoy" (abre Lab en una práctica corta de Redes y seguridad: hasta 6 retos o 3 minutos; no da minutos de ocio) y **vigilancia del DNS privado** (CleanBrowsing Family por defecto, AdGuard Family como alternativa): si el DNS se apaga o cambia, tras 60 s Foco pausa las apps de ocio y los navegadores conocidos hasta reactivarlo. Detalles, límites y pruebas en `docs/FASE-2-3.md`.
> **Fase 4 (permisos y registro de bloqueos):** registro propio `foco-log.db` (bloqueos, intentos, alternativas desde la pausa, ir a Inicio; 90 días), pantalla **Registro** con exportación CSV de 30 o 90 días, Acceso de uso bajo demanda y guía de batería para One UI. Detalles en `docs/FASE-4.md`.

## Funciones

- Presupuesto compartido de **5 minutos de ocio por ventana de 60 minutos**. La ventana comienza al abrir una app observada. Al agotar el presupuesto se bloquean esas apps hasta el final de la ventana, sin ampliaciones dentro de Foco.
- Llamadas, Inicio, Ajustes, WhatsApp y bancos reconocidos quedan disponibles. Se pueden elegir otras apps esenciales.
- Alternativas instaladas de lectura, educación y organización. La pausa muestra sus iconos reales en una órbita: seleccionar, girar con gestos, paginar y abrir. Las redes sociales no se recomiendan como libros.
- Interfaz de día y noche según hora local, terraza, vegetación y hojas flotantes delante del panel, con máscaras para no cubrir textos ni controles.
- Pausa con el vídeo limpio proporcionado por el usuario, local y en bucle. Los iconos interactivos se dibujan encima y se sincronizan con sus fotogramas.
- Música local: Morning Routine (Ghostrifter Official) y Colorful Flowers (Tokyo Music Walker). Durante el bloqueo solicita foco de audio temporal exclusivo, conservando el volumen del teléfono. La respuesta de otras apps depende de Android y de esas apps.
- Botón de ahorro y reducción de movimiento. Los efectos se detienen al salir, apagar la pantalla o activar las preferencias correspondientes. La música no se mantiene al apagar la pantalla.
- Apuntes diarios, historial y exportación TXT; recordatorio diario opcional; exportación CSV de siete días y copia JSON del plan y tiempos. Las notas se exportan por separado.
- Práctica corta desde la pausa y desde "Hoy": Redes y seguridad en el nivel guardado, hasta 6 retos o 3 minutos activos. Practicar no suma minutos de ocio ni cambia el presupuesto o la ventana.
- Filtro DNS del teléfono con el DNS privado de Android (CleanBrowsing Family por defecto; AdGuard Family seleccionable en Ajustes). Foco no filtra nada por sí mismo: solo comprueba con `LinkProperties` que el DNS privado está activo con el proveedor elegido. Si no lo está durante más de 60 s (y hay red), pausa las apps de ocio y Chrome, Samsung Internet, Firefox, Brave, Edge, Opera y DuckDuckGo con un botón "Abrir DNS privado". Ajustes, Teléfono, WhatsApp, bancos, Inicio y Foco nunca se pausan. La vigilancia se activa sola la primera vez que Foco ve el proveedor correcto, o desde Ajustes.
- Registro de bloqueos (Fase 4): por día, bloqueos e intentos por app, con hora; contraste opcional con el historial de uso de Android; exportación CSV de 30 o 90 días y botón para borrarlo. Es de mejor esfuerzo: si falla, el bloqueo sigue.
- Sin cuenta, telemetría ni servicio VPN.
- **Foco Home** (inicio opcional dentro de Foco): paisaje según el estado (Productivo, Tranquilo, Distraído, Noche, Despertar), dock sin ocio, cajón por secciones con buscador, reto rápido, banco y próximo paso, panel de sistema y mensajes de acompañamiento. Ver `docs/FOCO-HOME.md`.
- **Privacidad de Foco Home:** en Ajustes (`com.android.settings`), Accesibilidad lee la clase de la ventana y hasta 200 textos para saber qué pantalla es (Wi-Fi, DNS privado, Info de Foco…); solo registra el tipo de pantalla, nunca lo leído. El DNS de respaldo no abre conexiones.

## Arquitectura

- `foco-core/src/main/java` (módulo `:foco-core`, antes `:core`): reglas puras Java, presupuesto, ventanas, plazos y clasificación explícita de alternativas.
- `app/src/main/java/es/foco/next/data`: persistencia del plan, tiempos y apuntes; migración de versiones anteriores.
- `app/src/main/java/es/foco/next/runtime`: servicio de Accesibilidad, exclusiones bancarias, llamadas, recordatorios y audio.
- `app/src/main/java/es/foco/next/log`: registro de bloqueos (`foco-log.db`, hilo propio, poda de 90 días, contraste con UsageStats, CSV). API para otras partes: `BlockLog.record(context, BlockEvent.Type.X, paquete, acción)`.
- `app/src/main/java/es/foco/next/ui`: actividad principal, pausa, órbita, paisaje, hojas y reproductor del vídeo.
- `app/src/main/res`: recursos visuales y archivos multimedia locales.
- `foco-core/src/test` y `app/src/test`: pruebas JUnit y Robolectric.
- `lab-core`, `lab-data`, `lab-design`, `lab`: Cognitive Lab Arcade 2.1 (reglas Kotlin, Room, tema Compose y pantallas + `LabActivity`). Documentación original en `docs/lab/`.
- `verification/release-058`: resultados, informes y capturas de prueba de la entrega final.

Accesibilidad observa cambios de ventana para identificar la app activa. No lee mensajes ni vídeos. **Filtro web** (Lote 3): si un bloqueo tiene sitios web o palabras clave, lee la barra de direcciones de los navegadores compatibles (Chrome, Samsung Internet, Firefox, Brave, Edge, Opera, DuckDuckGo) y, solo si hay palabras clave, hasta 300 textos visibles de la página en esos navegadores; no guarda nada de lo que lee (el registro apunta hora, navegador y «web», sin la dirección). Ver `docs/LOTE-3-WEB.md`. **Secciones dentro de apps**: en YouTube, Instagram, Facebook y Snapchat, solo cuando un bloqueo de secciones está activo, lee los identificadores de vista y las descripciones de los elementos en pantalla (hasta 400) para detectar Shorts, Reels o Spotlight; no guarda nada (el registro apunta hora, app y `section:<id>`). La app nunca se bloquea entera. Ver `docs/SECCIONES.md`. Otra excepción: con el **Modo estricto** activado y durante un bloqueo, lee los textos de pantalla solo dentro de Ajustes y del instalador de apps para cerrar las pantallas que desinstalarían o desactivarían Foco; no guarda nada (ver `docs/MODO-ESTRICTO.md`). El motor usa intervalos observados; no calcula productividad ni clasifica vídeos virales individualmente. Los plazos usan reloj monotónico dentro del mismo arranque y respaldo de hora civil después de reiniciar.

## Compilar en Android Studio

1. Instalar Android Studio con JDK 17, Android SDK 35 y Build Tools 35.0.0.
2. Abrir esta carpeta como proyecto Gradle. El wrapper usa Gradle 8.9.
3. Configurar la ruta del SDK desde Android Studio o crear un `local.properties` propio con `sdk.dir=/ruta/al/android-sdk`.
4. Sincronizar dependencias; la primera compilación necesita Internet para descargarlas.

```sh
./gradlew :foco-core:test :lab-core:test :lab:testDebugUnitTest :app:testDebugUnitTest :app:lintDebug :app:assembleDebug
./gradlew :app:assembleRelease
```

En Windows, usar `gradlew.bat`. La APK debug aparece en `app/build/outputs/apk/debug/`. Sin llave configurada, release (y perf) se firman con la llave de depuración.

## Llave de firma (v47)

Android solo instala una actualización encima (sin borrar datos) si va firmada con **la misma llave**. Si la pierdes o la cambias, hay que desinstalar y se pierden los datos de Foco. Por eso hay una sola llave estable, `foco`, para debug, release y perf.

1. Escribe en `local.properties` (en la raíz del proyecto; **no se sube a git**, está en `.gitignore`):

   ```properties
   foco.storeFile=~/claves/foco-release.jks
   foco.keyAlias=foco
   foco.storePassword=TU_CONTRASEÑA
   foco.keyPassword=TU_CONTRASEÑA
   ```

   Nunca pongas la contraseña en el código, en un commit ni en un chat. Si no quieres tenerla en un archivo, deja solo `foco.storeFile` y `foco.keyAlias` y exporta `FOCO_STORE_PASSWORD` en la terminal antes de compilar.
2. Sin `local.properties` valen las variables de siempre (`FOCO_KEYSTORE`, `FOCO_STORE_PASSWORD`, `FOCO_KEY_ALIAS`, `FOCO_KEY_PASSWORD`). Sin ninguna, se usa la llave de depuración.

**Respaldar la llave.** Copia `foco-release.jks` a dos sitios fuera del PC (un USB y tu nube personal) y guarda la contraseña en un gestor de contraseñas. Sin la llave **y** su contraseña no se puede actualizar Foco sin desinstalar.

```sh
cp ~/claves/foco-release.jks /media/TU_USB/
```

**Huella SHA-1** (la pide Google Cloud para la copia en Drive). La contraseña se escribe en tu terminal, no se muestra:

```sh
keytool -list -v -keystore ~/claves/foco-release.jks -alias foco | grep SHA1
```

## Copia nocturna en Google Drive y NextDNS (v47)

Se configura una sola vez:

**Google Cloud** (para el permiso de Drive; Foco no lleva claves de Google):

1. En console.cloud.google.com crea un proyecto (p. ej. «Foco personal»).
2. «APIs y servicios › Biblioteca»: activa **Google Drive API** y **Google Sheets API**.
3. «Pantalla de consentimiento de OAuth» (Google Auth Platform): tipo **Externo**, nombre «Foco», tu correo como soporte. En «Acceso a datos» añade el permiso `.../auth/drive.file`. En «Público», deja la app en **Prueba** y añade tu cuenta de Google como **usuario de prueba**.
4. «Clientes › Crear cliente › Android»: paquete `es.foco.next` y la huella **SHA-1** de tu llave (arriba). Si también instalas builds debug con otra llave, crea otro cliente con esa SHA-1.
5. En Foco: Ajustes › «Copia nocturna en Google Drive» › **Conectar Google Drive**.

Con la app en «Prueba», Google puede pedir que vuelvas a conectar cada 7 días; Foco avisa con «Vuelve a conectar Drive…». Para evitarlo, publica la app («En producción»): con `drive.file` no hace falta verificación de Google para uso personal, aunque verás un aviso de «app no verificada» al conectar.

**NextDNS:** Ajustes › «NextDNS: control por modo» › escribe la **clave de API** (my.nextdns.io › Cuenta › API) y el **ID de perfil** (my.nextdns.io › Configuración) › **Probar conexión**. La clave se guarda cifrada con el Android Keystore y no se vuelve a mostrar.

## Firma y actualizaciones

El proyecto admite estas variables de entorno para firmar release:

- `FOCO_KEYSTORE`: ruta a tu almacén de claves.
- `FOCO_STORE_PASSWORD`: contraseña del almacén.
- `FOCO_KEY_ALIAS`: alias; por defecto `foco`.
- `FOCO_KEY_PASSWORD`: contraseña de la clave; por defecto usa la del almacén.

No guardar contraseñas en Git ni en archivos públicos. **Las claves usadas en las APK entregadas no se incluyen.** Una APK que firmes con una clave distinta no puede actualizar directamente una instalación anterior firmada con la clave de este entorno. Antes de desinstalar, exporta el plan y tiempos, y los apuntes TXT por separado. Conserva tu propia clave para futuras actualizaciones.

## Uso y permisos

Instalar y activar el servicio Foco desde Accesibilidad. Elegir apps de ocio, una intención y una alternativa en Mi plan. Permitir notificaciones si se quieren recordatorios y controles visibles de música. El audio usa el servicio foreground de reproducción ya declarado.

Permisos declarados: estado de red, notificaciones, recepción de reinicio, servicio foreground de reproducción multimedia, vibración (juegos de Lab), **Acceso de uso** (`PACKAGE_USAGE_STATS`) y **exclusión de optimización de batería** (`REQUEST_IGNORE_BATTERY_OPTIMIZATIONS`). No se solicita Internet, micrófono, ubicación, cámara, contactos, SMS, escucha de notificaciones, VPN ni cambio del volumen global. El **administrador del dispositivo** es opcional (Modo estricto, sin ninguna política: solo impide desinstalar Foco directamente).

**Modo estricto reforzado** (opcional, ver [docs/MODO-ESTRICTO.md](docs/MODO-ESTRICTO.md)): **Aparecer encima** (`SYSTEM_ALERT_WINDOW`, lo concede el usuario en Ajustes) y **servicio en primer plano de uso especial** (`FOREGROUND_SERVICE_SPECIAL_USE`, subtipo "Respaldo del bloqueo cuando Accesibilidad está desactivada"). Solo se usan mientras el candado de tiempo del modo estricto está activo: si la Accesibilidad se desactiva, el respaldo `GuardService` consulta Acceso de uso cada segundo y tapa con una pantalla completa las apps bloqueadas.

- **Acceso de uso** lo concede el usuario en Ajustes de Android (en Foco: Ajustes › Permisos de estabilidad › Abrir Acceso de uso, o desde la pantalla Registro). Solo sirve para contrastar, en el Registro, el tiempo que midió Accesibilidad con el historial de uso de Android (útil si el servicio estuvo dormido). Foco lo consulta al abrir el Registro y una vez al día, sin sondeo periódico. El bloqueo sigue usando Accesibilidad y no depende de este permiso.
- **Batería:** Foco pide quedar fuera de la optimización de batería para que One UI no duerma el servicio. En Samsung, además: Ajustes › Aplicaciones › Foco › Batería › **Sin restricciones**, y quitar Foco de "Aplicaciones en suspensión" o "en suspensión profunda" (Ajustes › Mantenimiento del dispositivo › Batería › Límites de uso en segundo plano).

Límites del DNS: no filtra el contenido dentro de apps ni las redes sociales abiertas en el navegador; Chrome puede usar su propio "DNS seguro" (el asistente de Ajustes pide dejarlo en el del sistema); una VPN activa cuenta como DNS apagado; sin red no se bloquea nada. Android permite apagar el DNS privado: Foco lo detecta y pausa ocio y navegadores, no lo impide.

Android conserva el control de Accesibilidad, DNS, Ajustes y desinstalación. La protección del plan dentro de Foco no impide que su propietario revoque permisos o desinstale la app.

## Datos y exportación

Los datos se almacenan localmente en el espacio privado de Android. El plan y tiempos usan `focus-lite-v1.json`; los apuntes y preferencias se almacenan aparte. No editar el archivo de estado manualmente durante una sesión. La migración conserva el archivo antiguo si existe.

CSV semanal: siete fechas, tiempos observados, aperturas de alternativas y eventos de presupuesto. No contiene mensajes ni contenido de otras apps.

Registro de bloqueos: base aparte `foco-log.db` (no toca `focus-lite-v1.json`). Tabla `block_event` (hora, zona horaria, tipo, app, presupuesto usado, ventana restante, duración del bloqueo, acción) y tabla `usage_day` (día, app, ms observados por Accesibilidad, ms según Android). Se conservan 90 días. Su CSV (30 o 90 días) tiene una fila por evento y una por app y día del contraste. Se puede borrar desde la pantalla Registro. Las cifras son observadas: no miden productividad ni prometen ahorro. La copia del plan no incluye los apuntes: usa también su exportación TXT. Las preferencias visuales, de música y recordatorio tampoco deben asumirse incluidas en la copia del plan.

## Verificación y límites

Entrega 0.58: **89 pruebas aprobadas (32 core y 57 Android), cero errores lint**, firma y archivo de vídeo comprobados dentro del APK. Las capturas Robolectric no prueban vídeo o interacción en hardware real.

Falta medir batería y fluidez y comprobar la respuesta al foco de audio de cada app en un Samsung real. No se promete silenciar apps que ignoren el foco de audio ni un porcentaje de ahorro energético. Suspensiones largas o cierres abruptos pueden perder parte del último intervalo observado; se evita inventar tiempo de uso. Con Acceso de uso concedido, el Registro muestra al lado la cifra de Android para comparar (Android cuenta también el tiempo en que la pausa de Foco cubre una app).

## Recursos e historial

El vídeo y las canciones fueron proporcionados por el usuario; los paisajes se generaron durante el proyecto. Los recursos se incluyen para reproducir la compilación. Antes de redistribuirlos, comprueba las licencias y atribuciones de sus autores. No se establece una licencia de código abierto por incluir este README.

`docs/historial/` guarda las notas de versión 0.51 a 0.58 (`RELEASE-0.53.md` a `RELEASE-0.58.md`, `README-HISTORIAL.md`, `README-0.51.md`, `README-0.52.md`) y `verification-notes.txt`. La versión final no contiene el antiguo sistema de arañas ni la VPN de la rama original.
