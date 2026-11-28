import { describe, expect, it } from "vitest";
import { purgeExpired, replayFor, type StoredEvent } from "./replay";

const HOUR = 3_600_000;
const NOW = 1_800_000_000_000;
const stored = (seq: number, ageHours: number): StoredEvent => ({
  seq,
  receivedAt: NOW - ageHours * HOUR,
  event: { schemaVersion: 1, id: `e${seq}`, type: "missed_call", occurredAt: "2026-11-28T09:30:00+01:00", from: { name: "Marie" } },
});
const buffer = [stored(1, 30), stored(2, 5), stored(3, 1), stored(4, 0)];
const seqs = (list: StoredEvent[]) => list.map((s) => s.seq);

describe("replayFor", () => {
  it("gives a newly opened screen what happened in the last day, oldest first", () => {
    expect(seqs(replayFor(buffer, undefined, "boot", NOW))).toEqual([2, 3, 4]);
  });

  it("gives a reconnecting screen only what it missed", () => {
    expect(seqs(replayFor(buffer, "boot-2", "boot", NOW))).toEqual([3, 4]);
    expect(seqs(replayFor(buffer, "boot-4", "boot", NOW))).toEqual([]);
  });

  it("treats an id from before a hub restart, or a malformed one, as a new screen", () => {
    expect(seqs(replayFor(buffer, "old-2", "boot", NOW))).toEqual([2, 3, 4]);
    expect(seqs(replayFor(buffer, "boot-x", "boot", NOW))).toEqual([2, 3, 4]);
    expect(seqs(replayFor(buffer, "boot-99", "boot", NOW))).toEqual([2, 3, 4]);
    for (const id of ["boot-", "boot-0x1", "boot-1e0", "boot- 2", "boot-+1", "boot-2.0", `boot-${"9".repeat(80)}`]) {
      expect(seqs(replayFor(buffer, id, "boot", NOW)), id).toEqual([2, 3, 4]);
    }
  });

  it("never replays events older than a day, even to a screen that was away longer", () => {
    expect(seqs(replayFor(buffer, "boot-0", "boot", NOW))).toEqual([2, 3, 4]);
  });

  it("always replays a pairing announcement, however many events came after it", () => {
    const paired: StoredEvent = { seq: 1, receivedAt: NOW, event: { schemaVersion: 1, id: "p", type: "device_paired", occurredAt: "2026-11-28T09:30:00+01:00", devices: 2 } };
    const flood = Array.from({ length: 80 }, (_, i) => stored(i + 2, 0));
    const replayed = replayFor([paired, ...flood], undefined, "boot", NOW);
    expect(replayed[0]?.event.type).toBe("device_paired");
    expect(replayed).toHaveLength(51);
  });

  it("never replays more than the screen keeps", () => {
    const many = Array.from({ length: 80 }, (_, i) => stored(i + 1, 0));
    const replayed = replayFor(many, undefined, "boot", NOW);
    expect(replayed).toHaveLength(50);
    expect(replayed.at(-1)?.seq).toBe(80);
  });
});

describe("purgeExpired", () => {
  it("forgets a missed call after a day and a message text after a few hours", () => {
    const message: StoredEvent = {
      seq: 9,
      receivedAt: NOW - 5 * HOUR,
      event: { schemaVersion: 1, id: "m", type: "new_message", channel: "sms", occurredAt: "2026-11-28T09:30:00+01:00", from: { name: "Marie" }, text: "Bonjour" },
    };
    const list = [...buffer, message];
    purgeExpired(list, NOW);
    expect(seqs(list)).toEqual([2, 3, 4]); // the 30 h old call and the 5 h old message are gone
  });
});
