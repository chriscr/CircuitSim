import { useEffect, useMemo, useState } from "react";
import Plot from "react-plotly.js";
import type { Simulation } from "../lib/types";
import { formatEngineeringValue as fmt } from "../lib/format";
type Props = { result: Simulation | null };
export function SimulationResults({ result }: Props) {
  const [tab, setTab] = useState<"waveforms" | "dc" | "diagnostics">(
    "waveforms",
  );
  const [traces, setTraces] = useState<string[]>([]);
  const [cursor, setCursor] = useState<number | null>(null);
  const nodes = useMemo(
    () => result?.nodes.filter((n) => n !== "0") ?? [],
    [result],
  );
  const samples = result?.samples ?? [];
  useEffect(() => {
    setTraces(
      result?.analysis === "TRANSIENT" && nodes.length ? [nodes[0]] : [],
    );
    setCursor(samples.length ? samples[0].time : null);
  }, [result, nodes, samples.length]);
  const times = samples.map((s) => s.time);
  const components = result?.components ?? [];
  const options = [...nodes, ...components.map((c) => `${c.id}:I`)];
  const active = traces.filter((t) => options.includes(t));
  const values = (t: string) =>
    samples.map((s) =>
      t.endsWith(":I")
        ? s.componentCurrents[t.slice(0, -2)]
        : s.nodeVoltages[t],
    );
  const cursorSample =
    cursor === null || !samples.length
      ? undefined
      : samples.reduce(
          (best, s) =>
            Math.abs(s.time - cursor) < Math.abs(best.time - cursor) ? s : best,
          samples[0],
        );
  return (
    <div className="panel analysis-panel">
      <div className="panel-head analysis-head">
        <div>
          <span className="section-kicker">02 / ANALYSIS</span>
          <h2>Results explorer</h2>
        </div>
        {result && (
          <div className={`status-chip ${result.successful ? "ok" : "bad"}`}>
            <i />
            {result.successful ? "SOLVER SUCCESS" : "SOLVER FAILED"}
          </div>
        )}
      </div>
      <div className="tabs">
        <button
          className={tab === "waveforms" ? "active" : ""}
          onClick={() => setTab("waveforms")}
        >
          Waveforms <span>{samples.length}</span>
        </button>
        <button
          className={tab === "dc" ? "active" : ""}
          onClick={() => setTab("dc")}
        >
          DC operating point
        </button>
        <button
          className={tab === "diagnostics" ? "active" : ""}
          onClick={() => setTab("diagnostics")}
        >
          Diagnostics{" "}
          {result?.diagnostics.length ? `(${result.diagnostics.length})` : ""}
        </button>
      </div>
      {tab === "waveforms" && (
        <div className="chart-area">
          {samples.length > 1 ? (
            <>
              <div className="chart-toolbar">
                <div>
                  <b>Transient response</b>
                  <span>Voltage / current vs. time</span>
                </div>
                <div className="chart-units">TIME · s</div>
              </div>
              <div className="trace-list">
                {options.map((t) => (
                  <label
                    key={t}
                    className={active.includes(t) ? "trace selected" : "trace"}
                  >
                    <input
                      type="checkbox"
                      checked={active.includes(t)}
                      onChange={() =>
                        setTraces((old) =>
                          old.includes(t)
                            ? old.filter((x) => x !== t)
                            : [...old, t],
                        )
                      }
                    />
                    <span className="trace-glyph">
                      {t.endsWith(":I") ? "↗" : "∿"}
                    </span>
                    {t.endsWith(":I")
                      ? `${t.slice(0, -2)} current`
                      : `Node ${t}`}
                    <small>{t.endsWith(":I") ? "A" : "V"}</small>
                  </label>
                ))}
              </div>
              <Plot
                data={active.map((t) => ({
                  x: times,
                  y: values(t),
                  type: "scatter" as const,
                  mode: "lines" as const,
                  name: t,
                  line: { width: 2 },
                  hovertemplate:
                    "t = %{x:.6g} s<br>value = %{y:.6g}<extra>%{fullData.name}</extra>",
                }))}
                layout={{
                  autosize: true,
                  height: 300,
                  margin: { l: 58, r: 18, t: 12, b: 48 },
                  paper_bgcolor: "rgba(0,0,0,0)",
                  plot_bgcolor: "rgba(0,0,0,0)",
                  font: { color: "#a9b7c7", size: 11 },
                  xaxis: { title: { text: "Time (s)" }, gridcolor: "#253240" },
                  yaxis: {
                    title: { text: "Signal value" },
                    gridcolor: "#253240",
                  },
                  legend: { orientation: "h", y: -0.22 },
                  hovermode: "x unified",
                  uirevision: "circuit-sim",
                }}
                config={{ responsive: true, displaylogo: false }}
                style={{ width: "100%" }}
                onHover={(e) => {
                  const x = e.points?.[0]?.x;
                  if (typeof x === "number") setCursor(x);
                }}
              />
              <div className="cursor-inspector">
                <div>
                  <span>TIME CURSOR</span>
                  <strong>{cursor === null ? "—" : `${fmt(cursor)} s`}</strong>
                </div>
                {active.slice(0, 3).map((t) => (
                  <div key={t}>
                    <span>{t}</span>
                    <strong>
                      {cursorSample
                        ? fmt(
                            t.endsWith(":I")
                              ? cursorSample.componentCurrents[t.slice(0, -2)]
                              : cursorSample.nodeVoltages[t],
                          )
                        : "—"}{" "}
                      {t.endsWith(":I") ? "A" : "V"}
                    </strong>
                  </div>
                ))}
              </div>
              <div className="range-row">
                <span>0 s</span>
                <input
                  aria-label="Time cursor"
                  type="range"
                  min={times[0] ?? 0}
                  max={times[times.length - 1] ?? 1}
                  step={
                    (times[times.length - 1] ?? 1) /
                    Math.max(1, times.length - 1)
                  }
                  value={cursor ?? times[0] ?? 0}
                  onChange={(e) => setCursor(Number(e.target.value))}
                />
                <span>{fmt(times[times.length - 1])} s</span>
              </div>
            </>
          ) : (
            <div className="empty-state">
              <div className="empty-icon">⌁</div>
              <h3>No transient samples yet</h3>
              <p>
                Load an RC or pulse-driven circuit, then run the simulation to
                explore waveform data.
              </p>
            </div>
          )}
        </div>
      )}
      {tab === "dc" && (
        <div className="table-area">
          <h3>
            Node potentials <small>V relative to ground</small>
          </h3>
          {result ? (
            <table>
              <thead>
                <tr>
                  <th>NODE</th>
                  <th>VOLTAGE (V)</th>
                </tr>
              </thead>
              <tbody>
                {Object.entries(result.nodeVoltages).map(([n, v]) => (
                  <tr key={n}>
                    <td>{n}</td>
                    <td className="numeric">{fmt(v)}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          ) : (
            <div className="empty-state compact">
              Run a simulation to populate operating-point results.
            </div>
          )}
          <h3>
            Component currents <small>A</small>
          </h3>
          {result && (
            <table>
              <thead>
                <tr>
                  <th>COMPONENT</th>
                  <th>CURRENT (A)</th>
                </tr>
              </thead>
              <tbody>
                {Object.entries(result.componentCurrents).map(([id, v]) => (
                  <tr key={id}>
                    <td>{id}</td>
                    <td className="numeric">{fmt(v)}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          )}
        </div>
      )}
      {tab === "diagnostics" && (
        <div className="diagnostic-area">
          {result ? (
            <>
              <div className="metric-grid">
                <div>
                  <span>ANALYSIS</span>
                  <strong>{result.analysis}</strong>
                </div>
                <div>
                  <span>ITERATIONS</span>
                  <strong>{result.iterations}</strong>
                </div>
                <div>
                  <span>ACCEPTED SAMPLES</span>
                  <strong>{result.samples.length}</strong>
                </div>
                <div>
                  <span>INTEGRATOR</span>
                  <strong>{result.method.replace("_", " ")}</strong>
                </div>
              </div>
              {result.diagnostics.length ? (
                result.diagnostics.map((d, i) => (
                  <div className="diagnostic-item" key={i}>
                    <b>{d.code}</b>
                    <p>{d.message}</p>
                    <span>
                      {d.stage}
                      {d.time !== null ? ` · t=${fmt(d.time)} s` : ""}
                    </span>
                  </div>
                ))
              ) : (
                <div className="success-note">
                  No solver diagnostics were returned. This is not independent
                  numerical validation.
                </div>
              )}
            </>
          ) : (
            <div className="empty-state compact">
              Run a simulation to inspect status and diagnostics.
            </div>
          )}
        </div>
      )}
    </div>
  );
}
