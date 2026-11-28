import AxeBuilder from "@axe-core/playwright";
import { expect, test, type APIRequestContext, type Page, type TestInfo } from "@playwright/test";
import { HUB, WRITER_TOKEN } from "../playwright.config";

/** A fixed morning, so screenshots do not change with the time of day. */
const NOW = new Date("2026-11-28T09:30:00+01:00");

let seq = 0;
async function send(request: APIRequestContext, event: Record<string, unknown>): Promise<void> {
  seq += 1;
  const res = await request.post(`${HUB}/events`, {
    headers: { authorization: `Bearer ${WRITER_TOKEN}` },
    data: { schemaVersion: 1, id: `e2e-${Date.now()}-${seq}`, occurredAt: NOW.toISOString(), ...event },
  });
  expect(res.status(), await res.text()).toBeLessThan(300);
}

/** Saves a full-page image under test-results/screens/<project>/ for review (no golden comparison yet). */
async function shot(page: Page, info: TestInfo, name: string): Promise<void> {
  await page.screenshot({ path: `test-results/screens/${info.project.name}/${name}.png`, fullPage: true });
}

/** No serious or critical WCAG 2.x A/AA violation (contrast, names, roles). */
async function expectAccessible(page: Page): Promise<void> {
  const result = await new AxeBuilder({ page }).withTags(["wcag2a", "wcag2aa", "wcag21aa"]).analyze();
  const serious = result.violations.filter((v) => v.impact === "serious" || v.impact === "critical");
  expect(serious.map((v) => `${v.id}: ${v.nodes.map((n) => n.target.join(" ")).join(", ")}`)).toEqual([]);
}

/**
 * Opens the home screen and waits until it listens to the hub: the hub does not replay past events to a new stream,
 * so an event sent before that would never be shown.
 */
async function openHome(page: Page): Promise<void> {
  await Promise.all([page.waitForResponse((r) => r.url().includes("/events/stream")), page.goto("/")]);
}

test.beforeEach(async ({ page }) => {
  await page.clock.setFixedTime(NOW);
});

test("home shows the time and date with nothing else happening", async ({ page }, info) => {
  await page.goto("/");
  await expect(page.getByText("09:30")).toBeVisible();
  await expect(page.getByText(/samedi/i)).toBeVisible();
  await shot(page, info, "01_idle");
  await expectAccessible(page);
});

test("a missed call from the phone appears on the screen", async ({ page, request }, info) => {
  await openHome(page);
  await send(request, { type: "missed_call", from: { name: "Marie" } });
  await expect(page.getByText("Marie vous a appelé")).toBeVisible();
  await shot(page, info, "02_missed_call");
  await expectAccessible(page);
});

test("a long message stays readable and inside the screen", async ({ page, request }, info) => {
  await openHome(page);
  const text = "Bonjour papa ! Nous passerons dimanche vers 15 heures avec les enfants, ils ont hâte de te montrer leurs dessins. Gros bisous.";
  await send(request, { type: "new_message", channel: "sms", from: { name: "Marie-Christine de La Rochefoucauld" }, text });
  await expect(page.getByText(text)).toBeVisible();
  const overflow = await page.evaluate(() => document.documentElement.scrollWidth > window.innerWidth);
  expect(overflow, "no horizontal scrolling on a kiosk screen").toBe(false);
  await shot(page, info, "03_long_message");
  await expectAccessible(page);
});

test("the screen keeps the clock when the hub cannot be reached", async ({ page }, info) => {
  await page.route(`${HUB}/**`, (route) => route.abort());
  await page.goto("/");
  await expect(page.getByText("09:30")).toBeVisible();
  await shot(page, info, "04_offline");
});
