import { type Weather, weather } from "@simple/contracts";
import { z } from "zod";
import { lang, type Lang } from "./lang";

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
export function describeWeather(code: number, l: Lang = lang): { icon: string; label: string } {
  const say = (fr: string, en: string) => (l === "en" ? en : fr);
  if (code === 0) return { icon: "☀️", label: say("Grand soleil", "Sunny") };
  if (code === 1 || code === 2) return { icon: "🌤️", label: say("Soleil et nuages", "Sun and clouds") };
  if (code === 3) return { icon: "☁️", label: say("Ciel couvert", "Cloudy") };
  if (code === 45 || code === 48) return { icon: "🌫️", label: say("Brouillard", "Fog") };
  if (code >= 51 && code <= 57) return { icon: "🌦️", label: say("Bruine", "Light rain") };
  if ((code >= 61 && code <= 67) || (code >= 80 && code <= 82)) return { icon: "🌧️", label: say("Pluie", "Rain") };
  if ((code >= 71 && code <= 77) || code === 85 || code === 86) return { icon: "❄️", label: say("Neige", "Snow") };
  if (code >= 95) return { icon: "⛈️", label: say("Orage", "Thunderstorm") };
  return { icon: "🌡️", label: say("Temps variable", "Changing weather") };
}

/** "18°" — whole degrees, a minus sign (not a hyphen) below zero. */
export const degrees = (value: number): string => `${Math.round(value) < 0 ? "−" : ""}${Math.abs(Math.round(value))}°`;
