package com.reservedkeyword.witchhunt

import com.reservedkeyword.witchhunt.models.WorldState
import com.reservedkeyword.witchhunt.utils.asyncDispatcher
import com.reservedkeyword.witchhunt.utils.broadcastPrefixedMessage
import com.reservedkeyword.witchhunt.utils.minecraftDispatcher
import com.reservedkeyword.witchhunt.utils.sendPrefixedMessage
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import org.bukkit.*
import java.io.File

class WorldManager(private val plugin: WitchHuntPlugin) {
    companion object {
        const val HARDCORE_PREFIX = "hardcore_"
        const val LOBBY_WORLD_NAME = "lobby"

        const val WORLD_NOON_TIME = 6000L
    }

    private val worldScope = CoroutineScope(plugin.asyncScope.coroutineContext + SupervisorJob())
    private val worldState = MutableStateFlow<WorldState?>(null)

    val currentState: WorldState get() = worldState.value ?: error("World manager not initialized")

    suspend fun activateNextWorld(): Pair<WorldState, Long> {
        return withContext(plugin.asyncDispatcher()) {
            val worldToActive = currentState.nextWorld ?: run {
                plugin.logger.warning("Next world not ready! Generating now...")
                createHardcoreWorldWithSeed().first
            }

            updateState { it.withActiveWorld(worldToActive) }
            val worldSeed = worldToActive.seed
            plugin.logger.info("Activated world: ${worldToActive.name} (seed: $worldSeed)")
            Pair(currentState, worldSeed)
        }
    }

    private fun cleanupOldHardcoreWorlds() {
        worldScope.launch {
            plugin.logger.info("Cleaning up old hardcore worlds...")

            val worldContainer = Bukkit.getWorldContainer()
            val hardcoreWorlds = worldContainer.listFiles()
                ?.filter { it.isDirectory && (it.name.startsWith(HARDCORE_PREFIX) || it.name.contains("${HARDCORE_PREFIX}\\d+_(nether|the_end)".toRegex())) }
                ?: emptyList()

            hardcoreWorlds.forEach { worldDir ->
                plugin.logger.info("Removing old hardcore world: ${worldDir.name}")
                worldDir.deleteRecursively()
            }

            plugin.logger.info("Cleanup complete. Removed ${hardcoreWorlds.size} old world(s).")
        }
    }

    private suspend fun configureHardcoreWorld(hardcoreWorld: World) {
        withContext(plugin.minecraftDispatcher()) {
            hardcoreWorld.setGameRule(GameRule.ANNOUNCE_ADVANCEMENTS, true)
            hardcoreWorld.setGameRule(GameRule.DO_DAYLIGHT_CYCLE, true)
            hardcoreWorld.setGameRule(GameRule.DO_IMMEDIATE_RESPAWN, false)
            hardcoreWorld.setGameRule(GameRule.DO_MOB_SPAWNING, true)
            hardcoreWorld.setSpawnFlags(true, true)
        }
    }

    private suspend fun configureLobbyWorld(lobbyWorld: World) {
        withContext(plugin.minecraftDispatcher()) {
            lobbyWorld.difficulty = Difficulty.PEACEFUL
            lobbyWorld.setGameRule(GameRule.ANNOUNCE_ADVANCEMENTS, false)
            lobbyWorld.setGameRule(GameRule.DO_DAYLIGHT_CYCLE, false)
            lobbyWorld.setGameRule(GameRule.DO_MOB_SPAWNING, false)
            lobbyWorld.setGameRule(GameRule.DO_WEATHER_CYCLE, false)
            lobbyWorld.time = WORLD_NOON_TIME
        }
    }

