import { createHash, randomBytes, timingSafeEqual } from "node:crypto";
import { existsSync, mkdirSync, readFileSync, renameSync, writeFileSync } from "node:fs";
import { join } from "node:path";

interface Claim { hash: string; expiresAt: number }
interface Device { hash: string; createdAt: number }

const sha256 = (s: string) => createHash("sha256").update(s).digest("hex");
const token = (bytes: number) => randomBytes(bytes).toString("base64url");

/**
 * Pairing without ever putting a long-lived secret in a QR code.
 *
 * `npm run pair` creates a one-time **claim** (valid {@link CLAIM_TTL_MS}, single use) and puts only that in the QR code. The phone
 * redeems it over the pinned HTTPS connection and receives its own **device token**, which is the long-lived credential. Only SHA-256
 * hashes are stored (a copy of the data folder does not give tokens), and a device can be revoked by removing its entry.
 *
 * State lives in two small files because the pairing command and the running hub are separate processes.
 */
export class DeviceRegistry {
  static readonly CLAIM_TTL_MS = 120_000;
  private readonly claimsFile: string;
  private readonly devicesFile: string;

  constructor(dir: string, private readonly clock: () => number = Date.now) {
    mkdirSync(dir, { recursive: true, mode: 0o700 });
    this.claimsFile = join(dir, "claims.json");
    this.devicesFile = join(dir, "devices.json");
  }

  /** A fresh single-use secret for the QR code. Older unused claims are dropped so only the latest QR works. */
  createClaim(): string {
    const secret = token(18);
    this.write(this.claimsFile, [{ hash: sha256(secret), expiresAt: this.clock() + DeviceRegistry.CLAIM_TTL_MS }]);
    return secret;
  }

  /** Exchanges a claim for a device token, once. Null if the claim is unknown, expired or already used. */
  redeemClaim(secret: string): string | null {
    const claims = this.read<Claim>(this.claimsFile);
    const hash = sha256(secret);
    const match = claims.find((c) => equal(c.hash, hash));
    // The claim is burnt whatever the outcome: one try per QR code.
    if (match) this.write(this.claimsFile, claims.filter((c) => c !== match));
    if (!match || this.clock() > match.expiresAt) return null;
    const deviceToken = token(24);
    this.write(this.devicesFile, [...this.read<Device>(this.devicesFile), { hash: sha256(deviceToken), createdAt: this.clock() }]);
    return deviceToken;
  }

  isDeviceToken(candidate: string): boolean {
    if (!candidate) return false;
    const hash = sha256(candidate);
    return this.read<Device>(this.devicesFile).some((d) => equal(d.hash, hash));
  }

  deviceCount(): number {
    return this.read<Device>(this.devicesFile).length;
  }

  /** Forgets every paired phone (they must pair again). */
  revokeAll(): void {
    this.write(this.devicesFile, []);
    this.write(this.claimsFile, []);
  }

  private read<T>(file: string): T[] {
    if (!existsSync(file)) return [];
    try {
      const data: unknown = JSON.parse(readFileSync(file, "utf8"));
      return Array.isArray(data) ? (data as T[]) : [];
    } catch {
      return []; // a damaged file means "nothing paired": safe, the phone pairs again
    }
  }

  private write(file: string, data: unknown): void {
    const tmp = `${file}.${process.pid}.tmp`;
    writeFileSync(tmp, JSON.stringify(data), { mode: 0o600 });
    renameSync(tmp, file); // atomic: the other process never reads half a file
  }
}

function equal(a: string, b: string): boolean {
  const x = Buffer.from(a);
  const y = Buffer.from(b);
  return x.length === y.length && timingSafeEqual(x, y);
}
