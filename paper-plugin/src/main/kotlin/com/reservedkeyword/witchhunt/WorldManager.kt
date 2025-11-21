package com.reservedkeyword.witchhunt

import com.reservedkeyword.witchhunt.models.WorldState
import com.reservedkeyword.witchhunt.utils.asyncDispatcher
import com.reservedkeyword.witchhunt.utils.broadcastPrefixedMessage
import com.reservedkeyword.witchhunt.utils.minecraftDispatcher
import com.reservedkeyword.witchhunt.utils.sendPrefixedMessage
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import org.bukkit.*
import java.io.File
import java.util.concurrent.atomic.AtomicReference

class WorldManager(private val plugin: WitchHuntPlugin) {
    companion object {
        const val HARDCORE_PREFIX = "hardcore_"
        const val LOBBY_WORLD_NAME = "lobby"
    }

    private val stateRef = AtomicReference<WorldState?>(null)

    private var pregenerationJob: Job? = null

    val currentState: WorldState get() = stateRef.get() ?: throw IllegalStateException("World manager not initialized")

    suspend fun activateNextWorld(): Pair<WorldState, Long> = withContext(plugin.asyncDispatcher()) {
        val worldToActive = currentState.nextWorld ?: run {
            plugin.logger.warning("Next world not ready! Generating now...")
            createHardcoreWorldWithSeed().first
        }

        val updatedState = currentState.withActiveWorld(worldToActive)
        stateRef.set(updatedState)

        val seed = worldToActive.seed
        plugin.logger.info("Activated world: ${worldToActive.name} (seed: $seed)")

        return@withContext Pair(updatedState, seed)
    }

    private suspend fun cleanupOldHardcoreWorlds() = withContext(plugin.asyncDispatcher()) {
        plugin.logger.info("Cleaning up old hardcore worlds...")

        val worldContainer = Bukkit.getWorldContainer()
        val hardcoreWorlds = worldContainer.listFiles()
            ?.filter { it.isDirectory && it.name.startsWith(HARDCORE_PREFIX) }
            ?: emptyList()

        hardcoreWorlds.forEach { worldDir ->
            plugin.logger.info("Removing old hardcore world: ${worldDir.name}")
            worldDir.deleteRecursively()
        }

        plugin.logger.info("Cleanup complete. Removed ${hardcoreWorlds.size} old world(s).")
    }

    private suspend fun configureHardcoreWorld(hardcoreWorld: World) = withContext(plugin.minecraftDispatcher()) {
        hardcoreWorld.setGameRule(GameRule.ANNOUNCE_ADVANCEMENTS, true)
        hardcoreWorld.setGameRule(GameRule.DO_DAYLIGHT_CYCLE, true)
        hardcoreWorld.setGameRule(GameRule.DO_IMMEDIATE_RESPAWN, false)
        hardcoreWorld.setGameRule(GameRule.DO_MOB_SPAWNING, true)
        hardcoreWorld.setSpawnFlags(true, true)

        hardcoreWorld.worldBorder.center = hardcoreWorld.spawnLocation
        hardcoreWorld.worldBorder.size = 10000.0 // Make this customizable in the future, maybe?
    }

    private suspend fun configureLobbyWorld(lobbyWorld: World) = withContext(plugin.minecraftDispatcher()) {
        lobbyWorld.difficulty = Difficulty.PEACEFUL
        lobbyWorld.setGameRule(GameRule.ANNOUNCE_ADVANCEMENTS, false)
        lobbyWorld.setGameRule(GameRule.DO_DAYLIGHT_CYCLE, false)
        lobbyWorld.setGameRule(GameRule.DO_MOB_SPAWNING, false)
        lobbyWorld.setGameRule(GameRule.DO_WEATHER_CYCLE, false)
        lobbyWorld.time = 6000 // Noon
    }

    private suspend fun createHardcoreWorldWithSeed(): Pair<World, Long> = withContext(plugin.minecraftDispatcher()) {
        val seed = System.currentTimeMillis()
        val attemptNumber = getNextAttemptName()
        val worldName = "$HARDCORE_PREFIX$attemptNumber"

        plugin.logger.info("Creating hardcore world, $worldName, with seed, $seed")

        val worldCreator = WorldCreator(worldName)
            .environment(World.Environment.NORMAL)
            .generateStructures(true)
            .hardcore(true)
            .seed(seed)
            .type(WorldType.NORMAL)

        val createdWorld =
            worldCreator.createWorld() ?: throw IllegalStateException("Failed to create hardcore world: $worldName")

        configureHardcoreWorld(createdWorld)

        plugin.logger.info("Created hardcore world")
        Pair(createdWorld, seed)
    }

    private suspend fun deleteLobbyDimensions() = withContext(plugin.asyncDispatcher()) {
        val worldContainer = Bukkit.getWorldContainer()
        val endFolder = File(worldContainer, "${LOBBY_WORLD_NAME}_the_end")
        val netherFolder = File(worldContainer, "${LOBBY_WORLD_NAME}_nether")

        if (endFolder.exists()) {
            endFolder.deleteRecursively()
            plugin.logger.info("Lobby End deleted recursively")
        }

        if (netherFolder.exists()) {
            netherFolder.deleteRecursively()
            plugin.logger.info("Lobby Nether deleted recursively")
        }
    }

