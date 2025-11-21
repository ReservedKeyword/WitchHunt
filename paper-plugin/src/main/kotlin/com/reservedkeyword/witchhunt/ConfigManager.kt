package com.reservedkeyword.witchhunt

import com.reservedkeyword.witchhunt.models.config.*

class ConfigManager(private val plugin: WitchHuntPlugin) {
    private var config: Config? = null

    fun getConfig(): Config {
        return config ?: throw IllegalStateException("Config not loaded! Call loadConfig() method first.")
    }

    fun loadConfig(): Config {
        plugin.saveDefaultConfig()
        plugin.reloadConfig()

        val apiCategory = APICategory(
            port = plugin.config.getInt("api.port"),
            twitchBotWebhookUrl = plugin.config.getString("api.twitchBotWebhookUrl") ?: "http://localhost:3000/webhook"
        )

        val loadoutCategory = LoadoutCategory(
            armor = plugin.config.getStringList("loadout.armor"),
            items = plugin.config.getStringList("loadout.items"),
            itemsPerCategory = plugin.config.getInt("loadout.itemsPerCategory"),
            weapons = plugin.config.getStringList("loadout.weapons")
        )

        val noShowBehaviorCategory = NoShowBehaviorCategory(
            immediateReselect = plugin.config.getBoolean("noShowBehavior.immediateReselect")
        )

        val restrictionsCategory = RestrictionsCategory(
            canBreakBlocks = plugin.config.getBoolean("restrictions.canBreakBlocks"),
            canOpenChests = plugin.config.getBoolean("restrictions.canOpenChests"),
            canPlaceBlocks = plugin.config.getBoolean("restrictions.canPlaceBlocks"),
            canUseCrafting = plugin.config.getBoolean("restrictions.canUseCrafting")
        )

        val streamerUsername = plugin.config.getString("streamer.username")
            ?: throw IllegalStateException("Streamer username must be set in config!")

        val spawnCategory = SpawnCategory(
            radiusBlocks = plugin.config.getInt("spawn.radiusBlocks"),
        )

        val timingCategory = TimingCategory(
            huntDurationMillis = plugin.config.getLong("timing.huntDuration") * 60 * 1000,
            joinTimeoutMillis = plugin.config.getLong("timing.joinTimeout") * 60 * 1000,
        )

        val uiCategory = UICategory(
            showDistanceToHunter = plugin.config.getBoolean("ui.showDistanceToHunter"),
            showHunterDistance = plugin.config.getBoolean("ui.showHunterDistance"),
            showHunterInfoToStreamer = plugin.config.getBoolean("ui.showHunterInfoToStreamer"),
            showTimerToHunter = plugin.config.getBoolean("ui.showTimerToHunter")
        )

        val config = Config(
            api = apiCategory,
            loadout = loadoutCategory,
            noShowBehavior = noShowBehaviorCategory,
            restrictions = restrictionsCategory,
            streamerUsername = streamerUsername,
            spawn = spawnCategory,
            timing = timingCategory,
            ui = uiCategory
        )

        this.config = config
        plugin.logger.info("Configuration loaded successfully!")
        return config
    }
}