import { z } from "zod";

export const SCHEMA_VERSION = 1;

const base = {
  schemaVersion: z.literal(SCHEMA_VERSION),
  id: z.string().min(1),
  /** ISO 8601 timestamp of when the event happened on the source device. */
  occurredAt: z.string().datetime({ offset: true }),
};

const httpUrl = z.string().url().refine((u) => /^https?:\/\//i.test(u), "http(s) only");

const person = z.object({
  /** Display name shown to the senior (e.g. "Marie"). */
  name: z.string().min(1).max(60),
  /** Optional local URL of the contact's photo. */
  photoUrl: httpUrl.optional(),
});

export const missedCallEvent = z.object({
  ...base,
  type: z.literal("missed_call"),
  from: person,
});

export const newPhotoEvent = z.object({
  ...base,
  type: z.literal("new_photo"),
  from: person,
  photoUrl: httpUrl,
});

export const messageChannel = z.enum(["sms", "whatsapp"]);

export const newMessageEvent = z.object({
  ...base,
  type: z.literal("new_message"),
  from: person,
  channel: messageChannel,
  /** Message body, shown (and read aloud) on the home display; kept in memory only. */
  text: z.string().min(1).max(500),
});

export const birthdayEvent = z.object({
  ...base,
  type: z.literal("birthday"),
  who: person,
});

export const presenceEvent = z.discriminatedUnion("type", [
  missedCallEvent,
  newPhotoEvent,
  newMessageEvent,
  birthdayEvent,
]);

export type PresenceEvent = z.infer<typeof presenceEvent>;
export type NewMessageEvent = z.infer<typeof newMessageEvent>;
export type MissedCallEvent = z.infer<typeof missedCallEvent>;

const DOSE = /\d\s*(mg|µg|mcg|g|ml|cl|ui|gouttes?|comprim|gélule|gelule|cuill|sachet|ampoule|pilule|unités?|unites?)/i;

/**
 * A reminder the family wrote for the home display (medicine, appointment). Kept on the Pi only, never sent anywhere.
 * `date` = once, on that day; otherwise `days` (0 = Sunday … 6 = Saturday) = on those days; neither = every day.
 */
export const reminder = z
  .object({
    id: z.string().min(1).max(40),
    // A reminder says *what*, never *how much*: a dose belongs to the prescription, not to a wall screen read in a hurry.
    title: z.string().trim().min(1).max(80).refine((t) => !DOSE.test(t), "no dose in a reminder"),
    /** Local time of day, 24 h: "08:00". */
    at: z.string().regex(/^([01]\d|2[0-3]):[0-5]\d$/),
    date: z.string().regex(/^\d{4}-\d{2}-\d{2}$/).optional(),
    days: z.array(z.number().int().min(0).max(6)).min(1).max(7).optional(),
  })
  .strict()
  .refine((r) => !(r.date && r.days), "date and days are exclusive");

/** The family photo folder listing for the display slideshow (file names only). */
export const photoList = z.object({ photos: z.array(z.string().min(1).max(120)).max(300) });

export const remindersResponse = z.object({ reminders: z.array(reminder).max(50) });

export type Reminder = z.infer<typeof reminder>;

/** The day's weather for the display (from the hub, which asks the weather service; see ADR 0006). */
export const weather = z.object({
  /** Current temperature in °C. */
  temperature: z.number().min(-90).max(70),
  /** WMO weather code (0 = clear sky … 99 = thunderstorm with hail). */
  code: z.number().int().min(0).max(99),
  min: z.number().min(-90).max(70),
  max: z.number().min(-90).max(70),
});

export type Weather = z.infer<typeof weather>;
/**
 * A loved one's birthday, entered on the Pi by the family. `day` is "MM-DD" (29 February counts on 28 February in other years);
 * `year` is optional and only used to say how old the person turns. Kept on the Pi only.
 */
export const birthday = z
  .object({
    id: z.string().min(1).max(40),
    name: z.string().trim().min(1).max(60),
    day: z.string().regex(/^(0[1-9]|1[0-2])-(0[1-9]|[12]\d|3[01])$/),
    year: z.number().int().min(1900).max(2100).optional(),
  })
  .strict()
  .refine((b) => {
    const [m, d] = b.day.split("-").map(Number) as [number, number];
    return d <= [31, 29, 31, 30, 31, 30, 31, 31, 30, 31, 30, 31][m - 1]!;
  }, "no such day");

export const birthdaysResponse = z.object({ birthdays: z.array(birthday).max(100) });

export type Birthday = z.infer<typeof birthday>;
