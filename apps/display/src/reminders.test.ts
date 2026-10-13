import type { Reminder } from "@simple/contracts";
import { describe, expect, it } from "vitest";
import { cardReminders, dismissKey, dueReminders, formatAt, nextReminder } from "./reminders";

const at = (iso: string) => new Date(iso); // local time
const med: Reminder = { id: "m", title: "Médicament", at: "08:00" };
const doctor: Reminder = { id: "d", title: "Médecin", at: "14:30", date: "2026-10-12" };
const monday: Reminder = { id: "w", title: "Marché", at: "09:00", days: [1] };

describe("dueReminders", () => {
  it("shows a daily reminder from its time for an hour", () => {
    expect(dueReminders([med], at("2026-10-12T07:59:00"))).toEqual([]);
    expect(dueReminders([med], at("2026-10-12T08:00:00"))).toEqual([med]);
    expect(dueReminders([med], at("2026-10-12T08:59:00"))).toEqual([med]);
    expect(dueReminders([med], at("2026-10-12T09:00:00"))).toEqual([]);
  });
  it("one-off reminders only on their day", () => {
    expect(dueReminders([doctor], at("2026-10-12T14:45:00"))).toEqual([doctor]);
    expect(dueReminders([doctor], at("2026-10-13T14:45:00"))).toEqual([]);
  });
  it("weekday reminders only on those days (2026-10-12 is a Monday)", () => {
    expect(dueReminders([monday], at("2026-10-12T09:10:00"))).toEqual([monday]);
    expect(dueReminders([monday], at("2026-10-13T09:10:00"))).toEqual([]);
  });
  it("earliest first", () => {
    const early: Reminder = { id: "e", title: "Tôt", at: "08:30", days: [1] };
    expect(dueReminders([early, med], at("2026-10-12T08:40:00")).map((r) => r.id)).toEqual(["m", "e"]);
  });
});

describe("nextReminder", () => {
  it("is the next one still to come today", () => {
    expect(nextReminder([med, doctor], at("2026-10-12T07:00:00"))).toEqual(med);
    expect(nextReminder([med, doctor], at("2026-10-12T09:00:00"))).toEqual(doctor);
    expect(nextReminder([med, doctor], at("2026-10-12T15:00:00"))).toBeUndefined();
  });
});

describe("dismissKey", () => {
  it("is per reminder and per day", () => {
    expect(dismissKey(med, at("2026-10-12T08:10:00"))).not.toBe(dismissKey(med, at("2026-10-13T08:10:00")));
  });
});

describe("cardReminders", () => {
  it("is full screen for five minutes, then only the small line remains", () => {
    expect(cardReminders([med], at("2026-10-12T08:04:00"))).toEqual([med]);
    expect(cardReminders([med], at("2026-10-12T08:05:00"))).toEqual([]);
    expect(dueReminders([med], at("2026-10-12T08:05:00"))).toEqual([med]);
  });
});

describe("formatAt", () => {
  it("has no leading zero on the hour", () => {
    expect(formatAt("08:05")).toBe("8 h 05");
    expect(formatAt("14:30")).toBe("14 h 30");
  });
});