    private suspend fun createHardcoreWorldDimensions(baseName: String, worldSeed: Long): Triple<World, World, World> {
        return withContext(plugin.minecraftDispatcher()) {
            val overworldCreator = WorldCreator(baseName)
                .environment(World.Environment.NORMAL)
                .generateStructures(true)
                .seed(worldSeed)
                .type(WorldType.NORMAL)

            val overworldWorld = overworldCreator.createWorld()
                ?: throw IllegalStateException("Failed to create hardcore overworld for: $baseName")

            val netherCreator = WorldCreator("${baseName}_nether")
                .environment(World.Environment.NETHER)
                .generateStructures(true)
                .seed(worldSeed)

            val netherWorld = netherCreator.createWorld()
                ?: throw IllegalStateException("Failed to create hardcore nether for: $baseName")

            val endCreator = WorldCreator("${baseName}_the_end")
                .environment(World.Environment.THE_END)
                .generateStructures(true)
                .seed(worldSeed)

            val endWorld =
                endCreator.createWorld() ?: throw IllegalStateException("Failed to create hardcore end for: $baseName")

            // Set difficulty for all worlds to HARD, effectively achieving the same as
            // hardcore, since we already manage kicking, state, etc.
            overworldWorld.difficulty = Difficulty.HARD
            netherWorld.difficulty = Difficulty.HARD
            endWorld.difficulty = Difficulty.HARD

            configureHardcoreWorld(overworldWorld)
            plugin.logger.info("Created Overworld, Nether, and The End dimensions for $baseName")
            Triple(overworldWorld, netherWorld, endWorld)
        }
    }

    private suspend fun createHardcoreWorldWithSeed(): Pair<World, Long> {
        return withContext(plugin.minecraftDispatcher()) {
            val attemptNumber = getNextAttemptName()
            val worldName = "$HARDCORE_PREFIX$attemptNumber"
            val worldSeed = System.currentTimeMillis()

            plugin.logger.info("Creating hardcore world, $worldName, with seed, $worldSeed")
            val (overworldWorld, _, _) = createHardcoreWorldDimensions(worldName, worldSeed)
            plugin.logger.info("Successfully created hardcore world!")
            Pair(overworldWorld, worldSeed)
        }
    }

    private suspend fun deleteLobbyDimensions() {
        withContext(plugin.asyncDispatcher()) {
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
    }

    private suspend fun deleteWorldAsync(worldName: String) {
        withContext(plugin.asyncDispatcher()) {
            try {
                plugin.logger.info("Deleting world, $worldName")

                val worldDimensions = listOf(worldName, "${worldName}_nether", "${worldName}_the_end")

                // Unload all worlds before deleting
                worldDimensions.forEach { dimensionName ->
                    val world = Bukkit.getWorld(dimensionName)

                    if (world != null) {
                        withContext(plugin.minecraftDispatcher()) {
                            Bukkit.unloadWorld(world, false)
                        }
                    }
                }

                // Delete all the world directories
                worldDimensions.forEach { dimensionName ->
                    val worldDir = File(Bukkit.getWorldContainer(), dimensionName)

                    if (worldDir.exists()) {
                        worldDir.deleteRecursively()
                        plugin.logger.info("World directory deleted: $worldDir")
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
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
        worldState.value = initialState

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
        plugin.logger.info("Lobby world successfully created")
        return createdWorld
    }

    private suspend fun moveAllPlayersToLobby(lobbyWorld: World) {
        withContext(plugin.minecraftDispatcher()) {
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
    }

    private fun pregenerateNextWorld() {
        worldScope.launch {
            updateState { it.withPregenerationStarted() }

            try {
                plugin.logger.info("Pre-generating next hardcore world...")

                val (world, worldSeed) = withContext(plugin.minecraftDispatcher()) {
                    createHardcoreWorldWithSeed()
                }

                preloadSpawnChunks(world)
                updateState { it.withNextWorld(world) }
                plugin.logger.info("World ${world.name} pre-generated (seed: $worldSeed).")

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
                updateState { it.copy(pregenerationInProgress = false) }
            }
        }
    }

    private suspend fun preloadSpawnChunks(world: World) {
        withContext(plugin.asyncDispatcher()) {
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
    }

    suspend fun resetWorld() {
        plugin.logger.info("Resetting world...")

        moveAllPlayersToLobby(currentState.lobbyWorld)
        pregenerateNextWorld()

        val oldWorld = currentState.activeWorld

        if (oldWorld != null) {
            deleteWorldAsync(oldWorld.name)
        }

        updateState { it.clearActiveWorld() }
    }

    fun shutdown() {
        worldScope.cancel()
        plugin.logger.info("World manager shut down!")
    }

    private fun updateState(updateFn: (WorldState) -> WorldState) {
        worldState.value?.let { currentWorldState ->
            worldState.value = updateFn(currentWorldState)
        }
    }
}
