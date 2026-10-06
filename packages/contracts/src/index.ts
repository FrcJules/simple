import { z } from "zod";

export const SCHEMA_VERSION = 1;

const base = {
  schemaVersion: z.literal(SCHEMA_VERSION),
  id: z.string().min(1),
  /** ISO 8601 timestamp of when the event happened on the source device. */
  occurredAt: z.string().datetime({ offset: true }),
};

const person = z.object({
  /** Display name shown to the senior (e.g. "Marie"). */
  name: z.string().min(1).max(60),
  /** Optional local URL of the contact's photo. */
  photoUrl: z.string().url().optional(),
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
  photoUrl: z.string().url(),
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
