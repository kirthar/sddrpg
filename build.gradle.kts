plugins {
    alias(libs.plugins.kotlin.multiplatform) apply false
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.kotest) apply false
    alias(libs.plugins.ksp) apply false
}

group = "io.github.kirthar.sddrpg"
version = "0.1.0-SNAPSHOT"

// Use the system-provided Node.js instead of downloading a distribution;
// direct distribution downloads are blocked behind some CI/sandbox proxies.
plugins.withType<org.jetbrains.kotlin.gradle.targets.js.nodejs.NodeJsRootPlugin> {
    extensions.configure<org.jetbrains.kotlin.gradle.targets.js.nodejs.NodeJsEnvSpec> {
        download.set(false)
    }
}
