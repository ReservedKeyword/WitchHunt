package com.reservedkeyword.witchhunt.listeners

import com.reservedkeyword.witchhunt.WitchHuntPlugin
import com.reservedkeyword.witchhunt.models.HunterEncounter
import com.reservedkeyword.witchhunt.models.HunterOutcome
import com.reservedkeyword.witchhunt.utils.broadcastPrefixedMessage
import kotlinx.coroutines.launch
import net.kyori.adventure.text.Component
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.player.PlayerQuitEvent
import java.time.Instant

class PlayerQuitListener(private val plugin: WitchHuntPlugin) : Listener {
    private fun handleHunterQuit(hunterPlayer: Player) {
        plugin.logger.info("Hunter ${hunterPlayer.name} disconnected")

        plugin.asyncScope.launch {
            plugin.playerRestrictions.removeHunter(hunterPlayer.uniqueId)
            plugin.hudManager.clear(hunterPlayer = hunterPlayer)

            val activeHuntSession = plugin.huntManager.getActiveHuntSession()

            if (activeHuntSession != null) {
                val currentHuntState = plugin.huntManager.currentState
                val activeHuntSession = currentHuntState.activeHuntSession

                if (activeHuntSession != null && activeHuntSession.hunterMinecraftName == hunterPlayer.name) {
                    val hunterEncounter = HunterEncounter(
                        endedAt = Instant.now(),
                        hunterMinecraftName = activeHuntSession.hunterMinecraftName,
                        hunterTwitchName = activeHuntSession.hunterTwitchName,
                        joinedAt = activeHuntSession.startedAt,
                        outcome = HunterOutcome.DISCONNECTED
                    )

                    plugin.huntManager.recordHunterEncounter(hunterEncounter)
                    plugin.huntManager.clearActiveHuntSession()
                    plugin.server.broadcastPrefixedMessage(Component.text("Hunter ${hunterPlayer.name} disconnected"))
                }
            }
        }
    }

    private fun handleStreamerQuit() {
        plugin.logger.warning("Streamer disconnected during active game!")

        plugin.asyncScope.launch {
            try {
                plugin.huntManager.pauseGame()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    fun onPlayerQuit(event: PlayerQuitEvent) {
        event.quitMessage(null)
        val player = event.player
        val streamerUsername = plugin.configManager.getConfig().streamerUsername

        if (plugin.playerRestrictions.isHunter(player)) {
            handleHunterQuit(player)
            return
        }

        if (player.name == streamerUsername && plugin.huntManager.isGameActive()) {
            handleStreamerQuit()
            return
        }
    }
}
