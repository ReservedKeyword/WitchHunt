export interface AddEntryOptions {
  queueEntry: QueueEntry;
}

export interface ContainsOptions {
  twitchUsername: string;
}

export interface QueueEntry {
  readonly joinedAt: Date;
  readonly minecraftUsername: string;
  readonly twitchUsername: string;
}

export interface QueueState {
  readonly entries: ReadonlyArray<QueueEntry>;
}

export interface RemoveEntryOptions {
  twitchUsername: string;
}

export const QueueState = {
  withState: (queueState: QueueState) => ({
    addEntry: ({ queueEntry }: AddEntryOptions): QueueState => ({
      entries: [...queueState.entries, queueEntry]
    }),

    contains: ({ twitchUsername }: ContainsOptions): boolean =>
      queueState.entries.some((queueEntry) => queueEntry.twitchUsername === twitchUsername),

    isEmpty: () => queueState.entries.length === 0,

    selectRandom: (): [QueueEntry | null, QueueState] => {
      if (queueState.entries.length === 0) {
        return [null, queueState];
      }

      const entryIndex = Math.floor(Math.random() * queueState.entries.length);
      const selectedEntry = queueState.entries[entryIndex];

      const updatedState: QueueState = {
        entries: [...queueState.entries.slice(0, entryIndex), ...queueState.entries.slice(entryIndex + 1)]
      };

      return [selectedEntry ?? null, updatedState];
    },

    size: (): number => queueState.entries.length
  })
};
