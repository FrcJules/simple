export type Lang = "fr" | "en";

/** "en", "en-GB", "EN"… → English; anything else, or nothing → French (the product's default language). */
export const resolveLang = (value: string | undefined): Lang => (value?.trim().toLowerCase().startsWith("en") ? "en" : "fr");

/**
 * Language of the home screen, fixed when the screen is built (`VITE_LANG` in /etc/simple/hub.env), like the senior's first name:
 * the family sets it once, nothing is detected or sent anywhere.
 */
export const lang: Lang = resolveLang(import.meta.env.VITE_LANG);
