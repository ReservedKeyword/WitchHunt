package com.reservedkeyword.witchhunt

import com.reservedkeyword.witchhunt.game.HuntManager
import com.reservedkeyword.witchhunt.game.PlayerRestrictions
import com.reservedkeyword.witchhunt.http.api.startHttpServer
import com.reservedkeyword.witchhunt.http.webhook.WebhookClient
import com.reservedkeyword.witchhunt.hud.HudManager
import com.reservedkeyword.witchhunt.listeners.*
import com.reservedkeyword.witchhunt.utils.asyncDispatcher
import com.reservedkeyword.witchhunt.utils.minecraftDispatcher
import io.ktor.server.cio.*
import io.ktor.server.engine.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import org.bukkit.plugin.java.JavaPlugin

class WitchHuntPlugin : JavaPlugin() {
    lateinit var configManager: ConfigManager
        private set

    lateinit var hudManager: HudManager
        private set

    lateinit var huntManager: HuntManager
        private set

    lateinit var playerRestrictions: PlayerRestrictions
        private set

    lateinit var webhookClient: WebhookClient
        private set

    private var httpServer: EmbeddedServer<CIOApplicationEngine, CIOApplicationEngine.Configuration>? = null

    val asyncScope by lazy {
        CoroutineScope(asyncDispatcher() + SupervisorJob())
    }

    val syncScope by lazy {
        CoroutineScope(minecraftDispatcher() + SupervisorJob())
    }

    override fun onEnable() {
        logger.info("Witch Hunt plugin is starting...")

        try {
            configManager = ConfigManager(this)
            configManager.loadConfig()
        } catch (e: Exception) {
            logger.severe("Failed to load configuration: ${e.message}")
            logger.severe("Plugin will be disabled!")
            server.pluginManager.disablePlugin(this)
            return
        }

        playerRestrictions = PlayerRestrictions(this)
        webhookClient = WebhookClient(this)
        hudManager = HudManager(this)
        huntManager = HuntManager(this)

        asyncScope.launch {
            try {
                huntManager.initialize()
                logger.info("Hunt manager initialized successfully!")
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        server.pluginManager.registerEvents(BlockBreakListener(this), this)
        server.pluginManager.registerEvents(BlockPlaceListener(this), this)
        server.pluginManager.registerEvents(CraftItemListener(this), this)
        server.pluginManager.registerEvents(EntityDeathListener(this), this)
        server.pluginManager.registerEvents(InventoryOpenListener(this), this)
        server.pluginManager.registerEvents(PlayerDeathListener(this), this)
        server.pluginManager.registerEvents(PlayerJoinListener(this), this)
        server.pluginManager.registerEvents(PlayerPortalListener(this), this)
        server.pluginManager.registerEvents(PlayerQuitListener(this), this)

        getCommand("hunt")?.setExecutor(HuntCommand(this))

        try {
            httpServer = startHttpServer()
        } catch (e: Exception) {
            e.printStackTrace()
        }

        logger.info("Witch Hunt plugin enabled successfully!")
    }

    override fun onDisable() {
        this.syncScope.launch {
            logger.info("Witch Hunt plugin is shutting down...")

            asyncScope.cancel()
            syncScope.cancel()

            httpServer?.stop(0, 0)
            playerRestrictions.clearAll()
            webhookClient.close()
            hudManager.clearAll()
            huntManager.shutdown()

            logger.info("Witch Hunt plugin disabled!")
        }
    }
}
