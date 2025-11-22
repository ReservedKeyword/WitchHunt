package com.reservedkeyword.witchhunt.listeners

import com.reservedkeyword.witchhunt.WitchHuntPlugin
import kotlinx.coroutines.launch
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.player.PlayerJoinEvent

class PlayerJoinListener(private val plugin: WitchHuntPlugin) : Listener {
    private fun handleHunterJoin(hunterPlayer: Player) {
        plugin.logger.info("Hunter ${hunterPlayer.name} joined the server")

        if (plugin.huntManager.isGamePaused()) {
            hunterPlayer.kick(Component.text("Game is currently paused. Please try again later.", NamedTextColor.RED))
            return
        }

        plugin.playerRestrictions.registerHunter(hunterPlayer.uniqueId)
        val activeHuntSession = plugin.huntManager.getActiveHuntSession()

        if (activeHuntSession != null) {
            plugin.asyncScope.launch {
                activeHuntSession.handleHunterJoined(hunterPlayer)
            }
        }
    }

    private fun handleStreamerRejoin(streamerPlayer: Player) {
        plugin.logger.info("Streamer ${streamerPlayer.name} rejoined during paused game")

        plugin.asyncScope.launch {
            try {
                plugin.huntManager.resumeGame()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    fun onPlayerJoined(event: PlayerJoinEvent) {
        event.joinMessage(null)
        val player = event.player

        // Check if the hunter is joining the game
        val huntGameState = plugin.huntManager.currentState
        val activeHuntSession = huntGameState.activeHuntSession

        if (activeHuntSession != null && activeHuntSession.hunterMinecraftName == player.name) {
            handleHunterJoin(player)
            return
        }

        // Check if streamer has joined back during a paused game
        val config = plugin.configManager.getConfig()
        val streamerUsername = config.streamerUsername

        if (player.name == streamerUsername && plugin.huntManager.isGameActive() && plugin.huntManager.isGamePaused()) {
            handleStreamerRejoin(player)
            return
        }
    }
}
