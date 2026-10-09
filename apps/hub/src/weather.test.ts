import { describe, expect, it, vi } from "vitest";
import { buildApp } from "./app";
import { roundCoordinate, WeatherService } from "./weather";

const reply = { current: { temperature_2m: 18.4, weather_code: 3 }, daily: { temperature_2m_min: [11.2], temperature_2m_max: [21.5] } };
const ok = (body: unknown = reply) => vi.fn<(input: URL, init?: RequestInit) => Promise<Response>>(async () => ({ ok: true, text: async () => JSON.stringify(body) }) as Response);

describe("WeatherService", () => {
  it("sends only coordinates rounded to 0.1° and no other personal detail", async () => {
    const f = ok();
    await new WeatherService(48.85661, 2.35222, f).get();
    const url = new URL(String((f.mock.calls[0] as [URL])[0]));
    expect(url.host).toBe("api.open-meteo.com");
    expect(url.searchParams.get("latitude")).toBe("48.9");
    expect(url.searchParams.get("longitude")).toBe("2.4");
    expect([...url.searchParams.keys()].sort()).toEqual(["current", "daily", "forecast_days", "latitude", "longitude", "timezone"]);
    expect((f.mock.calls[0] as [URL, RequestInit])[1]?.redirect).toBe("error");
  });

  it("returns the reading and caches it for 30 minutes", async () => {
    let now = 0;
    const f = ok();
    const w = new WeatherService(48.9, 2.4, f, () => now);
    expect(await w.get()).toEqual({ temperature: 18.4, code: 3, min: 11.2, max: 21.5 });
    now += 29 * 60_000;
    await w.get();
    expect(f).toHaveBeenCalledTimes(1);
    now += 2 * 60_000;
    await w.get();
    expect(f).toHaveBeenCalledTimes(2);
  });

  it("means no weather, never a crash, on errors and nonsense", async () => {
    const down = new WeatherService(1, 1, vi.fn(async () => { throw new Error("offline"); }));
    expect(await down.get()).toBeNull();
    const bad = new WeatherService(1, 1, vi.fn(async () => ({ ok: false }) as Response));
    expect(await bad.get()).toBeNull();
    const weird = new WeatherService(1, 1, ok({ current: { temperature_2m: 999, weather_code: 3 }, daily: { temperature_2m_min: [1], temperature_2m_max: [2] } }));
    expect(await weird.get()).toBeNull();
    expect(await new WeatherService(1, 1, ok({ nope: 1 })).get()).toBeNull();
  });

  it("makes one request for simultaneous asks, retries a failure after two minutes, and refuses a huge answer", async () => {
    let now = 0;
    const f = ok();
    const w = new WeatherService(48.9, 2.4, f, () => now);
    await Promise.all([w.get(), w.get(), w.get()]);
    expect(f).toHaveBeenCalledTimes(1);
    let up = false;
    const flaky = vi.fn<(input: URL, init?: RequestInit) => Promise<Response>>(async () => {
      if (!up) throw new Error("offline");
      return { ok: true, text: async () => JSON.stringify(reply) } as Response;
    });
    const v = new WeatherService(48.9, 2.4, flaky, () => now);
    expect(await v.get()).toBeNull();
    up = true;
    now += 60_000;
    expect(await v.get()).toBeNull(); // still inside the two minutes
    now += 61_000;
    expect(await v.get()).not.toBeNull();
    const huge = new WeatherService(48.9, 2.4, async () => ({ ok: true, text: async () => " ".repeat(70_000) + JSON.stringify(reply) }) as Response);
    expect(await huge.get()).toBeNull();
  });

  it("stays off without usable coordinates", () => {
    expect(WeatherService.fromEnv(undefined, "2")).toBeUndefined();
    expect(WeatherService.fromEnv("abc", "2")).toBeUndefined();
    expect(WeatherService.fromEnv("95", "2")).toBeUndefined();
    for (const bad of [" ", "0x10", "1e1", "48,8", ""]) expect(WeatherService.fromEnv(bad, "2"), JSON.stringify(bad)).toBeUndefined();
    expect(WeatherService.fromEnv(" 48.8 ", "-2.3")).toBeInstanceOf(WeatherService);
    expect(WeatherService.fromEnv("48.8", "2.3")).toBeInstanceOf(WeatherService);
    expect(roundCoordinate(-0.04)).toBe(-0);
  });
});

describe("GET /weather", () => {
  const DISPLAY = "display-token-0123456789";
  it("needs the display token, is absent when off, and gives null when the service fails", async () => {
    const app = buildApp({ pairingToken: "0123456789abcdef-token", displayToken: DISPLAY, weather: new WeatherService(48.9, 2.4, ok()) });
    expect((await app.inject({ method: "GET", url: "/weather" })).statusCode).toBe(401);
    const res = await app.inject({ method: "GET", url: "/weather", headers: { authorization: `Bearer ${DISPLAY}` } });
    expect(res.json()).toEqual({ weather: { temperature: 18.4, code: 3, min: 11.2, max: 21.5 } });
    const off = buildApp({ pairingToken: "0123456789abcdef-token", displayToken: DISPLAY });
    expect((await off.inject({ method: "GET", url: "/weather", headers: { authorization: `Bearer ${DISPLAY}` } })).statusCode).toBe(404);
    const failing = buildApp({ pairingToken: "0123456789abcdef-token", displayToken: DISPLAY, weather: new WeatherService(1, 1, vi.fn(async () => { throw new Error("x"); })) });
    expect((await failing.inject({ method: "GET", url: "/weather", headers: { authorization: `Bearer ${DISPLAY}` } })).json()).toEqual({ weather: null });
  });
});
