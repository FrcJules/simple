import { describe, expect, it } from "vitest";
import { body, greeting, headline, timeAgo } from "./format";

describe("format", () => {
  const now = new Date("2026-10-06T10:00:00Z");
  it("timeAgo handles minutes, hours and days", () => {
    expect(timeAgo(new Date("2026-10-06T09:40:00Z"), now)).toBe("il y a 20 minutes");
    expect(timeAgo(new Date("2026-10-06T09:59:50Z"), now)).toBe("à l'instant");
    expect(timeAgo(new Date("2026-10-06T07:00:00Z"), now)).toBe("il y a 3 heures");
    expect(timeAgo(new Date("2026-10-04T10:00:00Z"), now)).toBe("il y a 2 jours");
  });
  it("greets by time of day", () => {
    expect(greeting(new Date(2026, 9, 6, 9), "Jean-Pierre")).toBe("Bonjour Jean-Pierre");
    expect(greeting(new Date(2026, 9, 6, 20), "Jean-Pierre")).toBe("Bonsoir Jean-Pierre");
  });
  it("headlines a message and exposes its body", () => {
    const e = { schemaVersion: 1 as const, id: "2", type: "new_message" as const, occurredAt: "2026-10-06T10:00:00Z", from: { name: "Marie" }, channel: "sms" as const, text: "Bonjour !" };
    expect(headline(e)).toBe("Message de Marie");
    expect(body(e)).toBe("Bonjour !");
  });

  it("headlines a missed call", () => {
    expect(headline({ schemaVersion: 1, id: "1", type: "missed_call", occurredAt: "2026-10-06T10:00:00Z", from: { name: "Marie" } }))
      .toBe("Marie vous a appelé");
  });
});
