import { environment } from "./environment";
import { logger } from "./logger";

export interface HunterSelectionRequest {
  minecraftUsername: string;
  twitchUsername: string;
}

export interface StatusResponse {
  currentAttempt: number | null;
  gameActive: boolean;
  worldReady: boolean;
}

export class MinecraftClient {
  private readonly baseUrl: string;

  constructor() {
    this.baseUrl = environment.minecraft.apiUrl;
  }

  async getStatus(): Promise<StatusResponse | null> {
    try {
      const response = await fetch(`${this.baseUrl}/api/hunt/status`);
      if (!response.ok) return null;
      return (await response.json()) as StatusResponse;
    } catch (err) {
      logger.error("Failed to retrieve hunt status from Minecraft server", err);
      return null;
    }
  }

  async healthCheck(): Promise<boolean> {
    try {
      const response = await fetch(`${this.baseUrl}/api/health`);
      return response.ok;
    } catch (err) {
      logger.error("Failed to valid health of Minecraft server", err);
      return false;
    }
  }

  async notifyPlayerSelection(minecraftUsername: string, twitchUsername: string): Promise<boolean> {
    try {
      const hunterSelectionRequest: HunterSelectionRequest = {
        minecraftUsername,
        twitchUsername
      };

      logger.debug("Hunter selection request: ", JSON.stringify(hunterSelectionRequest));

      const response = await fetch(`${this.baseUrl}/api/hunt/select`, {
        body: JSON.stringify(hunterSelectionRequest),
        headers: { "Content-Type": "application/json" },
        method: "POST"
      });

      if (!response.ok) {
        logger.error("Failed to notify Minecraft server of hunter selection: ", response.statusText);
        return false;
      }

      logger.info("Minecraft server notified of hunter selection!");
      return true;
    } catch (err) {
      logger.error("Failed to notify Minecraft server of hunter selection", err);
      return false;
    }
  }
}
