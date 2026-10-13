import QRCode from "qrcode";
import { DeviceRegistry } from "./devices";
import { buildPairingLink, groupFingerprint, lanAddress } from "./pairing";
import { loadOrCreateTls } from "./tls";

// Run on request (`npm run pair -w @simple/hub`), never at startup. The QR code holds a one-time claim valid 2 minutes, not a
// long-lived token: even a QR code kept in a scanner's history or a terminal's scrollback gives nothing once it has been used.
const host = process.env.PRESENCE_HOST ?? lanAddress();
if (!host) {
  console.error("No home-network address found: set PRESENCE_HOST (e.g. 192.168.1.66).");
  process.exit(1);
}
const tls = loadOrCreateTls(process.env.PRESENCE_TLS_DIR ?? "./data/tls");
const devices = new DeviceRegistry(process.env.PRESENCE_DATA_DIR ?? "./data");
const name = process.argv[2]; // optional: `npm run pair -w @simple/hub -- "Téléphone de Marie"`, shown by `npm run devices`
const claim = devices.createClaim(name);
const link = buildPairingLink({ address: `https://${host}:${process.env.PRESENCE_TLS_PORT ?? 8788}`, claim, fingerprint: tls.fingerprint });

console.log(await QRCode.toString(link, { type: "terminal", small: true }));
console.log("Scannez ce QR code avec l'appareil photo du téléphone : il s'ouvre dans Simple (Réglages famille).");
console.log(`\nEMPREINTE de ce hub (à COMPARER avec celle affichée sur le téléphone avant d'appairer) :\n  ${groupFingerprint(tls.fingerprint)}\n`);
console.log("Ce QR code ne marche qu'une fois et pendant 2 minutes. Il faut relancer cette commande pour en obtenir un autre.");
