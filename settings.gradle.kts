// settings.gradle.kts
rootProject.name = "ResPublika"

include(":shared")     // Logique métier, parsing, SQL Exposed
include(":server")     // Backend Ktor
include(":composeApp") // UI Multiplatform (Android, iOS, Desktop, Wasm)