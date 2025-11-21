package com.reservedkeyword.witchhunt.game

import com.reservedkeyword.witchhunt.WitchHuntPlugin
import org.bukkit.entity.Player
import java.util.*

class PlayerRestrictions(private val plugin: WitchHuntPlugin) {
    private val activeHunters = mutableSetOf<UUID>()

    fun canBreakBlocks(): Boolean = plugin.configManager.getConfig().restrictions.canBreakBlocks

    fun canOpenChests(): Boolean = plugin.configManager.getConfig().restrictions.canOpenChests

    fun canPlaceBlocks(): Boolean = plugin.configManager.getConfig().restrictions.canPlaceBlocks

    fun canUseCrafting(): Boolean = plugin.configManager.getConfig().restrictions.canUseCrafting

    fun clearAll() = activeHunters.clear()

    fun isHunter(player: Player): Boolean = activeHunters.contains(player.uniqueId)

    fun isHunter(playerUuid: UUID): Boolean = activeHunters.contains(playerUuid)

    fun registerHunter(playerUuid: UUID) {
        activeHunters.add(playerUuid)
        plugin.logger.info("Added hunter $playerUuid to player restrictions")
    }

    fun removeHunter(playerUuid: UUID) {
        activeHunters.remove(playerUuid)
        plugin.logger.info("Removed hunter $playerUuid from player restrictions")
    }
}