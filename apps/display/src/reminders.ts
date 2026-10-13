import { remindersResponse, type Reminder } from "@simple/contracts";

const HUB = import.meta.env.VITE_HUB_URL ?? "http://localhost:8787";
const TOKEN = import.meta.env.VITE_DISPLAY_TOKEN ?? "";

/** A reminder stays on screen this long after its time unless it is touched: late in the morning it is still worth saying. */
export const SHOW_FOR_MINUTES = 60;

/** The full-screen card only lasts this long; then the home screen is back and the reminder stays as a small line until it is touched. */
export const CARD_FOR_MINUTES = 5;

/** "08:05" → "8 h 05". */
export const formatAt = (at: string) => `${Number(at.slice(0, 2))} h ${at.slice(3, 5)}`;

/** The family's reminders from the hub. Empty when the hub cannot be reached: the clock keeps working. */
export async function fetchReminders(): Promise<Reminder[]> {
  try {
    const res = await fetch(`${HUB}/reminders`, { headers: { Authorization: `Bearer ${TOKEN}` } });
    if (!res.ok) return [];
    const parsed = remindersResponse.safeParse(await res.json());
    return parsed.success ? parsed.data.reminders : [];
  } catch {
    return [];
  }
}

const pad = (n: number) => String(n).padStart(2, "0");
export const dayKey = (d: Date) => `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}`;
const minutesOf = (at: string) => Number(at.slice(0, 2)) * 60 + Number(at.slice(3, 5));

function happensToday(r: Reminder, now: Date): boolean {
  if (r.date) return r.date === dayKey(now);
  return !r.days || r.days.includes(now.getDay());
}

/** The reminders whose time has come (from their time to [SHOW_FOR_MINUTES] later), earliest first. */
export function dueReminders(list: Reminder[], now: Date): Reminder[] {
  const m = now.getHours() * 60 + now.getMinutes();
  return list
    .filter((r) => happensToday(r, now) && m >= minutesOf(r.at) && m < minutesOf(r.at) + SHOW_FOR_MINUTES)
    .sort((a, b) => minutesOf(a.at) - minutesOf(b.at));
}

/** The reminders to show as a full-screen card now: the due ones that started less than [CARD_FOR_MINUTES] ago. */
export function cardReminders(list: Reminder[], now: Date): Reminder[] {
  const m = now.getHours() * 60 + now.getMinutes();
  return dueReminders(list, now).filter((r) => m < minutesOf(r.at) + CARD_FOR_MINUTES);
}

/** The next one today that has not started yet, for the small line under the date. */
export function nextReminder(list: Reminder[], now: Date): Reminder | undefined {
  const m = now.getHours() * 60 + now.getMinutes();
  return list
    .filter((r) => happensToday(r, now) && minutesOf(r.at) > m)
    .sort((a, b) => minutesOf(a.at) - minutesOf(b.at))[0];
}

/** One key per reminder and day: touching "J'ai compris" hides it for today only. */
export const dismissKey = (r: Reminder, now: Date) => `${r.id}:${dayKey(now)}`;
