import { defineConfig } from "tsup";

export default defineConfig({
  entry: ["src/main.ts"],
  format: ["esm"],
  target: "node22",
  clean: true,
  // The contracts workspace ships TypeScript sources, so it must be bundled.
  noExternal: ["@presence/contracts"],
});
