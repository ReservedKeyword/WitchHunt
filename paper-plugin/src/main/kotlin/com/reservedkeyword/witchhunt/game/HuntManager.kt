package com.reservedkeyword.witchhunt.game

import com.reservedkeyword.witchhunt.WitchHuntPlugin
import com.reservedkeyword.witchhunt.WorldManager
import com.reservedkeyword.witchhunt.models.*
import com.reservedkeyword.witchhunt.models.tracking.AttemptSummary
import com.reservedkeyword.witchhunt.utils.broadcastPrefixedMessage
import com.reservedkeyword.witchhunt.utils.minecraftDispatcher
import com.reservedkeyword.witchhunt.utils.sendPrefixedMessage
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.withContext
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import org.bukkit.GameMode
import org.bukkit.GameRule
import org.bukkit.entity.Player
import java.time.Instant

class HuntManager(private val plugin: WitchHuntPlugin) {
    private val gameState = MutableStateFlow(HuntGameState())
    private val seedTracker = SeedTracker(plugin)
    private val worldManager = WorldManager(plugin)

    private var activeHuntSession: HuntSession? = null

    val currentState: HuntGameState get() = gameState.value

    suspend fun clearActiveHuntSession() {
        transitionState(GameEvent.HuntSessionCleared)
        activeHuntSession?.cancel()
        activeHuntSession = null
    }

    suspend fun endGame(attemptOutcome: AttemptOutcome) {
        val currentAttempt = currentState.currentAttempt

        transitionState(GameEvent.End(attemptOutcome)).getOrElse { errorMessage ->
            plugin.logger.warning(errorMessage.message)
            return
        }

        clearActiveHuntSession()

        val outcomeText = when (attemptOutcome) {
            AttemptOutcome.CANCELLED -> "Cancelled"
            AttemptOutcome.DEATH -> "Death"
            AttemptOutcome.IN_PROGRESS -> "In Progress"
            AttemptOutcome.VICTORY -> "Victory"
        }

        plugin.server.broadcastPrefixedMessage(
            Component.text(
                "Game has ended for reason: $outcomeText",
                NamedTextColor.BLUE
            )
        )

        when (attemptOutcome) {
            AttemptOutcome.DEATH -> plugin.webhookClient.sendEvent("streamer-died")
            AttemptOutcome.VICTORY -> plugin.webhookClient.sendEvent("streamer-victory")
            else -> plugin.webhookClient.sendEvent("game-ended")
        }

        if (attemptOutcome != AttemptOutcome.VICTORY) {
            worldManager.resetWorld()
        }

        plugin.logger.info("Game ended. Attempt #${currentAttempt?.attemptNumber ?: "unknown"}, Outcome: $attemptOutcome")
    }

    fun getAttemptSummary(): AttemptSummary = seedTracker.generateSummary(currentState.attemptHistory)

    fun getCurrentAttemptNumber(): Int? = currentState.currentAttempt?.attemptNumber

    fun getActiveHuntSession(): HuntSession? = activeHuntSession

    fun getWorldManager(): WorldManager = worldManager

    suspend fun initialize() {
        worldManager.initialize()

        val loadedHistory = seedTracker.loadHistory()
        val initialState = HuntGameState(attemptHistory = loadedHistory)

        gameState.value = initialState
        plugin.logger.info("Loaded ${loadedHistory.size} attempt(s) from history")
    }

    fun isGameActive(): Boolean = currentState.isActive

    fun isGamePaused(): Boolean = currentState.isPaused

    fun isWorldReady(): Boolean = worldManager.isNextWorldReady()

    suspend fun pauseGame() {
        transitionState(GameEvent.Pause).getOrElse { errorMessage ->
            plugin.logger.warning(errorMessage.message)
            return
        }

        clearActiveHuntSession()

        val activeWorld = worldManager.currentState.activeWorld

        if (activeWorld != null) {
            withContext(plugin.minecraftDispatcher()) {
                activeWorld.setGameRule(GameRule.DO_DAYLIGHT_CYCLE, false)
                plugin.logger.info("Day-night cycle has been paused!")
            }
        }

        plugin.webhookClient.sendEvent(
            "game-paused", mapOf(
                "attempt" to (currentState.currentAttempt?.attemptNumber?.toString() ?: "unknown")
            )
        )

        plugin.server.broadcastPrefixedMessage(
            Component.text(
                "Streamer disconnected! Game has been automatically paused.",
                NamedTextColor.BLUE
            )
        )

        plugin.logger.info("Game paused successfully!")
    }

    suspend fun recordHunterEncounter(hunterEncounter: HunterEncounter) {
        transitionState(GameEvent.HunterEncountered(hunterEncounter)).getOrElse { errorMessage ->
            plugin.logger.warning(errorMessage.message)
        }
    }

