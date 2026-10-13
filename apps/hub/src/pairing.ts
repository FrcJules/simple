import { isIPv4 } from "node:net";
import { networkInterfaces } from "node:os";

export interface PairingInfo {
  /** Where the phone reaches the hub over HTTPS, e.g. `https://192.168.1.66:8788`. */
  address: string;
  /** The one-time claim (see DeviceRegistry), never the long-lived token. Min 16 characters. */
  claim: string;
  /** SHA-256 of the hub's certificate, 64 hex characters (what the phone pins and the family compares). */
  fingerprint: string;
}

/** RFC 1918 ranges, on a complete IPv4 address (a name such as `10.evil.example` is not one). */
export function isPrivateIPv4(host: string): boolean {
  if (!isIPv4(host)) return false;
  const [a = 0, b = 0] = host.split(".").map(Number);
  return a === 10 || (a === 192 && b === 168) || (a === 172 && b >= 16 && b <= 31);
}

/**
 * The link a camera app opens in Simple: `simple://pair?u=…&s=…&f=…`. The phone validates it again and shows what it contains for
 * the family to verify; this only refuses to build something obviously wrong.
 */
export function buildPairingLink({ address, claim, fingerprint }: PairingInfo): string {
  const url = new URL(address);
  if (url.protocol !== "https:") throw new Error("the pairing address must be https");
  if (url.username || url.password || (url.pathname !== "/" && url.pathname !== "") || url.search || url.hash) throw new Error("the pairing address must be a bare host and port");
  if (!url.hostname.endsWith(".local") && !isPrivateIPv4(url.hostname)) throw new Error("the pairing address must be on the home network");
  if (claim.length < 16) throw new Error("the claim must be at least 16 characters");
  if (!/^[0-9A-Fa-f]{64}$/.test(fingerprint)) throw new Error("the fingerprint must be 64 hexadecimal characters");
  const q = new URLSearchParams({ u: url.origin, s: claim, f: fingerprint.toUpperCase() });
  return `simple://pair?${q.toString()}`;
}

/** `88A2 899B 9DF0 0490 …`: the fingerprint in groups of four, easy to compare by eye. */
export function groupFingerprint(fingerprint: string): string {
  return (fingerprint.toUpperCase().match(/.{1,4}/g) ?? []).join(" ");
}

const VIRTUAL = /^(docker|br-|veth|virbr|tailscale|zt|tun|tap)/i;

/** The first private IPv4 address of a real network interface (the one the phone can reach), if any. */
export function lanAddress(interfaces: ReturnType<typeof networkInterfaces> = networkInterfaces()): string | undefined {
  for (const [name, list] of Object.entries(interfaces)) {
    if (VIRTUAL.test(name)) continue; // containers and VPNs: not reachable from the family's phone
    const hit = list?.find((i) => i.family === "IPv4" && !i.internal && isPrivateIPv4(i.address));
    if (hit) return hit.address;
  }
  return undefined;
}
