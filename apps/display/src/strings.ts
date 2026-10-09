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

/** Texts of the home screen's reminders (French for now; one place to translate). */
export const homeText = {
  ok: "OK",
  okLabel: "OK, j'ai vu le rappel",
} as const;
