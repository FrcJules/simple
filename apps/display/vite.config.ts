/// <reference types="vitest/config" />
import react from "@vitejs/plugin-react";
import { defineConfig, loadEnv, type Plugin } from "vite";

/**
 * The built page carries a Content-Security-Policy: it may only talk to itself and to the hub (VITE_HUB_URL), so nothing in the display
 * can call another site, whatever the code does in the future (ADR 0006). Only in the build: the dev server needs inline scripts.
 */
function contentSecurityPolicy(hubUrl: string): Plugin {
  const hub = new URL(hubUrl).origin;
  const policy = [
    "default-src 'self'",
    "script-src 'self'",
    "style-src 'self' 'unsafe-inline'", // the photo background is an inline style
    "img-src 'self' blob: data:",
    `connect-src 'self' ${hub}`,
    "object-src 'none'",
    "base-uri 'none'",
    "form-action 'none'",
  ].join("; ");
  return {
    name: "presence-csp",
    apply: "build",
    transformIndexHtml: () => [{ tag: "meta", attrs: { "http-equiv": "Content-Security-Policy", content: policy }, injectTo: "head-prepend" }],
  };
}

export default defineConfig(({ mode }) => {
  const env = loadEnv(mode, process.cwd(), "VITE_");
  return {
    plugins: [react(), contentSecurityPolicy(env.VITE_HUB_URL ?? "http://localhost:8787")],
    test: { environment: "jsdom" },
  };
});
