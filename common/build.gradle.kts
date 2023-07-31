@file:Suppress(
    "DSL_SCOPE_VIOLATION",
    "MISSING_DEPENDENCY_CLASS",
    "FUNCTION_CALL_EXPECTED",
    "PropertyName",
    "UnstableApiUsage",
)

import dev.architectury.plugin.ModLoader
import dev.architectury.plugin.TransformingTask
import dev.architectury.plugin.loom.LoomInterface
import net.fabricmc.loom.task.RemapJarTask


val archives_name: String by rootProject

architectury {
    common(rootProject.property("enabled_platforms").toString().split(","))
}

val game by sourceSets.registering {
    compileClasspath += sourceSets.main.get().compileClasspath
    compileClasspath += sourceSets.main.get().output
}

repositories {
    exclusiveContent {
        forRepository {
            maven {
                name = "Modrinth"
                url = uri("https://api.modrinth.com/maven")
            }
        }
        filter {
            includeGroup("maven.modrinth")
        }
    }

    maven("https://maven.terraformersmc.com/releases")
    maven("https://maven.isxander.dev/releases")
    maven {
        name = "ParchmentMC"
        url = uri("https://maven.parchmentmc.org")
    }
}

dependencies {
    api(libs.kotlinx.serialization.core)
    api(libs.kotlinx.serialization.json)
    api(libs.kotlinx.coroutines)
    api(libs.kotlin.reflect)

    modApi(libs.yacl.common)
    modApi(libs.modmenu)

    modApi("maven.modrinth:kinecraft-serialization:${libs.versions.kinecraft.serialization.get()}-fabric")
}

tasks {
    jar {
        manifest.attributes(
            "FMLModType" to "LIBRARY",
        )
    }

    val modJar by registering(Jar::class) {
        archiveClassifier.set("game")
        from(sourceSets.named("game").get().output)
        destinationDirectory.set(project.buildDir.resolve("devlibs"))
        manifest.attributes(
            "FMLModType" to "GAMELIBRARY",
        )
    }

    val remapModJar by registering(RemapJarTask::class) {
        dependsOn(modJar)
        archiveClassifier.set("game")
        inputFile.convention(modJar.get().archiveFile)
    }

    named<ProcessResources>("processGameResources") {
        exclude("META-INF/mods.toml")
    }
}
