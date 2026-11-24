// Fase 1: el antiguo módulo `app` de Cognitive Lab Arcade 2.1, ahora biblioteca Android.
// Sin applicationId, firma, icono, launcher ni AppTheme: los aporta :app (Foco).
plugins { id("com.android.library"); id("org.jetbrains.kotlin.android") }
android {
 namespace = "personal.qqq.braintraining"
 compileSdk = 35
 buildToolsVersion = "35.0.0"
 // Todos los recursos de :lab llevan prefijo lab_ para no chocar con los de Foco.
 resourcePrefix = "lab_"
 defaultConfig { minSdk = 33; consumerProguardFiles("proguard-rules.pro") }
 compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
 kotlinOptions { jvmTarget = "17" }
 buildFeatures { compose = true }
 composeOptions { kotlinCompilerExtensionVersion = "1.5.15" }
 testOptions { unitTests.isIncludeAndroidResources = true }
 packaging { resources.excludes += "/META-INF/{AL2.0,LGPL2.1}" }
}
dependencies {
 implementation(project(":lab-core")); implementation(project(":lab-data")); implementation(project(":lab-design"))
 implementation(platform("androidx.compose:compose-bom:2024.09.03"))
 implementation("androidx.activity:activity-compose:1.9.3")
 implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
 implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
 implementation("androidx.compose.material3:material3")
 implementation("androidx.compose.material:material-icons-extended")
 implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")
 testImplementation("junit:junit:4.13.2")
 testImplementation("org.robolectric:robolectric:4.14.1")
 testImplementation("androidx.test:core:1.6.1")
 testImplementation(platform("androidx.compose:compose-bom:2024.09.03"))
 testImplementation("androidx.compose.ui:ui-test-junit4")
 // Registra androidx.activity.ComponentActivity solo para las pruebas (VisualScenesTest).
 // En una biblioteca, debugImplementation se filtraría al APK debug de Foco con una actividad exportada.
 testImplementation("androidx.compose.ui:ui-test-manifest")
}
