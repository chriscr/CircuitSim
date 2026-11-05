# CircuitSim Web Workbench

React + TypeScript + Vite UI with CodeMirror netlist editing, Plotly waveform plots, DC/result tables, diagnostics, and a topology viewer derived from the API's parsed circuit model.

## Requirements and commands

- Node.js 20+ recommended; npm 10+.
- API available at `http://localhost:8080` (or set `VITE_CIRCUITSIM_API`).

```sh
npm install
npm run dev
npm run build
npm test
```

The UI defaults to `http://localhost:5173`. The API must allow that origin. Plotly's cursor inspection follows actual returned sample arrays; hovering/clicking selects the nearest sampled time for the readout. The topology view is a connectivity summary, not a graphical schematic editor. Current frontend test suite is not yet comprehensive; verify end-to-end behavior with the real API and engine.
