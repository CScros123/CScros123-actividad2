plugins { id("com.android.library"); id("org.jetbrains.kotlin.android"); id("org.jetbrains.kotlin.kapt") }
android { namespace = "personal.qqq.braintraining.data"
compileSdk = 35
 buildToolsVersion = "35.0.0"
 defaultConfig { minSdk = 33 }
 compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
 kotlinOptions { jvmTarget = "17" }
 }
kapt { arguments { arg("room.schemaLocation", "$projectDir/schemas") } }
dependencies {
 implementation(project(":lab-core"))
 implementation("androidx.room:room-runtime:2.6.1")
 implementation("androidx.room:room-ktx:2.6.1")
 kapt("androidx.room:room-compiler:2.6.1")
 implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")
}
