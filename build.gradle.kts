plugins {
    kotlin("jvm") version "2.3.0"
    id("com.gradleup.shadow") version "9.3.0"
    application
}

group = "com.helltar"
version = "0.9.9"

repositories {
    mavenCentral()
}

object Versions {
    const val EXPOSED = "1.0.0"
    const val R2DBC_POSTGRESQL = "1.1.1.RELEASE"
}

dependencies {
    implementation("com.annimon:tgbots-module:9.2.0") { exclude("org.telegram", "telegrambots-webhook") }
    implementation("com.github.twitch4j:twitch4j:1.25.0")
    implementation("io.github.cdimascio:dotenv-kotlin:6.5.1")

    runtimeOnly("org.postgresql:r2dbc-postgresql:${Versions.R2DBC_POSTGRESQL}")
    implementation("org.jetbrains.exposed:exposed-core:${Versions.EXPOSED}")
    implementation("org.jetbrains.exposed:exposed-r2dbc:${Versions.EXPOSED}")
    implementation("org.jetbrains.exposed:exposed-java-time:${Versions.EXPOSED}")

    implementation("io.github.oshai:kotlin-logging-jvm:7.0.14")
    runtimeOnly("ch.qos.logback:logback-classic:1.5.27")
}

application {
    mainClass.set("com.helltar.twitchviewerbot.bot.TwitchViewerBot")
}

kotlin {
    jvmToolchain(21)
}
