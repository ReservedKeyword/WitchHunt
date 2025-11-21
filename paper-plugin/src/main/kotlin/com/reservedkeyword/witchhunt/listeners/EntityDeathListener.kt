package com.reservedkeyword.witchhunt.listeners

import com.reservedkeyword.witchhunt.WitchHuntPlugin
import com.reservedkeyword.witchhunt.models.AttemptOutcome
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.bukkit.entity.EnderDragon
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.entity.EntityDeathEvent

class EntityDeathListener(private val plugin: WitchHuntPlugin) : Listener {
    @EventHandler(priority = EventPriority.MONITOR)
    fun onEntityDied(event: EntityDeathEvent) {
        if (event.entity !is EnderDragon) {
            return
        }

        if (!plugin.huntManager.isGameActive()) {
            return
        }

        plugin.logger.info("Ender Dragon has been slain!")

        plugin.asyncScope.launch {
            try {
                delay(10000)
                plugin.huntManager.endGame(AttemptOutcome.VICTORY)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }
}