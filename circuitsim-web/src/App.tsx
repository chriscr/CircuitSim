import { WorkbenchHeader } from "./components/WorkbenchHeader";
import { NetlistEditor } from "./components/NetlistEditor";
import { SimulationResults } from "./components/SimulationResults";
import { CircuitTopology } from "./components/CircuitTopology";
import { useSimulation } from "./lib/useSimulation";
import { isNetlistStale } from "./lib/format";
export default function App() {
  const s = useSimulation();
  const stale = !!s.result && isNetlistStale(s.submittedNetlist, s.netlist);
  return (
    <div className="app-shell">
      <WorkbenchHeader
        busy={s.busy}
        resultSuccessful={s.result ? s.result.successful : null}
      />
      <main>
        <section className="intro">
          <div>
            <div className="eyebrow">
              ENGINEERING / TRANSIENT &amp; DC ANALYSIS
            </div>
            <h1>Simulation workbench</h1>
            <p>Define a circuit. Run the solver. Inspect the response.</p>
          </div>
          <button className="run-button" onClick={s.run} disabled={s.busy}>
            <span>{s.busy ? "◌" : "▶"}</span>
            {s.busy ? "Solving…" : "Run simulation"}
          </button>
        </section>
        {s.error && (
          <div className="error-banner">
            <strong>Simulation issue</strong>
            <span>{s.error}</span>
            <button onClick={() => s.setError("")} aria-label="Dismiss error">
              ×
            </button>
          </div>
        )}
        {stale && (
          <div className="stale-banner">
            Source edited — results below belong to the previously submitted
            netlist.
          </div>
        )}
        <div className="workspace">
          <NetlistEditor
            examples={s.examples}
            netlist={s.netlist}
            setNetlist={s.setNetlist}
            method={s.method}
            setMethod={s.setMethod}
            busy={s.busy}
            run={s.run}
          />
          <section className="analysis-column">
            <SimulationResults result={s.result} />
            <CircuitTopology result={s.result} />
          </section>
        </div>
        <footer>
          <span>CIRCUITSIM / RELEASE 2</span>
          <span>
            Numerical results reflect the engine response; not independently
            certified.
          </span>
        </footer>
      </main>
    </div>
  );
}
