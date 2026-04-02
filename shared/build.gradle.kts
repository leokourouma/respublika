// shared/build.gradle.kts
plugins {
    kotlin("multiplatform")
    kotlin("plugin.serialization")
}

kotlin {
    jvm() 
    sourceSets {
        commonMain.dependencies {
            implementation(libs.kotlin.serialization)
            implementation("org.jetbrains.exposed:exposed-kotlin-datetime:0.56.0")
            implementation(libs.exposed.core)
            implementation(libs.exposed.jdbc)
        }
    }
}
