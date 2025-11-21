import { type BunRequest, type Server } from "bun";
import type { BotStateManager } from "./BotStateManager";
import { environment } from "./environment";
import { logger } from "./logger";
import type { SelectionTimer } from "./SelectionTimer";

export interface WebhookEvent {
  event: string;
  data: Record<string, unknown>;
}

export interface WebhookServerOptions {
  botStateManager: BotStateManager;
  selectionTimer: SelectionTimer;
}

export class WebhookServer {
  private readonly botStateManager: BotStateManager;
  private readonly listenPort: number;
  private readonly selectionTimer: SelectionTimer;
  private server?: Server<undefined> | undefined;

  constructor({ botStateManager, selectionTimer }: WebhookServerOptions) {
    this.botStateManager = botStateManager;
    this.listenPort = environment.webhook.port;
    this.selectionTimer = selectionTimer;
  }

  private async handleIncomingWebhook(event: WebhookEvent) {
    logger.info(`Received incoming webhook: ${JSON.stringify(event)}`);

    switch (event.event) {
      case "game-ended":
        this.botStateManager.gameEnded();
        break;

      case "game-paused":
        this.botStateManager.gamePaused();
        break;

      case "game-resumed":
        this.botStateManager.gameResumed();
        break;

      case "game-started":
        this.botStateManager.gameStarted({
          attemptNumber: parseInt(event.data.attemptNumber as string),
          worldSeed: BigInt(event.data.worldSeed as string)
        });
        break;

      case "hunter-joined":
        this.botStateManager.hunterJoined({
          huntDurationMillis: parseInt(event.data.huntDurationMillis as string)
        });
        break;

      case "hunter-left":
        this.botStateManager.hunterLeft();
        break;

      case "streamer-died":
        this.botStateManager.gameEnded();
        break;

      case "streamer-victory":
        this.botStateManager.gameEnded();
        break;

      case "no-show-immediate-reselect":
        logger.info("Triggering immediate reselection, hunter did not join in time");
        this.selectionTimer.triggerNow();
        break;
    }
  }

  async start() {
    this.server = Bun.serve({
      fetch: () => new Response("Not Found", { status: 404 }),
      port: this.listenPort,
      routes: {
        "/health": new Response(JSON.stringify({ status: "ok" }), { headers: { "Content-Type": "application/json" } }),

        "/webhook": {
          POST: (async (req: BunRequest) => {
            try {
              const event = (await req.json()) as WebhookEvent;

              await this.handleIncomingWebhook(event);

              return new Response(JSON.stringify({ success: true }), {
                headers: { "Content-Type": "application/json" }
              });
            } catch (err) {
              logger.error("Failed to handle incoming webhook.", err);

              return new Response(JSON.stringify({ success: false }), {
                headers: { "Content-Type": "application/json" },
                status: 500
              });
            }
          }).bind(this)
        }
      }
    });

    logger.info(`Webhook server listening on port ${this.listenPort}...`);
  }

  async stop() {
    this.server?.stop();
    logger.info("Webhook server stopped!");
  }
}
