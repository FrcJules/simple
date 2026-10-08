import { DeviceRegistry } from "./devices";
import { groupFingerprint } from "./pairing";
import { rotateTls } from "./tls";

// `npm run rotate-cert -w @presence/hub`: new certificate (e.g. after the Pi's SD card was lost or copied). The phones pinned the old
// one, so every paired phone is forgotten at the same time and must pair again; restart the hub afterwards.
const tls = rotateTls(process.env.PRESENCE_TLS_DIR ?? "./data/tls");
new DeviceRegistry(process.env.PRESENCE_DATA_DIR ?? "./data").revokeAll();
console.log("Nouveau certificat créé. Tous les téléphones sont oubliés : ils doivent être appairés de nouveau.");
console.log(`Nouvelle EMPREINTE :\n  ${groupFingerprint(tls.fingerprint)}\n`);
console.log("Redémarrez le hub, puis lancez « npm run pair -w @presence/hub » pour chaque téléphone.");
