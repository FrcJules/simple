import QRCode from "qrcode";

const HUB = import.meta.env.VITE_HUB_URL ?? "http://localhost:8787";
const TOKEN = import.meta.env.VITE_DISPLAY_TOKEN ?? "";

export interface PairingOffer {
  /** `presence://pair?…`, what the QR code holds: a one-time claim, never a long-lived secret. */
  link: string;
  /** The hub certificate fingerprint in groups of four, to compare with the one the phone shows. */
  fingerprint: string;
  expiresInSeconds: number;
}

/** Asks the hub (same device) for a fresh one-time QR code. Null if the hub refuses or cannot be reached. */
export async function startPairing(): Promise<PairingOffer | null> {
  try {
    const res = await fetch(`${HUB}/pairing/start`, { method: "POST", headers: { Authorization: `Bearer ${TOKEN}` } });
    if (!res.ok) return null;
    const data: unknown = await res.json();
    if (typeof data !== "object" || data === null) return null;
    const { link, fingerprint, expiresInSeconds } = data as Record<string, unknown>;
    if (typeof link !== "string" || !link.startsWith("presence://pair?") || typeof fingerprint !== "string" || typeof expiresInSeconds !== "number") return null;
    return { link, fingerprint, expiresInSeconds };
  } catch {
    return null;
  }
}

/** The dark squares of the QR code as one SVG path (one unit per module), plus the side length in modules. */
export function qrPath(link: string): { d: string; size: number } {
  const { modules } = QRCode.create(link, { errorCorrectionLevel: "M" });
  const parts: string[] = [];
  for (let y = 0; y < modules.size; y++) {
    for (let x = 0; x < modules.size; x++) {
      if (modules.get(x, y)) parts.push(`M${x} ${y}h1v1h-1z`);
    }
  }
  return { d: parts.join(""), size: modules.size };
}
