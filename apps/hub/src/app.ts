import cors from "@fastify/cors";
import { presenceEvent, type PresenceEvent } from "@presence/contracts";
import Fastify, { type FastifyInstance } from "fastify";
import { timingSafeEqual } from "node:crypto";
import { DeviceRegistry } from "./devices";

export interface HubOptions {
  /** Pairing token the phone must present as `Authorization: Bearer <token>`. */
  pairingToken: string;
  /** Token the display must present to read events (header or `?token=` for EventSource). Distinct from the pairing token. */
  displayToken: string;
  /** The only browser origin allowed to read events. */
  displayOrigin?: string;
  /** Max events kept in memory. */
  maxEvents?: number;
  /** Max simultaneous SSE clients. */
  maxStreams?: number;
  /** Paired phones (one-time claim → device token). Without it, only [pairingToken] writes and /pair is off. */
  devices?: DeviceRegistry;
}

function tokenMatches(expected: string, given: string): boolean {
  const a = Buffer.from(given);
  const b = Buffer.from(expected);
  return a.length === b.length && timingSafeEqual(a, b);
}

export function buildApp(opts: HubOptions): FastifyInstance {
  if (opts.pairingToken.length < 16) throw new Error("pairingToken must be at least 16 characters");
  if (opts.displayToken.length < 16) throw new Error("displayToken must be at least 16 characters");
  if (opts.displayToken === opts.pairingToken) throw new Error("displayToken must differ from pairingToken");
  const maxEvents = opts.maxEvents ?? 200;
  const maxStreams = opts.maxStreams ?? 5;
  const origin = opts.displayOrigin ?? "http://localhost:5173";
  const bearer = (header: string | undefined) => (header?.startsWith("Bearer ") ? header.slice(7) : "");
  const canRead = (req: { headers: { authorization?: string }; query: unknown }) => {
    const q = (req.query as { token?: unknown }).token;
    return tokenMatches(opts.displayToken, bearer(req.headers.authorization) || (typeof q === "string" ? q : ""));
  };
  const events: PresenceEvent[] = [];
  const listeners = new Set<(e: PresenceEvent) => void>();
  const app = Fastify({ logger: false });

  void app.register(cors, { origin });

  app.get("/health", async () => ({ ok: true }));

  const canWrite = (bearerToken: string) => tokenMatches(opts.pairingToken, bearerToken) || (opts.devices?.isDeviceToken(bearerToken) ?? false);

  // Pairing: a phone redeems the one-time secret of the QR code for its own token. At most 10 tries a minute in total, so a
  // flood cannot even be used to guess (the secret has 144 bits anyway).
  const pairTries: number[] = [];
  app.post("/pair", async (req, reply) => {
    if (!opts.devices) return reply.code(404).send({ error: "not_found" });
    const now = Date.now();
    while (pairTries.length > 0 && now - (pairTries[0] ?? 0) > 60_000) pairTries.shift();
    if (pairTries.length >= 10) return reply.code(429).send({ error: "too_many_attempts" });
    pairTries.push(now);
    const claim = (req.body as { claim?: unknown } | null)?.claim;
    if (typeof claim !== "string" || claim.length < 16 || claim.length > 200) return reply.code(400).send({ error: "invalid_claim" });
    const deviceToken = opts.devices.redeemClaim(claim);
    if (!deviceToken) return reply.code(403).send({ error: "claim_refused" });
    return reply.code(200).send({ token: deviceToken });
  });

  app.post("/events", async (req, reply) => {
    if (!canWrite(bearer(req.headers.authorization))) {
      return reply.code(401).send({ error: "unauthorized" });
    }
    const parsed = presenceEvent.safeParse(req.body);
    if (!parsed.success) return reply.code(400).send({ error: "invalid_event" });
    events.push(parsed.data);
    if (events.length > maxEvents) events.shift();
    listeners.forEach((l) => l(parsed.data));
    return reply.code(202).send({ accepted: true });
  });

  // Message bodies are sensitive: reading requires the display token, writing requires the pairing token.
  app.get("/events", async (req, reply) => {
    if (!canRead(req)) return reply.code(401).send({ error: "unauthorized" });
    return { events };
  });

  app.get("/events/stream", (req, reply) => {
    if (!canRead(req)) return reply.code(401).send({ error: "unauthorized" });
    if (listeners.size >= maxStreams) return reply.code(503).send({ error: "too_many_streams" });
    reply.raw.writeHead(200, {
      "Content-Type": "text/event-stream",
      "Cache-Control": "no-cache",
      Connection: "keep-alive",
      "Access-Control-Allow-Origin": origin,
    });
    reply.raw.write(": connected\n\n");
    const listener = (e: PresenceEvent) => reply.raw.write(`data: ${JSON.stringify(e)}\n\n`);
    listeners.add(listener);
    req.raw.on("close", () => listeners.delete(listener));
    reply.hijack();
  });

  return app;
}
