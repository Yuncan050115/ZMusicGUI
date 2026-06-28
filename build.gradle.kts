import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    kotlin("jvm") version "2.1.20"
    id("com.gradleup.shadow") version "9.0.0-beta11"
}

group = "com.ourcraft"
version = "3.0.1"

repositories {
    mavenCentral()
    maven("https://repo.papermc.io/repository/maven-public/")
    maven("https://jitpack.io")
}

dependencies {
    compileOnly("io.papermc.paper:paper-api:1.21.4-R0.1-SNAPSHOT")
    compileOnly("com.github.MilkBowl:VaultAPI:1.7") {
        exclude(group = "org.bukkit")
    }
    implementation("com.google.code.gson:gson:2.10.1")
    // 不再依赖 ZMusic CE — 完全独立的 Kotlin 实现
    // 不再依赖 PlaceholderAPI — 歌词显示直接从 MusicPlayer.getState 读取
}

java {
    toolchain { languageVersion.set(JavaLanguageVersion.of(21)) }
}

kotlin {
    jvmToolchain { languageVersion.set(JavaLanguageVersion.of(21)) }
}

tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile> {
    compilerOptions { jvmTarget.set(JvmTarget.JVM_17) }
}

tasks.shadowJar {
    archiveClassifier.set("")
    relocate("kotlin", "com.ourcraft.zmusicgui.libs.kotlin")
    relocate("org.jetbrains", "com.ourcraft.zmusicgui.libs.jetbrains")
    relocate("org.intellij", "com.ourcraft.zmusicgui.libs.intellij")
    relocate("com.google.gson", "com.ourcraft.zmusicgui.libs.gson")
    dependencies {
        exclude(dependency("io.papermc.paper:paper-api:.*"))
        exclude(dependency("com.github.MilkBowl:VaultAPI:.*"))
    }
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
}

tasks.build { dependsOn(tasks.shadowJar) }

tasks.processResources {
    filesMatching("plugin.yml") { expand("version" to project.version) }
}
