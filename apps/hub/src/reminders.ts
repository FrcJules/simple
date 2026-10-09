import { reminder, type Reminder } from "@presence/contracts";
import { randomBytes } from "node:crypto";
import { mkdirSync, readFileSync, renameSync, writeFileSync } from "node:fs";
import { join } from "node:path";
import { z } from "zod";

const MAX_REMINDERS = 50;
const fileSchema = z.array(z.unknown());

/**
 * The family's reminders, in one small file on the Pi (`reminders.json`, owner only). Medicine and appointments are health data: they
 * never leave the Pi. A damaged entry is skipped, never a crash; a damaged file means "no reminders".
 */
export class RemindersStore {
  private readonly file: string;

  constructor(dir: string) {
    mkdirSync(dir, { recursive: true, mode: 0o700 });
    this.file = join(dir, "reminders.json");
  }

  list(): Reminder[] {
    try {
      const raw = fileSchema.safeParse(JSON.parse(readFileSync(this.file, "utf8")));
      if (!raw.success) return [];
      return raw.data.flatMap((entry) => {
        const parsed = reminder.safeParse(entry);
        return parsed.success ? [parsed.data] : [];
      });
    } catch {
      return [];
    }
  }

  /** Adds one reminder (validated like the display will read it) and returns it. Throws on invalid input or when the list is full. */
  add(input: Omit<Reminder, "id">): Reminder {
    const current = this.list();
    if (current.length >= MAX_REMINDERS) throw new Error(`at most ${MAX_REMINDERS} reminders`);
    const created = reminder.parse({ ...input, id: randomBytes(4).toString("hex") });
    this.write([...current, created]);
    return created;
  }

  remove(id: string): boolean {
    const current = this.list();
    const kept = current.filter((r) => r.id !== id);
    if (kept.length === current.length) return false;
    this.write(kept);
    return true;
  }

  private write(list: Reminder[]): void {
    const tmp = `${this.file}.${process.pid}.tmp`;
    writeFileSync(tmp, JSON.stringify(list), { mode: 0o600 });
    renameSync(tmp, this.file);
  }
}
