# Revisión estática de Lumosity y aplicación a Cognitive

## Resultado

Esta copia contiene evidencia concreta de manipulación de comprobaciones de firma. Debe tratarse como sospechosa. No se ha demostrado robo de datos, ransomware ni un virus específico; tampoco se puede certificar que esté libre de malware.

La captura aportada muestra tres avisos (AhnLab-V3, Trellix ENS e Ikarus), incluido ApkSignatureKiller. No se consultó un informe online, no se comprobó su fecha ni que corresponda al mismo SHA-256 de esta APK. Las detecciones de la captura no se deben tratar como un escaneo realizado aquí.

## Evidencia del archivo

- Tamaño: 174398023 bytes; 1174 entradas ZIP; dos archivos DEX y 7133 clases definidas.
- Paquete declarado: com.lumoslabs.lumosity; versión 10.19.77 (2500192); minSdk 24, targetSdk 35.
- SHA-256: `4077fc90bedd03b677c3d0ffc388f0df7ca2135266a72b77e214bf19abcedae3`.
- En classes.dex, `com.lumoslabs.lumosity.BrazeApplication` hereda de `bin.mt.signature.KillerApplication`. Es la clase indicada por el manifiesto como Application.
- En classes2.dex están `KillerApplication`, `KillerApplication$1` y los métodos `killPM`, `killOpen`, `hookApkPath`, `getApkPath`, `findField` y `getDataFile`.
- Se encontraron las cadenas SignatureKiller, assets/SignatureKiller/origin.apk y una referencia a ApkSignatureKillerEx. Esa ruta aparece como cadena; no existe un archivo con ese nombre dentro de la APK analizada.
- Incluye clases org.lsposed.hiddenapibypass. Su presencia por sí sola no identifica malware.
- apksigner verifica firmas v2 y v3. Firmante declarado: `CN="rockz5555 OU=Droid Freedom Unit O=Droid Freedom Inc L=Unknown S=Unknown C=LK"`. Huella SHA-256 del certificado: `0cfb4663831a0fb8d6973aad44e221a8ba78f7f684bd0b17d3b44bec82316484`.

Una firma criptográfica válida indica consistencia con ese firmante, no inocuidad. No se obtuvo un certificado oficial de referencia para comparar. La combinación de clase de inicio, métodos y cadenas es compatible con una copia modificada; no prueba quién la modificó ni con qué finalidad.

## Permisos declarados

- android.permission.ACCESS_NETWORK_STATE
- android.permission.INTERNET
- android.permission.RECEIVE_BOOT_COMPLETED
- android.permission.FOREGROUND_SERVICE
- android.permission.WRITE_EXTERNAL_STORAGE
- android.permission.READ_EXTERNAL_STORAGE
- android.permission.WAKE_LOCK
- android.permission.POST_NOTIFICATIONS
- com.google.android.c2dm.permission.RECEIVE
- android.permission.USE_BIOMETRIC
- android.permission.USE_FINGERPRINT
- android.permission.VIBRATE
- com.lumoslabs.lumosity.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION
- com.google.android.finsky.permission.BIND_GET_INSTALL_REFERRER_SERVICE

No aparecen permisos explícitos de SMS, contactos, ubicación, micrófono, cámara, instalación de paquetes o servicio de accesibilidad en el manifiesto examinado. Esto no descarta abuso de Internet, código nativo, WebView, cargas posteriores u otros mecanismos. READ/WRITE_EXTERNAL_STORAGE son permisos heredados; su declaración no prueba acceso real en Android moderno.

## Estructura observada

Incluye libflutter.so y libapp.so para Flutter, libunity.so y libil2cpp.so para Unity/IL2CPP, y librealm_dart.so. El manifiesto incluye integración de notificaciones Flutter, UnityPlayerActivity, Braze, Firebase, Facebook, WebView y un descargador. Las rutas declaradas incluyen home, games, stats y assessment.

Esto respalda una arquitectura con interfaz Flutter y componentes de juego Unity. La separación exacta de pantallas y juego es una inferencia estructural: no se ejecutó la aplicación ni se reconstruyó todo el código Dart o IL2CPP. Las URLs extraídas del DEX incluyen tanto endpoints como enlaces de documentación; no constituyen conexiones observadas. No se capturó tráfico.

## Decisión para Cognitive

Cognitive ya separa app, core, data y designsystem en Kotlin/Compose/Room y declara solamente notificaciones y reinicio. No requiere importar Flutter/Unity ni incorporar código, bibliotecas, imágenes o juegos de esta APK.

Principios de desarrollo aplicados con código propio: evidencia por ronda, retroalimentación útil, historial con detalle y comparación entre sesiones equivalentes. Próximas mejoras posibles: algoritmo adaptativo versionado y pruebas de migración, rutina basada en dificultades persistentes y más actividades cotidianas.

## Alcance y límites

Se verificaron integridad ZIP, manifiesto binario, clases y cadenas DEX, inventario de bibliotecas y firma. No es una auditoría completa: no hubo sandbox Android, ejecución dinámica, inspección completa de código nativo, análisis de tráfico ni antivirus actualizado. El parser local es una herramienta mínima de inspección, no sustituye una suite de análisis.

Por la manipulación encontrada, la recomendación es no instalar esta copia ni introducir credenciales en ella. Si ya está instalada, eliminar esa copia y obtener la aplicación por el canal oficial. No se envió la APK a servicios de terceros.
