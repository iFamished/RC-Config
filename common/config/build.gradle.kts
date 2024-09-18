plugins {
    alias(catalog.plugins.kotlin.jvm)
    alias(catalog.plugins.kotlin.plugin.serialization)
    alias(catalog.plugins.vanilla.gradle)
}

minecraft { version(catalog.versions.minecraft.get()) }

dependencies {
    api(catalog.kotlinx.serialization.core)
    api(catalog.kotlinx.serialization.json)
    api(catalog.kotlinx.coroutines)
    api(catalog.kotlin.reflect)
    api(variantOf(catalog.kinecraft.serialization) {
        classifier("common")
    })
}


tasks {
    jar {
        manifest.attributes(
            "FMLModType" to "LIBRARY",
        )
    }
}
