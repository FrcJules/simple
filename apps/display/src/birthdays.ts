import { birthdaysResponse, type Birthday } from "@simple/contracts";

const HUB = import.meta.env.VITE_HUB_URL ?? "http://localhost:8787";
const TOKEN = import.meta.env.VITE_DISPLAY_TOKEN ?? "";

/** The loved ones' birthdays from the hub. Empty when it cannot be reached: the clock keeps working. */
export async function fetchBirthdays(): Promise<Birthday[]> {
  try {
    const res = await fetch(`${HUB}/birthdays`, { headers: { Authorization: `Bearer ${TOKEN}` } });
    if (!res.ok) return [];
    const parsed = birthdaysResponse.safeParse(await res.json());
    return parsed.success ? parsed.data.birthdays : [];
  } catch {
    return [];
  }
}

const isLeap = (y: number) => (y % 4 === 0 && y % 100 !== 0) || y % 400 === 0;

/** Month (1-12) and day of a birthday in [year]: 29 February is celebrated on 28 February in a year that is not a leap year. */
function inYear(day: string, year: number): { m: number; d: number } {
  const m = Number(day.slice(0, 2));
  const d = Number(day.slice(3, 5));
  return m === 2 && d === 29 && !isLeap(year) ? { m: 2, d: 28 } : { m, d };
}

/** Whole days from today (local midnight) to the next occurrence of this birthday: 0 = today. */
export function daysUntil(b: Birthday, now: Date): number {
  const startOfToday = new Date(now.getFullYear(), now.getMonth(), now.getDate());
  for (const year of [now.getFullYear(), now.getFullYear() + 1]) {
    const { m, d } = inYear(b.day, year);
    const date = new Date(year, m - 1, d);
    if (date >= startOfToday) return Math.round((date.getTime() - startOfToday.getTime()) / 86_400_000);
  }
  return Number.POSITIVE_INFINITY;
}

/** The age the person turns at their next (or today's) birthday, if the year of birth is known. */
export function turnsAge(b: Birthday, now: Date): number | undefined {
  if (b.year === undefined) return undefined;
  const startOfToday = new Date(now.getFullYear(), now.getMonth(), now.getDate());
  const thisYear = inYear(b.day, now.getFullYear());
  const nextYear = new Date(now.getFullYear(), thisYear.m - 1, thisYear.d) >= startOfToday ? now.getFullYear() : now.getFullYear() + 1;
  return nextYear - b.year;
}

export const birthdaysToday = (list: Birthday[], now: Date) => list.filter((b) => daysUntil(b, now) === 0);

/** All the birthdays of the nearest day in the next [withinDays] days (not today), with how many days away it is. */
export function nextBirthdays(list: Birthday[], now: Date, withinDays = 7): { days: number; people: Birthday[] } | undefined {
  const coming = list.filter((b) => daysUntil(b, now) > 0 && daysUntil(b, now) <= withinDays);
  if (coming.length === 0) return undefined;
  const days = Math.min(...coming.map((b) => daysUntil(b, now)));
  return { days, people: coming.filter((b) => daysUntil(b, now) === days) };
}

/** An age worth saying out loud: a wrong year of birth must never give "-3 ans" or "140 ans". */
export const plausibleAge = (age: number | undefined): number | undefined => (age !== undefined && age >= 2 && age <= 110 ? age : undefined);
