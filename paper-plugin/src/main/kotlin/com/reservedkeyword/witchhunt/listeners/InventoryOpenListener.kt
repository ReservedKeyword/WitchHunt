package com.reservedkeyword.witchhunt.listeners

import com.reservedkeyword.witchhunt.WitchHuntPlugin
import com.reservedkeyword.witchhunt.utils.sendPrefixedMessage
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.inventory.InventoryOpenEvent
import org.bukkit.event.inventory.InventoryType

class InventoryOpenListener(private val plugin: WitchHuntPlugin) : Listener {
    @EventHandler(
        ignoreCancelled = true,
        priority = EventPriority.HIGHEST
    )
    fun onInventoryOpened(event: InventoryOpenEvent) {
        val player = event.player

        if (!plugin.playerRestrictions.isHunter(player.uniqueId)) {
            return
        }

        if (!plugin.playerRestrictions.canOpenChests()) {
            val inventory = event.inventory

            if (inventory.type != InventoryType.PLAYER) {
                event.isCancelled = true

                player.sendPrefixedMessage(
                    Component.text(
                        "You cannot open containers as the hunter!",
                        NamedTextColor.RED
                    )
                )
            }
        }
    }
}