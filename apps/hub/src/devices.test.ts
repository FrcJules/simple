import { existsSync, mkdirSync, mkdtempSync, readdirSync, readFileSync, rmSync, statSync, writeFileSync } from "node:fs";
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
    const all = () => [...readdirSync(join(dir, "claims")), ...readdirSync(join(dir, "devices"))].join("\n");
    expect(all()).not.toContain(claim);
    const token = r.redeemClaim(claim)!;
    expect(all()).not.toContain(token);
    const [file] = readdirSync(join(dir, "devices"));
    expect(readFileSync(join(dir, "devices", file!), "utf8")).not.toContain(token);
  });

  it("keeps its files private to the hub user", () => {
    const r = registry();
    r.redeemClaim(r.createClaim());
    expect(statSync(join(dir, "devices", readdirSync(join(dir, "devices"))[0]!)).mode & 0o077).toBe(0);
  });

  it("remembers the name given at pairing and when the phone was last seen", () => {
    const r = registry();
    const token = r.redeemClaim(r.createClaim("  Téléphone de Marie  "))!;
    expect(r.devices()).toMatchObject([{ label: "Téléphone de Marie", createdAt: now, lastUsedAt: now }]);
    now += 3_600_000;
    expect(r.isDeviceToken(token)).toBe(true);
    expect(r.devices()[0]!.lastUsedAt).toBe(now);
    now += 60_000; // used again within ten minutes: no disk write, the date stays
    r.isDeviceToken(token);
    expect(r.devices()[0]!.lastUsedAt).toBe(now - 60_000);
  });

  it("works without a name, and a name is capped", () => {
    const r = registry();
    r.redeemClaim(r.createClaim());
    r.redeemClaim(r.createClaim("x".repeat(100)));
    const labels = r.devices().map((d) => d.label);
    expect(labels).toContain(undefined);
    expect(labels).toContain("x".repeat(40));
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

  it("revokes one device by its id and leaves the others", () => {
    const r = registry();
    const a = r.redeemClaim(r.createClaim())!;
    now += 1;
    const b = r.redeemClaim(r.createClaim())!;
    const [first, second] = r.devices();
    expect(r.revoke(first!.id)).toBe(true);
    expect(r.isDeviceToken(a)).toBe(false);
    expect(r.isDeviceToken(b)).toBe(true);
    expect(r.devices().map((d) => d.id)).toEqual([second!.id]);
    expect(r.revoke(first!.id)).toBe(false); // already gone
  });

  it("refuses an id that is not hexadecimal (no path can be built from it)", () => {
    const r = registry();
    r.redeemClaim(r.createClaim());
    for (const id of ["", "..", "../x", "zz", "a"]) expect(r.revoke(id), id).toBe(false);
    expect(r.deviceCount()).toBe(1);
  });

  it("lets only one of two racing redemptions win (the claim file is the lock)", () => {
    const claim = registry().createClaim();
    const results = [registry().redeemClaim(claim), registry().redeemClaim(claim)];
    expect(results.filter((t) => t !== null)).toHaveLength(1);
    expect(registry().deviceCount()).toBe(1);
  });

  it("knows whether a QR code can still be redeemed", () => {
    const r = registry();
    expect(r.hasActiveClaim()).toBe(false);
    const claim = r.createClaim();
    expect(r.hasActiveClaim()).toBe(true);
    now += DeviceRegistry.CLAIM_TTL_MS + 1;
    expect(r.hasActiveClaim()).toBe(false);
    expect(readdirSync(join(dir, "claims"))).toHaveLength(0); // the expired claim was cleaned up
    expect(r.redeemClaim(claim)).toBeNull();
  });

  it("treats damaged or unexpected files as 'nothing paired', not a crash", () => {
    const r = registry();
    const hash = "a".repeat(64);
    writeFileSync(join(dir, "devices", hash), "{ not json");
    writeFileSync(join(dir, "devices", "b".repeat(64)), JSON.stringify({ createdAt: "yesterday" }));
    writeFileSync(join(dir, "devices", "notes.txt"), "ignored");
    mkdirSync(join(dir, "devices", "c".repeat(64)));
    expect(r.deviceCount()).toBe(0);
    expect(r.isDeviceToken("anything")).toBe(false);
    writeFileSync(join(dir, "claims", hash), "garbage");
    expect(r.hasActiveClaim()).toBe(false);
    expect(existsSync(join(dir, "claims", hash))).toBe(false);
  });

  it("never accepts an empty token", () => expect(registry().isDeviceToken("")).toBe(false));
});

