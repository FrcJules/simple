import type { SimpleEvent } from "@simple/contracts";

/** An accepted event as the hub keeps it in memory: [seq] orders events since the hub started. */
export interface StoredEvent {
  seq: number;
  /** Hub clock, not the phone's: used to decide what is recent. */
  receivedAt: number;
  event: SimpleEvent;
}

/** Same limit as the display's own list (apps/display/src/useEvents.ts). */
export const MAX_REPLAY = 50;
/** A screen that (re)starts shows what happened during the last day, e.g. "Marie vous a appelé, il y a 2 heures". */
export const REPLAY_WINDOW_MS = 24 * 3_600_000;

/** SSE event id: the hub's boot id (so ids from before a restart are recognised) and the sequence number. */
export const sseId = (bootId: string, seq: number): string => `${bootId}-${seq}`;

/**
 * Events to send to a screen that opens the stream. EventSource sends back the last id it saw when it reconnects
 * (`Last-Event-ID`): it then gets only what it missed. A new screen, or one that knew a previous run of the hub, gets the last day.
 * Oldest first, so the screen ends up showing the newest one.
 */
export function replayFor(stored: readonly StoredEvent[], lastEventId: string | undefined, bootId: string, now: number): StoredEvent[] {
  const latest = stored.at(-1)?.seq ?? 0;
  const prefix = `${bootId}-`;
  if (lastEventId?.startsWith(prefix)) {
    const seen = Number(lastEventId.slice(prefix.length));
    if (Number.isInteger(seen) && seen >= 0 && seen <= latest) return stored.filter((s) => s.seq > seen).slice(-MAX_REPLAY);
  }
  return stored.filter((s) => now - s.receivedAt <= REPLAY_WINDOW_MS).slice(-MAX_REPLAY);
}
