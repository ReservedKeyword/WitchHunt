package com.reservedkeyword.witchhunt.utils

import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import net.kyori.adventure.text.format.TextDecoration
import org.bukkit.Server

fun Server.broadcastPrefixedMessage(component: Component) {
    this.broadcast(
        Component.text("[WitchHunt] ", NamedTextColor.LIGHT_PURPLE)
            .append(component.colorIfAbsent(NamedTextColor.WHITE).decoration(TextDecoration.BOLD, false))
    )
}
