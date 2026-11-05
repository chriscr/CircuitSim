type Props = { busy: boolean; resultSuccessful: boolean | null };
export function WorkbenchHeader({ busy, resultSuccessful }: Props) {
  return (
    <header className="topbar">
      <div className="brand">
        <div className="brand-mark">∿</div>
        <div>
          <strong>CircuitSim</strong>
          <span>NUMERICAL WORKBENCH</span>
        </div>
      </div>
      <div className="header-meta">
        <span className="live-dot" />
        {busy
          ? "SIMULATION RUNNING"
          : resultSuccessful === null
            ? "READY"
            : resultSuccessful
              ? "RESULTS AVAILABLE"
              : "SOLVER FAILED"}
        <span className="version">R2</span>
      </div>
    </header>
  );
}
