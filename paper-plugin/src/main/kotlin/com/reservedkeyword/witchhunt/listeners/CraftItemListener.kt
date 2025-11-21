package com.reservedkeyword.witchhunt.listeners

import com.reservedkeyword.witchhunt.WitchHuntPlugin
import com.reservedkeyword.witchhunt.utils.sendPrefixedMessage
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.inventory.CraftItemEvent

class CraftItemListener(private val plugin: WitchHuntPlugin) : Listener {
    @EventHandler(
        ignoreCancelled = true,
        priority = EventPriority.HIGHEST
    )
    fun onItemCrafted(event: CraftItemEvent) {
        val player = event.whoClicked

        // Fall through if the player is not a hunter
        if (!plugin.playerRestrictions.isHunter(player.uniqueId)) {
            return
        }

        // Cancel event if the hunter is not allowed to craft items
        if (!plugin.playerRestrictions.canUseCrafting()) {
            event.isCancelled = true
            player.sendPrefixedMessage(Component.text("You cannot craft as the hunter!", NamedTextColor.RED))
        }
    }
}