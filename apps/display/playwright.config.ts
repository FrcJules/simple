import { defineConfig, devices } from "@playwright/test";

/**
 * End-to-end tests of the home display against a real hub (see docs/testing.md).
 * Both run on their own ports with throwaway tokens and data, so a dev hub on 8787 is not touched.
 */
export const HUB = "http://127.0.0.1:18787";
export const DISPLAY = "http://127.0.0.1:14173";
export const WRITER_TOKEN = "e2e-writer-token-0123456789";
export const HUB_TLS_PORT = 18788;
export const DISPLAY_TOKEN = "e2e-display-token-0123456789";

const hubEnv = [
  "PORT=18787",
  "SIMPLE_TLS_PORT=18788",
  "SIMPLE_HOST=simple-e2e.local", // only written in the QR code (must be a home-network name); the test pairs through 127.0.0.1
  "SIMPLE_DATA_DIR=test-results/hub-data",
  "SIMPLE_TLS_DIR=test-results/hub-data/tls",
  "SIMPLE_PHOTOS_DIR=test-results/hub-data/photos",
  `SIMPLE_PAIRING_TOKEN=${WRITER_TOKEN}`,
  `SIMPLE_DISPLAY_TOKEN=${DISPLAY_TOKEN}`,
  `SIMPLE_DISPLAY_ORIGIN=${DISPLAY}`,
].join(" ");

export default defineConfig({
  testDir: "e2e",
  fullyParallel: false, // one hub, shared event list: tests run in order
  workers: 1,
  retries: process.env.CI ? 1 : 0,
  reporter: [["list"], ["html", { open: "never" }]],
  use: { baseURL: DISPLAY, locale: "fr-FR", timezoneId: "Europe/Paris", trace: "retain-on-failure" },
  projects: [
    // The Pi screens Simple is installed on: a 1080p TV / monitor and the official 7"-10" panels.
    { name: "pi-1080p", use: { ...devices["Desktop Chrome"], viewport: { width: 1920, height: 1080 } } },
    { name: "pi-1280x800", use: { ...devices["Desktop Chrome"], viewport: { width: 1280, height: 800 } } },
  ],
  webServer: [
    {
      command: `env ${hubEnv} npx tsx ../hub/src/main.ts`,
      url: `${HUB}/health`,
      reuseExistingServer: false,
      timeout: 60_000,
    },
    {
      // The built page, with its Content-Security-Policy, as served on the Pi (not the dev server).
      command: `env VITE_HUB_URL=${HUB} VITE_DISPLAY_TOKEN=${DISPLAY_TOKEN} npx vite build --outDir test-results/dist && npx vite preview --outDir test-results/dist --host 127.0.0.1 --port 14173 --strictPort`,
      url: DISPLAY,
      reuseExistingServer: false,
      timeout: 120_000,
    },
  ],
});
