plugins {
    alias(libs.plugins.kotlin.jvm)
    application
}

kotlin {
    jvmToolchain(21)
}

dependencies {
    implementation(project(":core"))
    implementation(project(":content"))
}

application {
    mainClass.set("io.github.kirthar.sddrpg.demo.console.MainKt")
}
