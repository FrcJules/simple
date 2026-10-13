import { photoList } from "@simple/contracts";
import { useEffect, useState } from "react";

const HUB = import.meta.env.VITE_HUB_URL ?? "http://localhost:8787";
const TOKEN = import.meta.env.VITE_DISPLAY_TOKEN ?? "";
const headers = { Authorization: `Bearer ${TOKEN}` };

/** How long each photo stays, and how often the folder is read again (a photo copied to the Pi shows up within ten minutes). */
export const SLIDE_SECONDS = Number(import.meta.env.VITE_SLIDE_SECONDS ?? 30);
const REFRESH_LIST_MS = 10 * 60_000;

export async function fetchPhotoNames(): Promise<string[]> {
  try {
    const res = await fetch(`${HUB}/photos`, { headers });
    if (!res.ok) return [];
    const parsed = photoList.safeParse(await res.json());
    return parsed.success ? parsed.data.photos : [];
  } catch {
    return [];
  }
}

/** The photo as a local blob URL (the token is sent in a header, never put in a URL). Null if it cannot be loaded. */
async function fetchPhotoUrl(name: string): Promise<string | null> {
  try {
    const res = await fetch(`${HUB}/photos/${encodeURIComponent(name)}`, { headers });
    if (!res.ok) return null;
    const blob = await res.blob();
    if (!blob.type.startsWith("image/")) return null;
    const url = URL.createObjectURL(blob);
    // Decoded before it is shown: the screen never flashes the dark background between two photos.
    try {
      const img = new Image();
      img.src = url;
      await img.decode();
    } catch {
      URL.revokeObjectURL(url);
      return null; // not an image the browser can show: skip it
    }
    return url;
  } catch {
    return null;
  }
}

/** The next index in a list of [count] photos, starting from a random photo so the first one is not always the same. */
export const nextIndex = (current: number | null, count: number, random = Math.random): number =>
  current === null ? Math.floor(random() * count) : (current + 1) % count;

/** The photo to show behind the clock now (null when the folder is empty or the hub cannot be reached). */
export function useSlideshow(seconds = SLIDE_SECONDS): string | null {
  const [url, setUrl] = useState<string | null>(null);
  useEffect(() => {
    let alive = true;
    let names: string[] = [];
    let index: number | null = null;
    let current: string | null = null;
    const show = async () => {
      if (names.length === 0) return;
      index = nextIndex(index, names.length);
      const next = await fetchPhotoUrl(names[index] ?? "");
      if (!alive) { if (next) URL.revokeObjectURL(next); return; }
      if (next) {
        const old = current;
        current = next;
        setUrl(next);
        if (old) URL.revokeObjectURL(old);
      }
    };
    const refresh = async () => {
      const list = await fetchPhotoNames();
      if (!alive) return;
      const first = names.length === 0;
      names = list;
      if (list.length === 0) {
        setUrl(null);
        if (current) URL.revokeObjectURL(current);
        current = null;
        return;
      }
      if (first) await show();
    };
    void refresh();
    const slide = setInterval(() => void show(), Math.max(5, seconds) * 1000);
    const reload = setInterval(() => void refresh(), REFRESH_LIST_MS);
    return () => {
      alive = false;
      clearInterval(slide);
      clearInterval(reload);
      if (current) URL.revokeObjectURL(current);
    };
  }, [seconds]);
  return url;
}
