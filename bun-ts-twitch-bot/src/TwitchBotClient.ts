import { type ChatUserstate as ChatUserState, Client as TwitchClient } from "tmi.js";
import type { BotStateManager } from "./BotStateManager";
import type { Environment } from "./environment";
import { logger } from "./logger";

export interface TwitchBotClientOptions {
  botStateManager: BotStateManager;
  environment: Environment;
}

export class TwitchBotClient {
  private readonly botStateManager: BotStateManager;
  private readonly twitchClient: TwitchClient;

  constructor({ botStateManager, environment }: TwitchBotClientOptions) {
    this.botStateManager = botStateManager;

    const {
      twitch: { botUsername, channel: twitchChannel, oauthToken }
    } = environment;

    this.twitchClient = new TwitchClient({
      channels: [twitchChannel],
      connection: {
        reconnect: true,
        secure: true
      },
      identity: {
        username: botUsername,
        password: oauthToken.includes("oauth:") ? oauthToken : `oauth:${oauthToken}`
      },
      options: {
        debug: false
      }
    });

    this.setupEventHandler();
  }

  async connect() {
    try {
      await this.twitchClient.connect();
    } catch (err) {
      logger.error("Failed to connect to Twitch chat: ", err);
      throw err;
    }
  }

  async disconnect() {
    await this.twitchClient.disconnect();
  }

  private handleJoinCommand(channel: string, twitchUsername: string, message: string) {
    // TODO: REMOVE THIS
    if (twitchUsername === "ReservedKeyword") {
      twitchUsername = "MyAccountForTest";
    }

    const parts = message.trim().split(/\s+/);

    if (parts.length < 2) {
      logger.warn(`Twitch chatter ${twitchUsername} did not provide a Minecraft username`);
      return;
    }

    const minecraftUsername = parts[1]!;

    if (minecraftUsername?.length < 3 || minecraftUsername?.length > 16) {
      logger.warn(`Twitch chatter ${twitchUsername} did not provide a valid Minecraft username`);
      return;
    }

    const wasAddedToQueue = this.botStateManager.addToQueue({ minecraftUsername, twitchUsername });

    if (wasAddedToQueue) {
      const queueSize = this.botStateManager.getQueueSize();
      logger.info(`Twitch chatter ${twitchUsername} added to queue, position: ${queueSize}`);
    }
  }

  private handleMessage(channel: string, userState: ChatUserState, message: string) {
    const username = userState.username ?? userState["display-name"];

    if (!username) {
      logger.warn("Will not handle message, username is undefined");
      return;
    }

    if (message.startsWith("!join")) {
      this.handleJoinCommand(channel, username, message);
      return;
    }
  }

  say(channel: string, message: string) {
    this.twitchClient.say(channel, message);
  }

  private setupEventHandler() {
    this.twitchClient.on("connected", (address, port) => {
      logger.info(`Connected to Twitch IRC at ${address}:${port}`);
    });

    this.twitchClient.on("disconnected", (reason) => {
      logger.info(`Disconnected from Twitch: ${reason}`);
    });

    this.twitchClient.on("message", (channel, userState, message, self) => {
      if (self) return;
      this.handleMessage(channel, userState, message);
    });
  }
}
