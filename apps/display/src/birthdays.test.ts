import type { Birthday } from "@presence/contracts";
import { describe, expect, it } from "vitest";
import { birthdaysToday, daysUntil, nextBirthday, turnsAge } from "./birthdays";

const at = (iso: string) => new Date(iso);
const marie: Birthday = { id: "m", name: "Marie", day: "03-14", year: 1950 };
const leap: Birthday = { id: "l", name: "Léa", day: "02-29" };

describe("daysUntil", () => {
  it("0 today, counts forward, wraps to next year", () => {
    expect(daysUntil(marie, at("2026-03-14T08:00:00"))).toBe(0);
    expect(daysUntil(marie, at("2026-03-11T23:59:00"))).toBe(3);
    expect(daysUntil(marie, at("2026-03-15T00:00:00"))).toBe(364);
  });
  it("29 February is on the 28th in a year that is not a leap year", () => {
    expect(daysUntil(leap, at("2027-02-28T10:00:00"))).toBe(0);
    expect(daysUntil(leap, at("2028-02-29T10:00:00"))).toBe(0);
    expect(daysUntil(leap, at("2028-02-28T10:00:00"))).toBe(1);
  });
});

describe("what the screen shows", () => {
  it("today's birthdays and the next one within a week", () => {
    expect(birthdaysToday([marie, leap], at("2026-03-14T09:00:00"))).toEqual([marie]);
    expect(nextBirthday([marie], at("2026-03-10T09:00:00"))).toEqual(marie);
    expect(nextBirthday([marie], at("2026-03-01T09:00:00"))).toBeUndefined();
    expect(nextBirthday([marie], at("2026-03-14T09:00:00"))).toBeUndefined(); // today is not "next"
  });
  it("the age she turns", () => {
    expect(turnsAge(marie, at("2026-03-14T09:00:00"))).toBe(76);
    expect(turnsAge(marie, at("2026-03-10T09:00:00"))).toBe(76);
    expect(turnsAge(marie, at("2026-03-20T09:00:00"))).toBe(77);
    expect(turnsAge(leap, at("2026-03-10T09:00:00"))).toBeUndefined();
  });
});
