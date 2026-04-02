// server/build.gradle.kts
plugins {
    kotlin("jvm")
    kotlin("plugin.serialization")
    application
}

application {
    mainClass.set("com.respublika.MainKt")
}

dependencies {
    implementation(project(":shared"))
    implementation(libs.ktor.server.core)
    implementation(libs.ktor.server.netty)
    implementation(libs.ktor.server.content.negotiation)
    implementation(libs.ktor.serialization.json)
    implementation(libs.ktor.server.swagger)
    implementation(libs.ktor.server.status.pages)
    implementation(libs.kotlin.serialization)
    implementation(libs.exposed.core)
    implementation(libs.exposed.jdbc)
    implementation(libs.postgresql)
}

tasks.withType<JavaExec> {
    workingDir = rootProject.projectDir
}
