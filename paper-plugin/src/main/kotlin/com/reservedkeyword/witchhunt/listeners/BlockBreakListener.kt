package com.reservedkeyword.witchhunt.listeners

import com.reservedkeyword.witchhunt.WitchHuntPlugin
import com.reservedkeyword.witchhunt.utils.sendPrefixedMessage
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.block.BlockBreakEvent

class BlockBreakListener(private val plugin: WitchHuntPlugin) : Listener {
    @EventHandler(
        ignoreCancelled = true,
        priority = EventPriority.HIGHEST
    )
    fun onBlockBroken(event: BlockBreakEvent) {
        val player = event.player

        // Fall through if the player is not a hunter
        if (!plugin.playerRestrictions.isHunter(player)) {
            return
        }

        // Cancel event if the hunter is not allowed to break blocks
        if (!plugin.playerRestrictions.canBreakBlocks()) {
            event.isCancelled = true
            player.sendPrefixedMessage(Component.text("You cannot break blocks as the hunter!", NamedTextColor.RED))
        }
    }
}