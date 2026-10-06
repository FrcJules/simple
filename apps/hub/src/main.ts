import { buildApp } from "./app";

const token = process.env.PRESENCE_PAIRING_TOKEN;
if (!token) {
  console.error("PRESENCE_PAIRING_TOKEN is required (min 16 chars). See .env.example");
  process.exit(1);
}

const displayToken = process.env.PRESENCE_DISPLAY_TOKEN;
if (!displayToken) {
  console.error("PRESENCE_DISPLAY_TOKEN is required (min 16 chars, different from the pairing token). See .env.example");
  process.exit(1);
}

const app = buildApp({ pairingToken: token, displayToken, displayOrigin: process.env.PRESENCE_DISPLAY_ORIGIN });
const port = Number(process.env.PORT ?? 8787);
await app.listen({ port, host: process.env.HOST ?? "0.0.0.0" });
console.log(`Presence hub listening on :${port}`);
