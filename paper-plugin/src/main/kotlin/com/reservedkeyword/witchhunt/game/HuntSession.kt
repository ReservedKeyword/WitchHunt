package com.reservedkeyword.witchhunt.game

import com.reservedkeyword.witchhunt.WitchHuntPlugin
import com.reservedkeyword.witchhunt.models.HunterEncounter
import com.reservedkeyword.witchhunt.models.HunterOutcome
import com.reservedkeyword.witchhunt.models.config.Config
import com.reservedkeyword.witchhunt.utils.broadcastPrefixedMessage
import com.reservedkeyword.witchhunt.utils.minecraftDispatcher
import com.reservedkeyword.witchhunt.utils.sendPrefixedMessage
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
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
    private val sessionScope = CoroutineScope(plugin.asyncScope.coroutineContext + SupervisorJob())
    private val sessionState = MutableStateFlow<SessionState>(SessionState.WaitingForJoin)

    private var hudStarted: Boolean = false

    val currentState: SessionState get() = sessionState.value

    private fun broadcastHunterDeath(hunterPlayer: Player) {
        plugin.server.broadcastPrefixedMessage(
            Component.text(
                "Hunter ${hunterPlayer.name} has been eliminated!",
                NamedTextColor.GREEN
            )
        )
    }

    suspend fun cancel() {
        transitionEvent(SessionEvent.Cancelled)
        sessionScope.cancel()

        withContext(plugin.minecraftDispatcher()) {
            val hunterPlayer = Bukkit.getPlayer(minecraftUsername)

            if (hunterPlayer != null) {
                cleanupOnlineHunter(hunterPlayer)
            } else {
                removeFromWhitelist()
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
            hudStarted = false
        }

        withContext(plugin.minecraftDispatcher()) {
            hunterPlayer.kick(Component.text("Your hunt has ended!", NamedTextColor.DARK_RED, TextDecoration.BOLD))
            removeFromWhitelist()
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

        val updatedState = transitionEvent(SessionEvent.HuntTimedOut).getOrElse {
            plugin.logger.warning("Failed to transition: ${it.message}")
            return
        }

        if (updatedState is SessionState.Ended) {
            recordEncounter(updatedState)

            withContext(plugin.minecraftDispatcher()) {
                val hunterPlayer = Bukkit.getPlayer(minecraftUsername)

                if (hunterPlayer != null) {
                    cleanupOnlineHunter(hunterPlayer)
                } else {
                    removeFromWhitelist()
                }
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

        val updatedState = transitionEvent(SessionEvent.HunterDied(hunterPlayer)).getOrElse {
            plugin.logger.warning("Failed to transition: ${it.message}")
            return
        }

        if (updatedState is SessionState.Ended) {
            recordEncounter(updatedState)
            cleanupOnlineHunter(hunterPlayer)
            broadcastHunterDeath(hunterPlayer)
        }
    }

    private fun startHudUpdates(hunterPlayer: Player, joinedAt: Instant) {
        plugin.hudManager.startUpdates(
            huntDurationMillis = config.timing.huntDurationMillis,
            huntStartTime = joinedAt,
            hunterPlayer = hunterPlayer,
            streamerPlayer = streamerPlayer
        )

        hudStarted = true
    }


    suspend fun handleHunterJoin(hunterPlayer: Player) {
        plugin.logger.info("Hunter ${hunterPlayer.name} joined the server")

        val updatedState = transitionEvent(SessionEvent.HunterJoined(hunterPlayer)).getOrElse { error ->
            plugin.logger.warning("Failed to process hunter join: ${error.message}")
            return
        }

        if (updatedState is SessionState.Active) {
            withContext(plugin.minecraftDispatcher()) {
                setupHunter(hunterPlayer, updatedState.spawnLocation)
            }

            startHudUpdates(
                hunterPlayer = hunterPlayer,
                joinedAt = updatedState.joinedAt
            )

            scheduleHuntTimeout()
            notifyHunterJoined()
        }
    }

    private suspend fun handleJoinTimeout() {
        plugin.logger.info("Join timed out for $twitchUsername ($minecraftUsername): did not show in time")

        transitionEvent(SessionEvent.JoinTimedOut).getOrElse {
            plugin.logger.warning("Failed to transition: ${it.message}")
            return
        }

        removeFromWhitelist()
        plugin.huntManager.clearActiveHuntSession()

        if (plugin.configManager.getConfig().noShowBehavior.immediateReselect) {
            plugin.logger.info("Immediate reselect enabled, notify Twitch bot to send a new player...")
            notifyTwitchBot("no-show-immediate-reselect")
        }
    }

    private suspend fun notifyHunterJoined() {
        plugin.webhookClient.sendEvent(
            "hunter-joined",
            mapOf("huntDurationMillis" to config.timing.huntDurationMillis.toString())
        )
    }

    private suspend fun notifyTwitchBot(event: String, data: Map<String, String>? = null) {
        try {
            plugin.webhookClient.sendEvent(event, data)
        } catch (e: Exception) {
            plugin.logger.warning("Failed to notify Twitch bot: ${e.message}")
        }
    }

    private suspend fun recordEncounter(endedState: SessionState.Ended) {
        val hunterEncounter = HunterEncounter(
            endedAt = endedState.endedAt,
            hunterMinecraftName = minecraftUsername,
            hunterTwitchName = twitchUsername,
            joinedAt = endedState.joinedAt,
            outcome = endedState.hunterOutcome
        )

        plugin.huntManager.recordHunterEncounter(hunterEncounter)
    }

    private suspend fun removeFromWhitelist() {
        withContext(plugin.minecraftDispatcher()) {
            Bukkit.getOfflinePlayer(minecraftUsername).isWhitelisted = false
        }
    }

    suspend fun start() {
        plugin.logger.info("Hunt session started for $twitchUsername ($minecraftUsername)")

        withContext(plugin.minecraftDispatcher()) {
            Bukkit.getOfflinePlayer(minecraftUsername).isWhitelisted = true
        }

        transitionEvent(SessionEvent.Started)
        scheduleJoinTimeout()
    }

    private fun setupHunter(hunterPlayer: Player, spawnLocation: Location) {
        hunterPlayer.inventory.clear()
        val itemsLoadout = LoadoutGenerator(plugin).generateLoadout()
        itemsLoadout.forEach { item -> hunterPlayer.inventory.addItem(item) }

        hunterPlayer.foodLevel = 20
        hunterPlayer.gameMode = GameMode.SURVIVAL
        hunterPlayer.health = 20.0
        hunterPlayer.saturation = 20f

        hunterPlayer.giveExp(-hunterPlayer.totalExperience)
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

    private fun scheduleHuntTimeout() {
        sessionScope.launch {
            delay(config.timing.huntDurationMillis)
            handleHuntTimeout()
        }
    }

    private fun scheduleJoinTimeout() {
        sessionScope.launch {
            delay(config.timing.joinTimeoutMillis)
            handleJoinTimeout()
        }
    }

    private fun transitionEvent(sessionEvent: SessionEvent): Result<SessionState> {
        val current = currentState

        val updatedState = when (sessionEvent) {
            is SessionEvent.Cancelled -> {
                val joinedAt = (current as? SessionState.Active)?.joinedAt

                SessionState.Ended(
                    endedAt = Instant.now(),
                    hunterOutcome = HunterOutcome.CANCELLED,
                    joinedAt = joinedAt
                )
            }

            is SessionEvent.HuntTimedOut -> {
                if (current !is SessionState.Active) {
                    return Result.failure(IllegalStateException("No active hunt session to time out"))
                }

                SessionState.Ended(
                    endedAt = Instant.now(),
                    hunterOutcome = HunterOutcome.HUNT_TIMEOUT,
                    joinedAt = current.joinedAt
                )
            }

            is SessionEvent.HunterDied -> {
                if (current !is SessionState.Active) {
                    return Result.failure(IllegalStateException("No active hunt to end"))
                }

                SessionState.Ended(
                    endedAt = Instant.now(),
                    hunterOutcome = HunterOutcome.DIED,
                    joinedAt = current.joinedAt
                )
            }

            is SessionEvent.HunterJoined -> {
                if (current !is SessionState.WaitingForJoin) {
                    return Result.failure(IllegalStateException("Hunter already joined or session ended"))
                }

                val spawnLocation = generateSpawnLocation(streamerPlayer.location)

                SessionState.Active(
                    joinedAt = Instant.now(),
                    spawnLocation = spawnLocation
                )
            }

            is SessionEvent.JoinTimedOut -> {
                if (current !is SessionState.WaitingForJoin) {
                    return Result.failure(IllegalStateException("Joined already occurred or session ended"))
                }

                SessionState.Ended(
                    endedAt = Instant.now(),
                    hunterOutcome = HunterOutcome.JOIN_TIMEOUT,
                    joinedAt = null
                )
            }

            is SessionEvent.Started -> {
                if (current !is SessionState.WaitingForJoin) {
                    return Result.failure(IllegalStateException("Session already started"))
                }

                current
            }
        }

        sessionState.value = updatedState
        return Result.success(updatedState)
    }
}