import { birthday, type Birthday } from "@simple/contracts";
import { randomBytes } from "node:crypto";
import { mkdirSync, readFileSync, renameSync, writeFileSync } from "node:fs";
import { join } from "node:path";
import { z } from "zod";

const MAX_BIRTHDAYS = 100;
const fileSchema = z.array(z.unknown());

/**
 * The family's loved ones' birthdays, in one small file on the Pi (`birthdays.json`, owner only): names and dates of other people, kept
 * local. A damaged entry is skipped, never a crash; a damaged file means "no birthdays".
 */
export class BirthdaysStore {
  private readonly file: string;

  constructor(dir: string) {
    mkdirSync(dir, { recursive: true, mode: 0o700 });
    this.file = join(dir, "birthdays.json");
  }

  list(): Birthday[] {
    try {
      const raw = fileSchema.safeParse(JSON.parse(readFileSync(this.file, "utf8")));
      if (!raw.success) return [];
      return raw.data.flatMap((entry) => {
        const parsed = birthday.safeParse(entry);
        return parsed.success ? [parsed.data] : [];
      });
    } catch {
      return [];
    }
  }

  /** Adds one reminder (validated like the display will read it) and returns it. Throws on invalid input or when the list is full. */
  add(input: Omit<Birthday, "id">): Birthday {
    const current = this.list();
    if (current.length >= MAX_BIRTHDAYS) throw new Error(`at most ${MAX_BIRTHDAYS} birthdays`);
    const created = birthday.parse({ ...input, id: randomBytes(4).toString("hex") });
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

  private write(list: Birthday[]): void {
    const tmp = `${this.file}.${process.pid}.tmp`;
    writeFileSync(tmp, JSON.stringify(list), { mode: 0o600 });
    renameSync(tmp, this.file);
  }
}
