package com.reservedkeyword.witchhunt.listeners

import com.reservedkeyword.witchhunt.WitchHuntPlugin
import com.reservedkeyword.witchhunt.WorldManager
import com.reservedkeyword.witchhunt.utils.sendPrefixedMessage
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import org.bukkit.Bukkit
import org.bukkit.Location
import org.bukkit.Material
import org.bukkit.World
import org.bukkit.block.Block
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.player.PlayerPortalEvent
import org.bukkit.event.player.PlayerTeleportEvent
import kotlin.math.abs

class PlayerPortalListener(private val plugin: WitchHuntPlugin) : Listener {
    companion object {
        private val HARDCORE_DIMENSION_REGEX = Regex("${WorldManager.HARDCORE_PREFIX}\\d+_(nether|the_end)")

        private const val NETHER_SCALE_DOWN = 0.125
        private const val NETHER_SCALE_UP = 8.0

        private const val PORTAL_SEARCH_RADIUS_NETHER = 16
        private const val PORTAL_SEARCH_RADIUS_OVERWORLD = 128
    }

    private fun createEndPlatform(location: Location, world: World) {
        location.run {
            val platformY = blockY - 1

            (-2..2).forEach { x ->
                (-2..2).forEach { z ->
                    world.getBlockAt(blockX + x, platformY, blockZ + z).type = Material.OBSIDIAN

                    (1..3).forEach { y ->
                        world.getBlockAt(blockX + x, platformY + y, blockZ + z).type = Material.AIR
                    }
                }
            }
        }
    }

    private fun createNetherPortal(targetLocation: Location, world: World): Location {
        val buildLocation = findSafeNetherPortalBuildLocation(targetLocation, world)

        val baseX = buildLocation.blockX
        val baseY = buildLocation.blockY
        val baseZ = buildLocation.blockZ

        (0..4).forEach { y ->
            (0..3).forEach { x ->
                val isFrame = y == 0 || y == 4 || x == 0 || x == 3
                val block = world.getBlockAt(baseX + x, baseY + y, baseZ)

                if (isFrame) {
                    block.type = Material.OBSIDIAN
                } else if (y in 1..3 && x in 1..2) {
                    block.type = Material.AIR
                }
            }
        }

        val fireLocation = world.getBlockAt(baseX + 1, baseY + 1, baseZ)
        fireLocation.type = Material.FIRE
        return Location(world, baseX + 1.5, baseY + 1.0, baseZ + 0.5)
    }

    private fun createNetherPortalPlatform(targetLocation: Location, world: World): Location {
        val baseY = targetLocation.blockY.coerceIn(world.minHeight + 10, world.maxHeight - 10)

        (-1..4).forEach { x ->
            (-1..1).forEach { z ->
                world.getBlockAt(targetLocation.blockX + x, baseY, targetLocation.blockZ + z).type = Material.OBSIDIAN
            }
        }

        (-1..4).forEach { x ->
            (-1..1).forEach { z ->
                (1..5).forEach { y ->
                    world.getBlockAt(targetLocation.blockX + x, baseY + y, targetLocation.blockZ + z).type =
                        Material.AIR
                }
            }
        }

        return Location(
            world,
            targetLocation.blockX.toDouble(),
            (baseY + 1).toDouble(),
            targetLocation.blockZ.toDouble()
        )
    }

    private fun findExistingNetherPortal(searchRadius: Int, targetLocation: Location, world: World): Location? {
        val centerX = targetLocation.blockX
        val centerZ = targetLocation.blockZ

        val maxY = world.maxHeight
        val minY = world.minHeight

        ((centerX - searchRadius)..(centerX + searchRadius)).forEach { x ->
            ((centerZ - searchRadius)..(centerZ + searchRadius)).forEach { z ->
                (maxY downTo minY).forEach { y ->
                    val block = world.getBlockAt(x, y, z)

                    if (block.type == Material.NETHER_PORTAL) {
                        return block.location.add(0.5, 0.0, 0.5)
                    }
                }
            }
        }
        return null
    }

    private fun findSafeNetherPortalBuildLocation(targetLocation: Location, world: World): Location {
        val searchRadius = 16
        var closestDistance = Double.MAX_VALUE
        var closestLocation: Location? = null

        val maxSearchY = world.maxHeight
        val minSearchY = world.minHeight

        // Wide pass: 3x4 area with 4 blocks of air above
        (0..searchRadius).forEach { distance ->
            (-distance..distance).forEach { xOffset ->
                (-distance..distance).forEach zLoop@{ zOffset ->
                    if (abs(xOffset) != distance && abs(zOffset) != distance) return@zLoop

                    (maxSearchY downTo minSearchY).forEach { y ->
                        val testLocation = Location(
                            world,
                            targetLocation.blockX + xOffset.toDouble(),
                            y.toDouble(),
                            targetLocation.blockZ + zOffset.toDouble()
                        )

                        if (isValidWidePortalLocation(testLocation, world)) {
                            val distance = testLocation.distance(targetLocation)

                            if (distance < closestDistance) {
                                closestDistance = distance
                                closestLocation = testLocation
                            }
                        }
                    }
                }
            }
        }

        if (closestLocation != null) {
            return closestLocation
        }

        // Narrow pass: 1x4 area with 4 blocks of air above
        closestDistance = Double.MAX_VALUE
        closestLocation = null

        (0..searchRadius).forEach { distance ->
            (-distance..distance).forEach { xOffset ->
                (-distance..distance).forEach zLoop@{ zOffset ->
                    if (abs(xOffset) != distance && abs(zOffset) != distance) return@zLoop

                    (maxSearchY downTo minSearchY).forEach { y ->
                        val testLocation = Location(
                            world,
                            targetLocation.blockX + xOffset.toDouble(),
                            y.toDouble(),
                            targetLocation.blockZ + zOffset.toDouble()
                        )

                        if (isValidNarrowPortalLocation(testLocation, world)) {
                            val distance = testLocation.distance(targetLocation)

                            if (distance < closestDistance) {
                                closestDistance = distance
                                closestLocation = testLocation
                            }
                        }
                    }
                }
            }
        }

        if (closestLocation != null) {
            return closestLocation
        }

        // Force-clamp portal
        val clampedY = targetLocation.blockY.coerceIn(64, world.maxHeight - 16)
        val clampedLocation =
            Location(world, targetLocation.blockX.toDouble(), clampedY.toDouble(), targetLocation.blockZ.toDouble())
        return createNetherPortalPlatform(clampedLocation, world)
    }

