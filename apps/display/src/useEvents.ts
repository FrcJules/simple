import { simpleEvent, type SimpleEvent } from "@simple/contracts";
import { useEffect, useState } from "react";

const HUB = import.meta.env.VITE_HUB_URL ?? "http://localhost:8787";
const TOKEN = import.meta.env.VITE_DISPLAY_TOKEN ?? "";

/** Subscribes to the hub SSE stream. EventSource reconnects automatically; the clock keeps working offline. */
export function useEvents(): SimpleEvent[] {
  const [events, setEvents] = useState<SimpleEvent[]>([]);
  useEffect(() => {
    const es = new EventSource(`${HUB}/events/stream?token=${encodeURIComponent(TOKEN)}`);
    es.onmessage = (m) => {
      const parsed = simpleEvent.safeParse(JSON.parse(m.data));
      if (parsed.success) setEvents((prev) => [parsed.data, ...prev].slice(0, 50));
    };
    return () => es.close();
  }, []);
  return events;
}
