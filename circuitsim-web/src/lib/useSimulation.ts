import { useCallback, useEffect, useState } from "react";
import { getExamples, runSimulation } from "./api";
import type { Example, Simulation } from "./types";
const FALLBACK: Example[] = [
  {
    id: "resistor",
    name: "Resistive DC",
    netlist: "V1 in 0 DC 5\nR1 in 0 1k\n.END\n",
  },
  {
    id: "rc",
    name: "RC transient",
    netlist:
      "V1 in 0 PULSE (0 5 0 1u 1u 2m 4m)\nR1 in out 1k\nC1 out 0 1u\n.TRAN 100u 5m\n.END\n",
  },
  {
    id: "diode",
    name: "Diode DC",
    netlist: "V1 in 0 DC 0.7\nR1 in out 1k\nD1 out 0\n.END\n",
  },
];
export function useSimulation() {
  const [examples, setExamples] = useState<Example[]>(FALLBACK),
    [netlist, setNetlist] = useState(FALLBACK[1].netlist),
    [method, setMethod] = useState("TRAPEZOIDAL"),
    [result, setResult] = useState<Simulation | null>(null),
    [busy, setBusy] = useState(false),
    [error, setError] = useState(""),
    [submittedNetlist, setSubmittedNetlist] = useState<string | null>(null);
  useEffect(() => {
    getExamples()
      .then(setExamples)
      .catch(() => {
        /* API is optional until first run; fallback examples remain usable. */
      });
  }, []);
  const run = useCallback(async () => {
    setBusy(true);
    setError("");
    try {
      const next = await runSimulation(netlist, method);
      setResult(next);
      setSubmittedNetlist(netlist);
      if (!next.successful)
        setError(
          next.diagnostics.map((d) => d.message).join(" · ") ||
            "Simulation failed",
        );
    } catch (e) {
      setError(e instanceof Error ? e.message : "Unexpected simulation error");
    } finally {
      setBusy(false);
    }
  }, [netlist, method]);
  return {
    examples,
    netlist,
    setNetlist,
    method,
    setMethod,
    result,
    setResult,
    busy,
    error,
    setError,
    submittedNetlist,
    run,
  };
}
