import AxeBuilder from "@axe-core/playwright";
import { expect, test, type APIRequestContext, type Page, type TestInfo } from "@playwright/test";
import { X509Certificate } from "node:crypto";
import { readFileSync } from "node:fs";
import https from "node:https";
import { HUB, HUB_TLS_PORT, WRITER_TOKEN } from "../playwright.config";

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

/** Opens the home screen and waits until it listens to the hub, so the next event is received live. */
async function openHome(page: Page): Promise<void> {
  await Promise.all([page.waitForResponse((r) => r.url().includes("/events/stream")), page.goto("/")]);
}

test.beforeEach(async ({ page }) => {
  await page.clock.setFixedTime(NOW);
});

test("home shows the time and date with nothing else happening", async ({ page }, info) => {
  // The hub is shared by both screen sizes and replays the last day: start this screen with no events at all.
  await page.route(`${HUB}/events/stream*`, (route) => route.abort());
  await page.goto("/");
  await expect(page.getByText("09:30")).toBeVisible();
  await expect(page.getByText(/samedi/i)).toBeVisible();
  await expect(page.getByText(/vous a appelé|Message de/)).toHaveCount(0);
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

test("a call missed while the screen was off is shown when it starts", async ({ page, request }, info) => {
  await send(request, { type: "missed_call", from: { name: "Paul" } });
  await page.goto("/");
  await expect(page.getByText("Paul vous a appelé")).toBeVisible();
  await shot(page, info, "05_missed_while_off");
});

test("the screen keeps the clock when the hub cannot be reached", async ({ page }, info) => {
  await page.route(`${HUB}/**`, (route) => route.abort());
  await page.goto("/");
  await expect(page.getByText("09:30")).toBeVisible();
  await shot(page, info, "04_offline");
});

/** Redeems a QR code claim like the phone does: HTTPS to the hub's network port, trusting only its pinned certificate. */
function pairLikeAPhone(claim: string): Promise<number> {
  const cert = readFileSync("test-results/hub-data/tls/hub-cert.pem", "utf8");
  const pinned = new X509Certificate(cert).fingerprint256;
  return new Promise((resolve, reject) => {
    const req = https.request({
      host: "127.0.0.1", port: HUB_TLS_PORT, method: "POST", path: "/pair", headers: { "content-type": "application/json" },
      ca: cert,
      checkServerIdentity: (_host, peer) => (peer.fingerprint256 === pinned ? undefined : new Error("certificate does not match the pin")),
    }, (res) => { res.resume(); resolve(res.statusCode ?? 0); });
    req.on("error", reject);
    req.end(JSON.stringify({ claim }));
  });
}

test("pairing a phone with the QR code: \"C'est fait\", then a discreet line on the home screen", async ({ page }, info) => {
  await openHome(page);
  // Read the one-time claim the hub gives the screen, as the phone's camera would read it from the QR code.
  let claim = "";
  await page.route(`${HUB}/pairing/start`, async (route) => {
    const response = await route.fetch();
    const body = await response.text();
    if (route.request().method() === "POST" && response.ok()) claim = new URLSearchParams((JSON.parse(body) as { link: string }).link.split("?")[1]).get("s") ?? "";
    await route.fulfill({ response, body });
  });
  for (let i = 0; i < 5; i++) await page.locator(".time").click();
  await expect(page.locator(".qr")).toBeVisible();
  expect(claim).not.toBe("");
  await shot(page, info, "06_pairing_qr");

  expect(await pairLikeAPhone(claim)).toBe(200);
  await expect(page.getByText("C'est fait.")).toBeVisible();
  await shot(page, info, "07_pairing_done");
  // Back home after a few seconds: the clock again, and the discreet line in the panel.
  await expect(page.locator(".time")).toBeVisible({ timeout: 10_000 });
  await expect(page.locator(".new-phone")).toHaveText(/Nouveau téléphone relié \(\d+ en tout\)/);
  await shot(page, info, "08_new_phone_line");
  await expectAccessible(page);
});
