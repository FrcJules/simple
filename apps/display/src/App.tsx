import { useCallback, useEffect, useRef, useState } from "react";
import { body, formatDate, formatTime, greeting, headline, icon, timeAgo } from "./format";
import { PairingScreen } from "./PairingScreen";
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

/** Five touches on the clock within four seconds: nobody does that by accident, and it needs no extra button on Jean-Pierre's screen. */
export const SECRET_TAPS = 5;
const SECRET_WINDOW_MS = 4000;

export function App() {
  const now = useNow();
  const latest = useEvents()[0];
  const [pairing, setPairing] = useState(false);
  const taps = useRef<number[]>([]);
  const closePairing = useCallback(() => setPairing(false), []);
  const onClockTap = () => {
    const t = Date.now();
    taps.current = [...taps.current.filter((x) => t - x < SECRET_WINDOW_MS), t];
    if (taps.current.length >= SECRET_TAPS) {
      taps.current = [];
      setPairing(true);
    }
  };
  if (pairing) return <PairingScreen onClose={closePairing} />;
  return (
    <main className="screen">
      <h1>{greeting(now, NAME)}</h1>
      <p className="time" onClick={onClockTap}>{formatTime(now)}</p>
      <p className="date">{formatDate(now)}</p>
      {latest && (
        <section className="card" aria-live="polite">
          <span className="icon" aria-hidden="true">{icon(latest)}</span>
          <p className="headline">{headline(latest)}</p>
          {body(latest) && <p className="body">{body(latest)}</p>}
          <p className="ago">{timeAgo(new Date(latest.occurredAt), now)}</p>
        </section>
      )}
    </main>
  );
}
