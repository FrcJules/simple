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
  /** Open connections, in total and from one address. */
  maxConnections: number;
  maxPerAddress: number;
}

export const PHONE_SERVER_LIMITS: PhoneServerLimits = {
  handshakeMs: 5_000,
  headersMs: 10_000,
  requestMs: 15_000,
  keepAliveMs: 5_000,
  maxConnections: 64,
  maxPerAddress: 8,
};

/** Only what a phone needs is reachable from the network: pairing and pushing events. Reading events (the display token) stays on loopback. */
const PHONE_ROUTES = new Set(["POST /pair", "POST /events"]);

/**
 * HTTPS server for the home network. Besides the route allow-list, it closes connections that are slow on purpose (slowloris)
 * and caps them per address, so a device on the Wi-Fi cannot exhaust the Pi, which also serves the home display.
 */
export function createPhoneServer(app: FastifyInstance, tls: TlsMaterial, limits: PhoneServerLimits = PHONE_SERVER_LIMITS): Server {
  const server = createServer({ key: tls.key, cert: tls.cert, handshakeTimeout: limits.handshakeMs }, (req, res) => {
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

  const perAddress = new Map<string, number>();
  server.on("connection", (socket: Socket) => {
    const address = socket.remoteAddress ?? "?";
    const count = (perAddress.get(address) ?? 0) + 1;
    if (count > limits.maxPerAddress) {
      socket.destroy();
      return;
    }
    perAddress.set(address, count);
    socket.once("close", () => {
      const left = (perAddress.get(address) ?? 1) - 1;
      if (left > 0) perAddress.set(address, left);
      else perAddress.delete(address);
    });
  });
  return server;
}
