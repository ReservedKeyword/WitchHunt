import { logger } from "./logger";

export interface Environment {
  game: Game;
  minecraft: Minecraft;
  twitch: Twitch;
  webhook: Webhook;
}

export interface Game {
  huntDurationMillis: number;
  joinTimeoutMillis: number;
  selectionIntervalMillis: number;
}

export interface Minecraft {
  apiUrl: string;
}

export interface Twitch {
  botUsername: string;
  channel: string;
  oauthToken: string;
}

export interface Webhook {
  port: number;
}

export const environment: Environment = {
  game: {
    huntDurationMillis: parseInt(process.env.GAME_HUNT_DURATION ?? "15") * 60 * 1000,
    joinTimeoutMillis: parseInt(process.env.GAME_JOIN_TIMEOUT ?? "5") * 60 * 1000,
    selectionIntervalMillis: parseInt(process.env.GAME_SELECTION_INTERVAL ?? "30") * 60 * 1000
  },
  minecraft: {
    apiUrl: process.env.MINECRAFT_API_URL ?? "http://localhost:8080"
  },
  twitch: {
    botUsername: process.env.TWITCH_BOT_USERNAME ?? "",
    channel: process.env.TWITCH_CHANNEL ?? "",
    oauthToken: process.env.TWITCH_OAUTH_TOKEN ?? ""
  },
  webhook: {
    port: parseInt(process.env.WEBHOOK_PORT ?? "3000")
  }
};

if (!environment.twitch.botUsername || !environment.twitch.channel || !environment.twitch.oauthToken) {
  logger.error("Missing required Twitch configuration from environment variables!");
  process.exit(1);
}
