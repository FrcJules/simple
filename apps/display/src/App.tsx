import { useCallback, useEffect, useRef, useState } from "react";
import { body, formatDate, formatTime, greeting, headline, icon, timeAgo } from "./format";
import { birthdaysToday, fetchBirthdays, nextBirthdays, plausibleAge, turnsAge } from "./birthdays";
import { PairingScreen } from "./PairingScreen";
import { cardReminders, dismissKey, dueReminders, fetchReminders, formatAt, nextReminder } from "./reminders";
import { homeText } from "./strings";
import type { Birthday, Reminder } from "@presence/contracts";
import { useSlideshow } from "./slideshow";
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
  const photo = useSlideshow();
  const [reminders, setReminders] = useState<Reminder[]>([]);
  const [birthdays, setBirthdays] = useState<Birthday[]>([]);
  useEffect(() => {
    let alive = true;
    const load = () => void fetchBirthdays().then((list) => alive && setBirthdays(list));
    load();
    const timer = setInterval(load, 10 * 60_000);
    return () => { alive = false; clearInterval(timer); };
  }, []);
  const today = birthdaysToday(birthdays, now);
  const soon = nextBirthdays(birthdays, now);
  const [dismissed, setDismissed] = useState<Set<string>>(new Set());
  useEffect(() => {
    let alive = true;
    const load = () => void fetchReminders().then((list) => alive && setReminders(list));
    load();
    const timer = setInterval(load, 60_000); // a reminder added on the Pi appears within a minute
    return () => { alive = false; clearInterval(timer); };
  }, []);
  const pending = (r: Reminder) => !dismissed.has(dismissKey(r, now));
  const card = cardReminders(reminders, now).filter(pending); // all the ones of the moment on one card, never one after the other
  const waiting = dueReminders(reminders, now).filter(pending); // still not touched, after the card went away
  const next = nextReminder(reminders, now);
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
  if (card.length > 0) {
    return (
      <main className="screen reminder">
        <span className="icon big" aria-hidden="true">⏰</span>
        {card.slice(0, 3).map((r) => (
          <div key={r.id} role="alert">
            <h1>{formatAt(r.at)}</h1>
            <p className="headline">{r.title}</p>
          </div>
        ))}
        <button className="ok" aria-label={homeText.okLabel} onClick={() => setDismissed((s) => new Set([...s, ...card.map((r) => dismissKey(r, now))]))}>
          ✓ {homeText.ok}
        </button>
      </main>
    );
  }
  return (
    <main className={photo ? "screen photo" : "screen"} style={photo ? { backgroundImage: `url("${photo}")` } : undefined}>
      <div className="panel">
        <h1>{greeting(now, NAME)}</h1>
        <p className="time" onClick={onClockTap}>{formatTime(now)}</p>
        <p className="date">{formatDate(now)}</p>
        {waiting[0] && <p className="next waiting"><span aria-hidden="true">⏰</span> {formatAt(waiting[0].at)} — {waiting[0].title}</p>}
        {!waiting[0] && next && <p className="next"><span aria-hidden="true">⏰</span> {formatAt(next.at)} — {next.title}</p>}
        {today.length > 0 && (
          <p className="birthday">
            <span aria-hidden="true">🎂</span>{" "}
            {homeText.birthdayToday(today.map((b) => b.name), plausibleAge(turnsAge(today[0] as Birthday, now)))}
          </p>
        )}
        {today.length === 0 && soon && (
          <p className="next">
            <span aria-hidden="true">🎂</span> {homeText.birthdaySoon(soon.people.map((b) => b.name), soon.days, new Date(now.getFullYear(), now.getMonth(), now.getDate() + soon.days).getDay())}
          </p>
        )}
      </div>
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
