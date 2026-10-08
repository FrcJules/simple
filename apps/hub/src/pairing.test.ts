import { describe, expect, it } from "vitest";
import QRCode from "qrcode";
import { buildPairingLink, lanAddress } from "./pairing";

const fp = "88A2899B9DF00490B76854FB11FF4F1019470BA0D5FB7CD68BC700E4B4B7B68F";
const base = { address: "https://192.168.1.66:8788", token: "0123456789abcdef0123", fingerprint: fp };

describe("pairing link", () => {
  it("carries the address, the token and the fingerprint", () => {
    const link = new URL(buildPairingLink(base));
    expect(link.protocol).toBe("presence:");
    expect(link.host).toBe("pair");
    expect(link.searchParams.get("u")).toBe("https://192.168.1.66:8788");
    expect(link.searchParams.get("t")).toBe("0123456789abcdef0123");
    expect(link.searchParams.get("f")).toBe(fp);
  });

  it("normalises the fingerprint to uppercase", () => {
    expect(new URL(buildPairingLink({ ...base, fingerprint: fp.toLowerCase() })).searchParams.get("f")).toBe(fp);
  });

  it("refuses a public address, plain http, a weak token or a bad fingerprint", () => {
    expect(() => buildPairingLink({ ...base, address: "https://example.com:8788" })).toThrow();
    expect(() => buildPairingLink({ ...base, address: "http://192.168.1.66:8788" })).toThrow();
    expect(() => buildPairingLink({ ...base, token: "short" })).toThrow();
    expect(() => buildPairingLink({ ...base, fingerprint: "abc" })).toThrow();
  });

  it("accepts mDNS names and the three private ranges", () => {
    for (const a of ["https://presence.local:8788", "https://10.0.0.5:8788", "https://172.16.4.2:8788"]) {
      expect(() => buildPairingLink({ ...base, address: a })).not.toThrow();
    }
    expect(() => buildPairingLink({ ...base, address: "https://172.32.0.1:8788" })).toThrow();
  });

  it("is short enough for a QR code that a phone camera reads easily", async () => {
    const link = buildPairingLink(base);
    expect(link.length).toBeLessThan(220);
    expect((await QRCode.toString(link, { type: "terminal", small: true })).length).toBeGreaterThan(100);
  });
});

describe("lanAddress", () => {
  it("picks the first private IPv4 address and ignores loopback and public ones", () => {
    const ifaces = {
      lo: [{ address: "127.0.0.1", family: "IPv4", internal: true }],
      wan: [{ address: "8.8.8.8", family: "IPv4", internal: false }],
      eth0: [{ address: "fe80::1", family: "IPv6", internal: false }, { address: "192.168.1.66", family: "IPv4", internal: false }],
    } as unknown as Parameters<typeof lanAddress>[0];
    expect(lanAddress(ifaces)).toBe("192.168.1.66");
  });

  it("returns undefined when there is none", () => {
    expect(lanAddress({} as Parameters<typeof lanAddress>[0])).toBeUndefined();
  });
});
