import type { FastifyInstance } from "fastify";
import { createServer, type Server } from "node:https";
import type { Socket } from "node:net";
import type { TlsMaterial } from "./tls";

/** Limits of the port the phone reaches over the home network. Short on purpose: a phone sends one small request and leaves. */
export interface PhoneServerLimits {
  /** Time to finish the TLS handshake. */
  handshakeMs: number;
  /** Time to receive the request headers, then the whole request. */
  headersMs: number;
  requestMs: number;
  /** Idle time kept between two requests on the same connection. */
  keepAliveMs: number;
  /** A connection with no traffic at all (e.g. TLS done, then silence: headersTimeout does not cover it) is closed after this. */
  idleMs: number;
  /** How often Node checks the headers/request timeouts (its default, 30 s, would let a slow client hold on 30 s longer). */
  checkIntervalMs: number;
  /** A connection is closed after this long whatever it does (a phone sends one request per connection). */
  lifetimeMs: number;
  /** Open connections, in total and from one address. */
  maxConnections: number;
  maxPerAddress: number;
  /** New connections one address may open per [newPerAddressWindowMs] (each one costs the Pi a TLS key exchange). */
  newPerAddress: number;
  newPerAddressWindowMs: number;
}

export const PHONE_SERVER_LIMITS: PhoneServerLimits = {
  handshakeMs: 5_000,
  headersMs: 10_000,
  requestMs: 15_000,
  keepAliveMs: 5_000,
  idleMs: 10_000,
  checkIntervalMs: 1_000,
  lifetimeMs: 20_000,
  maxConnections: 64,
  maxPerAddress: 8,
  newPerAddress: 20,
  newPerAddressWindowMs: 10_000,
};

/** Only what a phone needs is reachable from the network: pairing and pushing events. Reading events (the display token) stays on loopback. */
const PHONE_ROUTES = new Set(["POST /pair", "POST /events"]);

/**
 * HTTPS server for the home network. Besides the route allow-list, it closes connections that are slow on purpose (slowloris),
 * serves one request per connection and caps connections per address, so a device on the Wi-Fi cannot exhaust the Pi, which also
 * serves the home display. Residual risk (docs/architecture.md): a device that takes many addresses on the LAN can still fill the
 * global cap for as long as it keeps trying, like it could jam the Wi-Fi; it learns nothing.
 * Listens on IPv4 only (main.ts): with dual stack, normalise `::ffff:` addresses and group IPv6 by /64 before counting.
 */
export function createPhoneServer(app: FastifyInstance, tls: TlsMaterial, limits: PhoneServerLimits = PHONE_SERVER_LIMITS): Server {
  const options = { key: tls.key, cert: tls.cert, handshakeTimeout: limits.handshakeMs, connectionsCheckingInterval: limits.checkIntervalMs };
  const server = createServer(options, (req, res) => {
    if (!PHONE_ROUTES.has(`${req.method} ${(req.url ?? "").split("?")[0]}`)) {
      res.writeHead(404).end();
      return;
    }
    app.routing(req, res);
  });
  server.headersTimeout = limits.headersMs;
  server.requestTimeout = limits.requestMs;
  server.keepAliveTimeout = limits.keepAliveMs;
  server.maxConnections = limits.maxConnections;
  server.maxRequestsPerSocket = 1;
  server.setTimeout(limits.idleMs, (socket) => socket.destroy());

  const perAddress = new Map<string, number>();
  const recentOpens = new Map<string, number[]>();
  server.prependListener("connection", (socket: Socket) => {
    const address = socket.remoteAddress ?? "?";
    const now = Date.now();
    const opens = (recentOpens.get(address) ?? []).filter((t) => now - t < limits.newPerAddressWindowMs);
    opens.push(now);
    recentOpens.set(address, opens);
    if (recentOpens.size > 1_000) recentOpens.clear(); // bounded memory; a phone opens a handful per window anyway
    const count = (perAddress.get(address) ?? 0) + 1;
    if (count > limits.maxPerAddress || opens.length > limits.newPerAddress) {
      socket.destroy();
      return;
    }
    perAddress.set(address, count);
    const lifetime = setTimeout(() => socket.destroy(), limits.lifetimeMs);
    lifetime.unref();
    socket.once("close", () => {
      clearTimeout(lifetime);
      const left = (perAddress.get(address) ?? 1) - 1;
      if (left > 0) perAddress.set(address, left);
      else perAddress.delete(address);
    });
  });
  return server;
}
