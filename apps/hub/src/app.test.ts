import { describe, expect, it } from "vitest";
import { buildApp } from "./app";

const TOKEN = "0123456789abcdef-token";
const event = {
  schemaVersion: 1,
  id: "e1",
  type: "missed_call",
  occurredAt: "2026-10-06T09:30:00+02:00",
  from: { name: "Marie" },
};

describe("hub", () => {
  it("rejects events without a valid token", async () => {
    const app = buildApp({ pairingToken: TOKEN });
    const res = await app.inject({ method: "POST", url: "/events", payload: event });
    expect(res.statusCode).toBe(401);
  });

  it("rejects invalid events", async () => {
    const app = buildApp({ pairingToken: TOKEN });
    const res = await app.inject({
      method: "POST", url: "/events", payload: { ...event, type: "nope" },
      headers: { authorization: `Bearer ${TOKEN}` },
    });
    expect(res.statusCode).toBe(400);
  });

  it("stores accepted events and lists them", async () => {
    const app = buildApp({ pairingToken: TOKEN });
    const post = await app.inject({
      method: "POST", url: "/events", payload: event,
      headers: { authorization: `Bearer ${TOKEN}` },
    });
    expect(post.statusCode).toBe(202);
    const list = await app.inject({ method: "GET", url: "/events" });
    expect(list.json().events).toHaveLength(1);
  });

  it("caps stored events", async () => {
    const app = buildApp({ pairingToken: TOKEN, maxEvents: 2 });
    for (const id of ["a", "b", "c"]) {
      await app.inject({
        method: "POST", url: "/events", payload: { ...event, id },
        headers: { authorization: `Bearer ${TOKEN}` },
      });
    }
    const list = await app.inject({ method: "GET", url: "/events" });
    expect(list.json().events.map((e: { id: string }) => e.id)).toEqual(["b", "c"]);
  });

  it("refuses a weak pairing token", () => {
    expect(() => buildApp({ pairingToken: "short" })).toThrow();
  });
});
