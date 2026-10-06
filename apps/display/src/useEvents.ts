import { presenceEvent, type PresenceEvent } from "@presence/contracts";
import { useEffect, useState } from "react";

const HUB = import.meta.env.VITE_HUB_URL ?? "http://localhost:8787";

/** Subscribes to the hub SSE stream. EventSource reconnects automatically; the clock keeps working offline. */
export function useEvents(): PresenceEvent[] {
  const [events, setEvents] = useState<PresenceEvent[]>([]);
  useEffect(() => {
    const es = new EventSource(`${HUB}/events/stream`);
    es.onmessage = (m) => {
      const parsed = presenceEvent.safeParse(JSON.parse(m.data));
      if (parsed.success) setEvents((prev) => [parsed.data, ...prev].slice(0, 50));
    };
    return () => es.close();
  }, []);
  return events;
}
