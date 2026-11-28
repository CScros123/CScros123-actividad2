# Compilar y firmar

Instala Android Studio, JDK 17, SDK Platform 35 y Build Tools 35.0.0. Abre Cognitive y sincroniza Gradle. La primera descarga de herramientas/dependencias requiere Internet; usar la aplicación no.

Versiones fijadas: Gradle 8.9, AGP 8.7.3, Kotlin 1.9.25, Compose Compiler 1.5.15, Compose BOM 2024.09.03 y Room 2.6.1. Mínimo API 33, objetivo 35. Android Studio genera tu local.properties; no se distribuyen rutas privadas del entorno.

```bash
./gradlew :core:test :app:testDebugUnitTest :app:lintRelease :app:assembleRelease
./gradlew :app:assembleDebug
```

En Windows usa `gradlew.bat`. Debug produce `app/build/outputs/apk/debug/app-debug.apk` con firma automática y el identificador `personal.qqq.braintraining.preview`, para instalarlo junto a tu app actual. Release produce `app/build/outputs/apk/release/app-release-unsigned.apk`; no se instala hasta firmarlo.

## Firma personal

En Android Studio: **Build → Generate Signed Bundle / APK → APK → Create new**. Crea la clave fuera del proyecto, alias `qqq-braintraining`, contraseña fuerte y validez de al menos 25 años. Guarda dos copias seguras y no compartas tu clave privada.

Alternativa desde una carpeta privada:

```bash
keytool -genkeypair -v -keystore qqq-braintraining-release.jks -alias qqq-braintraining -keyalg RSA -keysize 4096 -validity 10000
```

Usa esa clave en el asistente y selecciona release para una instalación nueva. Para actualizar una instalación existente debes usar su clave original. Esta entrega no incluye claves privadas ni credenciales. El APK de prueba se firma automáticamente con una clave debug del entorno y tiene un identificador separado.

Con Build Tools y platform-tools en PATH:

```bash
apksigner verify --verbose --print-certs Cognitive-1.5.0-release.apk
adb install -r Cognitive-1.5.0-release.apk
```

Para actualizar conserva applicationId `personal.qqq.braintraining` y la misma clave; aumenta versionCode. El APK de prueba permite revisar esta entrega sin reemplazar la instalación existente. No desinstales tu app anterior para probarlo; usa exportar/restaurar si quieres copiar el historial.

Referencia: https://developer.android.com/studio/publish/app-signing
