package settingdust.modsets.quilt

import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import net.minecraft.client.resources.language.I18n
import net.minecraft.network.chat.Component
import org.quiltmc.loader.api.ModContainer
import org.quiltmc.loader.api.ModContainer.BasicSourceType
import org.quiltmc.loader.api.ModMetadata
import org.quiltmc.loader.api.QuiltLoader
import org.quiltmc.loader.impl.QuiltLoaderImpl
import org.quiltmc.qsl.base.api.entrypoint.ModInitializer
import settingdust.modsets.ModSet
import settingdust.modsets.ModSets
import settingdust.modsets.config
import settingdust.modsets.rules
import kotlin.io.path.name

class Entrypoint : ModInitializer {
    @OptIn(DelicateCoroutinesApi::class)
    override fun onInitialize(container: ModContainer) {
        val modSets = ModSets.rules.modSets
        val modDir = QuiltLoaderImpl.INSTANCE.modsDir

        GlobalScope.launch(Dispatchers.IO) {
            ModSets.rules.ModSetsRegisterCallback.collect {
                for (mod in QuiltLoader.getAllMods()) {
                    if (mod.sourceType.equals(BasicSourceType.BUILTIN)) continue
                    val metadata = mod.metadata()
                    // I can't find the real case of muleiple quilt source paths. So, just use the first
                    val paths = mod.sourcePaths.singleOrNull() ?: continue
                    // Quilt will be writing the path like [path in system, path in jar] etc.
                    val pathInSystem = paths.singleOrNull() ?: continue
                    if (pathInSystem.startsWith(modDir) && modDir != pathInSystem.parent) {
                        val subDir = pathInSystem.parent.name
                        ModSets.logger.debug("Add {} to {}", pathInSystem, subDir)
                        if (subDir in modSets) ModSets.logger.warn("Duplicate mod set with directory name: $subDir")
                        modSets.getOrPut(subDir) {
                            ModSet(
                                Component.literal(subDir),
                                Component.literal(pathInSystem.toString()),
                                mutableSetOf()
                            )
                        }.mods.add(metadata.id())
                    }
                    if (metadata.id() in modSets) ModSets.logger.warn("Duplicate mod set with mod id: ${metadata.id()}")
                    modSets.putIfAbsent(metadata.id(), ModSet(metadata))
                }

                ModSets.config.disabledMods.forEach {
                    modSets.putIfAbsent(it, ModSet(
                        if (try {
                                I18n.exists("modmenu.nameTranslation.$it")
                            } catch (e: Exception) {
                                false
                            }
                        ) {
                            Component.translatable("modmenu.nameTranslation.$it")
                        } else {
                            Component.literal(it)
                        },
                        Component.literal("$it@disabled"),
                        mutableSetOf(it),
                    ))
                }
            }
        }
    }
}

fun ModSet(mod: ModMetadata) = ModSet(
    if (try {
            I18n.exists("modmenu.nameTranslation.${mod.id()}")
        } catch (e: Exception) {
            false
        }
    ) {
        Component.translatable("modmenu.nameTranslation.${mod.id()}")
    } else {
        Component.literal(mod.name())
    },
    Component.literal("${mod.id()}@${mod.version()}"),
    mutableSetOf(mod.id()),
)
