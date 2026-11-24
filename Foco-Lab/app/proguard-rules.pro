# All serialization is explicit; no reflective data-model keep rules required.

# Fase 9: endurecimiento de release.
# Actividades, servicios (incl. AccessibilityService) y receivers se conservan vía manifiesto (AAPT genera las reglas).
# Room (:lab-data, kapt) y Compose/AndroidX traen sus propias consumer rules; org.json no usa reflexión.
-repackageclasses ''
-allowaccessmodification
-renamesourcefileattribute SourceFile
-keepattributes SourceFile,LineNumberTable
# Quitar logs verbosos en release; se conservan Log.w y Log.e para diagnóstico.
-assumenosideeffects class android.util.Log {
    public static int v(...);
    public static int d(...);
    public static int i(...);
}
# Guardar app/build/outputs/mapping/release/mapping.txt de CADA versión publicada (necesario para retrace).
