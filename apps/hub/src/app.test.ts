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

describe("event stream", () => {
  /** Opens the stream of the hub listening at [address] and returns the text received until [until] matches (or 2 s pass). */
  async function readStream(address: string, headers: Record<string, string>, until: RegExp): Promise<string> {
    const controller = new AbortController();
    const timer = setTimeout(() => controller.abort(), 2_000);
    let text = "";
    try {
      const res = await fetch(`${address}/events/stream`, { headers: { authorization: `Bearer ${DISPLAY}`, ...headers }, signal: controller.signal });
      const reader = res.body?.getReader();
      const decoder = new TextDecoder();
      while (reader && !until.test(text)) {
        const { value, done } = await reader.read();
        if (done) break;
        text += decoder.decode(value);
      }
    } catch {
      // aborted after 2 s: return what arrived
    } finally {
      clearTimeout(timer);
      controller.abort();
    }
    return text;
  }

  async function hubWith(...names: string[]): Promise<{ app: ReturnType<typeof buildApp>; address: string }> {
    const app = buildApp(opts);
    for (const [i, name] of names.entries()) {
      await app.inject({ method: "POST", url: "/events", headers: { authorization: `Bearer ${TOKEN}` }, payload: { ...event, id: `e${i}`, from: { name } } });
    }
    return { app, address: await app.listen({ port: 0, host: "127.0.0.1" }) };
  }

  it("sends a screen that opens the stream the events it was not there for, oldest first, with ids", async () => {
    const { app, address } = await hubWith("Marie", "Paul");
    const text = await readStream(address, {}, /Paul/);
    await app.close();
    expect(text).toMatch(/id: [0-9a-f]+-1\ndata: .*Marie/);
    expect(text).toMatch(/id: [0-9a-f]+-2\ndata: .*Paul/);
    expect(text.indexOf("Marie")).toBeLessThan(text.indexOf("Paul"));
  });

  it("gives live events an id too, and sends each event exactly once", async () => {
    const { app, address } = await hubWith("Marie");
    const reading = readStream(address, {}, /Paul/);
    await new Promise((r) => setTimeout(r, 200)); // let the stream open and replay Marie
    await app.inject({ method: "POST", url: "/events", headers: { authorization: `Bearer ${TOKEN}` }, payload: { ...event, id: "live", from: { name: "Paul" } } });
    const text = await reading;
    await app.close();
    expect(text).toMatch(/id: [0-9a-f]+-2\ndata: .*Paul/);
    expect(text.split("Marie").length - 1).toBe(1);
    expect(text.split("Paul").length - 1).toBe(1);
  });

  it("sends a reconnecting screen only what came after the last id it saw", async () => {
    const { app, address } = await hubWith("Marie", "Paul");
    const first = await readStream(address, {}, /Paul/);
    const marieId = /id: ([0-9a-f]+-1)\n/.exec(first)?.[1] ?? "";
    const text = await readStream(address, { "last-event-id": marieId }, /Paul/);
    await app.close();
    expect(marieId).not.toBe("");
    expect(text).toContain("Paul");
    expect(text).not.toContain("Marie");
  });
});
