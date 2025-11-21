package com.reservedkeyword.witchhunt.game

import com.reservedkeyword.witchhunt.WitchHuntPlugin
import com.reservedkeyword.witchhunt.models.HunterEncounter
import com.reservedkeyword.witchhunt.models.HunterOutcome
import com.reservedkeyword.witchhunt.models.config.Config
import com.reservedkeyword.witchhunt.utils.broadcastPrefixedMessage
import com.reservedkeyword.witchhunt.utils.minecraftDispatcher
import com.reservedkeyword.witchhunt.utils.sendPrefixedMessage
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import net.kyori.adventure.text.format.TextDecoration
import org.bukkit.Bukkit
import org.bukkit.GameMode
import org.bukkit.Location
import org.bukkit.entity.Player
import java.time.Instant
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

class HuntSession(
    private val config: Config,
    private val minecraftUsername: String,
    private val plugin: WitchHuntPlugin,
    private val streamerPlayer: Player,
    private val twitchUsername: String
) {
    private var hudStarted: Boolean = false
    private var huntTimeoutJob: Job? = null
    private var joinTime: Instant? = null
    private var joinTimeoutJob: Job? = null

    suspend fun cancel() {
        huntTimeoutJob?.cancel()
        joinTimeoutJob?.cancel()

        withContext(plugin.minecraftDispatcher()) {
            if (hudStarted) {
                val hunterPlayer = Bukkit.getPlayer(minecraftUsername)

                plugin.hudManager.clear(
                    hunterPlayer = hunterPlayer,
                    streamerPlayer = streamerPlayer
                )

                plugin.hudManager.stopUpdates()
            }

            val hunterPlayer = Bukkit.getPlayer(minecraftUsername)

            if (hunterPlayer != null) {
                cleanupOnlineHunter(hunterPlayer)
            } else {
                Bukkit.getOfflinePlayer(minecraftUsername).isWhitelisted = false
                plugin.webhookClient.sendEvent("hunter-left")
            }
        }
    }

    private suspend fun cleanupOnlineHunter(hunterPlayer: Player) {
        if (hudStarted) {
            plugin.hudManager.clear(
                hunterPlayer = hunterPlayer,
                streamerPlayer = streamerPlayer
            )

            plugin.hudManager.stopUpdates()
        }

        withContext(plugin.minecraftDispatcher()) {
            hunterPlayer.kick(Component.text("Your hunt has ended!", NamedTextColor.DARK_RED, TextDecoration.BOLD))
            Bukkit.getOfflinePlayer(minecraftUsername).isWhitelisted = false
        }

        plugin.webhookClient.sendEvent("hunter-left")
    }

    private fun generateSpawnLocation(streamerLocation: Location): Location {
        val radiusBlocks = config.spawn.radiusBlocks
        val world = streamerLocation.world

        val angle = Random.nextDouble(0.0, 2 * Math.PI)
        val distance = Random.nextDouble(radiusBlocks * 0.5, radiusBlocks.toDouble())

        val x = streamerLocation.x + distance * cos(angle)
        val z = streamerLocation.z + distance * sin(angle)
        val y = world.getHighestBlockYAt(x.toInt(), z.toInt()).toDouble() + 1

        return Location(world, x, y, z)
    }

    private suspend fun handleHuntTimeout() {
        plugin.logger.info("Hunt timed out for $twitchUsername ($minecraftUsername)")

        val hunterEncounter = HunterEncounter(
            endedAt = Instant.now(),
            hunterMinecraftName = minecraftUsername,
            hunterTwitchName = twitchUsername,
            outcome = HunterOutcome.TIMEOUT
        )

        plugin.huntManager.recordHunterEncounter(hunterEncounter)

        withContext(plugin.minecraftDispatcher()) {
            val hunterPlayer = Bukkit.getPlayer(minecraftUsername)

            if (hunterPlayer != null) {
                cleanupOnlineHunter(hunterPlayer)
            } else {
                Bukkit.getOfflinePlayer(minecraftUsername).isWhitelisted = false
            }

            plugin.server.broadcastPrefixedMessage(
                Component.text(
                    "Hunter $minecraftUsername ran out of time!",
                    NamedTextColor.RED
                )
            )
        }
    }

    suspend fun handleHunterDeath(hunterPlayer: Player) {
        plugin.logger.info("Hunter ${hunterPlayer.name} has died")

        val hunterEncounter = HunterEncounter(
            endedAt = Instant.now(),
            hunterMinecraftName = minecraftUsername,
            hunterTwitchName = twitchUsername,
            joinedAt = joinTime,
            outcome = HunterOutcome.DIED
        )

        plugin.huntManager.recordHunterEncounter(hunterEncounter)
        cleanupOnlineHunter(hunterPlayer)

        plugin.server.broadcastPrefixedMessage(
            Component.text(
                "Hunter ${hunterPlayer.name} has been eliminated!",
                NamedTextColor.GREEN
            )
        )
    }

    suspend fun handleHunterJoin(hunterPlayer: Player) {
        plugin.logger.info("Hunter ${hunterPlayer.name} joined the server")
        joinTimeoutJob?.cancel()
        joinTime = Instant.now()

        withContext(plugin.minecraftDispatcher()) {
            setupHunter(hunterPlayer)
        }

        plugin.hudManager.startUpdates(
            huntDurationMillis = config.timing.huntDurationMillis,
            huntStartTime = joinTime ?: Instant.now(),
            hunterPlayer = hunterPlayer,
            streamerPlayer = streamerPlayer,
        )

        hudStarted = true
        startHuntTimeout()

        plugin.webhookClient.sendEvent(
            "hunter-joined",
            mapOf("huntDurationMillis" to config.timing.huntDurationMillis.toString())
        )
    }

    private suspend fun handleJoinTimeout() {
        plugin.logger.info("Join timed out for $twitchUsername ($minecraftUsername): did not show in time")

        withContext(plugin.minecraftDispatcher()) {
            Bukkit.getOfflinePlayer(minecraftUsername).isWhitelisted = false
        }

        plugin.huntManager.clearActiveHuntSession()

        if (plugin.configManager.getConfig().noShowBehavior.immediateReselect) {
            plugin.logger.info("Immediate reselect enabled, notify Twitch bot to send a new player...")
            notifyTwitchBot("no-show-immediate-reselect")
        }
    }

    private suspend fun notifyTwitchBot(event: String, data: Map<String, String>? = null) {
        try {
            plugin.webhookClient.sendEvent(event, data)
        } catch (e: Exception) {
            plugin.logger.warning("Failed to notify Twitch bot: ${e.message}")
        }
    }

    suspend fun start() {
        plugin.logger.info("Hunt session started for $twitchUsername ($minecraftUsername)")

        withContext(plugin.minecraftDispatcher()) {
            Bukkit.getOfflinePlayer(minecraftUsername).isWhitelisted = true
        }

        startJoinTimeout()
    }

    private fun setupHunter(hunterPlayer: Player) {
        hunterPlayer.inventory.clear()

        val itemsLoadout = LoadoutGenerator(plugin).generateLoadout()

        itemsLoadout.forEach { item ->
            hunterPlayer.inventory.addItem(item)
        }

        hunterPlayer.foodLevel = 20
        hunterPlayer.gameMode = GameMode.SURVIVAL
        hunterPlayer.health = 20.0
        hunterPlayer.saturation = 20f
        hunterPlayer.giveExp(-hunterPlayer.totalExperience)
        hunterPlayer.inventory.clear()

        val spawnLocation = generateSpawnLocation(streamerPlayer.location)
        hunterPlayer.teleport(spawnLocation)


        hunterPlayer.sendPrefixedMessage(
            Component.text(
                "You have ${config.timing.huntDurationMillis / 60000} minute(s) to eliminate ${streamerPlayer.name}",
                NamedTextColor.BLUE
            )
        )

        plugin.server.broadcastPrefixedMessage(
            Component.text(
                "Hunter ${hunterPlayer.name} is hunting ${streamerPlayer.name}",
                NamedTextColor.BLUE
            )
        )

        plugin.logger.info("Hunter ${hunterPlayer.name} spawned at ${spawnLocation.blockX}, ${spawnLocation.blockY}, ${spawnLocation.blockZ}")
    }

    private fun startHuntTimeout() {
        huntTimeoutJob = plugin.asyncScope.launch {
            delay(config.timing.huntDurationMillis)
            handleHuntTimeout()
        }
    }

    private fun startJoinTimeout() {
        joinTimeoutJob = plugin.asyncScope.launch {
            delay(config.timing.joinTimeoutMillis)
            handleJoinTimeout()
        }
    }
}