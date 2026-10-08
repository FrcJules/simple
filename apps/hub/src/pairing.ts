import { networkInterfaces } from "node:os";

export interface PairingInfo {
  /** Where the phone reaches the hub over HTTPS, e.g. `https://192.168.1.66:8788`. */
  address: string;
  /** The pairing token (phone → hub). Min 16 characters. */
  token: string;
  /** SHA-256 of the hub's certificate, 64 hex characters (what the phone pins). */
  fingerprint: string;
}

const PRIVATE_V4 = /^(10\.|192\.168\.|172\.(1[6-9]|2\d|3[01])\.)/;

/**
 * The link a camera app opens in Présence: `presence://pair?u=…&t=…&f=…`. The phone checks it again (https, home network,
 * token length, fingerprint), this only refuses to build something obviously wrong.
 */
export function buildPairingLink({ address, token, fingerprint }: PairingInfo): string {
  const url = new URL(address);
  if (url.protocol !== "https:") throw new Error("the pairing address must be https");
  if (!url.hostname.endsWith(".local") && !PRIVATE_V4.test(url.hostname)) throw new Error("the pairing address must be on the home network");
  if (token.length < 16) throw new Error("the pairing token must be at least 16 characters");
  if (!/^[0-9A-Fa-f]{64}$/.test(fingerprint)) throw new Error("the fingerprint must be 64 hexadecimal characters");
  const q = new URLSearchParams({ u: url.origin, t: token, f: fingerprint.toUpperCase() });
  return `presence://pair?${q.toString()}`;
}

/** The first private IPv4 address of this machine (the one the phone can reach), if any. */
export function lanAddress(interfaces: ReturnType<typeof networkInterfaces> = networkInterfaces()): string | undefined {
  return Object.values(interfaces)
    .flat()
    .find((i) => i && i.family === "IPv4" && !i.internal && PRIVATE_V4.test(i.address))?.address;
}
