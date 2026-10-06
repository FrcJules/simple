import cors from "@fastify/cors";
import { presenceEvent, type PresenceEvent } from "@presence/contracts";
import Fastify, { type FastifyInstance } from "fastify";
import { timingSafeEqual } from "node:crypto";

export interface HubOptions {
  /** Pairing token the phone must present as `Authorization: Bearer <token>`. */
  pairingToken: string;
  /** Max events kept in memory. */
  maxEvents?: number;
}

function tokenMatches(expected: string, header: string | undefined): boolean {
  const given = header?.startsWith("Bearer ") ? header.slice(7) : "";
  const a = Buffer.from(given);
  const b = Buffer.from(expected);
  return a.length === b.length && timingSafeEqual(a, b);
}

export function buildApp(opts: HubOptions): FastifyInstance {
  if (opts.pairingToken.length < 16) throw new Error("pairingToken must be at least 16 characters");
  const maxEvents = opts.maxEvents ?? 200;
  const events: PresenceEvent[] = [];
  const listeners = new Set<(e: PresenceEvent) => void>();
  const app = Fastify({ logger: false });

  void app.register(cors, { origin: true });

  app.get("/health", async () => ({ ok: true }));

  app.post("/events", async (req, reply) => {
    if (!tokenMatches(opts.pairingToken, req.headers.authorization)) {
      return reply.code(401).send({ error: "unauthorized" });
    }
    const parsed = presenceEvent.safeParse(req.body);
    if (!parsed.success) return reply.code(400).send({ error: "invalid_event" });
    events.push(parsed.data);
    if (events.length > maxEvents) events.shift();
    listeners.forEach((l) => l(parsed.data));
    return reply.code(202).send({ accepted: true });
  });

  // The display runs on the same LAN/device; reading is unauthenticated, writing is not.
  app.get("/events", async () => ({ events }));

  app.get("/events/stream", (req, reply) => {
    reply.raw.writeHead(200, {
      "Content-Type": "text/event-stream",
      "Cache-Control": "no-cache",
      Connection: "keep-alive",
      "Access-Control-Allow-Origin": "*",
    });
    reply.raw.write(": connected\n\n");
    const listener = (e: PresenceEvent) => reply.raw.write(`data: ${JSON.stringify(e)}\n\n`);
    listeners.add(listener);
    req.raw.on("close", () => listeners.delete(listener));
    reply.hijack();
  });

  return app;
}
