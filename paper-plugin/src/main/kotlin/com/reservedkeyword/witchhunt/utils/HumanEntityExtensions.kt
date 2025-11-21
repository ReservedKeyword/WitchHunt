package com.reservedkeyword.witchhunt.utils

import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import net.kyori.adventure.text.format.TextDecoration
import org.bukkit.entity.HumanEntity

fun HumanEntity.sendPrefixedMessage(component: Component) =
    this.sendMessage(
        Component.text("[WitchHunt] ", NamedTextColor.LIGHT_PURPLE)
            .append(component.decoration(TextDecoration.BOLD, false))
    )