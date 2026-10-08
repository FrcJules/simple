import { mkdtempSync, readFileSync, rmSync, statSync, writeFileSync } from "node:fs";
import { tmpdir } from "node:os";
import { join } from "node:path";
import { afterEach, beforeEach, describe, expect, it } from "vitest";
import { buildApp } from "./app";
import { DeviceRegistry } from "./devices";

let dir: string;
let now = 1_000_000;
beforeEach(() => { dir = mkdtempSync(join(tmpdir(), "presence-dev-")); now = 1_000_000; });
afterEach(() => rmSync(dir, { recursive: true, force: true }));
const registry = () => new DeviceRegistry(dir, () => now);

describe("DeviceRegistry", () => {
  it("exchanges a claim for a device token, once", () => {
    const r = registry();
    const claim = r.createClaim();
    const token = r.redeemClaim(claim);
    expect(token).toMatch(/^[A-Za-z0-9_-]{32}$/);
    expect(r.isDeviceToken(token!)).toBe(true);
    expect(r.redeemClaim(claim)).toBeNull(); // single use
  });

  it("refuses an unknown or expired claim", () => {
    const r = registry();
    expect(r.redeemClaim("not-a-claim-0123456789")).toBeNull();
    const claim = r.createClaim();
    now += DeviceRegistry.CLAIM_TTL_MS + 1;
    expect(r.redeemClaim(claim)).toBeNull();
  });

  it("burns a claim even when it is presented too late, and only the latest QR code works", () => {
    const r = registry();
    const first = r.createClaim();
    const second = r.createClaim();
    expect(r.redeemClaim(first)).toBeNull();
    expect(r.redeemClaim(second)).not.toBeNull();
  });

  it("stores only hashes: a copy of the data folder gives no usable token", () => {
    const r = registry();
    const claim = r.createClaim();
    expect(readFileSync(join(dir, "claims.json"), "utf8")).not.toContain(claim);
    const token = r.redeemClaim(claim)!;
    expect(readFileSync(join(dir, "devices.json"), "utf8")).not.toContain(token);
  });

  it("keeps its files private to the hub user", () => {
    const r = registry();
    r.redeemClaim(r.createClaim());
    expect(statSync(join(dir, "devices.json")).mode & 0o077).toBe(0);
  });

  it("is shared between the pairing command and the running hub (two instances, one folder)", () => {
    const claim = registry().createClaim();
    const token = registry().redeemClaim(claim)!;
    expect(registry().isDeviceToken(token)).toBe(true);
  });

  it("revokes every device", () => {
    const r = registry();
    const token = r.redeemClaim(r.createClaim())!;
    r.revokeAll();
    expect(r.isDeviceToken(token)).toBe(false);
    expect(r.deviceCount()).toBe(0);
  });

  it("treats a damaged file as 'nothing paired', not a crash", () => {
    writeFileSync(join(dir, "devices.json"), "{ not json");
    expect(registry().isDeviceToken("anything")).toBe(false);
    expect(registry().deviceCount()).toBe(0);
  });

  it("never accepts an empty token", () => expect(registry().isDeviceToken("")).toBe(false));
});

describe("hub pairing", () => {
  const TOKEN = "0123456789abcdef-token";
  const DISPLAY = "display-token-0123456789";
  const event = { schemaVersion: 1, id: "e1", type: "missed_call", occurredAt: "2026-10-06T09:30:00+02:00", from: { name: "Marie" } };
  const hub = () => buildApp({ pairingToken: TOKEN, displayToken: DISPLAY, devices: registry() });

  it("a phone redeems the claim and then writes events with its own token, not the master token", async () => {
    const app = hub();
    const claim = registry().createClaim();
    const pair = await app.inject({ method: "POST", url: "/pair", payload: { claim } });
    expect(pair.statusCode).toBe(200);
    const deviceToken = pair.json().token as string;
    expect(deviceToken).not.toBe(TOKEN);
    const post = await app.inject({ method: "POST", url: "/events", payload: event, headers: { authorization: `Bearer ${deviceToken}` } });
    expect(post.statusCode).toBe(202);
  });

  it("the claim works once", async () => {
    const app = hub();
    const claim = registry().createClaim();
    expect((await app.inject({ method: "POST", url: "/pair", payload: { claim } })).statusCode).toBe(200);
    expect((await app.inject({ method: "POST", url: "/pair", payload: { claim } })).statusCode).toBe(403);
  });

  it("refuses malformed claims", async () => {
    const app = hub();
    for (const payload of [{}, { claim: 5 }, { claim: "short" }, { claim: "x".repeat(201) }]) {
      expect((await app.inject({ method: "POST", url: "/pair", payload })).statusCode, JSON.stringify(payload)).toBe(400);
    }
  });

  it("limits attempts", async () => {
    const app = hub();
    const codes: number[] = [];
    for (let i = 0; i < 14; i++) codes.push((await app.inject({ method: "POST", url: "/pair", payload: { claim: `wrong-claim-${i}-0123456789` } })).statusCode);
    expect(codes.slice(0, 10).every((c) => c === 403)).toBe(true);
    expect(codes.slice(10).every((c) => c === 429)).toBe(true);
  });

  it("a revoked device can no longer write", async () => {
    const reg = registry();
    const app = buildApp({ pairingToken: TOKEN, displayToken: DISPLAY, devices: reg });
    const deviceToken = reg.redeemClaim(reg.createClaim())!;
    reg.revokeAll();
    expect((await app.inject({ method: "POST", url: "/events", payload: event, headers: { authorization: `Bearer ${deviceToken}` } })).statusCode).toBe(401);
  });

  it("there is no /pair without a device registry", async () => {
    const app = buildApp({ pairingToken: TOKEN, displayToken: DISPLAY });
    expect((await app.inject({ method: "POST", url: "/pair", payload: { claim: "x".repeat(20) } })).statusCode).toBe(404);
  });

  it("a device token can write but not read the display stream", async () => {
    const reg = registry();
    const app = buildApp({ pairingToken: TOKEN, displayToken: DISPLAY, devices: reg });
    const deviceToken = reg.redeemClaim(reg.createClaim())!;
    expect((await app.inject({ method: "GET", url: "/events", headers: { authorization: `Bearer ${deviceToken}` } })).statusCode).toBe(401);
  });
});
