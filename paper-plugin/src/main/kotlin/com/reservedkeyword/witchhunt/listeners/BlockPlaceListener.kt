package com.reservedkeyword.witchhunt.listeners

import com.reservedkeyword.witchhunt.WitchHuntPlugin
import com.reservedkeyword.witchhunt.utils.sendPrefixedMessage
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.block.BlockPlaceEvent

class BlockPlaceListener(private val plugin: WitchHuntPlugin) : Listener {
    @EventHandler(
        ignoreCancelled = true,
        priority = EventPriority.HIGHEST
    )
    fun onBlockPlaced(event: BlockPlaceEvent) {
        val player = event.player

        if (!plugin.playerRestrictions.isHunter(player)) {
            return
        }

        if (!plugin.playerRestrictions.canPlaceBlocks()) {
            event.isCancelled = true
            player.sendPrefixedMessage(Component.text("You cannot place blocks as the hunter!", NamedTextColor.RED))
        }
    }
}
