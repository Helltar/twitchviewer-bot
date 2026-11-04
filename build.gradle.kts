plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.shadow)
    application
}

group = "com.helltar"
version = "1.1.0"

repositories {
    mavenCentral()
}

dependencies {
    implementation(libs.tgbots.module) { exclude("org.telegram", "telegrambots-webhook") }
    implementation(libs.heartbeat)

    implementation(libs.twitch4j)
    implementation(libs.dotenv.kotlin)

    runtimeOnly(libs.r2dbc.postgresql)
    implementation(libs.exposed.core)
    implementation(libs.exposed.r2dbc)
    implementation(libs.exposed.java.time)

    implementation(libs.kotlin.logging)
    runtimeOnly(libs.logback.classic)

    testImplementation(kotlin("test"))
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

application {
    mainClass.set("com.helltar.twitchviewerbot.MainKt")
}

kotlin {
    jvmToolchain(21)
}

tasks.test {
    useJUnitPlatform()
}
