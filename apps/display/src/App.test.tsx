import { act, cleanup, fireEvent, render, screen } from "@testing-library/react";
import { afterEach, describe, expect, it, vi } from "vitest";

/** A stream the test can push events into, like the hub would. */
class FakeStream {
  static last: FakeStream | undefined;
  onmessage: ((m: { data: string }) => void) | null = null;
  constructor() { FakeStream.last = this; }
  close() {}
  emit(event: unknown) { this.onmessage?.({ data: JSON.stringify(event) }); }
}
vi.stubGlobal("EventSource", FakeStream);

import { App, SECRET_TAPS } from "./App";

const OFFER = { link: "simple://pair?u=https%3A%2F%2F192.168.1.2%3A8788&s=abcdefghijklmnop0123&f=AB", fingerprint: "ABAB ABAB", expiresInSeconds: 120 };

afterEach(() => {
  cleanup();
  vi.unstubAllGlobals();
  vi.stubGlobal("EventSource", FakeStream);
});

describe("App", () => {
  it("shows a greeting and no event card when nothing happened", () => {
    render(<App />);
    expect(screen.getByRole("heading", { level: 1 }).textContent).toMatch(/^Bon(jour|soir) /);
    expect(document.querySelector(".card")).toBeNull();
  });

  it("closes the QR code once a phone is paired and says so on the home screen", async () => {
    vi.stubGlobal("fetch", vi.fn(async (url: string) => ({ ok: url.endsWith("/pairing/start"), json: async () => OFFER })));
    render(<App />);
    const clock = document.querySelector(".time");
    expect(clock).not.toBeNull();
    for (let i = 0; i < SECRET_TAPS; i++) fireEvent.click(clock as Element);
    expect(await screen.findByText(OFFER.fingerprint)).toBeTruthy(); // the QR code screen is open

    act(() => FakeStream.last?.emit({
      schemaVersion: 1, id: "p1", type: "device_paired", occurredAt: new Date(Date.now() + 1000).toISOString(), label: "Téléphone de Marie", devices: 2,
    }));

    expect(await screen.findByText("Téléphone ajouté : Téléphone de Marie")).toBeTruthy();
    expect(screen.getByText("2 téléphones reliés à l'écran")).toBeTruthy();
    expect(screen.queryByText(OFFER.fingerprint)).toBeNull();
  });
});
