import { describe, expect, it } from "vitest";
import { FailureLimiter } from "./limiter";

describe("FailureLimiter", () => {
  it("blocks an address after its failures, for a minute, and nobody else", () => {
    let now = 0;
    const l = new FailureLimiter(3, 100, () => now);
    for (let i = 0; i < 3; i++) l.record("a");
    expect(l.blocked("a")).toBe(true);
    expect(l.blocked("b")).toBe(false);
    now = 59_999;
    expect(l.blocked("a")).toBe(true);
    now = 60_001;
    expect(l.blocked("a")).toBe(false);
  });

  it("forgets only the oldest address when full, never everybody", () => {
    let now = 0;
    const l = new FailureLimiter(1, 2, () => now);
    l.record("old"); now++;
    l.record("mid"); now++;
    l.record("new");
    expect(l.blocked("old")).toBe(false); // evicted
    expect(l.blocked("mid")).toBe(true);
    expect(l.blocked("new")).toBe(true);
  });
});
