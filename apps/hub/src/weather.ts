import { weather, type Weather } from "@simple/contracts";
import { z } from "zod";

const answer = z.object({
  current: z.object({ temperature_2m: z.number(), weather_code: z.number() }),
  daily: z.object({ temperature_2m_min: z.array(z.number()).min(1), temperature_2m_max: z.array(z.number()).min(1) }),
});

/** Coordinates rounded to 0.1° (about 11 km): a town, never a house (ADR 0006). */
export const roundCoordinate = (value: number) => Math.round(value * 10) / 10;

/** The part of `fetch` we use: lets tests give a plain function. */
export type FetchLike = (input: URL, init?: RequestInit) => Promise<Response>;

const REFRESH_MS = 30 * 60_000;
/** After a failure the next try comes sooner (a network that was not up yet at start), but never in a hurry. */
const RETRY_MS = 2 * 60_000;
const MAX_BODY_BYTES = 64 * 1024;
const NUMBER = /^-?\d+(\.\d+)?$/;
const TIMEOUT_MS = 5_000;

/**
 * The day's weather from Open-Meteo, asked by the hub (never by the browser or the phone), at most every 30 minutes, only when the
 * family configured coordinates. Nothing is logged or written to disk; any failure means "no weather" and the screen carries on.
 */
export class WeatherService {
  private cached: { at: number; value: Weather | null } | undefined;
  private inFlight: Promise<Weather | null> | undefined;
  private readonly latitude: number;
  private readonly longitude: number;

  constructor(
    latitude: number,
    longitude: number,
    private readonly fetchFn: FetchLike = fetch,
    private readonly clock: () => number = Date.now,
  ) {
    this.latitude = roundCoordinate(latitude);
    this.longitude = roundCoordinate(longitude);
  }

  /** Null when the coordinates are not usable numbers: the feature stays off. */
  static fromEnv(lat: string | undefined, lon: string | undefined, fetchFn?: FetchLike): WeatherService | undefined {
    if (!lat || !lon || !NUMBER.test(lat.trim()) || !NUMBER.test(lon.trim())) return undefined; // " ", "0x10", "1e1" do not switch it on
    const la = Number(lat.trim());
    const lo = Number(lon.trim());
    if (!Number.isFinite(la) || !Number.isFinite(lo) || Math.abs(la) > 90 || Math.abs(lo) > 180) return undefined;
    return new WeatherService(la, lo, fetchFn);
  }

  async get(): Promise<Weather | null> {
    const now = this.clock();
    if (this.cached && now - this.cached.at < (this.cached.value ? REFRESH_MS : RETRY_MS)) return this.cached.value;
    // Several screens asking at once make one request, not several.
    this.inFlight ??= this.fetchNow().then((value) => {
      this.cached = { at: now, value };
      return value;
    }).finally(() => { this.inFlight = undefined; });
    return this.inFlight;
  }

  private async fetchNow(): Promise<Weather | null> {
    const url = new URL("https://api.open-meteo.com/v1/forecast");
    url.search = new URLSearchParams({
      latitude: String(this.latitude),
      longitude: String(this.longitude),
      current: "temperature_2m,weather_code",
      daily: "temperature_2m_min,temperature_2m_max",
      forecast_days: "1",
      timezone: "auto",
    }).toString();
    try {
      const res = await this.fetchFn(url, { signal: AbortSignal.timeout(TIMEOUT_MS), redirect: "error" });
      if (!res.ok) return null;
      const text = await res.text();
      if (text.length > MAX_BODY_BYTES) return null;
      const parsed = answer.safeParse(JSON.parse(text));
      if (!parsed.success) return null;
      const out = weather.safeParse({
        temperature: parsed.data.current.temperature_2m,
        code: parsed.data.current.weather_code,
        min: parsed.data.daily.temperature_2m_min[0],
        max: parsed.data.daily.temperature_2m_max[0],
      });
      return out.success ? out.data : null;
    } catch {
      return null;
    }
  }
}
