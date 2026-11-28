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

import { App, PAIRED_CONFIRMATION_MS, SECRET_TAPS } from "./App";

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

  const paired = (id: string, label?: string, devices = 2) =>
    ({ schemaVersion: 1, id, type: "device_paired", occurredAt: new Date().toISOString(), devices, ...(label ? { label } : {}) });
  const call = (id: string) => ({ schemaVersion: 1, id, type: "missed_call", occurredAt: new Date().toISOString(), from: { name: "Marie" } });
  const stubPairingFetch = () => vi.stubGlobal("fetch", vi.fn(async (url: string) => ({ ok: url.endsWith("/pairing/start"), json: async () => OFFER })));
  const openQrCode = async () => {
    const clock = document.querySelector(".time");
    expect(clock).not.toBeNull();
    for (let i = 0; i < SECRET_TAPS; i++) fireEvent.click(clock as Element);
    expect(await screen.findByText(OFFER.fingerprint)).toBeTruthy();
  };

  it("says a phone was added on a discreet line, and keeps a missed call on the card", () => {
    render(<App />);
    act(() => { FakeStream.last?.emit(call("c1")); FakeStream.last?.emit(paired("p1", "Téléphone de Marie")); });
    expect(screen.getByText("Marie vous a appelé")).toBeTruthy(); // the card is still the call
    expect(screen.getByText("Nouveau téléphone : Téléphone de Marie (2 en tout)")).toBeTruthy();
  });

  it("the newly paired phone cannot hide its announcement by sending events", () => {
    render(<App />);
    act(() => {
      FakeStream.last?.emit(paired("p1"));
      for (let i = 0; i < 60; i++) FakeStream.last?.emit(call(`flood${i}`));
    });
    expect(screen.getByText("Nouveau téléphone relié (2 en tout)")).toBeTruthy();
  });

  it("shows \"C'est fait\" when the QR code was used, then goes back home", async () => {
    stubPairingFetch();
    render(<App />);
    await openQrCode();
    vi.useFakeTimers();
    // Arrives in the same batch as an event from the new phone: the QR code must still close.
    act(() => { FakeStream.last?.emit(paired("p1", "Téléphone de Marie")); FakeStream.last?.emit(call("c1")); });
    expect(screen.getByText("C'est fait : le téléphone est ajouté.")).toBeTruthy();
    expect(screen.queryByText(OFFER.fingerprint)).toBeNull();
    act(() => { vi.advanceTimersByTime(PAIRED_CONFIRMATION_MS); });
    expect(screen.getByText("Nouveau téléphone : Téléphone de Marie (2 en tout)")).toBeTruthy();
    vi.useRealTimers();
  });

  it("an announcement from before the QR code opened does not close it", async () => {
    stubPairingFetch();
    render(<App />);
    act(() => FakeStream.last?.emit(paired("old")));
    await openQrCode();
    act(() => FakeStream.last?.emit(paired("old"))); // replayed again after a reconnection
    expect(screen.getByText(OFFER.fingerprint)).toBeTruthy();
  });
});
