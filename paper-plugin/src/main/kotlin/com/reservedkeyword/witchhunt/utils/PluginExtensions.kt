package com.reservedkeyword.witchhunt.utils

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Runnable
import org.bukkit.Bukkit
import org.bukkit.plugin.Plugin
import kotlin.coroutines.CoroutineContext

fun Plugin.asyncDispatcher(): CoroutineDispatcher {
    return object : CoroutineDispatcher() {
        override fun dispatch(context: CoroutineContext, block: Runnable) {
            if (!Bukkit.isPrimaryThread()) {
                block.run()
            } else {
                Bukkit.getScheduler().runTaskAsynchronously(this@asyncDispatcher, block)
            }
        }
    }
}

fun Plugin.minecraftDispatcher(): CoroutineDispatcher {
    return object : CoroutineDispatcher() {
        override fun dispatch(context: CoroutineContext, block: Runnable) {
            if (Bukkit.isPrimaryThread()) {
                block.run()
            } else {
                Bukkit.getScheduler().runTask(this@minecraftDispatcher, block)
            }
        }
    }
}
