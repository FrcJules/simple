import QRCode from "qrcode";

const HUB = import.meta.env.VITE_HUB_URL ?? "http://localhost:8787";
const TOKEN = import.meta.env.VITE_DISPLAY_TOKEN ?? "";

export interface PairingOffer {
  /** `simple://pair?…`, what the QR code holds: a one-time claim, never a long-lived secret. */
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
    if (typeof link !== "string" || !link.startsWith("simple://pair?") || typeof fingerprint !== "string") return null;
    if (typeof expiresInSeconds !== "number" || !Number.isFinite(expiresInSeconds) || expiresInSeconds > 600) return null;
    return { link, fingerprint, expiresInSeconds };
  } catch {
    return null;
  }
}

/** The screen was closed: the hub withdraws the QR code at once. Best effort. */
export async function cancelPairing(): Promise<void> {
  try {
    await fetch(`${HUB}/pairing/cancel`, { method: "POST", headers: { Authorization: `Bearer ${TOKEN}` } });
  } catch {
    /* the code expires by itself in two minutes */
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
