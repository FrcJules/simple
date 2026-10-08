import { createHash, randomBytes } from "node:crypto";
import { mkdirSync, readdirSync, readFileSync, renameSync, unlinkSync, writeFileSync } from "node:fs";
import { join } from "node:path";
import { z } from "zod";

const claimFile = z.object({ expiresAt: z.number() }).strict();
const deviceFile = z.object({ createdAt: z.number() }).strict();
const HASH = /^[0-9a-f]{64}$/;

export interface DeviceInfo {
  /** The first 8 characters of the token's hash: enough to tell phones apart, useless to impersonate one. */
  id: string;
  createdAt: number;
}

const sha256 = (s: string) => createHash("sha256").update(s).digest("hex");
const token = (bytes: number) => randomBytes(bytes).toString("base64url");

/**
 * Pairing without ever putting a long-lived secret in a QR code.
 *
 * `npm run pair` creates a one-time **claim** (valid {@link CLAIM_TTL_MS}, single use) and puts only that in the QR code. The phone
 * redeems it over the pinned HTTPS connection and receives its own **device token**, which is the long-lived credential. Only SHA-256
 * hashes are stored (a copy of the data folder does not give tokens).
 *
 * The pairing command and the running hub are separate processes, so there is **one file per claim and per device** (`claims/<hash>`,
 * `devices/<hash>`): nothing is ever read-modified-written, and deleting a claim file is the lock that makes it single use even when
 * two requests race. A device is revoked by deleting its file ({@link revoke}).
 */
export class DeviceRegistry {
  static readonly CLAIM_TTL_MS = 120_000;
  private readonly claimsDir: string;
  private readonly devicesDir: string;

  constructor(dir: string, private readonly clock: () => number = Date.now) {
    this.claimsDir = join(dir, "claims");
    this.devicesDir = join(dir, "devices");
    mkdirSync(this.claimsDir, { recursive: true, mode: 0o700 });
    mkdirSync(this.devicesDir, { recursive: true, mode: 0o700 });
  }

  /** A fresh single-use secret for the QR code. Older unused claims are dropped so only the latest QR works. */
  createClaim(): string {
    for (const name of this.names(this.claimsDir)) this.remove(join(this.claimsDir, name));
    const secret = token(18);
    this.write(join(this.claimsDir, sha256(secret)), { expiresAt: this.clock() + DeviceRegistry.CLAIM_TTL_MS });
    return secret;
  }

  /** True while a QR code can still be redeemed: the hub does not even answer `/pair` otherwise. */
  hasActiveClaim(): boolean {
    return this.names(this.claimsDir).some((name) => {
      const file = join(this.claimsDir, name);
      const parsed = claimFile.safeParse(this.readJson(file));
      if (parsed.success && this.clock() <= parsed.data.expiresAt) return true;
      this.remove(file); // expired or damaged: clean up
      return false;
    });
  }

  /** Exchanges a claim for a device token, once. Null if the claim is unknown, expired or already used. */
  redeemClaim(secret: string): string | null {
    const file = join(this.claimsDir, sha256(secret));
    const parsed = claimFile.safeParse(this.readJson(file));
    if (!parsed.success) return null;
    // Deleting the file is the lock: of two racing requests (or processes) only one unlink succeeds. Burnt whatever the outcome below.
    if (!this.remove(file)) return null;
    if (this.clock() > parsed.data.expiresAt) return null;
    const deviceToken = token(24);
    this.write(join(this.devicesDir, sha256(deviceToken)), { createdAt: this.clock() });
    return deviceToken;
  }

  isDeviceToken(candidate: string): boolean {
    if (!candidate || candidate.length > 200) return false;
    // The file name is the hash of the candidate (hex only): nothing the caller sends can reach another path.
    return deviceFile.safeParse(this.readJson(join(this.devicesDir, sha256(candidate)))).success;
  }

  devices(): DeviceInfo[] {
    const out: DeviceInfo[] = [];
    for (const name of this.names(this.devicesDir)) {
      const parsed = deviceFile.safeParse(this.readJson(join(this.devicesDir, name)));
      if (parsed.success) out.push({ id: name.slice(0, 8), createdAt: parsed.data.createdAt });
    }
    return out.sort((a, b) => a.createdAt - b.createdAt);
  }

  deviceCount(): number {
    return this.devices().length;
  }

  /** Forgets one phone, by the id from {@link devices}. Returns false if no phone has this id (or the id is ambiguous). */
  revoke(id: string): boolean {
    if (!/^[0-9a-f]{8,64}$/.test(id)) return false;
    const matches = this.names(this.devicesDir).filter((n) => n.startsWith(id));
    const [only] = matches;
    return matches.length === 1 && only !== undefined && this.remove(join(this.devicesDir, only));
  }

  /** Forgets every paired phone (they must pair again) and every pending QR code. */
  revokeAll(): void {
    for (const name of this.names(this.devicesDir)) this.remove(join(this.devicesDir, name));
    for (const name of this.names(this.claimsDir)) this.remove(join(this.claimsDir, name));
  }

  private names(dir: string): string[] {
    try {
      return readdirSync(dir).filter((n) => HASH.test(n));
    } catch {
      return [];
    }
  }

  private readJson(file: string): unknown {
    try {
      return JSON.parse(readFileSync(file, "utf8"));
    } catch {
      return undefined; // absent or damaged means "nothing there": safe, the phone pairs again
    }
  }

  private remove(file: string): boolean {
    try {
      unlinkSync(file);
      return true;
    } catch {
      return false;
    }
  }

  private write(file: string, data: unknown): void {
    const tmp = `${file}.${process.pid}.tmp`;
    writeFileSync(tmp, JSON.stringify(data), { mode: 0o600 });
    renameSync(tmp, file); // atomic: the other process never reads half a file
  }
}
