import { mkdtempSync, rmSync, statSync, writeFileSync } from "node:fs";
import { tmpdir } from "node:os";
import { join } from "node:path";
import { afterEach, beforeEach, describe, expect, it } from "vitest";
import { buildApp } from "./app";
import { BirthdaysStore } from "./birthdays";

let dir: string;
beforeEach(() => { dir = mkdtempSync(join(tmpdir(), "simple-bd-")); });
afterEach(() => rmSync(dir, { recursive: true, force: true }));

describe("BirthdaysStore", () => {
  it("adds, lists and removes", () => {
    const s = new BirthdaysStore(dir);
    const a = s.add({ name: "Marie", day: "03-14", year: 1950 });
    s.add({ name: "Paul", day: "12-01" });
    expect(s.list().map((b) => b.name)).toEqual(["Marie", "Paul"]);
    expect(s.remove(a.id)).toBe(true);
    expect(s.remove(a.id)).toBe(false);
    expect(s.list().map((b) => b.name)).toEqual(["Paul"]);
  });
  it("refuses impossible days, empty names and a full list", () => {
    const s = new BirthdaysStore(dir);
    expect(() => s.add({ name: "X", day: "02-30" })).toThrow();
    expect(() => s.add({ name: "", day: "01-01" })).toThrow();
    for (let i = 0; i < 100; i++) s.add({ name: `n${i}`, day: "01-01" });
    expect(() => s.add({ name: "more", day: "01-01" })).toThrow();
  });
  it("is private to the Pi user and survives damage", () => {
    const s = new BirthdaysStore(dir);
    s.add({ name: "Marie", day: "03-14" });
    expect(statSync(join(dir, "birthdays.json")).mode & 0o077).toBe(0);
    writeFileSync(join(dir, "birthdays.json"), JSON.stringify([{ id: "a", name: "ok", day: "01-02" }, { id: "b", name: "bad", day: "99-99" }, 3]));
    expect(s.list().map((b) => b.id)).toEqual(["a"]);
    writeFileSync(join(dir, "birthdays.json"), "{ nope");
    expect(s.list()).toEqual([]);
  });
});

describe("GET /birthdays", () => {
  const DISPLAY = "display-token-0123456789";
  it("needs the display token; absent without the feature", async () => {
    const birthdays = new BirthdaysStore(dir);
    birthdays.add({ name: "Marie", day: "03-14" });
    const app = buildApp({ pairingToken: "0123456789abcdef-token", displayToken: DISPLAY, birthdays });
    expect((await app.inject({ method: "GET", url: "/birthdays" })).statusCode).toBe(401);
    const ok = await app.inject({ method: "GET", url: "/birthdays", headers: { authorization: `Bearer ${DISPLAY}` } });
    expect(ok.json().birthdays).toHaveLength(1);
    const none = buildApp({ pairingToken: "0123456789abcdef-token", displayToken: DISPLAY });
    expect((await none.inject({ method: "GET", url: "/birthdays", headers: { authorization: `Bearer ${DISPLAY}` } })).statusCode).toBe(404);
  });
});
