import { lang, type Lang } from "./lang";

/** Every text of the home screen, per language: one place to translate, none in the logic. */
export interface Texts {
  pairing: {
    title: string;
    reassurance: string;
    loading: string;
    failed: string;
    failedDetail: string;
    retry: string;
    scan: string;
    expires: (seconds: number) => string;
    close: string;
    qrLabel: string;
    /** Shown a few seconds once the phone is paired, before going back home. */
    done: string;
  };
  /** Discreet line of the home screen after a phone was paired: "Nouveau téléphone : Téléphone de Marie (2 en tout)". */
  newPhone: (label: string | undefined, count: number) => string;
  ok: string;
  okLabel: string;
  /** "de 12° à 19°" — the arguments are already formatted degrees. */
  range: (min: string, max: string) => string;
  and: string;
  others: (count: number) => string;
  weekdays: string[]; // Sunday first, like Date#getDay
  tomorrow: string;
  soonFallback: string;
  birthdayAge: (name: string, age: number) => string;
  birthdayOf: (names: string) => string; // today
  birthdayOn: (when: string, names: string) => string; // "Demain, anniversaire de Marie"
}

export const texts: Record<Lang, Texts> = {
  fr: {
    pairing: {
      title: "Pour la famille : ajouter un téléphone",
      reassurance: "Vous n'avez rien à faire. Touchez « Fermer ».",
      loading: "Un instant…",
      failed: "Ça n'a pas marché. Demandez à votre famille.",
      failedDetail: "Vérifiez que l'écran est connecté au Wi-Fi de la maison.",
      retry: "Réessayer",
      scan: "Scannez avec l'appareil photo du téléphone. Les chiffres et lettres ci-dessous doivent être les mêmes sur le téléphone :",
      expires: (seconds) => `Ce code se ferme dans ${seconds} seconde${seconds > 1 ? "s" : ""}.`,
      close: "Fermer",
      qrLabel: "QR code pour ajouter un téléphone",
      done: "C'est fait.",
    },
    newPhone: (label, n) => `${label ? `Nouveau téléphone : ${label}` : "Nouveau téléphone relié"} (${n} en tout)`,
    ok: "OK",
    okLabel: "OK, j'ai vu le rappel",
    range: (min, max) => `de ${min} à ${max}`,
    and: "et",
    others: (n) => `${n} ${n > 1 ? "autres" : "autre"}`,
    weekdays: ["Dimanche", "Lundi", "Mardi", "Mercredi", "Jeudi", "Vendredi", "Samedi"],
    tomorrow: "Demain",
    soonFallback: "Bientôt",
    birthdayAge: (name, age) => `Aujourd'hui, ${name} fête ses ${age} ans`,
    birthdayOf: (names) => `Aujourd'hui, anniversaire de ${names}`,
    birthdayOn: (when, names) => `${when}, anniversaire de ${names}`,
  },
  en: {
    pairing: {
      title: "For the family: add a phone",
      reassurance: "You don't need to do anything. Touch “Close”.",
      loading: "One moment…",
      failed: "That didn't work. Ask your family.",
      failedDetail: "Check that the screen is connected to the home Wi-Fi.",
      retry: "Try again",
      scan: "Scan with the phone's camera. The digits and letters below must be the same on the phone:",
      expires: (seconds) => `This code closes in ${seconds} second${seconds > 1 ? "s" : ""}.`,
      close: "Close",
      qrLabel: "QR code to add a phone",
      done: "Done.",
    },
    newPhone: (label, n) => `${label ? `New phone: ${label}` : "New phone linked"} (${n} in total)`,
    ok: "OK",
    okLabel: "OK, I saw the reminder",
    range: (min, max) => `${min} to ${max}`,
    and: "and",
    others: (n) => `${n} ${n > 1 ? "others" : "other"}`,
    weekdays: ["Sunday", "Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday"],
    tomorrow: "Tomorrow",
    soonFallback: "Soon",
    birthdayAge: (name, age) => `Today, ${name} turns ${age}`,
    birthdayOf: (names) => `Birthday today: ${names}`,
    birthdayOn: (when, names) => `${when}: birthday of ${names}`,
  },
};

/** Texts of the family-only pairing screen. */
export const pairingText = texts[lang].pairing;

/** "Marie", "Marie et Paul", "Marie, Paul et 2 autres": the panel never grows with the number of people. */
export const nameList = (names: string[], l: Lang = lang): string => {
  const t = texts[l];
  if (names.length <= 2) return names.join(` ${t.and} `);
  return `${names[0]}, ${names[1]} ${t.and} ${t.others(names.length - 2)}`;
};

export const birthdayText = {
  /** [age] only for one person alone, and only when it makes sense. */
  today: (names: string[], age?: number, l: Lang = lang) =>
    names.length === 1 && age ? texts[l].birthdayAge(names[0] as string, age) : texts[l].birthdayOf(nameList(names, l)),
  /** "Demain", or the weekday ("Samedi") rather than a number of days to count. */
  soon: (names: string[], days: number, weekday: number, l: Lang = lang) => {
    const t = texts[l];
    return t.birthdayOn(days === 1 ? t.tomorrow : (t.weekdays[weekday] ?? t.soonFallback), nameList(names, l));
  },
} as const;

/** Texts of the home screen's reminders and weather. */
export const homeText = {
  ok: texts[lang].ok,
  okLabel: texts[lang].okLabel,
  range: texts[lang].range,
  birthdayToday: birthdayText.today,
  newPhone: texts[lang].newPhone,
  birthdaySoon: birthdayText.soon,
} as const;
