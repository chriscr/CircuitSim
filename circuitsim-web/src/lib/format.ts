export function formatEngineeringValue(n: number | undefined): string {
  if (n === undefined || !Number.isFinite(n)) return "—";
  return Math.abs(n) >= 1e4 || (Math.abs(n) > 0 && Math.abs(n) < 1e-4)
    ? n.toExponential(4)
    : n.toPrecision(6);
}
export function isNetlistStale(
  submitted: string | null,
  current: string,
): boolean {
  return submitted !== null && submitted !== current;
}
