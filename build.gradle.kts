import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    kotlin("jvm") version "2.1.20"
    id("com.gradleup.shadow") version "9.0.0-beta11"
}

group = "com.ourcraft"
version = "1.1.3"

repositories {
    mavenCentral()
    maven("https://repo.papermc.io/repository/maven-public/")
    maven("https://repo.extendedclip.com/content/repositories/placeholderapi/")
}

dependencies {
    compileOnly("io.papermc.paper:paper-api:1.19.4-R0.1-SNAPSHOT")
    compileOnly("me.clip:placeholderapi:2.11.6")
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
    dependencies {
        exclude(dependency("io.papermc.paper:paper-api:.*"))
        exclude(dependency("me.clip:placeholderapi:.*"))
    }
}

tasks.build { dependsOn(tasks.shadowJar) }

tasks.processResources {
    filesMatching("plugin.yml") { expand("version" to project.version) }
}
