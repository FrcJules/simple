import { useEffect, useState } from "react";
import { formatDate, formatTime, greeting, headline, icon, timeAgo } from "./format";
import { useEvents } from "./useEvents";
import "./app.css";

const NAME = import.meta.env.VITE_SENIOR_NAME ?? "Jean-Pierre";

export function useNow(intervalMs = 15_000): Date {
  const [now, setNow] = useState(() => new Date());
  useEffect(() => {
    const t = setInterval(() => setNow(new Date()), intervalMs);
    return () => clearInterval(t);
  }, [intervalMs]);
  return now;
}

export function App() {
  const now = useNow();
  const latest = useEvents()[0];
  return (
    <main className="screen">
      <h1>{greeting(now, NAME)}</h1>
      <p className="time">{formatTime(now)}</p>
      <p className="date">{formatDate(now)}</p>
      {latest && (
        <section className="card" aria-live="polite">
          <span className="icon" aria-hidden="true">{icon(latest)}</span>
          <p className="headline">{headline(latest)}</p>
          <p className="ago">{timeAgo(new Date(latest.occurredAt), now)}</p>
        </section>
      )}
    </main>
  );
}
