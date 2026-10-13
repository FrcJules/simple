import { createServer } from "node:https";
import { buildApp } from "./app";
import { DeviceRegistry } from "./devices";
import { BirthdaysStore } from "./birthdays";
import { PhotosStore } from "./photos";
import { WeatherService } from "./weather";
import { RemindersStore } from "./reminders";
import { buildPairingLink, groupFingerprint, lanAddress } from "./pairing";
import { loadOrCreateTls } from "./tls";

function required(name: string, hint: string): string {
  const v = process.env[name];
  if (!v) {
    console.error(`${name} is required (${hint}). See .env.example`);
    process.exit(1);
  }
  return v;
}

// The phone reaches the hub over the home network: HTTPS with a self-signed certificate pinned at pairing.
const tls = loadOrCreateTls(process.env.SIMPLE_TLS_DIR ?? "./data/tls");
const tlsPort = Number(process.env.SIMPLE_TLS_PORT ?? 8788);
const devices = new DeviceRegistry(process.env.SIMPLE_DATA_DIR ?? "./data");

const app = buildApp({
  pairingToken: process.env.SIMPLE_PAIRING_TOKEN || undefined, // optional: phones normally pair with the QR code
  displayToken: required("SIMPLE_DISPLAY_TOKEN", "min 16 chars, different from the pairing token if any"),
  displayOrigin: process.env.SIMPLE_DISPLAY_ORIGIN,
  devices,
  reminders: new RemindersStore(process.env.SIMPLE_DATA_DIR ?? "./data"),
  weather: WeatherService.fromEnv(process.env.SIMPLE_WEATHER_LAT, process.env.SIMPLE_WEATHER_LON), // off unless configured (ADR 0006)
  birthdays: new BirthdaysStore(process.env.SIMPLE_DATA_DIR ?? "./data"),
  photos: new PhotosStore(process.env.SIMPLE_PHOTOS_DIR ?? "./data/photos"),
  startPairing: () => {
    const host = process.env.SIMPLE_HOST ?? lanAddress();
    if (!host) throw new Error("no home-network address");
    const link = buildPairingLink({ address: `https://${host}:${tlsPort}`, claim: devices.createClaim(), fingerprint: tls.fingerprint });
    return { link, fingerprint: groupFingerprint(tls.fingerprint), expiresInSeconds: DeviceRegistry.CLAIM_TTL_MS / 1000 };
  },
});

// The display runs on the same device: plain HTTP, loopback only.
const port = Number(process.env.PORT ?? 8787);
await app.listen({ port, host: "127.0.0.1" });

// Only what a phone needs is reachable from the network: pairing and pushing events. Reading events (the display token) stays on loopback.
const phoneRoutes = new Set(["POST /pair", "POST /events"]);
createServer({ key: tls.key, cert: tls.cert }, (req, res) => {
  if (!phoneRoutes.has(`${req.method} ${(req.url ?? "").split("?")[0]}`)) {
    res.writeHead(404).end();
    return;
  }
  app.routing(req, res);
}).listen(tlsPort, "0.0.0.0");

const lan = lanAddress() ?? "<ip-du-pi>";
console.log(`Simple hub: display on http://127.0.0.1:${port}`);
console.log(`Pairing — address: https://${lan}:${tlsPort}`);
console.log(`Pairing — certificate fingerprint (SHA-256): ${tls.fingerprint}`);
console.log("To pair the phone with a QR code: npm run pair -w @simple/hub");
