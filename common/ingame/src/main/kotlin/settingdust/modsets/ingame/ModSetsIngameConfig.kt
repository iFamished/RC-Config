package settingdust.modsets.ingame

import dev.isxander.yacl3.api.Option
import dev.isxander.yacl3.dsl.YetAnotherConfigLib
import dev.isxander.yacl3.dsl.onReady
import dev.isxander.yacl3.gui.YACLScreen
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.Contextual
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.decodeFromStream
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.modules.contextual
import kotlinx.serialization.modules.plus
import net.minecraft.client.gui.screens.Screen
import net.minecraft.network.chat.Component
import settingdust.kinecraft.serialization.ComponentSerializer
import settingdust.kinecraft.serialization.GsonElementSerializer
import settingdust.modsets.ModSets
import settingdust.modsets.PlatformHelper
import kotlin.io.path.createDirectories
import kotlin.io.path.createFile
import kotlin.io.path.div
import kotlin.io.path.inputStream
import kotlin.io.path.listDirectoryEntries
import kotlin.io.path.name
import kotlin.io.path.nameWithoutExtension
import kotlin.io.path.writeText

@Serializable
data class ModSet(
    val text: @Contextual Component,
    val description: @Contextual Component? = null,
    val mods: MutableSet<String>,
)

object ModSetsIngameConfig {
    private val json = Json(ModSets.json) {
        serializersModule += SerializersModule {
            contextual(GsonElementSerializer)
            contextual(ComponentSerializer)
        }
    }

    private val modSetsPath = PlatformHelper.configDir / "modsets.json"
    var modSets: MutableMap<String, ModSet> = mutableMapOf()
        private set
    val modIdToModSets = mutableMapOf<String, Set<ModSet>>()
    private var definedModSets = mutableMapOf<String, ModSet>()
    val MOD_SET_REGISTER_CALLBACK = WaitingSharedFlow<Unit>()

    private val rulesDir = PlatformHelper.configDir / "rules"
    var rules: MutableMap<String, RuleSet> = mutableMapOf()
        private set

    fun MutableMap<String, ModSet>.getOrThrow(name: String) =
        requireNotNull(get(name)) { "Mod sets $name not exist" }

    suspend fun reload() {
        runCatching {
            modSetsPath.createFile()
            modSetsPath.writeText("{}")
        }

        runCatching {
            definedModSets = json.decodeFromStream(modSetsPath.inputStream())
        }
        modSets.clear()
        modSets.putAll(definedModSets)

        MOD_SET_REGISTER_CALLBACK.emit(Unit)

        modIdToModSets.clear()
        modIdToModSets.putAll(
            modSets.entries.fold(mutableMapOf()) { map, curr ->
                for (mod in curr.value.mods) {
                    if (mod == curr.key) continue
                    val set = map.getOrPut(mod, ::mutableSetOf) as MutableSet
                    set += curr.value
                }
                map
            }
        )

        runCatching {
            rulesDir.createDirectories()
        }

        rules.clear()
        rulesDir.listDirectoryEntries("*.json").forEach {
            try {
                rules[it.nameWithoutExtension] = json.decodeFromStream(it.inputStream())
            } catch (e: Exception) {
                ModSets.LOGGER.error("Failed to load rule ${it.name}", e)
            }
        }
    }

    private fun save() {
        ModSets.save()
    }

    internal fun generateConfig() = YetAnotherConfigLib(ModSets.ID) {
        ModSets.reload()
        runBlocking { reload() }

        title { Component.translatable("modsets.name") }

        save { save() }

        if (rules.isNotEmpty()) {
            val options = mutableSetOf<Option<Any>>()

            for (ruleSetEntry in rules) {
                categories.register(ruleSetEntry.key) {
                    val ruleSet = ruleSetEntry.value
                    name(ruleSet.text)
                    ruleSet.description?.let { tooltip(it) }
                    for (rule in ruleSet.rules) {
                        rule.controller.registerCategory(rule, this@register)
                    }

                    thisCategory.onReady { category ->
                        // Since the options are instant and may be affected by the others.
                        // Update the changed options to correct value
                        val optionsInCategory = category.groups().flatMap { it.options() }
                        options.addAll(optionsInCategory as List<Option<Any>>)
                    }
                }
            }

            for (option in options) {
                option.addEventListener { _, event ->
                    var changed = false
                    for (anotherOption in options.filter { it != option && it.changed() }) {
                        anotherOption.requestSet(anotherOption.stateManager().get())
                        if (!changed && option.changed()) {
                            ModSets.LOGGER.warn(
                                "Option ${option.name()} is conflicting with ${anotherOption.name()}. Can't change"
                            )
                            changed = true
                        }
                    }
                    if (option.changed()) {
                        ModSets.LOGGER.warn(
                            "Option ${option.name()} is conflicting with unknown option. Can't change"
                        )
                        option.requestSet(option.stateManager().get())
                    }
                    save() // The save won't be called with the instant
                }
            }
        } else {
            categories.register("no_rules") { name(Component.translatable("modsets.no_rules")) }
        }
    }

    fun generateConfigScreen(lastScreen: Screen?): Screen =
        ModSetConfigScreen(lastScreen)

    class ModSetConfigScreen(parent: Screen?) : YACLScreen(generateConfig(), parent)
}
