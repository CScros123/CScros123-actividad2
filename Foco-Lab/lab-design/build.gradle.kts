plugins { id("com.android.library"); id("org.jetbrains.kotlin.android") }
android { namespace = "personal.qqq.braintraining.design"
compileSdk = 35
 buildToolsVersion = "35.0.0"
 defaultConfig { minSdk = 33 }
 compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
 kotlinOptions { jvmTarget = "17" }
buildFeatures { compose = true }
 composeOptions { kotlinCompilerExtensionVersion = "1.5.15" }
 }
dependencies {
 implementation(platform("androidx.compose:compose-bom:2024.09.03"))
 api("androidx.compose.material3:material3")
 api("androidx.compose.foundation:foundation")
 api("androidx.compose.ui:ui")
}
