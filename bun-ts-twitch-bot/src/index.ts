import { environment } from "./environment";
import { logger } from "./logger";
import { WitchHuntBot } from "./WitchHuntBot";

(async () => {
  const {
    game: { selectionIntervalMillis: selectionIntervalMs },
    twitch: { botUsername: twitchBotUsername, channel: twitchChannel }
  } = environment;

  const witchHuntBot = new WitchHuntBot();
  await witchHuntBot.start();

  logger.info("Witch Hunt Bot is running!");
  logger.info(`Bot Username: ${twitchBotUsername}`);
  logger.info(`Monitoring Twitch Channel: ${twitchChannel}`);
  logger.info(`Selection Interval: ${selectionIntervalMs / 60000} minute(s)`);

  process.on("SIGINT", async () => {
    await witchHuntBot.stop();
    process.exit(0);
  });

  process.on("SIGTERM", async () => {
    await witchHuntBot.stop();
    process.exit(0);
  });
})();
