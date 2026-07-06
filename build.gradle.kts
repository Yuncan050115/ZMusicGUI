import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import java.io.File
import java.io.FileOutputStream
import java.util.jar.JarFile
import java.util.jar.JarOutputStream
import java.util.jar.Attributes

plugins {
    kotlin("jvm") version "2.1.20"
    id("com.gradleup.shadow") version "9.0.0-beta11"
}

group = "com.ourcraft"
version = "3.1.0"

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
    exclude("META-INF/versions/**")
    exclude("META-INF/maven/**")
    // 关键修复: shadow 从 gson/kotlin 依赖继承 Multi-Release: true, 但 Bukkit PluginClassLoader
    // 不兼容多版本 jar — 看到此标记会在 META-INF/versions/ 查找类, 找不到就不回退到 jar 根目录,
    // 导致所有类 ClassNotFoundException。manifest{} 块会被 shadow 合并覆盖, 必须 doLast 修改
    doLast {
        // shadow 从 gson/kotlin 依赖继承 Multi-Release: true, Bukkit PluginClassLoader 不兼容
        val src = archiveFile.get().asFile
        val tmp = File(src.parentFile, src.name + ".tmp")
        val jf = JarFile(src)
        val manifest = jf.manifest
        manifest.mainAttributes.remove(Attributes.Name("Multi-Release"))
        JarOutputStream(FileOutputStream(tmp), manifest).use { jos ->
            val buf = ByteArray(8192)
            for (entry in jf.entries()) {
                if (entry.name == "META-INF/MANIFEST.MF") continue
                jos.putNextEntry(entry)
                jf.getInputStream(entry).use { stream ->
                    while (true) {
                        val n = stream.read(buf)
                        if (n < 0) break
                        jos.write(buf, 0, n)
                    }
                }
                jos.closeEntry()
            }
        }
        jf.close()
        src.delete()
        tmp.renameTo(src)
    }
}

tasks.build { dependsOn(tasks.shadowJar) }

tasks.processResources {
    filesMatching("plugin.yml") { expand("version" to project.version) }
}
