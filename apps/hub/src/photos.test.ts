import { mkdirSync, mkdtempSync, rmSync, symlinkSync, writeFileSync } from "node:fs";
import { tmpdir } from "node:os";
import { join } from "node:path";
import { afterEach, beforeEach, describe, expect, it } from "vitest";
import { buildApp } from "./app";
import { PhotosStore } from "./photos";

let root: string;
let dir: string;
beforeEach(() => {
  root = mkdtempSync(join(tmpdir(), "presence-ph-"));
  dir = join(root, "photos");
  mkdirSync(dir);
});
afterEach(() => rmSync(root, { recursive: true, force: true }));
const put = (name: string, content = "img") => writeFileSync(join(dir, name), content);

describe("PhotosStore", () => {
  it("lists only plain image files, sorted", () => {
    put("b.jpg"); put("a.PNG"); put("c.webp"); put("notes.txt"); put(".hidden.jpg"); put("été 2025.jpeg"); put("empty.jpg", "");
    mkdirSync(join(dir, "folder.jpg"));
    expect(new PhotosStore(dir).list()).toEqual(["a.PNG", "b.jpg", "c.webp", "été 2025.jpeg"]);
  });

  it("does not follow links out of the folder", () => {
    writeFileSync(join(root, "secret.jpg"), "secret");
    symlinkSync(join(root, "secret.jpg"), join(dir, "link.jpg"));
    put("ok.jpg");
    const s = new PhotosStore(dir);
    expect(s.list()).toEqual(["ok.jpg"]);
    expect(s.read("link.jpg")).toBeNull();
  });

  it("serves only listed names, never a path", () => {
    put("ok.jpg", "bytes");
    writeFileSync(join(root, "outside.jpg"), "x");
    const s = new PhotosStore(dir);
    expect(s.read("ok.jpg")?.data.toString()).toBe("bytes");
    expect(s.read("ok.jpg")?.type).toBe("image/jpeg");
    for (const bad of ["../outside.jpg", "..%2Foutside.jpg", "/etc/passwd", "ok.jpg/../ok.jpg", "", "nope.jpg"]) expect(s.read(bad), bad).toBeNull();
  });

  it("no folder means no photos", () => expect(new PhotosStore(join(root, "missing")).list()).toEqual([]));
});

describe("photo routes", () => {
  const DISPLAY = "display-token-0123456789";
  const auth = { authorization: `Bearer ${DISPLAY}` };
  it("need the display token, set the safe headers and refuse paths", async () => {
    put("ok.jpg", "bytes");
    const app = buildApp({ displayToken: DISPLAY, devices: undefined, photos: new PhotosStore(dir), pairingToken: "0123456789abcdef-token" });
    expect((await app.inject({ method: "GET", url: "/photos" })).statusCode).toBe(401);
    expect((await app.inject({ method: "GET", url: "/photos", headers: auth })).json()).toEqual({ photos: ["ok.jpg"] });
    const img = await app.inject({ method: "GET", url: "/photos/ok.jpg", headers: auth });
    expect(img.statusCode).toBe(200);
    expect(img.headers["content-type"]).toBe("image/jpeg");
    expect(img.headers["x-content-type-options"]).toBe("nosniff");
    expect((await app.inject({ method: "GET", url: "/photos/..%2Fsecret.jpg", headers: auth })).statusCode).toBe(404);
    expect((await app.inject({ method: "GET", url: "/photos/ok.jpg", headers: { ...auth, host: "evil.example" } })).statusCode).toBe(404);
  });
  it("do not exist without the feature", async () => {
    const app = buildApp({ displayToken: DISPLAY, pairingToken: "0123456789abcdef-token" });
    expect((await app.inject({ method: "GET", url: "/photos", headers: auth })).statusCode).toBe(404);
  });
});
