import { type Weather, weather } from "@presence/contracts";
import { z } from "zod";

const HUB = import.meta.env.VITE_HUB_URL ?? "http://localhost:8787";
const TOKEN = import.meta.env.VITE_DISPLAY_TOKEN ?? "";

const response = z.object({ weather: weather.nullable() });

/** The day's weather from the hub (which asks the weather service, ADR 0006). Null when off, unavailable, or the hub cannot be reached. */
export async function fetchWeather(): Promise<Weather | null> {
  try {
    const res = await fetch(`${HUB}/weather`, { headers: { Authorization: `Bearer ${TOKEN}` } });
    if (!res.ok) return null;
    const parsed = response.safeParse(await res.json());
    return parsed.success ? parsed.data.weather : null;
  } catch {
    return null;
  }
}

/** WMO weather code → a picture and plain words. */
export function describeWeather(code: number): { icon: string; label: string } {
  if (code === 0) return { icon: "☀️", label: "Grand soleil" };
  if (code === 1 || code === 2) return { icon: "🌤️", label: "Soleil et nuages" };
  if (code === 3) return { icon: "☁️", label: "Ciel couvert" };
  if (code === 45 || code === 48) return { icon: "🌫️", label: "Brouillard" };
  if (code >= 51 && code <= 57) return { icon: "🌦️", label: "Bruine" };
  if ((code >= 61 && code <= 67) || (code >= 80 && code <= 82)) return { icon: "🌧️", label: "Pluie" };
  if ((code >= 71 && code <= 77) || code === 85 || code === 86) return { icon: "❄️", label: "Neige" };
  if (code >= 95) return { icon: "⛈️", label: "Orage" };
  return { icon: "🌡️", label: "Temps variable" };
}

/** "18°" — whole degrees, a minus sign (not a hyphen) below zero. */
export const degrees = (value: number): string => `${Math.round(value) < 0 ? "−" : ""}${Math.abs(Math.round(value))}°`;
