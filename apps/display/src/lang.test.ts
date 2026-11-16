import { describe, expect, it } from "vitest";
import { birthdayText, nameList, texts } from "./strings";
import { formatDate, formatTime, greeting, headline, timeAgo } from "./format";
import { describeWeather } from "./weather";
import { formatAt } from "./reminders";
import { lang, resolveLang } from "./lang";

describe("language choice", () => {
  it("is French unless English is asked for", () => {
    expect(resolveLang(undefined)).toBe("fr");
    expect(resolveLang("")).toBe("fr");
    expect(resolveLang("de")).toBe("fr");
    expect(resolveLang("en")).toBe("en");
    expect(resolveLang(" EN-GB ")).toBe("en");
  });

  it("the tests themselves run in French (no VITE_LANG)", () => {
    expect(lang).toBe("fr");
  });
});

describe("English home screen", () => {
  const at = new Date(2026, 9, 6, 9, 5); // Tuesday 6 October 2026, 09:05

  it("greets by the time of day", () => {
    expect(greeting(new Date(2026, 9, 6, 9), "Jean-Pierre", "en")).toBe("Good morning Jean-Pierre");
    expect(greeting(new Date(2026, 9, 6, 14), "Jean-Pierre", "en")).toBe("Good afternoon Jean-Pierre");
    expect(greeting(new Date(2026, 9, 6, 19), "Jean-Pierre", "en")).toBe("Good evening Jean-Pierre");
  });

  it("formats date and time the English way", () => {
    expect(formatDate(at, "en")).toBe("Tuesday, October 6");
    expect(formatTime(at, "en")).toBe("9:05 AM");
    expect(formatTime(at, "fr")).toMatch(/^09[:h]05$/);
  });

  it("says how long ago, with plurals", () => {
    const now = new Date(2026, 9, 6, 12, 0);
    expect(timeAgo(new Date(now.getTime() - 20_000), now, "en")).toBe("just now");
    expect(timeAgo(new Date(now.getTime() - 60_000), now, "en")).toBe("1 minute ago");
    expect(timeAgo(new Date(now.getTime() - 20 * 60_000), now, "en")).toBe("20 minutes ago");
    expect(timeAgo(new Date(now.getTime() - 2 * 3_600_000), now, "en")).toBe("2 hours ago");
    expect(timeAgo(new Date(now.getTime() - 3 * 86_400_000), now, "en")).toBe("3 days ago");
  });

  it("titles every kind of event", () => {
    const base = { schemaVersion: 1 as const, id: "1", occurredAt: "2026-10-06T10:00:00Z" };
    const from = { name: "Marie" };
    expect(headline({ ...base, type: "missed_call", from }, "en")).toBe("Marie called you");
    expect(headline({ ...base, type: "new_message", from, channel: "sms", text: "Hi" }, "en")).toBe("Message from Marie");
    expect(headline({ ...base, type: "birthday", who: from }, "en")).toBe("Marie's birthday today");
  });

  it("words the reminders and the weather", () => {
    expect(formatAt("08:05", "en")).toBe("8:05 AM");
    expect(formatAt("00:30", "en")).toBe("12:30 AM");
    expect(formatAt("12:00", "en")).toBe("12:00 PM");
    expect(formatAt("15:45", "en")).toBe("3:45 PM");
    expect(formatAt("15:45", "fr")).toBe("15 h 45");
    expect(describeWeather(0, "en").label).toBe("Sunny");
    expect(describeWeather(61, "en").label).toBe("Rain");
    expect(describeWeather(0, "fr").label).toBe("Grand soleil");
    expect(texts.en.range("12°", "19°")).toBe("12° to 19°");
  });

  it("words the birthdays", () => {
    expect(nameList(["Marie"], "en")).toBe("Marie");
    expect(nameList(["Marie", "Paul"], "en")).toBe("Marie and Paul");
    expect(nameList(["Marie", "Paul", "Anne"], "en")).toBe("Marie, Paul and 1 other");
    expect(nameList(["Marie", "Paul", "Anne", "Luc"], "en")).toBe("Marie, Paul and 2 others");
    expect(birthdayText.today(["Marie"], 76, "en")).toBe("Today Marie turns 76");
    expect(birthdayText.today(["Marie", "Paul"], undefined, "en")).toBe("Today is Marie and Paul's birthday");
    expect(birthdayText.soon(["Marie"], 1, 3, "en")).toBe("Tomorrow: Marie's birthday");
    expect(birthdayText.soon(["Marie"], 3, 6, "en")).toBe("Saturday: Marie's birthday");
  });

  it("has exactly the same texts as French (nothing left untranslated)", () => {
    const shape = (o: object): string[] => Object.entries(o).flatMap(([k, v]) => (typeof v === "object" && !Array.isArray(v) ? shape(v).map((x) => `${k}.${x}`) : [k]));
    expect(shape(texts.en)).toEqual(shape(texts.fr));
    expect(texts.en.weekdays).toHaveLength(7);
  });
});
