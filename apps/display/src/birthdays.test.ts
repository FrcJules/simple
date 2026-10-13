import type { Birthday } from "@simple/contracts";
import { describe, expect, it } from "vitest";
import { birthdaysToday, daysUntil, nextBirthdays, plausibleAge, turnsAge } from "./birthdays";
import { birthdayText, nameList } from "./strings";

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
    expect(nextBirthdays([marie], at("2026-03-10T09:00:00"))).toEqual({ days: 4, people: [marie] });
    expect(nextBirthdays([marie], at("2026-03-01T09:00:00"))).toBeUndefined();
    expect(nextBirthdays([marie], at("2026-03-14T09:00:00"))).toBeUndefined(); // today is not "next"
  });
  it("two people on the same coming day are both shown", () => {
    const paul: Birthday = { id: "p", name: "Paul", day: "03-14" };
    expect(nextBirthdays([marie, paul], at("2026-03-10T09:00:00"))?.people).toEqual([marie, paul]);
  });
  it("the age she turns", () => {
    expect(turnsAge(marie, at("2026-03-14T09:00:00"))).toBe(76);
    expect(turnsAge(marie, at("2026-03-10T09:00:00"))).toBe(76);
    expect(turnsAge(marie, at("2026-03-20T09:00:00"))).toBe(77);
    expect(turnsAge(leap, at("2026-03-10T09:00:00"))).toBeUndefined();
  });
});

describe("texts", () => {
  it("groups names and keeps the line short", () => {
    expect(nameList(["Marie"])).toBe("Marie");
    expect(nameList(["Marie", "Paul"])).toBe("Marie et Paul");
    expect(nameList(["Marie", "Paul", "Léa"])).toBe("Marie, Paul et 1 autre");
    expect(nameList(["Marie", "Paul", "Léa", "Jo"])).toBe("Marie, Paul et 2 autres");
  });
  it("says the age only for one person and a believable age", () => {
    expect(birthdayText.today(["Marie"], 76)).toBe("Aujourd'hui, Marie fête ses 76 ans");
    expect(birthdayText.today(["Marie", "Paul"], 76)).toBe("Aujourd'hui, anniversaire de Marie et Paul");
    expect(birthdayText.today(["Marie"])).toBe("Aujourd'hui, anniversaire de Marie");
    expect(plausibleAge(-3)).toBeUndefined();
    expect(plausibleAge(140)).toBeUndefined();
    expect(plausibleAge(1)).toBeUndefined();
    expect(plausibleAge(76)).toBe(76);
  });
  it("names the weekday, or says tomorrow", () => {
    expect(birthdayText.soon(["Marie"], 1, 6)).toBe("Demain, anniversaire de Marie");
    expect(birthdayText.soon(["Marie"], 3, 6)).toBe("Samedi, anniversaire de Marie");
  });
});
