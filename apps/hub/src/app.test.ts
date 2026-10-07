import { describe, expect, it } from "vitest";
import { buildApp } from "./app";

const TOKEN = "0123456789abcdef-token";
const DISPLAY = "display-token-0123456789";
const opts = { pairingToken: TOKEN, displayToken: DISPLAY };
const event = {
  schemaVersion: 1,
  id: "e1",
  type: "missed_call",
  occurredAt: "2026-10-06T09:30:00+02:00",
  from: { name: "Marie" },
};

describe("hub", () => {
  it("rejects events without a valid token", async () => {
    const app = buildApp(opts);
    const res = await app.inject({ method: "POST", url: "/events", payload: event });
    expect(res.statusCode).toBe(401);
  });

  it("rejects invalid events", async () => {
    const app = buildApp(opts);
    const res = await app.inject({
      method: "POST", url: "/events", payload: { ...event, type: "nope" },
      headers: { authorization: `Bearer ${TOKEN}` },
    });
    expect(res.statusCode).toBe(400);
  });

  it("stores accepted events and lists them", async () => {
    const app = buildApp(opts);
    const post = await app.inject({
      method: "POST", url: "/events", payload: event,
      headers: { authorization: `Bearer ${TOKEN}` },
    });
    expect(post.statusCode).toBe(202);
    const list = await app.inject({ method: "GET", url: "/events", headers: { authorization: `Bearer ${DISPLAY}` } });
    expect(list.json().events).toHaveLength(1);
  });

  it("caps stored events", async () => {
    const app = buildApp({ ...opts, maxEvents: 2 });
    for (const id of ["a", "b", "c"]) {
      await app.inject({
        method: "POST", url: "/events", payload: { ...event, id },
        headers: { authorization: `Bearer ${TOKEN}` },
      });
    }
    const list = await app.inject({ method: "GET", url: "/events", headers: { authorization: `Bearer ${DISPLAY}` } });
    expect(list.json().events.map((e: { id: string }) => e.id)).toEqual(["b", "c"]);
  });

  it("refuses a weak pairing token", () => {
    expect(() => buildApp({ ...opts, pairingToken: "short" })).toThrow();
  });

  it("requires the display token to read events, and the pairing token cannot read", async () => {
    const app = buildApp(opts);
    expect((await app.inject({ method: "GET", url: "/events" })).statusCode).toBe(401);
    expect((await app.inject({ method: "GET", url: "/events", headers: { authorization: `Bearer ${TOKEN}` } })).statusCode).toBe(401);
    expect((await app.inject({ method: "GET", url: `/events?token=${DISPLAY}` })).statusCode).toBe(200);
    expect((await app.inject({ method: "GET", url: "/events/stream" })).statusCode).toBe(401);
  });

  it("refuses identical or weak display tokens", () => {
    expect(() => buildApp({ ...opts, displayToken: TOKEN })).toThrow();
    expect(() => buildApp({ ...opts, displayToken: "short" })).toThrow();
  });
});
