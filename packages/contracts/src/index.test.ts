import { describe, expect, it } from "vitest";
import { birthday, simpleEvent, reminder, weather } from "./index";

const base = { schemaVersion: 1, id: "e1", occurredAt: "2026-10-06T09:30:00+02:00" };

describe("simpleEvent", () => {
  it("accepts a missed call", () => {
    const r = simpleEvent.safeParse({ ...base, type: "missed_call", from: { name: "Marie" } });
    expect(r.success).toBe(true);
  });

  it("accepts a WhatsApp message and rejects an empty or oversized one", () => {
    const msg = { ...base, type: "new_message", from: { name: "Marie" }, channel: "whatsapp", text: "Bonjour !" };
    expect(simpleEvent.safeParse(msg).success).toBe(true);
    expect(simpleEvent.safeParse({ ...msg, text: "" }).success).toBe(false);
    expect(simpleEvent.safeParse({ ...msg, text: "x".repeat(501) }).success).toBe(false);
    expect(simpleEvent.safeParse({ ...msg, channel: "telegram" }).success).toBe(false);
  });

  it("rejects non-http photo URLs", () => {
    const e = { ...base, type: "new_photo", from: { name: "Jules" } };
    expect(simpleEvent.safeParse({ ...e, photoUrl: "file:///etc/passwd" }).success).toBe(false);
    expect(simpleEvent.safeParse({ ...e, photoUrl: "javascript:alert(1)" }).success).toBe(false);
    expect(simpleEvent.safeParse({ ...e, photoUrl: "http://192.168.1.5/p.jpg" }).success).toBe(true);
  });

  it("rejects an unknown type", () => {
    expect(simpleEvent.safeParse({ ...base, type: "nope" }).success).toBe(false);
  });

  it("rejects a new photo without a photo URL", () => {
    const r = simpleEvent.safeParse({ ...base, type: "new_photo", from: { name: "Jules" } });
    expect(r.success).toBe(false);
  });

  it("rejects an unsupported schema version", () => {
    const r = simpleEvent.safeParse({ ...base, schemaVersion: 2, type: "missed_call", from: { name: "Marie" } });
    expect(r.success).toBe(false);
  });
});

describe("reminder", () => {
  const r = { id: "r1", title: "Prendre le médicament", at: "08:00" };
  it("accepts every day, some days, or one date", () => {
    expect(reminder.safeParse(r).success).toBe(true);
    expect(reminder.safeParse({ ...r, days: [1, 3, 5] }).success).toBe(true);
    expect(reminder.safeParse({ ...r, date: "2026-10-12" }).success).toBe(true);
  });
  it("accepts a title without a dose", () => {
    for (const title of ["Médicament du matin", "Médecin à 14 h 30", "Appeler Marie", "Kiné"]) expect(reminder.safeParse({ ...r, title }).success, title).toBe(true);
  });
  it("refuses bad times, days, titles and extra fields", () => {
    for (const bad of [{ at: "8:00" }, { at: "24:00" }, { at: "08:60" }, { days: [7] }, { days: [] }, { title: "" }, { title: "x".repeat(81) }, { date: "12/10/2026" }, { extra: 1 }, { date: "2026-10-12", days: [1] }, { title: "Prendre 2 comprimés" }, { title: "Doliprane 500 mg" }, { title: "10 gouttes" }]) {
      expect(reminder.safeParse({ ...r, ...bad }).success, JSON.stringify(bad)).toBe(false);
    }
  });
});

describe("birthday", () => {
  const b = { id: "b1", name: "Marie", day: "03-14" };
  it("accepts a day with or without a year", () => {
    expect(birthday.safeParse(b).success).toBe(true);
    expect(birthday.safeParse({ ...b, year: 1950 }).success).toBe(true);
    expect(birthday.safeParse({ ...b, day: "02-29" }).success).toBe(true);
  });
  it("refuses impossible days, bad formats and extra fields", () => {
    for (const bad of [{ day: "02-30" }, { day: "04-31" }, { day: "13-01" }, { day: "00-10" }, { day: "3-14" }, { name: "" }, { year: 1800 }, { extra: 1 }]) {
      expect(birthday.safeParse({ ...b, ...bad }).success, JSON.stringify(bad)).toBe(false);
    }
  });
});

describe("weather", () => {
  it("accepts a reading and refuses nonsense", () => {
    expect(weather.safeParse({ temperature: 18.4, code: 3, min: 11, max: 21 }).success).toBe(true);
    for (const bad of [{ temperature: 500 }, { code: 120 }, { code: 1.5 }, { min: "cold" }]) {
      expect(weather.safeParse({ temperature: 18, code: 3, min: 11, max: 21, ...bad }).success, JSON.stringify(bad)).toBe(false);
    }
  });
});