    suspend fun resumeGame() {
        transitionState(GameEvent.Resume).getOrElse { errorMessage ->
            plugin.logger.warning(errorMessage.message)
            return
        }

        val activeWorld = worldManager.currentState.activeWorld

        if (activeWorld != null) {
            withContext(plugin.minecraftDispatcher()) {
                activeWorld.setGameRule(GameRule.DO_DAYLIGHT_CYCLE, true)
                plugin.logger.info("Day-night cycle has resumed.")
            }
        }

        plugin.webhookClient.sendEvent(
            "game-resumed", mapOf(
                "attempt" to (currentState.currentAttempt?.attemptNumber?.toString() ?: "unknown")
            )
        )

        plugin.server.broadcastPrefixedMessage(
            Component.text(
                "Streamer reconnected! Game will automatically resume.",
                NamedTextColor.GREEN
            )
        )

        plugin.logger.info("Game resumed successfully")
    }

    suspend fun setActiveHuntSession(huntSession: HuntSession, huntSessionState: HuntSessionState) {
        transitionState(GameEvent.HuntSessionStarted(huntSession, huntSessionState))
        activeHuntSession = huntSession
    }

    suspend fun shutdown() {
        clearActiveHuntSession()
        seedTracker.saveHistory(currentState.attemptHistory)
        worldManager.shutdown()
    }

    suspend fun startNewGame(streamerPlayer: Player): GameAttempt? {
        val transitionResult = transitionState(GameEvent.Start(streamerPlayer))

        val updatedState = transitionResult.getOrElse { errorMessage ->
            streamerPlayer.sendPrefixedMessage(
                Component.text(
                    errorMessage.message ?: "Unknown error message",
                    NamedTextColor.RED
                )
            )

            return null
        }

        val currentAttempt = updatedState.currentAttempt!!
        val activeWorld = worldManager.currentState.activeWorld!!

        withContext(plugin.minecraftDispatcher()) {
            streamerPlayer.foodLevel = 20
            streamerPlayer.gameMode = GameMode.SURVIVAL
            streamerPlayer.health = 20.0
            streamerPlayer.saturation = 20f
            streamerPlayer.giveExp(-streamerPlayer.totalExperience)
            streamerPlayer.inventory.clear()
            streamerPlayer.teleport(activeWorld.spawnLocation)
        }

        plugin.server.broadcastPrefixedMessage(
            Component.text(
                "Run started! Attempt #${currentAttempt.attemptNumber}",
                NamedTextColor.GREEN
            )
        )

        plugin.webhookClient.sendEvent(
            "game-started", mapOf(
                "attemptNumber" to currentAttempt.attemptNumber.toString(),
                "worldSeed" to currentAttempt.seed.toString()
            )
        )

        plugin.logger.info("New game started: Attempt #${currentAttempt.attemptNumber}, Seed: ${currentAttempt.seed}")
        return currentAttempt
    }

    private suspend fun transitionState(gameEvent: GameEvent): Result<HuntGameState> {
        val updatedState = when (gameEvent) {
            is GameEvent.End -> {
                if (currentState.currentAttempt == null || !currentState.isActive) {
                    return Result.failure(IllegalStateException("Cannot end game, no active game in progress"))
                }

                currentState.withEndedAttempt(gameEvent.attemptOutcome)
            }

            is GameEvent.HuntSessionCleared -> {
                currentState.clearHuntSession()
            }

            is GameEvent.HuntSessionStarted -> {
                currentState.withHuntSession(gameEvent.huntSessionState)
            }

            is GameEvent.HunterEncountered -> {
                if (!currentState.isActive) {
                    return Result.failure(IllegalStateException("Cannot record hunter encounter, game is not active"))
                }

                currentState.withHunterEncounter(gameEvent.hunterEncounter)
            }

            is GameEvent.Pause -> {
                if (!currentState.isActive || currentState.isPaused) {
                    return Result.failure(IllegalStateException("Cannot pause game, game not active or is paused"))
                }

                currentState.withPaused()
            }

            is GameEvent.Resume -> {
                if (!currentState.isActive || !currentState.isPaused) {
                    return Result.failure(IllegalStateException("Cannot resume game, game is not active or is not paused"))
                }

                currentState.withResumed()
            }

            is GameEvent.Start -> {
                if (currentState.isActive) {
                    return Result.failure(IllegalStateException("Cannot start new game, game is already active"))
                }

                if (!worldManager.isNextWorldReady()) {
                    return Result.failure(IllegalStateException("Cannot start game, next world is not currently ready."))
                }

                val attemptNumber = currentState.attemptHistory.size + 1
                val (worldState, worldSeed) = worldManager.activateNextWorld()

                val gameAttempt = GameAttempt(
                    attemptNumber = attemptNumber,
                    seed = worldSeed,
                    startedAt = Instant.now(),
                    streamerName = gameEvent.streamerPlayer.name,
                    worldName = worldState.activeWorld!!.name
                )

                currentState.withNewAttempt(gameAttempt)
            }
        }

        gameState.value = updatedState
        seedTracker.saveHistory(updatedState.attemptHistory)
        return Result.success(updatedState)
    }
}
