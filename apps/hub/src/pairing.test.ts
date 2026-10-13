import QRCode from "qrcode";
import { describe, expect, it } from "vitest";
import { buildPairingLink, groupFingerprint, isPrivateIPv4, lanAddress } from "./pairing";

const fp = "88A2899B9DF00490B76854FB11FF4F1019470BA0D5FB7CD68BC700E4B4B7B68F";
const base = { address: "https://192.168.1.66:8788", claim: "claim-0123456789abcdef", fingerprint: fp };

describe("pairing link", () => {
  it("carries the address, the one-time claim and the fingerprint (never a long-lived token)", () => {
    const link = new URL(buildPairingLink(base));
    expect(link.protocol).toBe("simple:");
    expect(link.host).toBe("pair");
    expect(link.searchParams.get("u")).toBe("https://192.168.1.66:8788");
    expect(link.searchParams.get("s")).toBe(base.claim);
    expect(link.searchParams.get("f")).toBe(fp);
    expect([...link.searchParams.keys()].sort()).toEqual(["f", "s", "u"]);
  });

  it("normalises the fingerprint to uppercase", () => {
    expect(new URL(buildPairingLink({ ...base, fingerprint: fp.toLowerCase() })).searchParams.get("f")).toBe(fp);
  });

  it("refuses a public address, plain http, a weak claim or a bad fingerprint", () => {
    expect(() => buildPairingLink({ ...base, address: "https://example.com:8788" })).toThrow();
    expect(() => buildPairingLink({ ...base, address: "http://192.168.1.66:8788" })).toThrow();
    expect(() => buildPairingLink({ ...base, claim: "short" })).toThrow();
    expect(() => buildPairingLink({ ...base, fingerprint: "abc" })).toThrow();
  });

  it("refuses an address that is not a bare host and port", () => {
    for (const a of ["https://192.168.1.66:8788@10.9.9.9", "https://user:pw@192.168.1.66:8788", "https://192.168.1.66:8788/path", "https://192.168.1.66:8788/?x=1", "https://192.168.1.66:8788/#frag"]) {
      expect(() => buildPairingLink({ ...base, address: a }), a).toThrow();
    }
  });

  it("accepts mDNS names and the three private ranges", () => {
    for (const a of ["https://simple.local:8788", "https://10.0.0.5:8788", "https://172.16.4.2:8788"]) {
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

describe("isPrivateIPv4", () => {
  it("only accepts complete private IPv4 addresses, not names that start like one", () => {
    expect(isPrivateIPv4("192.168.1.66")).toBe(true);
    expect(isPrivateIPv4("10.evil.example")).toBe(false);
    expect(isPrivateIPv4("192.168.evil.example")).toBe(false);
    expect(isPrivateIPv4("010.0.0.1")).toBe(false); // leading zero: read as octal by some resolvers
    expect(isPrivateIPv4("8.8.8.8")).toBe(false);
  });
});

describe("groupFingerprint", () => {
  it("shows the fingerprint in groups of four", () => expect(groupFingerprint(fp).startsWith("88A2 899B 9DF0 0490 ")).toBe(true));
});

describe("lanAddress", () => {
  const net = (o: Record<string, unknown>) => o as unknown as Parameters<typeof lanAddress>[0];

  it("picks the first private IPv4 address and ignores loopback and public ones", () => {
    expect(
      lanAddress(net({
        lo: [{ address: "127.0.0.1", family: "IPv4", internal: true }],
        wan: [{ address: "8.8.8.8", family: "IPv4", internal: false }],
        eth0: [{ address: "fe80::1", family: "IPv6", internal: false }, { address: "192.168.1.66", family: "IPv4", internal: false }],
      })),
    ).toBe("192.168.1.66");
  });

  it("skips container and VPN interfaces the phone cannot reach", () => {
    expect(
      lanAddress(net({
        docker0: [{ address: "172.17.0.1", family: "IPv4", internal: false }],
        tailscale0: [{ address: "10.99.0.2", family: "IPv4", internal: false }],
        eth0: [{ address: "192.168.1.66", family: "IPv4", internal: false }],
      })),
    ).toBe("192.168.1.66");
  });

  it("returns undefined when there is none", () => expect(lanAddress(net({}))).toBeUndefined());
});
