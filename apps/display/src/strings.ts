/** Texts of the family-only pairing screen (French for now; one place to translate, none in the logic). */
export const pairingText = {
  title: "Pour la famille : ajouter un téléphone",
  reassurance: "Vous n'avez rien à faire. Touchez « Fermer ».",
  loading: "Un instant…",
  failed: "Ça n'a pas marché. Demandez à votre famille.",
  failedDetail: "Vérifiez que l'écran est connecté au Wi-Fi de la maison.",
  retry: "Réessayer",
  scan: "Scannez avec l'appareil photo du téléphone. Les chiffres et lettres ci-dessous doivent être les mêmes sur le téléphone :",
  expires: (seconds: number) => `Ce code se ferme dans ${seconds} secondes.`,
  close: "Fermer",
  qrLabel: "QR code pour ajouter un téléphone",
} as const;

const WEEKDAYS = ["Dimanche", "Lundi", "Mardi", "Mercredi", "Jeudi", "Vendredi", "Samedi"];

/** "Marie", "Marie et Paul", "Marie, Paul et 2 autres": the panel never grows with the number of people. */
export const nameList = (names: string[]): string => {
  if (names.length <= 2) return names.join(" et ");
  return `${names[0]}, ${names[1]} et ${names.length - 2} ${names.length - 2 > 1 ? "autres" : "autre"}`;
};

export const birthdayText = {
  /** [age] only for one person alone, and only when it makes sense. */
  today: (names: string[], age?: number) =>
    names.length === 1 && age ? `Aujourd'hui, ${names[0]} fête ses ${age} ans` : `Aujourd'hui, anniversaire de ${nameList(names)}`,
  /** "Demain", or the weekday ("Samedi") rather than a number of days to count. */
  soon: (names: string[], days: number, weekday: number) => `${days === 1 ? "Demain" : (WEEKDAYS[weekday] ?? "Bientôt")}, anniversaire de ${nameList(names)}`,
} as const;

/** Texts of the home screen's reminders (French for now; one place to translate). */
export const homeText = {
  ok: "OK",
  okLabel: "OK, j'ai vu le rappel",
  birthdayToday: birthdayText.today,
  birthdaySoon: birthdayText.soon,
} as const;
