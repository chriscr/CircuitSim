import type { Component, Simulation } from "../lib/types";
import { formatEngineeringValue as fmt } from "../lib/format";
function symbol(kind: string) {
  return (
    (
      { R: "▭", C: "‖", L: "∿", V: "◉", I: "↑", D: "▷|" } as Record<
        string,
        string
      >
    )[kind] ?? "◇"
  );
}
export function CircuitTopology({ result }: { result: Simulation | null }) {
  if (!result)
    return (
      <div className="panel topology-panel">
        <div className="panel-head">
          <div>
            <span className="section-kicker">03 / CONNECTIVITY</span>
            <h2>Circuit topology</h2>
          </div>
          <span className="pill">VIEW ONLY</span>
        </div>
        <div className="empty-state compact">
          Topology will appear after a simulation request.
        </div>
      </div>
    );
  const nodes = result.nodes.includes("0")
    ? result.nodes
    : [...result.nodes, "0"];
  const width = 700,
    height = 230;
  const xAt = (n: string) =>
    50 +
    (Math.max(0, nodes.indexOf(n)) * (width - 100)) /
      Math.max(1, nodes.length - 1);
  const components: Component[] = result.components;
  return (
    <div className="panel topology-panel">
      <div className="panel-head">
        <div>
          <span className="section-kicker">03 / CONNECTIVITY</span>
          <h2>Circuit topology</h2>
        </div>
        <span className="pill">VIEW ONLY</span>
      </div>
      <p className="subtle">
        Topology is derived from the parsed circuit model returned by the API.
      </p>
      <div className="topology">
        <svg
          className="topology-svg"
          viewBox={`0 0 ${width} ${height}`}
          role="img"
          aria-label="Circuit connectivity topology"
        >
          {components.map((c, i) => {
            const x1 = xAt(c.n1),
              x2 = xAt(c.n2),
              xc = (x1 + x2) / 2,
              y = 38 + (i % 3) * 38;
            return (
              <g key={c.id}>
                <path
                  d={`M ${x1} 178 L ${x1} ${y + 12} L ${xc - 27} ${y + 12} M ${xc + 27} ${y + 12} L ${x2} ${y + 12} L ${x2} 178`}
                  className="wire"
                />
                <rect
                  x={xc - 29}
                  y={y - 2}
                  width="58"
                  height="28"
                  rx="5"
                  className="component-symbol-box"
                />
                <text
                  x={xc}
                  y={y + 9}
                  className="component-symbol-text"
                  textAnchor="middle"
                >
                  {symbol(c.kind)}
                </text>
                <text
                  x={xc}
                  y={y - 8}
                  className="component-label"
                  textAnchor="middle"
                >
                  {c.id}
                </text>
                <text
                  x={xc}
                  y={y + 39}
                  className="component-value"
                  textAnchor="middle"
                >
                  {c.kind === "D"
                    ? "Shockley diode"
                    : `${fmt(c.value ?? undefined)} ${c.unit}`}
                </text>
              </g>
            );
          })}
          {nodes.map((n) => (
            <g key={n} className="node-symbol">
              <circle cx={xAt(n)} cy={178} r="6" />
              <text x={xAt(n)} y={200} textAnchor="middle">
                {n === "0" ? "0 / GND" : n}
              </text>
            </g>
          ))}
        </svg>
        <div className="topology-legend">
          <span>
            <i className="legend-wire" />
            Electrical connection
          </span>
          <span>
            <i className="legend-node" />
            Circuit node
          </span>
        </div>
      </div>
    </div>
  );
}
