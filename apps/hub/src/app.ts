import cors from "@fastify/cors";
import { presenceEvent, type PresenceEvent } from "@presence/contracts";
import Fastify, { type FastifyInstance, type FastifyReply } from "fastify";
import { timingSafeEqual } from "node:crypto";
import { z } from "zod";
import { DeviceRegistry } from "./devices";
import { FailureLimiter } from "./limiter";

const pairBody = z.object({ claim: z.string().min(16).max(200) }).strict();
const MAX_PAIR_FAILURES_PER_MINUTE = 5;
const MAX_AUTH_FAILURES_PER_MINUTE = 10;

export interface HubOptions {
  /** Optional shared token a phone can present as `Authorization: Bearer <token>`; paired devices (see `devices`) do not need it. */
  pairingToken?: string;
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
  if (opts.pairingToken === undefined && !opts.devices) throw new Error("nothing could write events: set pairingToken or devices");
  if (opts.pairingToken !== undefined && opts.pairingToken.length < 16) throw new Error("pairingToken must be at least 16 characters");
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

  const canWrite = (bearerToken: string) => (opts.pairingToken !== undefined && tokenMatches(opts.pairingToken, bearerToken)) || (opts.devices?.isDeviceToken(bearerToken) ?? false);

  // Pairing: a phone redeems the one-time secret of the QR code for its own token. The hub only answers while a QR code is active
  // (otherwise /pair does not exist: nothing to flood, nothing to learn), and counts *failures* per address, so an attacker on the
  // network locks only itself out, never the family's phone.
  const pairFailures = new FailureLimiter(MAX_PAIR_FAILURES_PER_MINUTE);
  app.post("/pair", { bodyLimit: 1024 }, async (req, reply) => {
    const devices = opts.devices;
    if (!devices) return reply.code(404).send({ error: "not_found" });
    // Blocked addresses are refused before any disk access.
    if (pairFailures.blocked(req.ip)) return reply.code(429).send({ error: "too_many_attempts" });
    if (!devices.hasActiveClaim()) return reply.code(404).send({ error: "not_found" });
    const fail = (code: number, error: string) => {
      pairFailures.record(req.ip);
      return reply.code(code).send({ error });
    };
    const body = pairBody.safeParse(req.body);
    if (!body.success) return fail(400, "invalid_claim");
    const deviceToken = devices.redeemClaim(body.data.claim);
    if (!deviceToken) return fail(403, "claim_refused");
    return reply.code(200).send({ token: deviceToken });
  });

  // Wrong tokens on the other routes are counted too: the display token or a static pairing token chosen by a human cannot be
  // guessed at network speed. A blocked address gets 429 even with the right token until its minute is over.
  const authFailures = new FailureLimiter(MAX_AUTH_FAILURES_PER_MINUTE);
  const unauthorized = (ip: string, reply: FastifyReply) => {
    authFailures.record(ip);
    return reply.code(401).send({ error: "unauthorized" });
  };
  const tooMany = (reply: FastifyReply) => reply.code(429).send({ error: "too_many_attempts" });

  app.post("/events", async (req, reply) => {
    if (authFailures.blocked(req.ip)) return tooMany(reply);
    if (!canWrite(bearer(req.headers.authorization))) return unauthorized(req.ip, reply);
    const parsed = presenceEvent.safeParse(req.body);
    if (!parsed.success) return reply.code(400).send({ error: "invalid_event" });
    events.push(parsed.data);
    if (events.length > maxEvents) events.shift();
    listeners.forEach((l) => l(parsed.data));
    return reply.code(202).send({ accepted: true });
  });

  // Message bodies are sensitive: reading requires the display token, writing requires the pairing token.
  app.get("/events", async (req, reply) => {
    if (authFailures.blocked(req.ip)) return tooMany(reply);
    if (!canRead(req)) return unauthorized(req.ip, reply);
    return { events };
  });

  app.get("/events/stream", (req, reply) => {
    if (authFailures.blocked(req.ip)) return tooMany(reply);
    if (!canRead(req)) return unauthorized(req.ip, reply);
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
