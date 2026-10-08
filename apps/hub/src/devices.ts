import { createHash, randomBytes } from "node:crypto";
import { mkdirSync, readdirSync, readFileSync, renameSync, statSync, unlinkSync, utimesSync, writeFileSync } from "node:fs";
import { join } from "node:path";
import { z } from "zod";

const label = z.string().max(40);
const claimFile = z.object({ expiresAt: z.number(), label: label.optional() }).strict();
const deviceFile = z.object({ createdAt: z.number(), label: label.optional() }).strict();
/** A used token touches its file at most this often: "last seen" is accurate to ten minutes, and requests do not write the disk. */
const TOUCH_EVERY_MS = 600_000;
const HASH = /^[0-9a-f]{64}$/;

export interface DeviceInfo {
  /** The first 8 characters of the token's hash: enough to tell phones apart, useless to impersonate one. */
  id: string;
  /** The name given when the QR code was made (e.g. "Téléphone de Marie"), if any. */
  label?: string;
  createdAt: number;
  /** When the phone last pushed an event (to ten minutes), or when it paired. */
  lastUsedAt: number;
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
  private readonly touched = new Map<string, number>();

  constructor(dir: string, private readonly clock: () => number = Date.now) {
    this.claimsDir = join(dir, "claims");
    this.devicesDir = join(dir, "devices");
    mkdirSync(this.claimsDir, { recursive: true, mode: 0o700 });
    mkdirSync(this.devicesDir, { recursive: true, mode: 0o700 });
  }

  /** A fresh single-use secret for the QR code. Older unused claims are dropped so only the latest QR works. */
  createClaim(deviceLabel?: string): string {
    const name = deviceLabel?.trim().slice(0, 40) || undefined;
    for (const name of this.names(this.claimsDir)) this.remove(join(this.claimsDir, name));
    const secret = token(18);
    this.write(join(this.claimsDir, sha256(secret)), { expiresAt: this.clock() + DeviceRegistry.CLAIM_TTL_MS, ...(name ? { label: name } : {}) });
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
    const path = join(this.devicesDir, sha256(deviceToken));
    this.write(path, { createdAt: this.clock(), ...(parsed.data.label ? { label: parsed.data.label } : {}) });
    this.setTimes(path, this.clock());
    return deviceToken;
  }

  isDeviceToken(candidate: string): boolean {
    if (!candidate || candidate.length > 200) return false;
    // The file name is the hash of the candidate (hex only): nothing the caller sends can reach another path.
    const hash = sha256(candidate);
    const file = join(this.devicesDir, hash);
    if (!deviceFile.safeParse(this.readJson(file)).success) return false;
    const now = this.clock();
    if (now - (this.touched.get(hash) ?? 0) >= TOUCH_EVERY_MS) {
      this.touched.set(hash, now);
      this.setTimes(file, now);
    }
    return true;
  }

  devices(): DeviceInfo[] {
    const out: DeviceInfo[] = [];
    for (const name of this.names(this.devicesDir)) {
      const parsed = deviceFile.safeParse(this.readJson(join(this.devicesDir, name)));
      if (!parsed.success) continue;
      const lastUsedAt = this.lastUsed(join(this.devicesDir, name)) ?? parsed.data.createdAt;
      out.push({ id: name.slice(0, 8), createdAt: parsed.data.createdAt, lastUsedAt, ...(parsed.data.label ? { label: parsed.data.label } : {}) });
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

  private setTimes(file: string, ms: number): void {
    try {
      utimesSync(file, ms / 1000, ms / 1000);
    } catch {
      /* the device was revoked in between: nothing to record */
    }
  }

  private lastUsed(file: string): number | undefined {
    try {
      return statSync(file).mtimeMs;
    } catch {
      return undefined;
    }
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
