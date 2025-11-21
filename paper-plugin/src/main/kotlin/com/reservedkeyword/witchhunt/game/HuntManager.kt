package com.reservedkeyword.witchhunt.game

import com.reservedkeyword.witchhunt.WitchHuntPlugin
import com.reservedkeyword.witchhunt.WorldManager
import com.reservedkeyword.witchhunt.models.*
import com.reservedkeyword.witchhunt.models.tracking.AttemptSummary
import com.reservedkeyword.witchhunt.utils.broadcastPrefixedMessage
import com.reservedkeyword.witchhunt.utils.minecraftDispatcher
import com.reservedkeyword.witchhunt.utils.sendPrefixedMessage
import kotlinx.coroutines.withContext
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import org.bukkit.GameMode
import org.bukkit.GameRule
import org.bukkit.entity.Player
import java.time.Instant
import java.util.concurrent.atomic.AtomicReference

class HuntManager(private val plugin: WitchHuntPlugin) {
    private val seedTracker = SeedTracker(plugin)
    private val stateRef = AtomicReference<HuntGameState>(HuntGameState())
    private val worldManager = WorldManager(plugin)

    private var activeHuntSession: HuntSession? = null

    val currentState: HuntGameState get() = stateRef.get()

    suspend fun clearActiveHuntSession() {
        val updatedState = currentState.clearHuntSession()
        stateRef.set(updatedState)

        activeHuntSession?.cancel()
        activeHuntSession = null
    }

    suspend fun endGame(attemptOutcome: AttemptOutcome) {
        if (currentState.currentAttempt == null || !currentState.isActive) {
            plugin.logger.warning("Attempted to end game, but no game is currently active")
            return
        }

        val currentAttempt = currentState.currentAttempt!!
        clearActiveHuntSession()

        val updatedState = currentState.withEndedAttempt(attemptOutcome)
        stateRef.set(updatedState)
        seedTracker.saveHistory(updatedState.attemptHistory)

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

        plugin.logger.info("Game ended. Attempt #${currentAttempt.attemptNumber}, Outcome: $attemptOutcome")
    }

    fun getAttemptSummary(): AttemptSummary = seedTracker.generateSummary(currentState.attemptHistory)

    fun getCurrentAttemptNumber(): Int? = currentState.currentAttempt?.attemptNumber

    fun getActiveHuntSession(): HuntSession? = activeHuntSession

    fun getWorldManager(): WorldManager = worldManager

    suspend fun initialize() {
        worldManager.initialize()

        val loadedHistory = seedTracker.loadHistory()
        val initialState = HuntGameState(attemptHistory = loadedHistory)
        stateRef.set(initialState)

        plugin.logger.info("Loaded ${loadedHistory.size} attempt(s) from history")
    }

    fun isGameActive(): Boolean = currentState.isActive

    fun isGamePaused(): Boolean = currentState.isPaused

    fun isWorldReady(): Boolean = worldManager.isNextWorldReady()

    suspend fun pauseGame() {
        if (!currentState.isActive || currentState.isPaused) {
            plugin.logger.warning("Game cannot paused, it's not active or already paused")
            return
        }

        plugin.logger.info("Pausing game...")
        clearActiveHuntSession()

        val updatedState = currentState.withPaused()
        stateRef.set(updatedState)

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

    suspend fun recordHunterEncounter(hunterEncounter: HunterEncounter): HuntGameState {
        val updatedState = currentState.withHunterEncounter(hunterEncounter)
        stateRef.set(updatedState)
        seedTracker.saveHistory(updatedState.attemptHistory)
        return updatedState
    }

    suspend fun resumeGame() {
        if (!currentState.isActive || !currentState.isPaused) {
            plugin.logger.warning("Game cannot resume, it's already active or not paused")
            return
        }

        plugin.logger.info("Resuming game...")

        val updatedState = currentState.withResumed()
        stateRef.set(updatedState)

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

    fun setActiveHuntSession(huntSession: HuntSession, huntSessionState: HuntSessionState) {
        val updatedState = currentState.withHuntSession(huntSessionState)
        stateRef.set(updatedState)
        activeHuntSession = huntSession
    }

    suspend fun shutdown() {
        clearActiveHuntSession()
        seedTracker.saveHistory(currentState.attemptHistory)
        worldManager.shutdown()
    }

    suspend fun startNewGame(streamerPlayer: Player): GameAttempt? {
        if (currentState.isActive) {
            streamerPlayer.sendPrefixedMessage(Component.text("Game is already active!", NamedTextColor.RED))
            return null
        }

        if (!worldManager.isNextWorldReady()) {
            streamerPlayer.sendPrefixedMessage(
                Component.text(
                    "World is still being pre-generated...",
                    NamedTextColor.BLUE
                )
            )

            return null
        }

        val (worldState, worldSeed) = worldManager.activateNextWorld()
        val world = worldState.activeWorld!!

        withContext(plugin.minecraftDispatcher()) {
            streamerPlayer.foodLevel = 20
            streamerPlayer.gameMode = GameMode.SURVIVAL
            streamerPlayer.health = 20.0
            streamerPlayer.saturation = 20f
            streamerPlayer.giveExp(-streamerPlayer.totalExperience)
            streamerPlayer.inventory.clear()
            streamerPlayer.teleport(world.spawnLocation)
        }

        val attemptNumber = currentState.attemptHistory.size + 1

        val gameAttempt = GameAttempt(
            attemptNumber = attemptNumber,
            seed = worldSeed,
            startedAt = Instant.now(),
            streamerName = streamerPlayer.name,
            worldName = world.name
        )

        val updatedState = currentState.withNewAttempt(gameAttempt)
        stateRef.set(updatedState)

        plugin.server.broadcastPrefixedMessage(
            Component.text(
                "Run started! Attempt $attemptNumber",
                NamedTextColor.GREEN
            )
        )

        plugin.webhookClient.sendEvent(
            "game-started", mapOf(
                "attemptNumber" to attemptNumber.toString(),
                "worldSeed" to worldSeed.toString()
            )
        )

        plugin.logger.info("New game started: Attempt #$attemptNumber, Seed: $worldSeed")
        return gameAttempt
    }
}