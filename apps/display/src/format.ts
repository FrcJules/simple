import type { PresenceEvent } from "@presence/contracts";

const fmtDate = new Intl.DateTimeFormat("fr-FR", { weekday: "long", day: "numeric", month: "long" });
const fmtTime = new Intl.DateTimeFormat("fr-FR", { hour: "2-digit", minute: "2-digit" });

export const formatDate = (d: Date) => fmtDate.format(d);
export const formatTime = (d: Date) => fmtTime.format(d);

export function greeting(d: Date, name: string): string {
  return `${d.getHours() < 18 ? "Bonjour" : "Bonsoir"} ${name}`;
}

/** "il y a 20 minutes" style label, in French. */
export function timeAgo(from: Date, now: Date): string {
  const min = Math.max(0, Math.round((now.getTime() - from.getTime()) / 60000));
  if (min < 1) return "à l'instant";
  if (min < 60) return `il y a ${min} minute${min > 1 ? "s" : ""}`;
  const h = Math.round(min / 60);
  if (h < 24) return `il y a ${h} heure${h > 1 ? "s" : ""}`;
  const days = Math.round(h / 24);
  return `il y a ${days} jour${days > 1 ? "s" : ""}`;
}

export function headline(e: PresenceEvent): string {
  switch (e.type) {
    case "missed_call": return `${e.from.name} vous a appelé`;
    case "new_message": return `Message de ${e.from.name}`;
    case "new_photo": return `Nouvelle photo envoyée par ${e.from.name}`;
    case "birthday": return `Anniversaire de ${e.who.name} aujourd'hui`;
  }
}

export const icon = (e: PresenceEvent) =>
  ({ missed_call: "📞", new_photo: "📷", new_message: "💬", birthday: "🎂" })[e.type];

/** Extra line under the headline (message body), if the event carries one. */
export const body = (e: PresenceEvent): string | undefined => (e.type === "new_message" ? e.text : undefined);
