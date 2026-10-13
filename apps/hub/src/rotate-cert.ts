import { DeviceRegistry } from "./devices";
import { groupFingerprint } from "./pairing";
import { rotateTls } from "./tls";

// ARRÊTEZ D'ABORD LE HUB. `npm run rotate-cert -w @simple/hub`: new certificate (e.g. after the Pi's SD card was lost or copied). The phones pinned the old
// one, so every paired phone is forgotten at the same time and must pair again; restart the hub afterwards.
// Stop the hub first (a running hub keeps serving the old certificate until it restarts). Phones are forgotten *before* the rotation, so a
// failure half-way leaves no phone trusting a certificate that is about to disappear.
new DeviceRegistry(process.env.SIMPLE_DATA_DIR ?? "./data").revokeAll();
const tls = rotateTls(process.env.SIMPLE_TLS_DIR ?? "./data/tls");
console.log("Nouveau certificat créé. Tous les téléphones sont oubliés : ils doivent être appairés de nouveau.");
console.log(`Nouvelle EMPREINTE :\n  ${groupFingerprint(tls.fingerprint)}\n`);
console.log("Redémarrez le hub, puis lancez « npm run pair -w @simple/hub » pour chaque téléphone.");
