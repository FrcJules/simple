import { describe, expect, it } from "vitest";
import { presenceEvent } from "./index";

const base = { schemaVersion: 1, id: "e1", occurredAt: "2026-10-06T09:30:00+02:00" };

describe("presenceEvent", () => {
  it("accepts a missed call", () => {
    const r = presenceEvent.safeParse({ ...base, type: "missed_call", from: { name: "Marie" } });
    expect(r.success).toBe(true);
  });

  it("rejects an unknown type", () => {
    expect(presenceEvent.safeParse({ ...base, type: "nope" }).success).toBe(false);
  });

  it("rejects a new photo without a photo URL", () => {
    const r = presenceEvent.safeParse({ ...base, type: "new_photo", from: { name: "Jules" } });
    expect(r.success).toBe(false);
  });

  it("rejects an unsupported schema version", () => {
    const r = presenceEvent.safeParse({ ...base, schemaVersion: 2, type: "missed_call", from: { name: "Marie" } });
    expect(r.success).toBe(false);
  });
});
