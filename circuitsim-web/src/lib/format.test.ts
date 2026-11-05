import { describe, it, expect } from "vitest";
import { formatEngineeringValue, isNetlistStale } from "./format";
describe("engineering value formatting", () => {
  it("uses a dash for absent and nonfinite values", () => {
    expect(formatEngineeringValue(undefined)).toBe("—");
    expect(formatEngineeringValue(Number.NaN)).toBe("—");
  });
  it("uses readable significant digits and scientific notation for extremes", () => {
    expect(formatEngineeringValue(5)).toBe("5.00000");
    expect(formatEngineeringValue(0.0000001)).toBe("1.0000e-7");
  });
});
describe("submitted netlist freshness", () => {
  it("does not mark the first run as stale before a submission exists", () =>
    expect(isNetlistStale(null, "R1 a 0 1k")).toBe(false));
  it("marks results stale when editable text diverges from the submitted source", () => {
    expect(isNetlistStale("R1 a 0 1k", "R1 a 0 2k")).toBe(true);
    expect(isNetlistStale("R1 a 0 1k", "R1 a 0 1k")).toBe(false);
  });
});
