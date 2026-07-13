import org.jetbrains.compose.desktop.application.dsl.TargetFormat

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.compose)
    alias(libs.plugins.kotlin.compose.compiler)
    alias(libs.plugins.android.application)
}

kotlin {
    jvmToolchain(21)

    androidTarget()

    jvm("desktop")

    wasmJs {
        browser {
            // wasmJs is verified by compilation + producing the browser distribution,
            // never by browser tests (research R8) -- no Karma/browser harness here.
            testTask { enabled = false }
        }
        binaries.executable()
    }

    sourceSets {
        commonMain.dependencies {
            implementation(project(":core"))
            implementation(project(":content"))
            implementation(compose.runtime)
            implementation(compose.foundation)
            implementation(compose.material3)
        }
        commonTest.dependencies {
            implementation(libs.kotest.framework.engine)
            implementation(libs.kotest.assertions.core)
        }
        androidMain.dependencies {
            implementation(libs.androidx.activity.compose)
        }
        val desktopMain by getting {
            dependencies {
                implementation(compose.desktop.currentOs)
            }
        }
        val desktopTest by getting {
            dependencies {
                implementation(libs.kotest.runner.junit5)
            }
        }
    }
}

android {
    namespace = "io.github.kirthar.sddrpg.demo.app"
    compileSdk = 36

    defaultConfig {
        applicationId = "io.github.kirthar.sddrpg.demo.app"
        minSdk = 24
        targetSdk = 36
        versionCode = 1
        versionName = "0.1.0"
    }
}

compose.desktop {
    application {
        mainClass = "io.github.kirthar.sddrpg.demo.app.MainKt"
        nativeDistributions {
            targetFormats(TargetFormat.Deb)
            packageName = "sddrpg-demo-app"
            packageVersion = "1.0.0"
        }
    }
}

tasks.withType<Test>().configureEach {
    useJUnitPlatform()
}

// The kotest suite runs on the desktop JVM target (research R8); Android unit tests
// would re-run the identical commonTest logic on a slower host -- and AGP 8.13's
// embedded Kotlin (2.2.x, also used by lint) cannot consume this project's 2.4.0
// binaries anyway, so both unit-test and lint tasks are disabled for this module.
tasks.configureEach {
    if (name.contains("UnitTest") || name.startsWith("lint")) enabled = false
}
