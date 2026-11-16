import type { SimpleEvent } from "@simple/contracts";
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
  if (l === "en") return `${d.getHours() < 12 ? "Good morning" : morning ? "Good afternoon" : "Good evening"} ${name}`;
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

export function headline(e: SimpleEvent, l: Lang = lang): string {
  const en = l === "en";
  switch (e.type) {
    case "missed_call": return en ? `${e.from.name} called you` : `${e.from.name} vous a appelé`;
    case "new_message": return en ? `Message from ${e.from.name}` : `Message de ${e.from.name}`;
    case "new_photo": return en ? `New photo sent by ${e.from.name}` : `Nouvelle photo envoyée par ${e.from.name}`;
    case "birthday": return en ? `${e.who.name}'s birthday today` : `Anniversaire de ${e.who.name} aujourd'hui`;
  }
}

export const icon = (e: SimpleEvent) =>
  ({ missed_call: "📞", new_photo: "📷", new_message: "💬", birthday: "🎂" })[e.type];

/** Extra line under the headline (message body), if the event carries one. */
export const body = (e: SimpleEvent): string | undefined => (e.type === "new_message" ? e.text : undefined);
