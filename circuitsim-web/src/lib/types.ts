export type Component = {
  id: string;
  kind: string;
  n1: string;
  n2: string;
  value: number | null;
  unit: string;
  waveformType?: string | null;
  initialValue?: number | null;
};
export type Sample = {
  time: number;
  nodeVoltages: Record<string, number>;
  componentCurrents: Record<string, number>;
};
export type Diagnostic = {
  code: string;
  message: string;
  stage: string;
  line: number | null;
  time: number | null;
  iteration: number | null;
};
export type Simulation = {
  status: "SUCCESS" | "FAILED";
  successful: boolean;
  analysis: "DC" | "TRANSIENT";
  method: string;
  step: number | null;
  stop: number | null;
  iterations: number;
  nodes: string[];
  components: Component[];
  nodeVoltages: Record<string, number>;
  componentCurrents: Record<string, number>;
  samples: Sample[];
  diagnostics: Diagnostic[];
  units: { time: string; voltage: string; current: string };
};
export type Example = { id: string; name: string; netlist: string };
