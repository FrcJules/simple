import { isStillShown, type DisplayEvent } from "@simple/contracts";

/** An accepted event as the hub keeps it in memory: [seq] orders events since the hub started. */
export interface StoredEvent {
  seq: number;
  /**
   * Monotonic hub time (`performance.now()`), not the phone's clock nor the wall clock: a Pi has no battery clock and jumps
   * when NTP syncs after boot, which would make a minute-old missed call look days old.
   */
  receivedAt: number;
  event: DisplayEvent;
}

/** Same limit as the display's own list (apps/display/src/useEvents.ts). */
export const MAX_REPLAY = 50;

/** SSE event id: the hub's boot id (so ids from before a restart are recognised) and the sequence number. */
export const sseId = (bootId: string, seq: number): string => `${bootId}-${seq}`;

/**
 * Events to send to a screen that opens the stream. EventSource sends back the last id it saw when it reconnects
 * (`Last-Event-ID`): it then gets only what it missed. A new screen, or one that knew a previous run of the hub, gets what may still
 * be shown (DISPLAY_RETENTION_MS: a day for a missed call, a few hours for a message).
 * Oldest first, so the screen ends up showing the newest one.
 */
export function replayFor(stored: readonly StoredEvent[], lastEventId: string | undefined, bootId: string, now: number): StoredEvent[] {
  const recent = stored.filter((s) => isStillShown(s.event, now - s.receivedAt));
  const seen = seenSeq(lastEventId, bootId, stored.at(-1)?.seq ?? 0);
  return (seen === undefined ? recent : recent.filter((s) => s.seq > seen)).slice(-MAX_REPLAY);
}

/** The sequence number in a `Last-Event-ID` sent back by a screen, if it is one of ours (this run, digits only, not in the future). */
function seenSeq(lastEventId: string | undefined, bootId: string, latest: number): number | undefined {
  const prefix = `${bootId}-`;
  if (lastEventId === undefined || lastEventId.length > 64 || !lastEventId.startsWith(prefix)) return undefined;
  const digits = lastEventId.slice(prefix.length);
  if (!/^\d{1,15}$/.test(digits)) return undefined;
  const seen = Number(digits);
  return seen <= latest ? seen : undefined;
}

/** Forgets, in place, the events that may no longer be shown: the hub keeps nothing longer than the screen may display it. */
export function purgeExpired(stored: StoredEvent[], now: number): void {
  const kept = stored.filter((s) => isStillShown(s.event, now - s.receivedAt));
  stored.splice(0, stored.length, ...kept);
}
