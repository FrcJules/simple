import { describe, expect, it } from "vitest";
import { degrees, describeWeather } from "./weather";

describe("describeWeather", () => {
  it("gives a picture and plain words for the common codes", () => {
    expect(describeWeather(0)).toEqual({ icon: "☀️", label: "Grand soleil" });
    expect(describeWeather(3).label).toBe("Ciel couvert");
    expect(describeWeather(63).label).toBe("Pluie");
    expect(describeWeather(73).label).toBe("Neige");
    expect(describeWeather(95).label).toBe("Orage");
  });
  it("never fails on a code it does not know", () => expect(describeWeather(30).label).toBe("Temps variable"));
});

describe("degrees", () => {
  it("rounds and uses a real minus sign", () => {
    expect(degrees(18.4)).toBe("18°");
    expect(degrees(-2.6)).toBe("−3°");
    expect(degrees(-0.2)).toBe("0°");
  });
});
