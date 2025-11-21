package com.reservedkeyword.witchhunt.hud

import net.kyori.adventure.bossbar.BossBar
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import net.kyori.adventure.text.format.TextDecoration
import org.bukkit.Location
import kotlin.math.sqrt

object HudComponents {
    fun calculateDistance(loc1: Location, loc2: Location): Double {
        if (loc1.world != loc2.world) {
            return Double.MAX_VALUE
        }

        val dx = loc1.x - loc2.x
        val dz = loc1.z - loc2.z

        return sqrt(dx * dx + dz * dz)
    }

    fun calculateProgressRemaining(durationMillis: Long, startTimeMillis: Long): Float {
        val elapsedMillis = System.currentTimeMillis() - startTimeMillis
        val progressRemaining = elapsedMillis.toFloat() / durationMillis.toFloat()
        return (1f - progressRemaining).coerceIn(0f, 1f)
    }

    fun createDistanceText(distance: Double, label: String = "Distance"): Component {
        val distanceInt = distance.toInt()

        val textColor = when {
            distance < 50 -> NamedTextColor.RED
            distance < 150 -> NamedTextColor.YELLOW
            else -> NamedTextColor.GREEN
        }

        return Component.text("$label: ", NamedTextColor.GRAY)
            .append(Component.text("${distanceInt}m", textColor, TextDecoration.BOLD))
    }

    fun createTimerBar(color: BossBar.Color, progress: Float, title: String): BossBar = BossBar.bossBar(
        Component.text(title),
        progress.coerceIn(0f, 1f),
        color,
        BossBar.Overlay.PROGRESS
    )

    fun formatTimeRemaining(millisRemaining: Long): String {
        val toSeconds = (millisRemaining / 1000).coerceAtLeast(0)
        val minutesRemaining = toSeconds / 60
        val secondsRemaining = toSeconds % 60
        return String.format("%02d:%02d", minutesRemaining, secondsRemaining)
    }
}
