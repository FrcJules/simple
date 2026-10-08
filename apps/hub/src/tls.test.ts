import { mkdtempSync, rmSync } from "node:fs";
import https from "node:https";
import { createHash, X509Certificate } from "node:crypto";
import { tmpdir } from "node:os";
import { join } from "node:path";
import { afterAll, describe, expect, it } from "vitest";
import { buildApp } from "./app";
import { loadOrCreateTls, rotateTls } from "./tls";

const dir = mkdtempSync(join(tmpdir(), "presence-tls-"));
afterAll(() => rmSync(dir, { recursive: true, force: true }));

describe("tls", () => {
  it("rotates to a different certificate", () => {
    const rotDir = mkdtempSync(join(tmpdir(), "presence-rot-"));
    try {
      const before = loadOrCreateTls(rotDir);
      const after = rotateTls(rotDir);
      expect(after.fingerprint).not.toBe(before.fingerprint);
      expect(loadOrCreateTls(rotDir).fingerprint).toBe(after.fingerprint); // and it is the one that is kept
    } finally {
      rmSync(rotDir, { recursive: true, force: true });
    }
  });

  it("creates a certificate once and reuses it", () => {
    const a = loadOrCreateTls(dir);
    const b = loadOrCreateTls(dir);
    expect(a.fingerprint).toMatch(/^[0-9A-F]{64}$/);
    expect(b.fingerprint).toBe(a.fingerprint);
  });

  it("fingerprint is the SHA-256 of the DER certificate, as the phone computes it", () => {
    const t = loadOrCreateTls(dir);
    const der = new X509Certificate(t.cert).raw;
    expect(createHash("sha256").update(der).digest("hex").toUpperCase()).toBe(t.fingerprint);
  });

  it("serves the pinned certificate over HTTPS through the same app", async () => {
    const t = loadOrCreateTls(dir);
    const app = buildApp({ pairingToken: "0123456789abcdef-token", displayToken: "display-token-0123456789" });
    await app.ready();
    const server = https.createServer({ key: t.key, cert: t.cert }, (req, res) => app.routing(req, res));
    await new Promise<void>((r) => server.listen(0, "127.0.0.1", r));
    const port = (server.address() as { port: number }).port;
    const { status, seen } = await new Promise<{ status: number; seen: string }>((resolve, reject) => {
      https.get({ host: "127.0.0.1", port, path: "/health", rejectUnauthorized: false }, (res) => {
        const seen = (res.socket as unknown as { getPeerCertificate: () => { fingerprint256: string } })
          .getPeerCertificate().fingerprint256.replaceAll(":", "");
        res.resume();
        resolve({ status: res.statusCode ?? 0, seen });
      }).on("error", reject);
    });
    server.close();
    expect(status).toBe(200);
    expect(seen).toBe(t.fingerprint);
  });
});
