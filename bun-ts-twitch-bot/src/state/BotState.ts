import { HunterState } from "./HunterState";
import { QueueState, type QueueEntry } from "./QueueState";

export interface BotState {
  readonly currentAttempt: number;
  readonly currentWorldSeed?: bigint | null | undefined;
  readonly gameActive: boolean;
  readonly gamePaused: boolean;
  readonly hunter: HunterState;
  readonly queue: QueueState;
}

export interface GameStartedOptions {
  attemptNumber: number;
  worldSeed: bigint;
}

export interface PlayerJoinedOptions {
  huntDurationMillis: number;
}

export interface PlayerSelectionOptions {
  joinTimeoutMillis: number;
  queueEntry: QueueEntry;
}

export const BotState = {
  withState: (botState: BotState) => ({
    gameEnded: (): BotState => ({
      ...botState,
      gameActive: false,
      gamePaused: false,
      hunter: HunterState.clear()
    }),

    gamePaused: (): BotState => ({
      ...botState,
      gamePaused: true,
      hunter: HunterState.clear()
    }),

    gameResumed: (): BotState => ({
      ...botState,
      gamePaused: false
    }),

    gameStarted: ({ attemptNumber, worldSeed }: GameStartedOptions): BotState => ({
      ...botState,
      currentAttempt: attemptNumber,
      currentWorldSeed: worldSeed,
      gameActive: true,
      gamePaused: false
    }),

    hunterJoined: ({ huntDurationMillis }: PlayerJoinedOptions): BotState => ({
      ...botState,
      hunter: HunterState.withState(botState.hunter).markJoined({ huntDurationMillis })
    }),

    hunterLeft: (): BotState => ({
      ...botState,
      hunter: HunterState.clear()
    }),

    hunterSelected: ({ joinTimeoutMillis, queueEntry }: PlayerSelectionOptions): BotState => {
      const [_, updatedQueue] = QueueState.withState(botState.queue).selectRandom();

      return {
        ...botState,
        hunter: HunterState.select({ joinTimeoutMillis, queueEntry }),
        queue: updatedQueue
      };
    },

    selectionExpired: (): BotState => ({
      ...botState,
      hunter: HunterState.clear()
    })
  })
};
