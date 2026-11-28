import { mkdtempSync, rmSync } from "node:fs";
import https from "node:https";
import { connect, type Socket } from "node:net";
import { tmpdir } from "node:os";
import { join } from "node:path";
import { afterAll, afterEach, describe, expect, it } from "vitest";
import { buildApp } from "./app";
import { createPhoneServer, PHONE_SERVER_LIMITS } from "./phone-server";
import { loadOrCreateTls } from "./tls";

const dir = mkdtempSync(join(tmpdir(), "simple-phone-"));
const tls = loadOrCreateTls(dir);
afterAll(() => rmSync(dir, { recursive: true, force: true }));

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
    const req = https.request({ host: "127.0.0.1", port, method, path, rejectUnauthorized: false }, (res) => {
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

  it("uses short limits by default", () => {
    expect(PHONE_SERVER_LIMITS.handshakeMs).toBeLessThanOrEqual(10_000);
    expect(PHONE_SERVER_LIMITS.requestMs).toBeLessThanOrEqual(30_000);
    expect(PHONE_SERVER_LIMITS.maxPerAddress).toBeLessThan(PHONE_SERVER_LIMITS.maxConnections);
  });
});
