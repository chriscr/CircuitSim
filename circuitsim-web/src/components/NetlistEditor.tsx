import CodeMirror from "@uiw/react-codemirror";
import type { Example } from "../lib/types";
type Props = {
  examples: Example[];
  netlist: string;
  setNetlist: (v: string) => void;
  method: string;
  setMethod: (v: string) => void;
  busy: boolean;
  run: () => void;
};
export function NetlistEditor({
  examples,
  netlist,
  setNetlist,
  method,
  setMethod,
  busy,
  run,
}: Props) {
  return (
    <aside className="editor-panel panel">
      <div className="panel-head">
        <div>
          <span className="section-kicker">01 / INPUT</span>
          <h2>Circuit definition</h2>
        </div>
        <span className="pill">NETLIST</span>
      </div>
      <label className="field-label" htmlFor="example-select">
        EXAMPLE CIRCUITS
      </label>
      <select
        id="example-select"
        defaultValue=""
        onChange={(e) => {
          const ex = examples.find((x) => x.id === e.target.value);
          if (ex) setNetlist(ex.netlist);
        }}
      >
        <option value="">Load an example…</option>
        {examples.map((e) => (
          <option key={e.id} value={e.id}>
            {e.name}
          </option>
        ))}
      </select>
      <div className="editor-wrap">
        <CodeMirror
          value={netlist}
          height="310px"
          theme="dark"
          onChange={setNetlist}
          basicSetup={{
            lineNumbers: true,
            foldGutter: false,
            highlightActiveLine: true,
          }}
        />
      </div>
      <div className="editor-foot">
        <span>
          <i className="status-dot" /> Editable source
        </span>
        <span>{netlist.split("\n").length} lines</span>
      </div>
      <div className="control-row">
        <label htmlFor="method">INTEGRATION</label>
        <select
          id="method"
          value={method}
          onChange={(e) => setMethod(e.target.value)}
        >
          <option value="TRAPEZOIDAL">Trapezoidal</option>
          <option value="BACKWARD_EULER">Backward Euler</option>
        </select>
      </div>
      <div className="hint">
        <span>i</span>
        <p>
          Supported syntax is intentionally bounded. Use{" "}
          <code>.TRAN step stop</code> for transient analysis and finish each
          netlist with <code>.END</code>.
        </p>
      </div>
      <button className="run-button full" onClick={run} disabled={busy}>
        {busy ? "Solving…" : "▶ Run current netlist"}
      </button>
    </aside>
  );
}
