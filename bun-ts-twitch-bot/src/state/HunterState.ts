import type { QueueEntry } from "./QueueState";

export interface HunterState {
  readonly huntDeadline?: Date | null | undefined;
  readonly huntStartTime?: Date | null | undefined;
  readonly hunterJoined: boolean;
  readonly joinDeadline?: Date | null | undefined;
  readonly selectedEntry?: QueueEntry | null | undefined;
  readonly selectionTime?: Date | null | undefined;
}

export interface MarkJoinedOptions {
  huntDurationMillis: number;
}

export interface SelectOptions {
  joinTimeoutMillis: number;
  queueEntry: QueueEntry;
}

export const HunterState = {
  clear: (): HunterState => ({
    huntDeadline: null,
    huntStartTime: null,
    hunterJoined: false,
    joinDeadline: null,
    selectedEntry: null,
    selectionTime: null
  }),

  select: ({ joinTimeoutMillis, queueEntry }: SelectOptions): HunterState => {
    const timeNow = new Date();
    const joinDeadline = new Date(timeNow.getTime() + joinTimeoutMillis);

    return {
      huntDeadline: null,
      huntStartTime: null,
      hunterJoined: false,
      joinDeadline,
      selectedEntry: queueEntry,
      selectionTime: timeNow
    };
  },

  withState: (hunterState: HunterState) => ({
    markJoined: ({ huntDurationMillis }: MarkJoinedOptions): HunterState => {
      const timeNow = new Date();
      const huntDeadline = new Date(timeNow.getTime() + huntDurationMillis);

      return {
        ...hunterState,
        huntDeadline: huntDeadline,
        huntStartTime: timeNow,
        hunterJoined: true
      };
    }
  })
};
