package com.reservedkeyword.witchhunt.hud

import com.reservedkeyword.witchhunt.WitchHuntPlugin
import com.reservedkeyword.witchhunt.utils.minecraftDispatcher
import kotlinx.coroutines.withContext
import net.kyori.adventure.bossbar.BossBar
import net.kyori.adventure.text.Component
import org.bukkit.entity.Player
import java.time.Instant

class HudRenderer(private val plugin: WitchHuntPlugin) {
    private val hunterBossBars = mutableMapOf<Player, BossBar>()
    private val streamerBossBars = mutableMapOf<Player, BossBar>()

    suspend fun clearAll() {
        hunterBossBars.forEach { (hunterPlayer) -> clearHunterHud(hunterPlayer) }
        streamerBossBars.forEach { (streamerPlayer) -> clearStreamerHud(streamerPlayer) }
    }

    suspend fun clearHunterHud(hunterPlayer: Player) = withContext(plugin.minecraftDispatcher()) {
        hunterBossBars.remove(hunterPlayer)?.let { bossBar ->
            hunterPlayer.hideBossBar(bossBar)
        }
    }

    suspend fun clearStreamerHud(streamerPlayer: Player) = withContext(plugin.minecraftDispatcher()) {
        streamerBossBars.remove(streamerPlayer)?.let { bossBar ->
            streamerPlayer.hideBossBar(bossBar)
        }
    }

    suspend fun updateHunterHud(
        huntDurationMillis: Long,
        huntStartTime: Instant,
        hunterPlayer: Player,
        streamerPlayer: Player
    ) = withContext(plugin.minecraftDispatcher()) {
        val config = plugin.configManager.getConfig()
        val nowMillis = System.currentTimeMillis()
        val startMillis = huntStartTime.toEpochMilli()
        val endMillis = startMillis + huntDurationMillis
        val remainingMillis = endMillis - nowMillis

        if (config.ui.showTimerToHunter) {
            val bossBar = hunterBossBars.getOrPut(hunterPlayer) {
                val timerBar = HudComponents.createTimerBar(
                    color = BossBar.Color.RED,
                    progress = 1f,
                    title = "Hunt Active"
                )

                hunterPlayer.showBossBar(timerBar)
                timerBar
            }

            val progressRemaining = HudComponents.calculateProgressRemaining(
                durationMillis = huntDurationMillis,
                startTimeMillis = startMillis
            )

            val timeRemaining = HudComponents.formatTimeRemaining(remainingMillis)

            bossBar.name(Component.text("Hunt Active: $timeRemaining"))
            bossBar.progress(progressRemaining)

            val timeColor = when {
                remainingMillis < 60000 -> BossBar.Color.RED
                remainingMillis < 300000 -> BossBar.Color.YELLOW
                else -> BossBar.Color.GREEN
            }

            bossBar.color(timeColor)
        }

        if (config.ui.showDistanceToHunter) {
            val distanceToStreamer = HudComponents.calculateDistance(hunterPlayer.location, streamerPlayer.location)
            val actionBarComponent = HudComponents.createDistanceText(distanceToStreamer, "Target Distance")
            hunterPlayer.sendActionBar(actionBarComponent)
        }
    }

    suspend fun updateStreamerHud(
        huntDurationMillis: Long,
        huntStartTime: Instant,
        hunterPlayer: Player,
        streamerPlayer: Player
    ) = withContext(plugin.minecraftDispatcher()) {
        val config = plugin.configManager.getConfig()

        if (!config.ui.showHunterInfoToStreamer) {
            return@withContext
        }

        val nowMillis = System.currentTimeMillis()
        val startMillis = huntStartTime.toEpochMilli()
        val endMillis = startMillis + huntDurationMillis
        val remainingMillis = endMillis - nowMillis

        val bossBar = streamerBossBars.getOrPut(streamerPlayer) {
            val timerBar = HudComponents.createTimerBar(
                color = BossBar.Color.RED,
                progress = 1f,
                title = "Hunter Active"
            )

            streamerPlayer.showBossBar(timerBar)
            timerBar
        }

        val progressRemaining = HudComponents.calculateProgressRemaining(
            durationMillis = huntDurationMillis,
            startTimeMillis = startMillis
        )

        val timeRemaining = HudComponents.formatTimeRemaining(remainingMillis)

        bossBar.name(Component.text("Hunter: ${hunterPlayer.name} ($timeRemaining remaining)"))
        bossBar.progress(progressRemaining)
        bossBar.color(BossBar.Color.RED)

        if (config.ui.showHunterDistance) {
            val distanceToHunter = HudComponents.calculateDistance(streamerPlayer.location, hunterPlayer.location)
            val actionBarComponent = HudComponents.createDistanceText(distanceToHunter, "Hunter Distance")
            streamerPlayer.sendActionBar(actionBarComponent)
        }
    }
}