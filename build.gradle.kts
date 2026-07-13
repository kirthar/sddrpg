plugins {
    alias(libs.plugins.kotlin.multiplatform) apply false
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.kotest) apply false
    alias(libs.plugins.ksp) apply false
    // AGP and the Kotlin plugin must share the root build classpath: loading AGP
    // only in demo-app's classloader leaves Kotlin unable to see AGP's classes
    // ("Can't infer current AndroidGradlePluginVersion").
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.compose) apply false
    alias(libs.plugins.kotlin.compose.compiler) apply false
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

// Same for the wasmJs targets' own Node provisioning (spec 009).
plugins.withType<org.jetbrains.kotlin.gradle.targets.wasm.nodejs.WasmNodeJsRootPlugin> {
    extensions.configure<org.jetbrains.kotlin.gradle.targets.wasm.nodejs.WasmNodeJsEnvSpec> {
        download.set(false)
    }
}

// Binaryen (wasm-opt, used to optimize production wasm binaries) likewise cannot be
// downloaded from GitHub releases behind this proxy -- use the system-provided one
// (installed via `npm install -g binaryen`) instead (spec 009). Unlike the Node
// root plugins, BinaryenPlugin applies per-subproject.
allprojects {
    plugins.withType<org.jetbrains.kotlin.gradle.targets.wasm.binaryen.BinaryenPlugin> {
        extensions.configure<org.jetbrains.kotlin.gradle.targets.wasm.binaryen.BinaryenEnvSpec> {
            download.set(false)
        }
    }
}
