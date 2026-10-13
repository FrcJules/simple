import { act, cleanup, fireEvent, render, screen } from "@testing-library/react";
import { afterEach, describe, expect, it, vi } from "vitest";

vi.stubGlobal("EventSource", class { onmessage = null; close() {} });

import { App, SECRET_TAPS } from "./App";
import { PairingScreen } from "./PairingScreen";

const LINK = "simple://pair?u=https%3A%2F%2F192.168.1.66%3A8788&s=claim-0123456789abcdef&f=" + "AB".repeat(32);
const offer = { link: LINK, fingerprint: "ABAB ABAB ABAB ABAB", expiresInSeconds: 3 };
const okFetch = () => vi.fn().mockResolvedValue({ ok: true, json: async () => offer });

afterEach(() => { cleanup(); vi.unstubAllGlobals(); vi.stubGlobal("EventSource", class { onmessage = null; close() {} }); vi.useRealTimers(); });

describe("PairingScreen", () => {
  it("shows a QR code and the fingerprint, then closes itself when the code expires", async () => {
    vi.useFakeTimers();
    const fetchMock = okFetch();
    vi.stubGlobal("fetch", fetchMock);
    const onClose = vi.fn();
    render(<PairingScreen onClose={onClose} />);
    await act(async () => { await Promise.resolve(); });
    expect(screen.getByRole("img", { name: "QR code pour ajouter un téléphone" })).toBeTruthy();
    expect(screen.getByText("ABAB ABAB ABAB ABAB")).toBeTruthy();
    expect(fetchMock).toHaveBeenCalledWith(expect.stringMatching(/\/pairing\/start$/), expect.objectContaining({ method: "POST" }));
    expect(onClose).not.toHaveBeenCalled();
    await act(async () => { await vi.advanceTimersByTimeAsync(3100); });
    expect(onClose).toHaveBeenCalled();
  });

  it("says so plainly when the hub does not answer", async () => {
    vi.stubGlobal("fetch", vi.fn().mockRejectedValue(new Error("offline")));
    render(<PairingScreen onClose={() => {}} />);
    expect(await screen.findByText(/Ça n'a pas marché/)).toBeTruthy();
  });

  it("goes back by itself when the hub never answers, and offers to retry", async () => {
    vi.useFakeTimers();
    vi.stubGlobal("fetch", vi.fn().mockRejectedValue(new Error("offline")));
    const onClose = vi.fn();
    render(<PairingScreen onClose={onClose} />);
    await act(async () => { await Promise.resolve(); });
    expect(screen.getByRole("button", { name: "Réessayer" })).toBeTruthy();
    await act(async () => { await vi.advanceTimersByTimeAsync(30_100); });
    expect(onClose).toHaveBeenCalled();
  });

  it("treats an already expired offer as a failure", async () => {
    vi.stubGlobal("fetch", vi.fn().mockResolvedValue({ ok: true, json: async () => ({ ...offer, expiresInSeconds: 0 }) }));
    render(<PairingScreen onClose={() => {}} />);
    expect(await screen.findByText(/Ça n'a pas marché/)).toBeTruthy();
  });

  it("refuses an answer that is not a simple link", async () => {
    vi.stubGlobal("fetch", vi.fn().mockResolvedValue({ ok: true, json: async () => ({ ...offer, link: "https://evil.example" }) }));
    render(<PairingScreen onClose={() => {}} />);
    expect(await screen.findByText(/Ça n'a pas marché/)).toBeTruthy();
  });
});

describe("secret gesture", () => {
  it("opens the pairing screen only after five quick touches on the clock", async () => {
    vi.stubGlobal("fetch", okFetch());
    render(<App />);
    const clock = document.querySelector(".time")!;
    for (let i = 0; i < SECRET_TAPS - 1; i++) fireEvent.click(clock);
    expect(screen.queryByText(/ajouter un téléphone/)).toBeNull();
    fireEvent.click(clock);
    expect(await screen.findByText(/ajouter un téléphone/)).toBeTruthy();
  });
});
