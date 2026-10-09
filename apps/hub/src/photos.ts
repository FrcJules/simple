import { lstatSync, readdirSync, readFileSync } from "node:fs";
import { join } from "node:path";

const NAME = /^[\p{L}\p{N} ._()-]{1,100}\.(jpe?g|png|webp)$/iu;
const TYPES: Record<string, string> = { jpg: "image/jpeg", jpeg: "image/jpeg", png: "image/png", webp: "image/webp" };
export const MAX_PHOTOS = 300;
export const MAX_PHOTO_BYTES = 12 * 1024 * 1024;

/**
 * The family photos of the home display: the images in **one folder on the Pi** that the family chose (e.g. copied over the network
 * or from a USB key). Never the phone's photo library (ADR 0005). Only plain files with a plain image name are listed or served: no
 * sub-folder, no link, no hidden file, nothing from outside the folder whatever name is asked for.
 */
export class PhotosStore {
  constructor(private readonly dir: string) {}

  list(): string[] {
    let names: string[];
    try {
      names = readdirSync(this.dir);
    } catch {
      return []; // no folder yet: no photos, the clock simply stays on its dark background
    }
    return names
      .filter((n) => NAME.test(n) && !n.startsWith("."))
      .filter((n) => {
        try {
          const st = lstatSync(join(this.dir, n)); // lstat: a link to a file elsewhere is not a photo of this folder
          return st.isFile() && st.size > 0 && st.size <= MAX_PHOTO_BYTES;
        } catch {
          return false;
        }
      })
      .sort((a, b) => a.localeCompare(b, "fr"))
      .slice(0, MAX_PHOTOS);
  }

  /** The bytes and type of a listed photo, or null for anything else (the name is looked up in the list, never joined blindly). */
  read(name: string): { data: Buffer; type: string } | null {
    if (!this.list().includes(name)) return null;
    try {
      const ext = name.slice(name.lastIndexOf(".") + 1).toLowerCase();
      return { data: readFileSync(join(this.dir, name)), type: TYPES[ext] ?? "application/octet-stream" };
    } catch {
      return null;
    }
  }
}
