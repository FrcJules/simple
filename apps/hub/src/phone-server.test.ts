import { mkdtempSync, rmSync } from "node:fs";
import https from "node:https";
import { connect, type Socket } from "node:net";
import { connect as tlsConnect, type PeerCertificate } from "node:tls";
import { tmpdir } from "node:os";
import { join } from "node:path";
import { afterAll, afterEach, describe, expect, it } from "vitest";
import { buildApp } from "./app";
import { createPhoneServer, PHONE_SERVER_LIMITS } from "./phone-server";
import { loadOrCreateTls } from "./tls";

const dir = mkdtempSync(join(tmpdir(), "simple-phone-"));
const tls = loadOrCreateTls(dir);
afterAll(() => rmSync(dir, { recursive: true, force: true }));

/** Like the phone: the hub's self-signed certificate is trusted only if its SHA-256 matches the pinned fingerprint. */
const pinned = {
  ca: tls.cert,
  checkServerIdentity: (_host: string, cert: PeerCertificate) =>
    cert.fingerprint256.replaceAll(":", "") === tls.fingerprint ? undefined : new Error("certificate does not match the pinned fingerprint"),
};

const opened: Socket[] = [];
const servers: https.Server[] = [];
afterEach(async () => {
  opened.splice(0).forEach((s) => s.destroy());
  await Promise.all(servers.splice(0).map((s) => new Promise((r) => s.close(r))));
});

async function start(limits = PHONE_SERVER_LIMITS): Promise<number> {
  const app = buildApp({ pairingToken: "0123456789abcdef-token", displayToken: "display-token-0123456789" });
  await app.ready();
  const server = createPhoneServer(app, tls, limits);
  servers.push(server);
  await new Promise<void>((r) => server.listen(0, "127.0.0.1", r));
  return (server.address() as { port: number }).port;
}

/** A raw TCP connection that never speaks TLS, like a slowloris client; resolves with how long the server kept it open. */
function silentConnection(port: number): Promise<number> {
  return new Promise((resolve) => {
    const started = Date.now();
    const socket = connect(port, "127.0.0.1");
    opened.push(socket);
    socket.on("error", () => undefined);
    socket.on("close", () => resolve(Date.now() - started));
  });
}

function request(port: number, method: string, path: string): Promise<number> {
  return new Promise((resolve, reject) => {
    const req = https.request({ host: "127.0.0.1", port, method, path, ...pinned }, (res) => {
      res.resume();
      resolve(res.statusCode ?? 0);
    });
    req.on("error", reject);
    req.end();
  });
}

describe("phone server", () => {
  it("only exposes pairing and pushing events", async () => {
    const port = await start();
    expect(await request(port, "GET", "/events")).toBe(404);
    expect(await request(port, "GET", "/events/stream?token=x")).toBe(404);
    expect(await request(port, "POST", "/events")).toBe(401); // reached the app, which asks for a token
  });

  it("drops a connection that never finishes the TLS handshake", async () => {
    const port = await start({ ...PHONE_SERVER_LIMITS, handshakeMs: 300 });
    const keptOpen = await silentConnection(port);
    expect(keptOpen).toBeLessThan(2_000);
  });

  it("refuses more connections from one address than the cap, and accepts again once they close", async () => {
    const port = await start({ ...PHONE_SERVER_LIMITS, maxPerAddress: 2, handshakeMs: 10_000 });
    const first = silentConnection(port);
    const second = silentConnection(port);
    await new Promise((r) => setTimeout(r, 100));
    const third = await silentConnection(port); // closed straight away
    expect(third).toBeLessThan(1_000);
    opened.slice(0, 2).forEach((s) => s.destroy());
    await Promise.all([first, second]);
    await new Promise((r) => setTimeout(r, 50));
    expect(await request(port, "POST", "/events")).toBe(401); // a slot is free again
  });

  it("drops a connection that sends its headers slowly, without waiting 30 s", async () => {
    const port = await start({ ...PHONE_SERVER_LIMITS, headersMs: 300, requestMs: 400, checkIntervalMs: 100 });
    const keptOpen = await new Promise<number>((resolve) => {
      const started = Date.now();
      const socket = tlsConnect({ host: "127.0.0.1", port, ...pinned }, () => {
        socket.write("POST /events HTTP/1.1\r\nHost: x\r\n"); // headers started, never finished
      });
      socket.resume(); // read the server's 408, or "close" would wait for it
      socket.on("error", () => undefined);
      socket.on("close", () => resolve(Date.now() - started));
    });
    expect(keptOpen).toBeLessThan(2_000);
  });

  it("drops a connection that finishes TLS and then stays silent", async () => {
    const port = await start({ ...PHONE_SERVER_LIMITS, idleMs: 300 });
    const keptOpen = await new Promise<number>((resolve) => {
      const started = Date.now();
      const socket = tlsConnect({ host: "127.0.0.1", port, ...pinned });
      socket.on("error", () => undefined);
      socket.on("close", () => resolve(Date.now() - started));
    });
    expect(keptOpen).toBeLessThan(2_000);
  });

  it("closes a connection at the end of its lifetime, even a busy one", async () => {
    const port = await start({ ...PHONE_SERVER_LIMITS, lifetimeMs: 300 });
    expect(await silentConnection(port)).toBeLessThan(2_000);
  });

  it("limits how many new connections one address opens in a row", async () => {
    const port = await start({ ...PHONE_SERVER_LIMITS, newPerAddress: 3, newPerAddressWindowMs: 60_000 });
    for (let i = 0; i < 3; i++) expect(await request(port, "POST", "/events")).toBe(401);
    await expect(request(port, "POST", "/events")).rejects.toThrow();
  });

  it("uses short limits by default", () => {
    expect(PHONE_SERVER_LIMITS.handshakeMs).toBeLessThanOrEqual(10_000);
    expect(PHONE_SERVER_LIMITS.requestMs).toBeLessThanOrEqual(30_000);
    expect(PHONE_SERVER_LIMITS.maxPerAddress).toBeLessThan(PHONE_SERVER_LIMITS.maxConnections);
  });
});
