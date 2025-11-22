package com.reservedkeyword.witchhunt.hud

import com.reservedkeyword.witchhunt.WitchHuntPlugin
import kotlinx.coroutines.launch
import org.bukkit.Bukkit
import org.bukkit.entity.Player
import org.bukkit.scheduler.BukkitTask
import java.time.Instant

class HudManager(private val plugin: WitchHuntPlugin) {
    private val hudRenderer = HudRenderer(plugin)
    private var updateTask: BukkitTask? = null

    suspend fun clear(hunterPlayer: Player? = null, streamerPlayer: Player? = null) {
        hunterPlayer?.let { hudRenderer.clearHunterHud(it) }
        streamerPlayer?.let { hudRenderer.clearStreamerHud(it) }
    }

    suspend fun clearAll() {
        stopUpdates()
        hudRenderer.clearAll()
    }

    fun startUpdates(huntDurationMillis: Long, huntStartTime: Instant, hunterPlayer: Player, streamerPlayer: Player) {
        stopUpdates()
        plugin.logger.info("Starting HUD updates for hunt...")

        updateTask = Bukkit.getScheduler().runTaskTimer(plugin, Runnable {
            if (!hunterPlayer.isOnline || !streamerPlayer.isOnline) {
                stopUpdates()
                return@Runnable
            }

            plugin.syncScope.launch {
                hudRenderer.updateHunterHud(
                    huntDurationMillis = huntDurationMillis,
                    huntStartTime = huntStartTime,
                    hunterPlayer = hunterPlayer,
                    streamerPlayer = streamerPlayer
                )

                hudRenderer.updateStreamerHud(
                    huntDurationMillis = huntDurationMillis,
                    huntStartTime = huntStartTime,
                    hunterPlayer = hunterPlayer,
                    streamerPlayer = streamerPlayer
                )
            }

        }, 0L, 10L)
    }

    fun stopUpdates() {
        updateTask?.cancel()
        updateTask = null
    }
}
