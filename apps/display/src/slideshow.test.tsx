import { render, screen, cleanup } from "@testing-library/react";
import { afterEach, describe, expect, it, vi } from "vitest";

vi.stubGlobal("EventSource", class { onmessage = null; close() {} });

import { App } from "./App";
import { nextIndex } from "./slideshow";

afterEach(() => { cleanup(); vi.unstubAllGlobals(); vi.stubGlobal("EventSource", class { onmessage = null; close() {} }); });

describe("nextIndex", () => {
  it("starts at a random photo then goes round", () => {
    expect(nextIndex(null, 4, () => 0.5)).toBe(2);
    expect(nextIndex(3, 4)).toBe(0);
    expect(nextIndex(0, 4)).toBe(1);
  });
});

describe("slideshow in the app", () => {
  it("shows a photo behind the clock when the hub has some, using the token in a header", async () => {
    const fetchMock = vi.fn(async (url: string) => {
      if (url.endsWith("/reminders")) return { ok: true, json: async () => ({ reminders: [] }) };
      if (url.endsWith("/photos")) return { ok: true, json: async () => ({ photos: ["a b.jpg"] }) };
      return { ok: true, blob: async () => new Blob(["x"], { type: "image/jpeg" }) };
    });
    vi.stubGlobal("fetch", fetchMock);
    vi.stubGlobal("URL", Object.assign(URL, { createObjectURL: () => "blob:photo-1", revokeObjectURL: () => {} }));
    vi.stubGlobal("Image", class { src = ""; decode() { return Promise.resolve(); } });
    render(<App />);
    await vi.waitFor(() => expect(document.querySelector("main.photo")).not.toBeNull());
    expect(fetchMock).toHaveBeenCalledWith(expect.stringMatching(/\/photos\/a%20b\.jpg$/), expect.objectContaining({ headers: expect.objectContaining({ Authorization: expect.stringContaining("Bearer") }) }));
    expect(screen.getByRole("heading", { level: 1 })).toBeTruthy(); // the greeting is still there
  });

  it("keeps the plain dark background when there is no photo", async () => {
    vi.stubGlobal("fetch", vi.fn(async (url: string) => ({ ok: true, json: async () => (url.endsWith("/photos") ? { photos: [] } : { reminders: [] }) })));
    render(<App />);
    await new Promise((r) => setTimeout(r, 20));
    expect(document.querySelector("main.photo")).toBeNull();
  });
});
