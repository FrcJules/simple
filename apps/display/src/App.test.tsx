import { render, screen } from "@testing-library/react";
import { describe, expect, it, vi } from "vitest";

vi.stubGlobal("EventSource", class { onmessage = null; close() {} });

import { App } from "./App";

describe("App", () => {
  it("shows a greeting and no event card when nothing happened", () => {
    render(<App />);
    expect(screen.getByRole("heading", { level: 1 }).textContent).toMatch(/^Bon(jour|soir) /);
    expect(document.querySelector(".card")).toBeNull();
  });
});
