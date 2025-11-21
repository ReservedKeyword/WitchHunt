import type { BotStateManager } from "./BotStateManager";
import { environment } from "./environment";
import { logger } from "./logger";
import type { MinecraftClient } from "./MinecraftClient";
import type { TwitchBotClient } from "./TwitchBotClient";

export interface SelectionTimerOptions {
  botStateManager: BotStateManager;
  channel: string;
  intervalMs: number;
  minecraftClient: MinecraftClient;
  twitchBotClient: TwitchBotClient;
}

export class SelectionTimer {
  private readonly botStateManager: BotStateManager;
  private readonly channel: string;
  private intervalId?: Timer | null | undefined;
  private readonly intervalMs: number;
  private readonly minecraftClient: MinecraftClient;
  private readonly twitchBotClient: TwitchBotClient;

  constructor({ botStateManager, channel, intervalMs, minecraftClient, twitchBotClient }: SelectionTimerOptions) {
    this.botStateManager = botStateManager;
    this.channel = channel;
    this.intervalMs = intervalMs;
    this.minecraftClient = minecraftClient;
    this.twitchBotClient = twitchBotClient;
  }

  private async performSelection() {
    if (!this.botStateManager.isGameActive()) {
      logger.info("Game is not active, will skip selection.");
      return;
    }

    if (this.botStateManager.isGamePaused()) {
      logger.info("Game is paused, will skip selection.");
      return;
    }

    if (this.botStateManager.isHunterActive()) {
      logger.info("Hunter is in the game, will skip selection.");
      return;
    }

    if (this.botStateManager.isQueueEmpty()) {
      logger.info("No players is queue, will skip selection");
      return;
    }

    const selectedChatter = this.botStateManager.selectRandomFromQueue();

    if (!selectedChatter) {
      logger.info("Selection failed, no chatter was randomly returned");
      return;
    }

    const response = await this.minecraftClient.notifyPlayerSelection(
      selectedChatter.minecraftUsername,
      selectedChatter.twitchUsername
    );

    if (response) {
      const {
        game: { joinTimeoutMillis }
      } = environment;

      this.botStateManager.hunterSelected({ joinTimeoutMillis, queueEntry: selectedChatter });
    } else {
      logger.error("Failed to notify Minecraft server of player selection!");
    }
  }

  start() {
    this.intervalId = setInterval(() => {
      this.performSelection();
    }, this.intervalMs);
  }

  stop() {
    if (this.intervalId) {
      clearInterval(this.intervalId);
      this.intervalId = null;
      logger.info("Selection timer stopped!");
    }
  }

  triggerNow() {
    this.performSelection();
  }
}
