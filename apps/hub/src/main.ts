import { createServer } from "node:https";
import { networkInterfaces } from "node:os";
import { buildApp } from "./app";
import { loadOrCreateTls } from "./tls";

function required(name: string, hint: string): string {
  const v = process.env[name];
  if (!v) {
    console.error(`${name} is required (${hint}). See .env.example`);
    process.exit(1);
  }
  return v;
}

const app = buildApp({
  pairingToken: required("PRESENCE_PAIRING_TOKEN", "min 16 chars"),
  displayToken: required("PRESENCE_DISPLAY_TOKEN", "min 16 chars, different from the pairing token"),
  displayOrigin: process.env.PRESENCE_DISPLAY_ORIGIN,
});

// The display runs on the same device: plain HTTP, loopback only.
const port = Number(process.env.PORT ?? 8787);
await app.listen({ port, host: "127.0.0.1" });

// The phone reaches the hub over the home network: HTTPS with a self-signed certificate pinned at pairing.
const tls = loadOrCreateTls(process.env.PRESENCE_TLS_DIR ?? "./data/tls");
const tlsPort = Number(process.env.PRESENCE_TLS_PORT ?? 8788);
createServer({ key: tls.key, cert: tls.cert }, (req, res) => app.routing(req, res)).listen(tlsPort, "0.0.0.0");

const lan = Object.values(networkInterfaces()).flat().find((i) => i && i.family === "IPv4" && !i.internal)?.address ?? "<ip-du-pi>";
console.log(`Presence hub: display on http://127.0.0.1:${port}`);
console.log(`Pairing — address: https://${lan}:${tlsPort}`);
console.log(`Pairing — certificate fingerprint (SHA-256): ${tls.fingerprint}`);
