import { isStillShown, type DisplayEvent } from "@simple/contracts";
import { lang, type Lang } from "./lang";

const LOCALE: Record<Lang, string> = { fr: "fr-FR", en: "en-US" };
const fmtDate = (l: Lang) => new Intl.DateTimeFormat(LOCALE[l], { weekday: "long", day: "numeric", month: "long" });
const fmtTime = (l: Lang) => new Intl.DateTimeFormat(LOCALE[l], { hour: l === "fr" ? "2-digit" : "numeric", minute: "2-digit" });
const DATE: Record<Lang, Intl.DateTimeFormat> = { fr: fmtDate("fr"), en: fmtDate("en") };
const TIME: Record<Lang, Intl.DateTimeFormat> = { fr: fmtTime("fr"), en: fmtTime("en") };

export const formatDate = (d: Date, l: Lang = lang) => DATE[l].format(d);
export const formatTime = (d: Date, l: Lang = lang) => TIME[l].format(d);

export function greeting(d: Date, name: string, l: Lang = lang): string {
  const morning = d.getHours() < 18;
  if (l === "en") return `${d.getHours() < 12 ? "Good morning" : morning ? "Good afternoon" : "Good evening"}, ${name}`;
  return `${morning ? "Bonjour" : "Bonsoir"} ${name}`;
}

/** "il y a 20 minutes" / "20 minutes ago" style label. */
export function timeAgo(from: Date, now: Date, l: Lang = lang): string {
  const min = Math.max(0, Math.round((now.getTime() - from.getTime()) / 60000));
  const h = Math.round(min / 60);
  const days = Math.round(h / 24);
  if (l === "en") {
    const unit = (n: number, word: string) => `${n} ${word}${n > 1 ? "s" : ""} ago`;
    if (min < 1) return "just now";
    if (min < 60) return unit(min, "minute");
    return h < 24 ? unit(h, "hour") : unit(days, "day");
  }
  if (min < 1) return "à l'instant";
  if (min < 60) return `il y a ${min} minute${min > 1 ? "s" : ""}`;
  if (h < 24) return `il y a ${h} heure${h > 1 ? "s" : ""}`;
  return `il y a ${days} jour${days > 1 ? "s" : ""}`;
}

export function headline(e: DisplayEvent, l: Lang = lang): string {
  const en = l === "en";
  switch (e.type) {
    case "missed_call": return en ? `${e.from.name} called you` : `${e.from.name} vous a appelé`;
    case "new_message": return en ? `Message from ${e.from.name}` : `Message de ${e.from.name}`;
    case "new_photo": return en ? `New photo sent by ${e.from.name}` : `Nouvelle photo envoyée par ${e.from.name}`;
    case "birthday": return en ? `${e.who.name}'s birthday today` : `Anniversaire de ${e.who.name} aujourd'hui`;
    case "device_paired":
      if (e.label) return en ? `Phone added: ${e.label}` : `Téléphone ajouté : ${e.label}`;
      return en ? "A phone was added" : "Un téléphone a été ajouté";
  }
}

export const icon = (e: DisplayEvent) =>
  ({ missed_call: "📞", new_photo: "📷", new_message: "💬", birthday: "🎂", device_paired: "📱" })[e.type];

/** Extra line under the headline: the message body, or how many phones are paired (so the family notices one too many). */
export function body(e: DisplayEvent, l: Lang = lang): string | undefined {
  if (e.type === "new_message") return e.text;
  if (e.type !== "device_paired") return undefined;
  if (l === "en") return e.devices === 1 ? "1 phone linked to this screen" : `${e.devices} phones linked to this screen`;
  return e.devices === 1 ? "1 téléphone relié à l'écran" : `${e.devices} téléphones reliés à l'écran`;
}

/**
 * The event the home screen shows: the newest one that may still be shown (a day for a missed call, a few hours for a
 * message's text: DISPLAY_RETENTION_MS). An old message does not stay on the living-room screen for days.
 */
export const currentEvent = (events: readonly DisplayEvent[], now: Date): DisplayEvent | undefined =>
  events.find((e) => isStillShown(e, now.getTime() - new Date(e.occurredAt).getTime()));