    private suspend fun deleteWorldAsync(worldName: String) = withContext(plugin.asyncDispatcher()) {
        try {
            plugin.logger.info("Deleting world, $worldName")

            val world = Bukkit.getWorld(worldName)

            if (world != null) {
                withContext(plugin.minecraftDispatcher()) {
                    Bukkit.unloadWorld(world, false)
                }

                delay(1000)
            }

            val worldFolder = File(Bukkit.getWorldContainer(), worldName)

            if (worldFolder.exists()) {
                worldFolder.deleteRecursively()
                plugin.logger.info("World folder deleted, $worldName")
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun getNextAttemptName(): Int {
        val currentAttempts = Bukkit.getWorlds()
            .map { it.name }
            .filter { it.startsWith(HARDCORE_PREFIX) }
            .mapNotNull { it.removePrefix(HARDCORE_PREFIX).toIntOrNull() }
            .maxOrNull() ?: 0

        return currentAttempts + 1
    }

    suspend fun initialize(): WorldState {
        plugin.logger.info("Initializing world manager...")

        val lobbyWorld = loadOrCreateLobby()
        cleanupOldHardcoreWorlds()

        val initialState = WorldState(lobbyWorld = lobbyWorld)
        stateRef.set(initialState)

        pregenerateNextWorld()

        plugin.logger.info("World manager initialized!")
        return initialState
    }

    fun isNextWorldReady(): Boolean = currentState.nextWorld != null

    private suspend fun loadOrCreateLobby(): World {
        val existingWorld = Bukkit.getWorld(LOBBY_WORLD_NAME)

        if (existingWorld != null) {
            plugin.logger.info("Loaded existing lobby world")
            return existingWorld
        }

        val worldCreator = WorldCreator(LOBBY_WORLD_NAME)
            .environment(World.Environment.NORMAL)
            .generateStructures(false)
            .type(WorldType.FLAT)

        val createdWorld = worldCreator.createWorld() ?: throw IllegalStateException("Failed to create lobby world!")
        configureLobbyWorld(createdWorld)
        deleteLobbyDimensions()
        plugin.logger.info("Lobby world created")
        return createdWorld
    }

    private suspend fun moveAllPlayersToLobby(lobbyWorld: World) = withContext(plugin.minecraftDispatcher()) {
        val spawnLocation = lobbyWorld.spawnLocation

        Bukkit.getOnlinePlayers().forEach { player ->
            player.teleport(spawnLocation)
            player.gameMode = GameMode.ADVENTURE

            player.sendPrefixedMessage(
                Component.text(
                    "World is resetting, you have been moved to the lobby.",
                    NamedTextColor.BLUE
                )
            )
        }
    }

    private fun pregenerateNextWorld() {
        pregenerationJob?.cancel()

        pregenerationJob = plugin.asyncScope.launch {
            stateRef.set(currentState.withPregenerationStarted())

            try {
                plugin.logger.info("Pre-generating next world...")

                val (world, seed) = withContext(plugin.minecraftDispatcher()) {
                    createHardcoreWorldWithSeed()
                }

                preloadSpawnChunks(world)

                val updatedState = currentState.withNextWorld(world)
                stateRef.set(updatedState)
                plugin.logger.info("World ${world.name} pre-generated successfully (seed: $seed)")

                withContext(plugin.minecraftDispatcher()) {
                    plugin.server.broadcastPrefixedMessage(
                        Component.text(
                            "Next hardcore world has successfully pregenerated!",
                            NamedTextColor.GREEN
                        )
                    )
                }
            } catch (e: Exception) {
                e.printStackTrace()
                val updatedState = currentState.copy(pregenerationInProgress = false)
                stateRef.set(updatedState)
            }
        }
    }

    private suspend fun preloadSpawnChunks(world: World) = withContext(plugin.asyncDispatcher()) {
        val spawnLocation = world.spawnLocation
        val chunkX = spawnLocation.blockX shr 4
        val chunkZ = spawnLocation.blockZ shr 4
        val radius = 5

        plugin.logger.info("Pre-loading spawn chunks for ${world.name}...")

        for (x in -radius..radius) {
            for (z in -radius..radius) {
                withContext(plugin.minecraftDispatcher()) {
                    world.getChunkAtAsync(chunkX + x, chunkZ + z)
                }
            }
        }

        plugin.logger.info("Spawn chunks loaded for ${world.name}!")
    }

    suspend fun resetWorld(): WorldState {
        plugin.logger.info("Resetting world...")

        moveAllPlayersToLobby(currentState.lobbyWorld)
        pregenerateNextWorld()

        val oldWorld = currentState.activeWorld

        if (oldWorld != null) {
            deleteWorldAsync(oldWorld.name)
        }

        val updatedState = currentState.clearActiveWorld()
        stateRef.set(updatedState)
        return updatedState
    }

    fun shutdown() {
        pregenerationJob?.cancel()
        plugin.logger.info("World manager shut down!")
    }
}