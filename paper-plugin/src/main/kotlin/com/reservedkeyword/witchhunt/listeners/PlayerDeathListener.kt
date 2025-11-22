package com.reservedkeyword.witchhunt.listeners

import com.reservedkeyword.witchhunt.WitchHuntPlugin
import com.reservedkeyword.witchhunt.models.AttemptOutcome
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.entity.PlayerDeathEvent

class PlayerDeathListener(private val plugin: WitchHuntPlugin) : Listener {
    private fun handleHunterDeath(hunterPlayer: Player) {
        plugin.logger.info("Hunter ${hunterPlayer.name} died")
        val activeHuntSession = plugin.huntManager.getActiveHuntSession()

        if (activeHuntSession != null) {
            plugin.asyncScope.launch {
                activeHuntSession.handleHunterDied(hunterPlayer)
                plugin.huntManager.clearActiveHuntSession()
            }
        }

        plugin.playerRestrictions.removeHunter(hunterPlayer.uniqueId)
    }

    private fun handleStreamerDeath(streamerPlayer: Player) {
        if (!plugin.huntManager.isGameActive()) {
            return
        }

        plugin.logger.info("Streamer ${streamerPlayer.name} died, ending game...")

        plugin.asyncScope.launch {
            try {
                delay(3000)
                plugin.huntManager.endGame(AttemptOutcome.DEATH)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    fun onPlayerDied(event: PlayerDeathEvent) {
        val player = event.player
        val streamerUsername = plugin.configManager.getConfig().streamerUsername

        if (plugin.playerRestrictions.isHunter(player)) {
            handleHunterDeath(player)
            return
        }

        if (player.name == streamerUsername) {
            handleStreamerDeath(player)
            return
        }
    }
}
