plugins { id("org.jetbrains.kotlin.jvm") }
// Fase 1: igual que :foco-core, compila a Java 17 con el JDK que ejecute Gradle (17 o superior).
// Antes: kotlin { jvmToolchain(17) }, que exigía tener un JDK 17 instalado aparte si Gradle corre con JBR 21.
java { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile>().configureEach {
 compilerOptions.jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
}
dependencies { testImplementation("junit:junit:4.13.2") }
