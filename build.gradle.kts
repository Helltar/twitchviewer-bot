plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.shadow)
    application
}

group = "com.helltar"
version = "0.11.1"

repositories {
    mavenCentral()
}

dependencies {
    implementation(libs.tgbots.module) { exclude("org.telegram", "telegrambots-webhook") }

    implementation(libs.twitch4j)
    implementation(libs.dotenv.kotlin)

    runtimeOnly(libs.r2dbc.postgresql)
    implementation(libs.exposed.core)
    implementation(libs.exposed.r2dbc)
    implementation(libs.exposed.java.time)

    implementation(libs.kotlin.logging)
    runtimeOnly(libs.logback.classic)
}

application {
    mainClass.set("com.helltar.twitchviewerbot.MainKt")
}

kotlin {
    jvmToolchain(21)
}
