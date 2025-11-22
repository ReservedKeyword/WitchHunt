package com.reservedkeyword.witchhunt.game

import com.reservedkeyword.witchhunt.WitchHuntPlugin
import org.bukkit.Material
import org.bukkit.inventory.ItemStack

class LoadoutGenerator(private val plugin: WitchHuntPlugin) {
    fun generateLoadout(): List<ItemStack> {
        val config = plugin.configManager.getConfig()
        val itemStacks = mutableListOf<ItemStack>()

        itemStacks.addAll(selectRandomItems(config.loadout.armor, config.loadout.itemsPerCategory))
        itemStacks.addAll(selectRandomItems(config.loadout.items, config.loadout.itemsPerCategory))
        itemStacks.addAll(selectRandomItems(config.loadout.weapons, config.loadout.itemsPerCategory))

        return itemStacks
    }

    private fun parseItemString(itemString: String): ItemStack? {
        val strParts = itemString.split(":", limit = 2)
        val materialName = strParts[0].uppercase()
        val materialAmount = strParts.getOrNull(1)?.toIntOrNull() ?: 1

        return try {
            val material = Material.valueOf(materialName)
            ItemStack(material, materialAmount)
        } catch (e: Exception) {
            plugin.logger.warning("Invalid material in loadout config: $materialName")
            e.printStackTrace()
            null
        }
    }

    private fun selectRandomItems(itemStringList: List<String>, count: Int): List<ItemStack> {
        if (itemStringList.isEmpty() || count <= 0) return emptyList()
        val selectedItems = itemStringList.shuffled().take(count)
        return selectedItems.mapNotNull { parseItemString(it) }
    }
}
