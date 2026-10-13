import { execFileSync } from "node:child_process";
import { X509Certificate } from "node:crypto";
import { chmodSync, existsSync, mkdirSync, readFileSync, renameSync, rmSync } from "node:fs";
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
  if (!existsSync(keyPath) || !existsSync(certPath)) generate(dir, keyPath, certPath);
  chmodSync(keyPath, 0o600); // whatever the umask or an existing folder allowed
  const key = readFileSync(keyPath, "utf8");
  const cert = readFileSync(certPath, "utf8");
  return { key, cert, fingerprint: fingerprintOf(cert) };
}

/** Writes the pair under temporary names, then renames: a failing `openssl` never leaves a missing or half-written key. */
function generate(dir: string, keyPath: string, certPath: string): void {
  mkdirSync(dir, { recursive: true, mode: 0o700 });
  const tmpKey = `${keyPath}.${process.pid}.tmp`;
  const tmpCert = `${certPath}.${process.pid}.tmp`;
  try {
    execFileSync(
      "openssl",
      ["req", "-x509", "-newkey", "ec", "-pkeyopt", "ec_paramgen_curve:prime256v1", "-nodes",
        "-keyout", tmpKey, "-out", tmpCert, "-days", "3650", "-subj", "/CN=simple-hub"],
      { stdio: "ignore" },
    );
    chmodSync(tmpKey, 0o600);
    renameSync(tmpKey, keyPath);
    renameSync(tmpCert, certPath);
  } finally {
    rmSync(tmpKey, { force: true });
    rmSync(tmpCert, { force: true });
  }
}

/**
 * A new key and certificate, replacing the old ones only once the new ones exist. Phones pin the old fingerprint, so none of them can
 * reach the hub afterwards: the caller must forget the paired devices (they pair again with a new QR code).
 */
export function rotateTls(dir: string): TlsMaterial {
  generate(dir, join(dir, "hub-key.pem"), join(dir, "hub-cert.pem"));
  return loadOrCreateTls(dir);
}

export function fingerprintOf(certPem: string): string {
  return new X509Certificate(certPem).fingerprint256.replaceAll(":", "").toUpperCase();
}
