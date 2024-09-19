import net.minecraftforge.gradle.userdev.tasks.RenameJarInPlace

plugins {
    alias(catalog.plugins.shadow)
    alias(catalog.plugins.forge.gradle)
}

val archives_name: String by rootProject
val mod_name: String by rootProject

minecraft {
    mappings("official", catalog.versions.minecraft.get())
}

jarJar.enable()

dependencies {
    minecraft(catalog.minecraft.forge)

    jarJar(project(":common")) {
        isTransitive = false
        jarJar.ranged(this, "[$version,)")
    }
    jarJar(project(":forge:ingame")) {
        isTransitive = false
        jarJar.ranged(this, "[$version,)")
    }

    jarJar(project(":forge:mod")) {
        isTransitive = false
        jarJar.ranged(this, "[$version,)")
    }
    jarJar(project(":forge:setup-mod-hook")) {
        isTransitive = false
        jarJar.ranged(this, "[$version,)")
    }

    shadow(project(":forge:mod-locator")) {
        isTransitive = false
    }

    runtimeOnly(catalog.kotlin.forge)
    runtimeOnly(fg.deobf(catalog.yacl.forge.get()))

    jarJar(catalog.kinecraft.serialization) {
        jarJar.ranged(this, "[$version,)")
    }
    jarJar(catalog.preloading.tricks) {
        jarJar.ranged(this, "[$version,)")
    }
}

tasks {
    jar { enabled = false }

    shadowJar {
        configurations = listOf(project.configurations.shadow.get())
        archiveClassifier = "dev"
    }

    this.jarJar {
        dependsOn(shadowJar, ":common:ingame:jar")

        archiveClassifier = ""

        from(shadowJar.flatMap { it.archiveFile }.map { zipTree(it) })

        manifest {
            attributes(
                "FMLModType" to "LIBRARY",
            )
        }

        eachFile {
            if (path.startsWith("META-INF/jarjar")) {
                if (name != "metadata.json")
                    path = "META-INF/jars/$name"
                else {
                    filter {
                        it.replace("\"path\": \"META-INF/jarjar", "\"path\": \"META-INF/jars")
                    }
                }
            }
        }
    }

    afterEvaluate {
        named<RenameJarInPlace>("reobfJar") {
            input = shadowJar.flatMap { it.archiveFile }
        }
    }
}
