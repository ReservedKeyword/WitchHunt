package com.reservedkeyword.witchhunt

import com.reservedkeyword.witchhunt.models.AttemptOutcome
import com.reservedkeyword.witchhunt.utils.sendPrefixedMessage
import kotlinx.coroutines.launch
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import org.bukkit.command.Command
import org.bukkit.command.CommandExecutor
import org.bukkit.command.CommandSender
import org.bukkit.command.TabCompleter
import org.bukkit.entity.Player

class HuntCommand(private val plugin: WitchHuntPlugin) : CommandExecutor, TabCompleter {
    private fun handleEnd(player: Player, args: Array<out String>) {
        if (args.size < 2) {
            player.sendPrefixedMessage(Component.text("Usage: /hunt end <cancel|death|victory>", NamedTextColor.RED))
            return
        }

        val chosenOutcome = when (args[1].lowercase()) {
            "cancel" -> AttemptOutcome.CANCELLED
            "death" -> AttemptOutcome.DEATH
            "victory" -> AttemptOutcome.VICTORY
            else -> {
                player.sendPrefixedMessage(
                    Component.text(
                        "Invalid outcome. Must be: cancel, death, or victory",
                        NamedTextColor.RED
                    )
                )

                return
            }
        }

        plugin.asyncScope.launch {
            try {
                plugin.huntManager.endGame(chosenOutcome)
            } catch (e: Exception) {
                player.sendPrefixedMessage(Component.text("Failed to end game. Check console.", NamedTextColor.RED))
                e.printStackTrace()
            }
        }
    }

    private fun handleStart(player: Player) {
        plugin.asyncScope.launch {
            try {
                plugin.huntManager.startNewGame(player)
            } catch (e: Exception) {
                player.sendPrefixedMessage(
                    Component.text(
                        "Failed to start new game. Check console.",
                        NamedTextColor.RED
                    )
                )

                e.printStackTrace()
            }
        }
    }

    private fun handleStats(player: Player) {
        try {
            val attemptSummary = plugin.huntManager.getAttemptSummary()

            player.sendPrefixedMessage(Component.text("--- ATTEMPT STATISTICS ---"))
            player.sendPrefixedMessage(Component.text("Cancelled: ${attemptSummary.cancelled}"))
            player.sendPrefixedMessage(Component.text("Deaths: ${attemptSummary.deaths}"))
            player.sendPrefixedMessage(Component.text("Victories: ${attemptSummary.victories}"))
            player.sendPrefixedMessage(Component.text("Total Attempts: ${attemptSummary.totalAttempts}"))
        } catch (e: Exception) {
            player.sendPrefixedMessage(Component.text("Failed to fetch statistics. Check console", NamedTextColor.RED))
            e.printStackTrace()
        }
    }

    private fun handleStatus(player: Player) {
        try {
            val attemptNumber = plugin.huntManager.getCurrentAttemptNumber()
            val gameActive = plugin.huntManager.isGameActive()
            val worldState = plugin.huntManager.getWorldManager().currentState

            player.sendPrefixedMessage(Component.text("--- WORLD STATUS ---"))

            if (worldState.activeWorld != null) {
                player.sendPrefixedMessage(Component.text("Active World: ${worldState.activeWorld.name} (seed: ${worldState.activeWorld.seed})"))
            } else {
                player.sendPrefixedMessage(Component.text("Active World: None"))
            }

            player.sendPrefixedMessage(Component.text("Game Active: ${if (gameActive) "Yes (Attempt #$attemptNumber)" else "No"}"))
            player.sendPrefixedMessage(Component.text("Lobby World: ${worldState.lobbyWorld.name}"))

            if (worldState.nextWorld != null) {
                player.sendPrefixedMessage(Component.text("Next World: ${worldState.nextWorld.name} (seed: ${worldState.nextWorld.seed})"))
            } else if (worldState.pregenerationInProgress) {
                player.sendPrefixedMessage(Component.text("Next World: Pregenerating..."))
            } else {
                player.sendPrefixedMessage(Component.text("Next World: Not Started"))
            }
        } catch (e: Exception) {
            player.sendPrefixedMessage(
                Component.text(
                    "Failed to fetch world status. Check console.",
                    NamedTextColor.RED
                )
            )

            e.printStackTrace()
        }
    }

    override fun onCommand(
        sender: CommandSender,
        command: Command,
        label: String,
        args: Array<out String>
    ): Boolean {
        if (sender !is Player) {
            sender.sendMessage("This command can only be used by a player")
            return true
        }

        if (!sender.hasPermission("witchhunt.admin")) {
            sender.sendPrefixedMessage(
                Component.text(
                    "You don't have permission to use this command!",
                    NamedTextColor.RED
                )
            )

            return true
        }

        if (args.isEmpty()) {
            showHelp(sender)
            return true
        }

        when (args[0].lowercase()) {
            "end" -> handleEnd(sender, args)
            "start" -> handleStart(sender)
            "stats" -> handleStats(sender)
            "status" -> handleStatus(sender)
            else -> {
                sender.sendPrefixedMessage(
                    Component.text(
                        "Unknown subcommand. Use /hunt for help.",
                        NamedTextColor.RED
                    )
                )
            }
        }

        return true
    }

    override fun onTabComplete(
        sender: CommandSender,
        command: Command,
        label: String,
        args: Array<out String>
    ): List<String?> {
        if (args.size == 1) {
            return listOf("end", "start", "stats", "status")
                .filter { it.startsWith(args[0].lowercase()) }
        }

        if (args.size == 2 && args[0].lowercase() == "end") {
            return listOf("cancel", "death", "victory")
                .filter { it.startsWith(args[1].lowercase()) }
        }
        return emptyList()
    }

    private fun showHelp(player: Player) {
        player.sendPrefixedMessage(Component.text("--- AVAILABLE COMMANDS ---"))
        player.sendPrefixedMessage(Component.text("/hunt end <cancel|death|victory> - Ends the current game"))
        player.sendPrefixedMessage(Component.text("/hunt start - Starts a new hardcore run"))
        player.sendPrefixedMessage(Component.text("/hunt stats - View all attempt statistics"))
        player.sendPrefixedMessage(Component.text("/hunt status - Check the current world status"))
    }
}