    private fun handleEndPortal(baseWorldName: String, fromWorld: World, player: Player) {
        val targetWorldName = when (fromWorld.environment) {
            World.Environment.THE_END -> baseWorldName
            else -> "${baseWorldName}_the_end"
        }

        val targetWorld = Bukkit.getWorld(targetWorldName) ?: run {
            player.sendPrefixedMessage(Component.text("Target dimension not found!", NamedTextColor.RED))
            plugin.logger.warning("Failed to find The End target world: $targetWorldName")
            return
        }

        val targetLocation = when (targetWorld.environment) {
            World.Environment.THE_END -> Location(targetWorld, 100.0, 48.0, 0.0).also {
                createEndPlatform(
                    location = it,
                    world = targetWorld
                )
            }

            else -> player.respawnLocation ?: targetWorld.spawnLocation
        }
        player.teleport(targetLocation)
    }

    private fun handleNetherPortal(baseWorldName: String, fromWorld: World, player: Player) {
        val targetWorldName = when (fromWorld.environment) {
            World.Environment.NORMAL -> "${baseWorldName}_nether"
            else -> baseWorldName
        }

        val targetWorld = Bukkit.getWorld(targetWorldName) ?: run {
            player.sendPrefixedMessage(Component.text("Target dimension not found!", NamedTextColor.RED))
            plugin.logger.warning("Failed to find Nether target world: $targetWorldName")
            return
        }

        val scale = if (fromWorld.environment == World.Environment.NORMAL) NETHER_SCALE_DOWN else NETHER_SCALE_UP

        val searchRadius =
            if (fromWorld.environment == World.Environment.NORMAL) PORTAL_SEARCH_RADIUS_NETHER else PORTAL_SEARCH_RADIUS_OVERWORLD

        player.location.run {
            val targetLocation = Location(
                targetWorld,
                x * scale,
                y.coerceIn(targetWorld.minHeight.toDouble(), targetWorld.maxHeight - 1.0),
                z * scale,
                yaw,
                pitch
            )

            val netherPortalLocation = findExistingNetherPortal(
                searchRadius = searchRadius,
                targetLocation = targetLocation,
                world = targetWorld
            ) ?: createNetherPortal(
                targetLocation = targetLocation,
                world = targetWorld
            )

            player.teleport(netherPortalLocation)
        }
    }

    private fun isHardcoreWorld(world: World): Boolean =
        world.name.startsWith(WorldManager.HARDCORE_PREFIX) || world.name.contains(HARDCORE_DIMENSION_REGEX)

    private fun isReplaceable(block: Block): Boolean = block.type.isAir || (!block.isLiquid && !block.type.isSolid)

    private fun isValidNarrowPortalLocation(location: Location, world: World): Boolean {
        (0..3).forEach { z ->
            val floorBlock = world.getBlockAt(location.blockX, location.blockY, location.blockZ + z)
            if (!floorBlock.type.isSolid || floorBlock.isLiquid) return false

            (1..4).forEach { y ->
                val blockAbove = world.getBlockAt(location.blockX, location.blockY + y, location.blockZ + z)
                if (!isReplaceable(blockAbove)) return false
            }
        }
        return true
    }

    private fun isValidWidePortalLocation(location: Location, world: World): Boolean {
        (0..2).forEach { x ->
            (0..3).forEach { z ->
                val floorBlock = world.getBlockAt(location.blockX + x, location.blockY, location.blockZ + z)
                if (!floorBlock.type.isSolid || floorBlock.isLiquid) return false

                (1..4).forEach { y ->
                    val blockAbove = world.getBlockAt(location.blockX + x, location.blockY + y, location.blockZ + z)
                    if (!isReplaceable(blockAbove)) return false
                }
            }
        }
        return true
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    fun onPlayerPortaled(event: PlayerPortalEvent) {
        val player = event.player
        val fromWorld = player.world

        if (!isHardcoreWorld(fromWorld)) return
        val baseWorldName = fromWorld.name.replace(Regex("_(nether|the_end)$"), "")

        when (event.cause) {
            PlayerTeleportEvent.TeleportCause.END_PORTAL -> {
                event.isCancelled = true

                handleEndPortal(
                    baseWorldName = baseWorldName,
                    fromWorld = fromWorld,
                    player = player
                )
            }

            PlayerTeleportEvent.TeleportCause.NETHER_PORTAL -> {
                event.isCancelled = true

                handleNetherPortal(
                    baseWorldName = baseWorldName,
                    fromWorld = fromWorld,
                    player = player
                )
            }

            else -> Unit
        }
    }
}
