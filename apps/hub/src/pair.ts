import QRCode from "qrcode";
import { buildPairingLink, lanAddress } from "./pairing";
import { loadOrCreateTls } from "./tls";

// Run on request (`npm run pair -w @presence/hub`), never at startup: the QR code contains the pairing token, which must not end up in logs.
const token = process.env.PRESENCE_PAIRING_TOKEN;
if (!token) {
  console.error("PRESENCE_PAIRING_TOKEN is required. See .env.example");
  process.exit(1);
}
const host = process.env.PRESENCE_HOST ?? lanAddress();
if (!host) {
  console.error("No home-network address found: set PRESENCE_HOST (e.g. 192.168.1.66).");
  process.exit(1);
}
const tls = loadOrCreateTls(process.env.PRESENCE_TLS_DIR ?? "./data/tls");
const link = buildPairingLink({ address: `https://${host}:${process.env.PRESENCE_TLS_PORT ?? 8788}`, token, fingerprint: tls.fingerprint });

console.log(await QRCode.toString(link, { type: "terminal", small: true }));
console.log("Scannez ce QR code avec l'appareil photo du téléphone : il s'ouvre dans Présence (Réglages famille).");
console.log("⚠ Il contient le code d'appairage : ne le partagez pas, ne le photographiez pas, fermez ce terminal ensuite.");