describe("hub pairing", () => {
  const TOKEN = "0123456789abcdef-token";
  const DISPLAY = "display-token-0123456789";
  const event = { schemaVersion: 1, id: "e1", type: "missed_call", occurredAt: "2026-10-06T09:30:00+02:00", from: { name: "Marie" } };
  const hub = () => buildApp({ pairingToken: TOKEN, displayToken: DISPLAY, devices: registry() });
  // The tests that follow need a QR code to be active, as it is when `npm run pair` has just been run.

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
    expect((await app.inject({ method: "POST", url: "/pair", payload: { claim } })).statusCode).toBe(404); // no QR code is active any more
    registry().createClaim(); // a newer QR code exists: the used one is now a plain refusal
    expect((await app.inject({ method: "POST", url: "/pair", payload: { claim } })).statusCode).toBe(403);
  });

  it("refuses malformed bodies", async () => {
    const app = hub();
    registry().createClaim();
    for (const payload of [{}, { claim: 5 }, { claim: "short" }, { claim: "x".repeat(201) }, { claim: "x".repeat(20), extra: 1 }]) {
      expect((await app.inject({ method: "POST", url: "/pair", payload })).statusCode, JSON.stringify(payload)).toBe(400);
    }
  });

  it("limits failed attempts per address, and the real phone still pairs from another address", async () => {
    const reg = registry();
    const app = buildApp({ pairingToken: TOKEN, displayToken: DISPLAY, devices: reg });
    const claim = reg.createClaim();
    const codes: number[] = [];
    for (let i = 0; i < 8; i++) codes.push((await app.inject({ method: "POST", url: "/pair", remoteAddress: "10.0.0.66", payload: { claim: `wrong-claim-${i}-0123456789` } })).statusCode);
    expect(codes.slice(0, 5).every((c) => c === 403)).toBe(true);
    expect(codes.slice(5).every((c) => c === 429)).toBe(true);
    // the flood did not use up or block the real claim for anybody else
    expect((await app.inject({ method: "POST", url: "/pair", remoteAddress: "10.0.0.7", payload: { claim } })).statusCode).toBe(200);
  });

  it("does not answer at all when no QR code is active (nothing to flood, nothing to learn)", async () => {
    const app = hub();
    for (let i = 0; i < 20; i++) {
      expect((await app.inject({ method: "POST", url: "/pair", payload: { claim: `wrong-claim-${i}-0123456789` } })).statusCode).toBe(404);
    }
    // ...and the flood did not count against anybody: a QR code made afterwards works at once
    const claim = registry().createClaim();
    expect((await app.inject({ method: "POST", url: "/pair", payload: { claim } })).statusCode).toBe(200);
  });

  it("works without any static pairing token", async () => {
    const reg = registry();
    const app = buildApp({ displayToken: DISPLAY, devices: reg });
    const deviceToken = reg.redeemClaim(reg.createClaim())!;
    expect((await app.inject({ method: "POST", url: "/events", payload: event, headers: { authorization: `Bearer ${deviceToken}` } })).statusCode).toBe(202);
    expect((await app.inject({ method: "POST", url: "/events", payload: event, headers: { authorization: "Bearer " } })).statusCode).toBe(401);
    expect(() => buildApp({ displayToken: DISPLAY })).toThrow();
  });

  it("a revoked device can no longer write", async () => {
    const reg = registry();
    const app = buildApp({ pairingToken: TOKEN, displayToken: DISPLAY, devices: reg });
    const deviceToken = reg.redeemClaim(reg.createClaim())!;
    reg.revokeAll();
    expect((await app.inject({ method: "POST", url: "/events", payload: event, headers: { authorization: `Bearer ${deviceToken}` } })).statusCode).toBe(401);
  });

  it("counts wrong tokens on every route and blocks that address, not the others", async () => {
    const reg = registry();
    const app = buildApp({ pairingToken: TOKEN, displayToken: DISPLAY, devices: reg });
    const bad = (url: string, ip: string) => app.inject({ method: "GET", url, remoteAddress: ip, headers: { authorization: "Bearer nope-nope-nope-nope" } });
    for (let i = 0; i < 10; i++) expect((await bad("/events", "10.0.0.66")).statusCode).toBe(401);
    expect((await bad("/events", "10.0.0.66")).statusCode).toBe(429);
    expect((await app.inject({ method: "GET", url: "/events/stream", remoteAddress: "10.0.0.66", headers: { authorization: `Bearer ${DISPLAY}` } })).statusCode).toBe(429);
    expect((await app.inject({ method: "POST", url: "/events", payload: event, remoteAddress: "10.0.0.66", headers: { authorization: `Bearer ${TOKEN}` } })).statusCode).toBe(429);
    expect((await app.inject({ method: "GET", url: "/events", remoteAddress: "10.0.0.7", headers: { authorization: `Bearer ${DISPLAY}` } })).statusCode).toBe(200);
  });

  it("a valid device token is never blocked, even from an address that failed a lot; and a bad body is refused before it is parsed", async () => {
    const reg = registry();
    const app = buildApp({ pairingToken: TOKEN, displayToken: DISPLAY, devices: reg });
    const deviceToken = reg.redeemClaim(reg.createClaim())!;
    for (let i = 0; i < 12; i++) await app.inject({ method: "POST", url: "/events", remoteAddress: "10.0.0.9", payload: event, headers: { authorization: "Bearer wrong-wrong-wrong-wrong" } });
    expect((await app.inject({ method: "POST", url: "/events", remoteAddress: "10.0.0.9", payload: event, headers: { authorization: `Bearer ${deviceToken}` } })).statusCode).toBe(202);
    expect((await app.inject({ method: "POST", url: "/events", remoteAddress: "10.0.0.9", payload: event, headers: { authorization: `Bearer ${TOKEN}` } })).statusCode).toBe(429);
    const huge = { ...event, from: { name: "x".repeat(20_000) } };
    expect((await app.inject({ method: "POST", url: "/events", remoteAddress: "10.0.0.10", payload: huge, headers: { authorization: `Bearer ${TOKEN}` } })).statusCode).toBe(413);
  });

  it("the display starts a pairing with its token, at most every 3 seconds, and the answer holds a working one-time claim", async () => {
    const reg = registry();
    const app = buildApp({
      displayToken: DISPLAY, devices: reg,
      startPairing: () => ({ link: `presence://pair?s=${reg.createClaim()}`, fingerprint: "AAAA BBBB", expiresInSeconds: 120 }),
    });
    const go = (headers: Record<string, string>) => app.inject({ method: "POST", url: "/pairing/start", headers });
    expect((await go({})).statusCode).toBe(401);
    const ok = await go({ authorization: `Bearer ${DISPLAY}` });
    expect(ok.statusCode).toBe(200);
    expect(ok.json()).toMatchObject({ fingerprint: "AAAA BBBB", expiresInSeconds: 120 });
    expect(reg.hasActiveClaim()).toBe(true);
    expect((await go({ authorization: `Bearer ${DISPLAY}` })).statusCode).toBe(429); // too soon
  });

  it("/pairing/start refuses a foreign Host or Origin, a token in the URL, and the QR code can be cancelled", async () => {
    const reg = registry();
    const app = buildApp({ displayToken: DISPLAY, devices: reg, displayOrigin: "http://localhost:5173", startPairing: () => ({ link: `presence://pair?s=${reg.createClaim()}`, fingerprint: "A", expiresInSeconds: 120 }) });
    const auth = { authorization: `Bearer ${DISPLAY}` };
    const post = (url: string, headers: Record<string, string>) => app.inject({ method: "POST", url, headers });
    expect((await post("/pairing/start", { ...auth, host: "evil.example:8787" })).statusCode).toBe(404);
    expect((await post("/pairing/start", { ...auth, origin: "http://evil.example" })).statusCode).toBe(404);
    expect((await app.inject({ method: "POST", url: `/pairing/start?token=${DISPLAY}` })).statusCode).toBe(401);
    expect(reg.hasActiveClaim()).toBe(false);
    const ok = await post("/pairing/start", { ...auth, host: "localhost:8787", origin: "http://localhost:5173" });
    expect(ok.statusCode).toBe(200);
    expect(ok.headers["cache-control"]).toBe("no-store");
    expect(reg.hasActiveClaim()).toBe(true);
    expect((await post("/pairing/cancel", {})).statusCode).toBe(401);
    expect((await post("/pairing/cancel", auth)).statusCode).toBe(204);
    expect(reg.hasActiveClaim()).toBe(false);
  });

  it("/pairing/start is absent without the feature, refuses other addresses and reports a missing network", async () => {
    const reg = registry();
    const plain = buildApp({ displayToken: DISPLAY, devices: reg });
    expect((await plain.inject({ method: "POST", url: "/pairing/start", headers: { authorization: `Bearer ${DISPLAY}` } })).statusCode).toBe(404);
    const app = buildApp({ displayToken: DISPLAY, devices: reg, startPairing: () => { throw new Error("no address"); } });
    expect((await app.inject({ method: "POST", url: "/pairing/start", remoteAddress: "192.168.1.20", headers: { authorization: `Bearer ${DISPLAY}` } })).statusCode).toBe(404);
    expect((await app.inject({ method: "POST", url: "/pairing/start", headers: { authorization: `Bearer ${DISPLAY}` } })).statusCode).toBe(503);
  });

  it("there is no /pair without a device registry", async () => {
    registry().createClaim();
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
