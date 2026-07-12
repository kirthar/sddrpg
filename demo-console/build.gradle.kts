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
    testImplementation(libs.kotest.framework.engine)
    testImplementation(libs.kotest.assertions.core)
    testImplementation(libs.kotest.runner.junit5)
}

application {
    mainClass.set("io.github.kirthar.sddrpg.demo.console.MainKt")
}

tasks.withType<Test>().configureEach {
    useJUnitPlatform()
}
