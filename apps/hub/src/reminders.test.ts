import { mkdtempSync, readFileSync, rmSync, statSync, writeFileSync } from "node:fs";
import { tmpdir } from "node:os";
import { join } from "node:path";
import { afterEach, beforeEach, describe, expect, it } from "vitest";
import { buildApp } from "./app";
import { RemindersStore } from "./reminders";

let dir: string;
beforeEach(() => { dir = mkdtempSync(join(tmpdir(), "presence-rem-")); });
afterEach(() => rmSync(dir, { recursive: true, force: true }));

describe("RemindersStore", () => {
  it("adds, lists and removes", () => {
    const s = new RemindersStore(dir);
    const a = s.add({ title: "Médicament", at: "08:00" });
    s.add({ title: "Médecin", at: "14:30", date: "2026-10-12" });
    expect(s.list().map((r) => r.title)).toEqual(["Médicament", "Médecin"]);
    expect(s.remove(a.id)).toBe(true);
    expect(s.remove(a.id)).toBe(false);
    expect(s.list().map((r) => r.title)).toEqual(["Médecin"]);
  });

  it("refuses invalid input and a full list", () => {
    const s = new RemindersStore(dir);
    expect(() => s.add({ title: "", at: "08:00" })).toThrow();
    expect(() => s.add({ title: "x", at: "8h" })).toThrow();
    for (let i = 0; i < 50; i++) s.add({ title: `r${i}`, at: "08:00" });
    expect(() => s.add({ title: "one more", at: "08:00" })).toThrow();
  });

  it("is private to the Pi user", () => {
    const s = new RemindersStore(dir);
    s.add({ title: "Médicament", at: "08:00" });
    expect(statSync(join(dir, "reminders.json")).mode & 0o077).toBe(0);
  });

  it("skips damaged entries and survives a damaged file", () => {
    const s = new RemindersStore(dir);
    writeFileSync(join(dir, "reminders.json"), JSON.stringify([{ id: "a", title: "ok", at: "09:00" }, { id: "b", title: "", at: "99:99" }, 5]));
    expect(s.list().map((r) => r.id)).toEqual(["a"]);
    writeFileSync(join(dir, "reminders.json"), "{ not json");
    expect(s.list()).toEqual([]);
    expect(readFileSync(join(dir, "reminders.json"), "utf8")).toContain("not json"); // reading never rewrites
  });
});

describe("GET /reminders", () => {
  const TOKEN = "0123456789abcdef-token";
  const DISPLAY = "display-token-0123456789";
  it("needs the display token and returns the list", async () => {
    const reminders = new RemindersStore(dir);
    reminders.add({ title: "Médicament", at: "08:00" });
    const app = buildApp({ pairingToken: TOKEN, displayToken: DISPLAY, reminders });
    expect((await app.inject({ method: "GET", url: "/reminders" })).statusCode).toBe(401);
    expect((await app.inject({ method: "GET", url: "/reminders", headers: { authorization: `Bearer ${TOKEN}` } })).statusCode).toBe(401);
    const ok = await app.inject({ method: "GET", url: "/reminders", headers: { authorization: `Bearer ${DISPLAY}` } });
    expect(ok.statusCode).toBe(200);
    expect(ok.json().reminders).toHaveLength(1);
    expect(ok.headers["cache-control"]).toBe("no-store");
  });
  it("does not exist without the feature", async () => {
    const app = buildApp({ pairingToken: TOKEN, displayToken: DISPLAY });
    expect((await app.inject({ method: "GET", url: "/reminders", headers: { authorization: `Bearer ${DISPLAY}` } })).statusCode).toBe(404);
  });
});
