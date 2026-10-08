import { execFileSync } from "node:child_process";
import { X509Certificate } from "node:crypto";
import { existsSync, mkdirSync, readFileSync, rmSync } from "node:fs";
import { join } from "node:path";

export interface TlsMaterial {
  key: string;
  cert: string;
  /** SHA-256 of the DER certificate, uppercase hex without separators: what the phone pins at pairing. */
  fingerprint: string;
}

/** Self-signed certificate created once with the system `openssl`, then reused across restarts. */
export function loadOrCreateTls(dir: string): TlsMaterial {
  const keyPath = join(dir, "hub-key.pem");
  const certPath = join(dir, "hub-cert.pem");
  if (!existsSync(keyPath) || !existsSync(certPath)) {
    mkdirSync(dir, { recursive: true, mode: 0o700 });
    execFileSync(
      "openssl",
      ["req", "-x509", "-newkey", "ec", "-pkeyopt", "ec_paramgen_curve:prime256v1", "-nodes",
        "-keyout", keyPath, "-out", certPath, "-days", "3650", "-subj", "/CN=presence-hub"],
      { stdio: "ignore" },
    );
  }
  const key = readFileSync(keyPath, "utf8");
  const cert = readFileSync(certPath, "utf8");
  return { key, cert, fingerprint: fingerprintOf(cert) };
}

/**
 * A new key and certificate. Phones pin the old fingerprint, so none of them can reach the hub afterwards: the caller must forget the
 * paired devices (they pair again with a new QR code).
 */
export function rotateTls(dir: string): TlsMaterial {
  rmSync(join(dir, "hub-key.pem"), { force: true });
  rmSync(join(dir, "hub-cert.pem"), { force: true });
  return loadOrCreateTls(dir);
}

export function fingerprintOf(certPem: string): string {
  return new X509Certificate(certPem).fingerprint256.replaceAll(":", "").toUpperCase();
}
