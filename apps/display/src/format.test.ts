import { describe, expect, it } from "vitest";
import { body, currentEvent, greeting, headline, timeAgo } from "./format";

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

describe("currentEvent", () => {
  const now = new Date("2026-11-28T12:00:00Z");
  const at = (hoursAgo: number) => new Date(now.getTime() - hoursAgo * 3_600_000).toISOString();
  const call = (hoursAgo: number) => ({ schemaVersion: 1 as const, id: `c${hoursAgo}`, type: "missed_call" as const, occurredAt: at(hoursAgo), from: { name: "Marie" } });
  const message = (hoursAgo: number) => ({ schemaVersion: 1 as const, id: `m${hoursAgo}`, type: "new_message" as const, channel: "sms" as const, occurredAt: at(hoursAgo), from: { name: "Paul" }, text: "Bonjour" });

  it("shows the newest event", () => {
    expect(currentEvent([message(1), call(2)], now)?.id).toBe("m1");
  });

  it("drops a message after a few hours and falls back to a missed call of the day", () => {
    expect(currentEvent([message(5), call(6)], now)?.id).toBe("c6");
  });

  it("shows nothing once everything is too old", () => {
    expect(currentEvent([message(5), call(30)], now)).toBeUndefined();
  });
});

describe("device_paired", () => {
  const paired = { schemaVersion: 1 as const, id: "p", type: "device_paired" as const, occurredAt: "2026-11-28T12:00:00Z", devices: 1 };
  it("names the phone when it has a name, and counts the phones", () => {
    expect(headline({ ...paired, label: "Téléphone de Marie" }, "fr")).toBe("Téléphone ajouté : Téléphone de Marie");
    expect(headline(paired, "fr")).toBe("Un téléphone a été ajouté");
    expect(headline(paired, "en")).toBe("A phone was added");
    expect(body(paired, "fr")).toBe("1 téléphone relié à l'écran");
    expect(body({ ...paired, devices: 3 }, "en")).toBe("3 phones linked to this screen");
  });
});
