/**
 * Counts *failures* per address over a sliding minute. Only failures count, so a flood from one address locks that address out and
 * nobody else. The table is bounded: when it is full the oldest address is forgotten, never all of them.
 */
export class FailureLimiter {
  private readonly failures = new Map<string, number[]>();

  constructor(private readonly maxPerMinute: number, private readonly maxAddresses = 1_000, private readonly clock: () => number = Date.now) {}

  blocked(ip: string): boolean {
    return this.recent(ip).length >= this.maxPerMinute;
  }

  record(ip: string): void {
    const recent = this.recent(ip);
    this.failures.delete(ip); // re-inserted last: iteration order is oldest first
    if (this.failures.size >= this.maxAddresses) this.failures.delete(this.failures.keys().next().value as string);
    this.failures.set(ip, [...recent, this.clock()]);
  }

  private recent(ip: string): number[] {
    const now = this.clock();
    const recent = (this.failures.get(ip) ?? []).filter((t) => now - t < 60_000);
    if (recent.length > 0) this.failures.set(ip, recent);
    else this.failures.delete(ip);
    return recent;
  }
}
