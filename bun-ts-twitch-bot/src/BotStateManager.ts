import {
  BotState,
  type GameStartedOptions,
  type PlayerJoinedOptions,
  type PlayerSelectionOptions
} from "./state/BotState";
import { HunterState } from "./state/HunterState";
import { QueueState, type QueueEntry } from "./state/QueueState";

export interface AddToQueueOptions {
  minecraftUsername: string;
  twitchUsername: string;
}

export class BotStateManager {
  private botState: BotState = {
    currentAttempt: 0,
    currentWorldSeed: null,
    gameActive: false,
    gamePaused: false,
    hunter: HunterState.clear(),
    queue: { entries: [] }
  };

  addToQueue({ minecraftUsername, twitchUsername }: AddToQueueOptions): boolean {
    const currentQueueState = QueueState.withState(this.botState.queue);

    if (currentQueueState.contains({ twitchUsername })) {
      return false;
    }

    const queueEntry: QueueEntry = {
      joinedAt: new Date(),
      minecraftUsername,
      twitchUsername
    };

    const updatedQueueState = currentQueueState.addEntry({ queueEntry });
    this.botState = { ...this.botState, queue: updatedQueueState };
    return true;
  }

  gameEnded() {
    this.botState = BotState.withState(this.botState).gameEnded();
  }

  gamePaused() {
    this.botState = BotState.withState(this.botState).gamePaused();
  }

  gameResumed() {
    this.botState = BotState.withState(this.botState).gameResumed();
  }

  gameStarted(options: GameStartedOptions) {
    this.botState = BotState.withState(this.botState).gameStarted(options);
  }

  getQueueSize(): number {
    return QueueState.withState(this.botState.queue).size();
  }

  isGameActive(): boolean {
    return this.botState.gameActive;
  }

  isGamePaused(): boolean {
    return this.botState.gamePaused;
  }

  isHunterActive(): boolean {
    return this.botState.hunter.hunterJoined;
  }

  isQueueEmpty(): boolean {
    return QueueState.withState(this.botState.queue).isEmpty();
  }

  hunterJoined({ huntDurationMillis }: PlayerJoinedOptions) {
    this.botState = BotState.withState(this.botState).hunterJoined({ huntDurationMillis });
  }

  hunterLeft() {
    this.botState = BotState.withState(this.botState).hunterLeft();
  }

  hunterSelected({ joinTimeoutMillis, queueEntry }: PlayerSelectionOptions) {
    this.botState = BotState.withState(this.botState).hunterSelected({ joinTimeoutMillis, queueEntry });
  }

  selectRandomFromQueue(): QueueEntry | null {
    const [selectedChatter, updatedQueueState] = QueueState.withState(this.botState.queue).selectRandom();
    this.botState = { ...this.botState, queue: updatedQueueState };
    return selectedChatter;
  }
}
