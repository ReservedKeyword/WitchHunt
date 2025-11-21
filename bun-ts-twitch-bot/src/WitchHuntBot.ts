import { BotStateManager } from "./BotStateManager";
import { environment } from "./environment";
import { logger } from "./logger";
import { MinecraftClient } from "./MinecraftClient";
import { SelectionTimer } from "./SelectionTimer";
import { TwitchBotClient } from "./TwitchBotClient";
import { WebhookServer } from "./WebhookServer";

export class WitchHuntBot {
  private readonly botStateManager: BotStateManager;
  private readonly minecraftClient: MinecraftClient;
  private readonly selectionTimer: SelectionTimer;
  private twitchBotClient: TwitchBotClient;
  private readonly webhookServer: WebhookServer;

  constructor() {
    logger.info("Witch Hunt Bot is starting...");

    this.botStateManager = new BotStateManager();
    this.minecraftClient = new MinecraftClient();

    this.twitchBotClient = new TwitchBotClient({
      botStateManager: this.botStateManager,
      environment
    });

    const {
      game: { selectionIntervalMillis: selectionIntervalMs },
      twitch: { channel: twitchChannel }
    } = environment;

    this.selectionTimer = new SelectionTimer({
      botStateManager: this.botStateManager,
      channel: twitchChannel,
      intervalMs: selectionIntervalMs,
      minecraftClient: this.minecraftClient,
      twitchBotClient: this.twitchBotClient
    });

    this.webhookServer = new WebhookServer({
      botStateManager: this.botStateManager,
      selectionTimer: this.selectionTimer
    });
  }

  async start() {
    try {
      logger.info("Checking Minecraft server connection...");
      const isMinecraftServerHealthy = await this.minecraftClient.healthCheck();

      if (!isMinecraftServerHealthy) {
        logger.warn("Cannot connect to Minecraft server.");
        logger.warn("Bot will start, but selections will fail until Minecraft server is up!");
      } else {
        logger.info("Minecraft server is up and reachable!");
      }

      await this.webhookServer.start();
      await this.twitchBotClient.connect();
      this.selectionTimer.start();
    } catch (err) {
      logger.error("Failed to start bot: ", err);
      throw err;
    }
  }

  async stop() {
    logger.info("Shutting down...");
    this.selectionTimer.stop();
    this.webhookServer.stop();
    await this.twitchBotClient.disconnect();
    logger.info("Bot stopped!");
  }
}
