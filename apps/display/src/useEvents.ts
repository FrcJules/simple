import { displayEvent, type DevicePairedEvent, type DisplayEvent } from "@simple/contracts";
import { useEffect, useState } from "react";

const HUB = import.meta.env.VITE_HUB_URL ?? "http://localhost:8787";
const TOKEN = import.meta.env.VITE_DISPLAY_TOKEN ?? "";

export interface Received {
  /** Calls, messages, photos, birthdays: newest first, 50 at most. */
  events: DisplayEvent[];
  /** "A phone was paired" announcements, kept apart so that no number of later events can push them out. Newest first. */
  pairings: DevicePairedEvent[];
}

/** Subscribes to the hub SSE stream. EventSource reconnects automatically; the clock keeps working offline. */
export function useEvents(): Received {
  const [received, setReceived] = useState<Received>({ events: [], pairings: [] });
  useEffect(() => {
    const es = new EventSource(`${HUB}/events/stream?token=${encodeURIComponent(TOKEN)}`);
    es.onmessage = (m) => {
      const parsed = displayEvent.safeParse(JSON.parse(m.data));
      if (!parsed.success) return;
      const e = parsed.data;
      setReceived((prev) => e.type === "device_paired"
        ? { ...prev, pairings: [e, ...prev.pairings].slice(0, 20) }
        : { ...prev, events: [e, ...prev.events].slice(0, 50) });
    };
    return () => es.close();
  }, []);
  return received;
}
