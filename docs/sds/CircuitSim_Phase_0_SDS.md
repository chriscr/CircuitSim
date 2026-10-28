# CircuitSim Software Design Specification (SDS)

**Document status:** Approved by the project owner; repository placement pending  
**Workstream:** CircuitSim WS01 — Requirements & Architecture  
**Project:** CircuitSim  
**Target runtime:** Java 25 LTS  
**Last updated:** 2026-10-22

> **Status note:** The project owner approved this SDS on 2026-10-22. Repository placement is pending. Approval establishes the SDS baseline but does not, by itself, authorize implementation or release. Items marked *Open* or *Provisional* must not be treated as settled requirements.

## 1. Purpose and authority

This SDS defines the product scope, architecture, externally observable contracts, requirements, and acceptance criteria for CircuitSim. It is intended to be the durable design reference for implementation and verification workstreams.

The project owner retains authority over scope, priorities, and decisions that change approved requirements. Workstreams may make implementation-level decisions within their assigned responsibilities and the constraints in this document. They must not silently change approved behavior or reinterpret provisional choices as validated facts.

### 1.1 Decision-status vocabulary

- **Approved** — explicitly accepted by the project owner.
- **Provisional** — an agreed starting point that requires validation or later confirmation.
- **Open** — not yet decided; the named workstream should propose a resolution.
- **Deferred** — intentionally outside the current release scope.

## 2. Project goals and release scope

CircuitSim is a small, rigorously engineered electrical circuit simulator implemented in Java. The initial emphasis is on educational and engineering-scale circuits, transparent numerical behavior, clear diagnostics, and a modular architecture that can support a later API and frontend without duplicating simulation logic.

### 2.1 Release plan

**Release 1 — CLI simulator**

- Parse a bounded SPICE-inspired netlist subset.
- Construct and validate one canonical circuit model and analysis request.
- Solve DC operating points and transient analyses.
- Support the component set and numerical methods defined below.
- Report structured outcomes and diagnostics.
- Support text and JSON reporting and transient CSV export.

**Release 2 — REST API and React frontend**

- Add a REST API and React.js frontend.
- Reuse the same engine and canonical model as Release 1.
- Map API input into the same canonical model; do not create a parallel simulation path.
- Preserve engine result and failure semantics across the CLI and API boundaries.

### 2.2 Explicit exclusions

Unless separately approved, the following are out of scope:

- Full SPICE compatibility.
- Comprehensive MOSFET or semiconductor-device libraries.
- RF/electromagnetic simulation.
- Advanced parasitic extraction or model-order reduction.
- Distributed simulation.
- A database without a demonstrated need.
- A C++ port.
- A drag-and-drop schematic editor.
- Adaptive timestep control in Release 1.
- Arbitrary user-specified capacitor voltages or inductor currents as transient initial conditions.
- User-defined diode models and `.MODEL` cards.
- Unsupported analysis types or source waveforms.

### 2.3 Delivery and repository policy

- The project uses separate Maven modules for `circuitsim-engine` and `circuitsim-cli`; Release 2 adds `circuitsim-api` and `circuitsim-web`.
- The engine must not depend on CLI, HTTP, Spring Boot, or React.
- Local version control is permitted during development.
- Publish to GitHub only at the two agreed milestones: after the CLI release is complete and after the API/frontend release is complete. Do not push incremental development commits.
- The approved SDS is intended to be placed at `/docs/SDS/CircuitSim_Phase_0_SDS.md`; repository placement is pending.

### 2.4 Technology baseline

- Java 25 LTS.
- Maven.
- JUnit for automated testing.
- Dense linear algebra initially; sparse linear algebra is deferred.
- A mature dense linear algebra dependency must be selected after Java compatibility and license review.
- The exact dependency is **Open**.

## 3. Architectural principles

1. **One engine, one canonical model.** CLI and future API input must converge on the same model and engine.
2. **Separation of responsibilities.** Parsing, model validation, analysis coordination, MNA assembly, linear solving, nonlinear iteration, transient integration, result formatting, and output delivery are distinct responsibilities.
3. **Explicit outcomes.** Invalid input, numerical failure, non-convergence, infeasibility where applicable, and output-delivery failure must not be conflated.
4. **No silent substitutions.** Missing or unavailable results must not silently become zero.
5. **Strict bounded input.** Unsupported syntax is rejected rather than guessed or ignored.
6. **Verification-led numerical confidence.** Numerical defaults and acceptance tolerances are not considered validated until benchmark evidence supports them.
7. **Implementation flexibility within contracts.** Conceptual responsibilities do not require a one-to-one mapping to Java classes or packages.

## 4. Target module and responsibility boundaries

### 4.1 Modules

- `circuitsim-engine`: canonical model, input-independent analysis contracts, component behavior, numerical solvers, simulation results, and engine diagnostics.
- `circuitsim-cli`: command-line argument parsing, netlist file access, presentation formatting, CSV export, and process exit codes.
- `circuitsim-api` (Release 2): HTTP request/response mapping and API boundary behavior.
- `circuitsim-web` (Release 2): React frontend.

The engine must not perform CLI formatting or depend on transport-specific types. Output formatting and delivery are outside the numerical engine.

### 4.2 Conceptual analysis flow

```text
Netlist input
    |
    v
Parser ----------------------> source-located syntax diagnostics
    |
    v
Model construction/validation -> validation diagnostics
    |
    v
Canonical CircuitModel + AnalysisRequest
    |
    v
Analysis coordinator
    |
    +---- DC operating-point solve ---------+
    |                                       |
    +---- Transient integration loop -------+  (MNA system per solve/step)
                                            |
                                            v
                                      MNA assembly
                                            |
                                            v
                              Nonlinear iteration (where needed)
                                            |
                                            v
                                      Dense linear solver
                                            |
                                            v
                               Solve/update candidate state
                                            |
                         +------------------+------------------+
                         |                                     |
                 Iterate if needed                    Accept converged state
                                                               |
                                                   (transient: advance history
                                                    and continue next step)
                                                               |
                                                               v
                                              Structured simulation result
    |
    +---- CLI text/JSON presentation
    +---- transient CSV delivery
    +---- future REST response mapping
```

The diagram is conceptual. It does not prescribe exact class names or require every box to become a separate class.

## 5. Canonical model and contracts

### 5.1 `CircuitModel`

Represents validated, immutable circuit topology and component definitions:

- Nodes and stable node references/IDs.
- Component identities and types.
- Ordered terminal connections.
- Type-specific parameters normalized to SI units.
- Typed source waveform definitions.

The circuit model is immutable during simulation. Component definitions do not perform numerical solving.

### 5.2 `AnalysisRequest`

Represents the requested analysis and its settings independently from circuit topology. For Release 1, the supported analyses are DC operating-point analysis and transient analysis. A `.TRAN` directive is parsed into this request rather than stored as a circuit component.

### 5.3 `SimulationState`

Contains mutable numerical state during solving, such as voltage estimates, capacitor history, inductor currents, nonlinear iteration state, and transient history. It must not mutate the canonical circuit model.

### 5.4 Component representation

Use typed component representations rather than a generic parameter map or one class with many optional fields. The exact Java constructs and class names are implementation-level decisions.

Supported component types:

- `Resistor`
- `Capacitor`
- `Inductor`
- `VoltageSource`
- `CurrentSource`
- `Diode`

Each component is immutable and has a unique case-insensitive identifier, type, ordered terminals, and type-specific parameters.

### 5.5 Terminal and sign conventions

- R/C/L: terminals `n1`, `n2`; voltage is `V(n1) - V(n2)`; positive branch current flows from `n1` to `n2`.
- Voltage source: terminal 1 is positive, terminal 2 negative; `V(n1) - V(n2) = Vs`.
- Current source: positive current flows from terminal 1 to terminal 2.
- Diode: terminal 1 is anode, terminal 2 is cathode; `VD = V(n1) - V(n2)`.

Swapping R/C/L terminals reverses reported voltage/current signs but does not change ideal electrical behavior.

Zero resistance, capacitance, and inductance are rejected in Release 1; these values are not silently reinterpreted as shorts or opens. Voltage and current source DC values may be zero or signed.

### 5.6 Source representation

Sources store typed waveform definitions rather than unparsed waveform strings. A waveform evaluator is separate from MNA assembly. The transient solver uses waveform event information to align integration steps with known source events.

## 6. Release 1 netlist input specification

This is a bounded, CircuitSim-specific SPICE-inspired grammar, not a claim of full SPICE compatibility.

### 6.1 Component statements

The supported prefixes and component types are:

| Prefix | Component | Required data |
|---|---|---|
| `R` | Resistor | identifier, two nodes, resistance |
| `C` | Capacitor | identifier, two nodes, capacitance |
| `L` | Inductor | identifier, two nodes, inductance |
| `V` | Independent voltage source | identifier, two nodes, source definition |
| `I` | Independent current source | identifier, two nodes, source definition |
| `D` | Diode | identifier, anode, cathode |

Identifiers are case-insensitive; `R1` and `r1` refer to the same identifier. Duplicate identifiers, ignoring case, are rejected.

### 6.2 Nodes and ground

- Node names consist of letters, digits, and underscores, with no whitespace.
- Node names are case-insensitive.
- Nodes are created implicitly on first use.
- `0` is the sole ground identifier.
- **Approved:** at least one reference to node `0` is required during model validation.
- Ground presence does not guarantee solvability. Floating subcircuits, singular systems, and inconsistent ideal constraints must still be diagnosed during numerical analysis where practical.

### 6.3 Comments, blank lines, and terminator

- Blank lines are permitted.
- A full-line comment begins with `*`.
- Inline comments are not supported; a statement containing trailing comment text is rejected.
- Exactly one `.END` is required.
- No circuit statements may follow `.END`.
- Trailing blank lines and full-line comments after `.END` are permitted.
- Unsupported directives and malformed statements are rejected.

### 6.4 Directive and keyword casing/placement

- Directives and waveform keywords are case-insensitive.
- At most one `.TRAN` directive is allowed.
- `.TRAN` may appear anywhere before `.END`.
- `.TRAN` requires exactly two numeric parameters: nominal timestep and stop time.
- Unsupported analysis directives and extra tokens after required directive arguments are rejected.

### 6.5 Numeric syntax and engineering suffixes

Supported forms include integers, decimals, scientific notation, and the following engineering suffixes:

| Suffix | Multiplier |
|---|---:|
| `T` | `1e12` |
| `G` | `1e9` |
| `meg` | `1e6` |
| `k` | `1e3` |
| `m` | `1e-3` |
| `u` | `1e-6` |
| `n` | `1e-9` |
| `p` | `1e-12` |
| `f` | `1e-15` |

Suffix matching is case-insensitive. `meg` is the only mega suffix; `m` means milli. For example, `1Meg` means `1e6`, while `10m` means `0.01`.

Numeric values are single tokens. Embedded spaces, unit strings, and embedded engineering notation are rejected. Examples:

- Accepted: `1k`, `10m`, `1Meg`, `2.5e-3`
- Rejected: `1 k`, `1kOhm`, `4K7`, unknown suffixes

### 6.6 Numeric edge validation

- Reject NaN and positive/negative infinity.
- Reject conversion overflow to a non-finite value.
- Reject a nonzero literal that underflows to zero during parsing or conversion.
- Reject non-finite or physically invalid component parameters.
- Explicit zero is valid for signed/zero DC source values but invalid for R/C/L and positive-required analysis timings.
- Reject malformed numeric tokens and unknown suffixes; never silently reinterpret them.

The implementation must distinguish an explicitly written zero from a nonzero literal that becomes zero through underflow. Exact parsing strategy and edge-case tests are owned by WS02/WS04.

### 6.7 Source grammar

Sources require explicit, case-insensitive waveform keywords.

DC examples:

```text
V1 in 0 DC 5
I1 out 0 DC 2m
```

PULSE example:

```text
V1 in 0 PULSE(0 5 1m 100u 100u 2m 5m)
```

- `DC` requires exactly one numeric value.
- `PULSE` requires exactly seven numeric parameters: `V1`, `V2`, `TD`, `TR`, `TF`, `PW`, `PER`.
- The first two values use the source's units (volts for voltage sources, amperes for current sources); the remaining five are seconds.
- Parameters are whitespace-separated. Commas, missing values, extra values, unsupported waveforms, and bare numeric source values without `DC` are rejected.

PULSE timing constraints:

- `TD`, `TR`, and `TF` are nonnegative.
- `PW` and `PER` are positive.
- `TR + PW + TF <= PER`.
- Zero rise/fall time means an instantaneous transition.

Waveform behavior:

- Before delay, output is `V1`.
- At the pulse start, transition toward `V2` over `TR`.
- Hold `V2` for `PW`.
- Transition back toward `V1` over `TF`.
- Remain at `V1` until the next period.
- Cycle starts occur at `TD + k * PER`.
- Instantaneous transitions are right-continuous: the event-time value is the new value.
- Finite ramp start/end times are also integration boundaries.

These are CircuitSim-specific semantics.

### 6.8 Transient directive

`.TRAN TSTEP TSTOP` requires two positive finite values, for example:

```text
.TRAN 10u 10m
```

- Simulation begins at `t = 0`.
- `TSTEP` is the nominal timestep.
- Steps are shortened to land on known source events and the final stop time.
- `TSTART`, `TMAX`, and `UIC` are not supported in Release 1.
- Initial transient conditions come from a converged DC operating point only.
- Arbitrary explicit capacitor voltage or inductor current initial conditions are deferred.

### 6.9 Diode syntax

Approved syntax:

```text
D1 anode cathode
```

Release 1 uses the built-in Shockley model only. `.MODEL` cards, model names, and per-instance parameter overrides are not supported.

Proposed default parameters, subject to verification before being treated as validated constants:

- Saturation current `Is = 1e-14 A`
- Ideality factor `n = 1.0`
- Temperature `T = 300.15 K` (approximately 27 °C)

The model equation is:

`ID = Is * (exp(VD / (n * VT)) - 1)`

where `VT` is thermal voltage. The implementation must safely evaluate the exponential and derivative, use a documented voltage-limiting approach during nonlinear iteration, detect non-finite values, and verify the final solution against the original Shockley model and circuit residuals. Limiting intermediate values alone cannot establish convergence. Reverse breakdown, junction capacitance, and reverse recovery are excluded.

## 7. Parsing, model validation, and numerical execution boundaries

Three stages are distinct:

1. **Parsing:** grammar, malformed numeric forms, unsupported syntax, directive count/placement, and source locations.
2. **Model construction and validation:** identifiers, component arity, parameter validity, node references, ground requirement, and analysis settings.
3. **Numerical execution:** singular/inconsistent systems, nonlinear non-convergence, and other numerical failures.

Invalid input/model must not enter numerical execution. Where safe, report multiple diagnostics instead of stopping at the first error. The exact maximum diagnostic count is open. Do not promise static detection of every floating or ill-posed circuit.

Diagnostics should include a category/code, severity, human-readable message, source location when applicable, analysis stage, and progress context such as time, timestep, or iteration when relevant. Exact field names and the full code catalog remain open.

Example:

```text
NETLIST_SYNTAX_ERROR
line: 3
message: Resistor R1 requires two nodes and a resistance value.
```

## 8. Numerical analysis architecture and requirements

### 8.1 MNA

Use a shared Modified Nodal Analysis (MNA) assembly framework with separate DC and transient component contributions. Circuit topology and component identities are shared; analysis mode determines the component equations.

The MNA unknowns include non-ground node voltages and selected branch currents, especially ideal voltage-source currents. An illustrative linear system is:

```text
[ G   B ] [ v   ] = [ j ]
[ B?  0 ] [ i_V ]   [ e ]
```

Responsibilities remain distinct:

- MNA assembly builds equations.
- Linear solver solves the assembled linear system.
- Nonlinear iteration coordinates repeated assembly and linear solves.
- DC solver finds the operating point.
- Transient integrator advances the solution using history, timestep, and integration method.
- Analysis coordinator selects and orchestrates the requested analysis.

Component behavior:

- Resistor: conductance contribution.
- Independent sources: current injection or ideal voltage constraint.
- Capacitor: open circuit in DC steady state; dynamic contribution in transient analysis.
- Ideal inductor: short in DC steady state subject to consistency; dynamic contribution in transient analysis.
- Diode: nonlinear Shockley relation.

Singular, floating, or inconsistent systems should be diagnosed where practical, not silently solved.

### 8.2 DC operating point

The engine must solve a DC operating point for DC analysis and use a converged DC operating point to initialize transient analysis.

Acceptance criteria include:

- 5 V source and 1 kO resistor produce 5 mA.
- Series and parallel resistor networks produce expected results.
- Ideal voltage-source constraints are satisfied.
- Current-source behavior satisfies KCL.
- Capacitors are open in DC steady state.
- Ideal inductors are short in DC steady state, subject to consistency.
- Diode solutions satisfy the Shockley model and circuit equations.
- Singular or inconsistent systems report failure.
- Residual checks and explicit status/diagnostics are applied.

### 8.3 Transient integration

Release 1 supports:

- Backward Euler.
- Trapezoidal integration.
- Fixed nominal timestep with shortening at source events and final stop time.
- Initial state from a converged DC operating point.
- Retention of successful earlier samples when a later timestep fails, where practical.

Requirements:

- A timestep must not cross a known discontinuity without landing on the event time.
- Finite source ramp boundaries are integration boundaries.
- The final sample must occur at the stop time within representational precision.
- Sample times are strictly increasing and have no duplicates.
- A failed timestep stops the run; the overall run is unsuccessful even if earlier samples exist.
- RC charge/discharge benchmarks must agree with analytical references within approved, benchmark-established tolerances.
- Transient accuracy is distinct from nonlinear convergence: convergence at a timestep does not prove that the timestep captures the physical dynamics.

Adaptive timestep control is deferred.

### 8.4 Nonlinear convergence

Nonlinear solving uses a Newton–Raphson-style iteration. Both update convergence and original-equation residual convergence must pass. Small updates alone are insufficient.

Provisional defaults, subject to WS04 benchmark validation:

| Parameter | Provisional value |
|---|---:|
| Maximum nonlinear iterations per solve | 100 |
| `RELTOL` | `1e-3` |
| `VNTOL` | `1e-6 V` |
| `ABSTOL` | `1e-12 A` |

Residual checks are row-by-row with physically meaningful scaling, not a single global norm. Current-balance equations use current tolerances; voltage constraints use voltage tolerances.

Proposed forms, with exact scale definitions and thresholds still open:

- `|rI| <= ABSTOL + RELTOL * I_scale`
- `|rV| <= VNTOL + RELTOL * V_scale`

Residuals are evaluated against the original circuit equations at the candidate solution, not only the linearized equations.

Distinguish:

- Update convergence.
- Residual convergence.
- Nonlinear non-convergence.
- Linear-solve failure.
- Invalid/non-finite numerical state.
- Input/model validation failure.

If the iteration limit is reached, report non-convergence. Linear-solve failure and non-finite values are explicit numerical failures. During transient analysis, timestep failure stops the run and the overall result remains failed.

## 9. Simulation result contract

The result contract distinguishes four aspects:

1. **Simulation status:** whether the requested analysis completed, failed, or never started.
2. **Solution data:** node voltages, branch currents, and transient samples that are available.
3. **Diagnostics:** errors, warnings, numerical details, and progress context.
4. **Delivery status:** whether requested formats/files were successfully produced.

Working status concepts are `NOT_STARTED`, `RUNNING`, `COMPLETED`, and `FAILED`. Whether `RUNNING` is exposed in a final result or used only internally/progressively is open. Exact enum and serialization names are open.

- DC results contain the solved operating-point node voltages and branch currents.
- Transient results contain time-indexed samples.
- Time is in seconds, voltage in volts relative to ground, and branch current in amperes.
- Branch-current direction follows the component terminal/sign conventions in Section 5.5.
- Missing/unavailable values must not silently become zero.
- Diagnostics should carry category/code, severity, message, source location when applicable, analysis stage, and relevant progress information.
- A later transient failure should preserve earlier successful samples where practical and mark the overall run unsuccessful.
- Text and JSON must convey the same simulation status and diagnostic meaning.
- Output-delivery failure does not change the truth of the simulation result, but the overall CLI operation is unsuccessful when a requested output fails.
- The future REST response must represent these same outcomes without requiring engine semantic changes.

Exact JSON fields, diagnostic code names, and detailed schemas are open.

## 10. CLI requirements

### 10.1 Invocation and output modes

Illustrative invocation:

```text
circuitsim simulate examples/rc-transient.cir
```

- One netlist file per invocation.
- Analysis settings come from the netlist.
- Text is the default output format.
- `--format` supports `text`, `json`, or `both`.
- Transient samples can independently be exported, e.g. `--output results.csv`.
- One simulation execution may feed multiple output formats.
- Text and JSON must not be concatenated into the same stdout stream if that makes JSON unparsable.
- Exact routing for `--format both`, JSON file options/naming, and collision behavior are **Open**.
- Console output is concise by default. Users can select node voltages and component branch currents, or request all available variables; exact flags are **Open**.

### 10.2 CSV

- CSV is for transient samples.
- Include time in seconds and clear voltage/current column names.
- Preserve explicit units and stable variable identification.
- Exact schema, column naming, and branch-current availability conventions are **Open**.
- If a run fails after producing samples, export completed samples when practical and identify the export as partial; do not mask the simulation failure.

### 10.3 File safety

Recommended policy, to be confirmed in the CLI specification:

- Create an output file if it does not exist.
- Refuse to overwrite an existing file by default.
- An explicit `--overwrite` option permits replacement.
- If a parent directory does not exist, report an error rather than silently creating directories.
- Report write/export failures clearly.
- Exact flags and edge behavior remain open until the CLI contract is finalized.

### 10.4 Exit codes

Proposed scheme, subject to final CLI contract approval:

| Code | Meaning |
|---:|---|
| `0` | Requested simulation and output operations succeeded |
| `1` | Numerical simulation failure |
| `2` | CLI arguments or file access invalid |
| `3` | Parse/model validation failure |
| `4` | Internal application error |
| `5` | Simulation succeeded but requested output/export failed |

Precedence when more than one failure occurs is open. The contract must preserve the distinction between simulation status and delivery status. In text mode, normal report output goes to stdout and diagnostics to stderr. When JSON is the sole console format, stdout must contain one parseable JSON result; unexpected process-level errors must not corrupt it.

## 11. Acceptance criteria

### 11.1 Parser acceptance

- **PAR-01:** Support R, C, L, V, I, and D statements.
- **PAR-02:** Component identifiers are case-insensitive.
- **PAR-03:** Nodes are implicit; `0` is ground.
- **PAR-04:** Parse integer, decimal, scientific, and approved engineering-suffix forms.
- **PAR-05:** Support blank lines and full-line `*` comments; reject inline comments.
- **PAR-06:** Require exactly one `.END`; reject circuit statements after it.
- **PAR-07:** Map `.TRAN` to `AnalysisRequest`.
- **PAR-08:** Reject unknown prefixes/directives, malformed numeric forms, extra tokens, and unsupported syntax.
- **PAR-09:** Preserve source locations where available.
- **PAR-10:** Malformed netlists cannot be executed.
- **PAR-11:** Directives and waveform keywords are case-insensitive.
- **PAR-12:** At most one `.TRAN`, located anywhere before `.END`.
- **PAR-13:** Source statements require explicit `DC` or `PULSE`; exact parameter counts are enforced.
- **PAR-14:** Require at least one reference to ground node `0` during model validation.
- **PAR-15:** Diode syntax is `Dname anode cathode`, using the built-in model only.

### 11.2 Model-validation acceptance

- **MOD-01:** Reject duplicate component IDs ignoring case.
- **MOD-02:** Validate component arity and required parameters.
- **MOD-03:** Require positive finite R/C/L values.
- **MOD-04:** Validate DC/PULSE values and timing constraints.
- **MOD-05:** Use built-in Shockley diode model with valid parameters.
- **MOD-06:** Resolve terminal references to canonical nodes.
- **MOD-07:** Normalize numeric values to SI units.
- **MOD-08:** Require positive finite analysis settings.
- **MOD-09:** Keep `CircuitModel` separate from `AnalysisRequest`.
- **MOD-10:** Keep the validated circuit model immutable.
- **MOD-11:** Invalid models do not enter numerical execution.
- **MOD-12:** Produce structured diagnostics.
- **MOD-13:** Reject NaN, infinity, conversion overflow, nonzero underflow to zero, and unknown suffixes.
- **MOD-14:** Enforce pulse boundaries, including zero rise/fall and `TR + PW + TF <= PER`.
- **MOD-15:** Report multiple diagnostics where safe; invalid partial models cannot leak into the solver.

### 11.3 Numerical acceptance

**DC**

- **DC-01:** 5 V / 1 kO benchmark produces 5 mA.
- **DC-02:** Series/parallel resistor networks match references.
- **DC-03:** Ideal voltage-source constraints are satisfied.
- **DC-04:** Current-source behavior satisfies KCL.
- **DC-05:** Capacitors are open in DC steady state.
- **DC-06:** Ideal inductors are short in DC steady state, subject to consistency.
- **DC-07:** Diode solution satisfies Shockley and circuit equations.
- **DC-08:** Singular/inconsistent systems fail explicitly.
- **DC-09:** Residual checks are applied.
- **DC-10:** Outcomes and diagnostics are explicit.

**Transient**

- **TR-01:** Initialize from a converged DC operating point.
- **TR-02:** Implement Backward Euler equations.
- **TR-03:** Implement trapezoidal equations.
- **TR-04:** RC charge/discharge matches analytical benchmark within approved tolerance.
- **TR-05:** Use nominal timestep except where event/stop alignment requires shortening.
- **TR-06:** Do not cross known discontinuities without landing on the event time.
- **TR-07:** Include finite source-ramp boundaries.
- **TR-08:** Include final sample at stop time within representational precision.
- **TR-09:** Sample times strictly increase without duplicates.
- **TR-10:** Stop on failed timestep; retain earlier successful samples where practical.

**Numerical robustness**

- **NUM-01:** Iteration-limit exhaustion reports non-convergence.
- **NUM-02:** Both update and residual convergence are required.
- **NUM-03:** Residuals use original circuit equations.
- **NUM-04:** Linear-solve failures are explicit.
- **NUM-05:** Detect non-finite values.
- **NUM-06:** Safely evaluate diode exponential and verify final original-model residual.
- **NUM-07:** Distinguish failure categories.
- **NUM-08:** Include iteration/timestep progress in diagnostics where available.
- **NUM-09:** Preserve partial results where practical.
- **NUM-10:** A failed run is never marked successful.

### 11.4 Result and CLI acceptance

- **RES-01:** Distinguish simulation status, solution data, diagnostics, and delivery status.
- **RES-02:** Distinguish zero values from unavailable values.
- **RES-03:** Preserve partial transient data on later failure where practical.
- **RES-04:** Text and JSON communicate the same simulation outcome and diagnostic meaning.
- **RES-05:** Simulation success plus CSV failure preserves simulation success but makes the overall CLI operation unsuccessful.
- **CLI-01:** Support text, JSON, and both modes.
- **CLI-02:** Support independent transient CSV export.
- **CLI-03:** Allow selection of reported variables and an all-available-variables mode.
- **CLI-04:** Keep JSON parseable on simulation failure and include structured status/diagnostics/partial data.
- **CLI-05:** Keep process-level unexpected errors from corrupting JSON output.
- **CLI-06:** Apply a documented exit-code scheme and precedence.
- **CLI-07:** Apply documented overwrite and destination-path behavior.
- **CLI-08:** Text and JSON output use consistent units and status semantics.

## 12. Verification and benchmark strategy

WS04 owns detailed benchmark definitions and evidence. At minimum, benchmarks must cover:

- Exact resistive circuits.
- Series/parallel resistor networks.
- Ideal voltage and current constraints.
- RC and RL transients.
- Both integration methods over multiple timestep sizes.
- Pulse event boundaries and finite ramps.
- Forward- and reverse-biased diode cases.
- Singular, inconsistent, and difficult circuits.
- Controlled non-convergence and linear-solver failure.
- Non-finite and invalid-input handling.
- Partial transient result retention.
- CLI output and export failures.

Each benchmark should record its reference, error metric, acceptance threshold, method, and outcome. Exact numerical tolerances, residual scaling, and detailed reference values remain open until benchmark verification.

## 13. Open decisions and ownership

| Open item | Proposed owner / action |
|---|---|
| Dense linear algebra library and license/Java compatibility | WS02/WS03: select before linear solver implementation |
| Exact MNA and solver interfaces | WS02 + WS03: coordinate contracts |
| Exact residual scales and final numerical tolerances | WS03 proposes; WS04 validates with benchmarks; owner approves baseline changes |
| Diode default parameters and voltage-limiting method | WS03 proposes/implements; WS04 verifies |
| Exact result-status enum and JSON fields | WS01 defines semantic contract; WS05 aligns API mapping |
| Diagnostic codes, field names, and diagnostic-count policy | WS01 + WS02/WS03 + WS04 coordination |
| CSV schema and branch-current availability | WS01/WS03 define; WS04 tests |
| `--format both` routing and JSON file destination/naming | CLI contract, before CLI acceptance |
| Exact variable-selection flags | CLI contract |
| Exit-code precedence | CLI contract |
| Output overwrite flags and path behavior | CLI contract |
| Exact Java class names and package structure | Implementation workstreams, within approved module boundaries |
| Benchmark reference values and thresholds | WS04 |
| Whether `RUNNING` appears in final result object or only progress interfaces | WS01 result-contract decision |
| Maximum diagnostics per validation pass | WS02/WS04 proposal consistent with preserving useful errors |

## 14. Workstream handoff boundaries

### WS02 — Circuit Model & MNA

May define detailed model types, node indexing, component stamping contracts, parser implementation design, and MNA interfaces. Must preserve approved grammar, terminal conventions, validation boundaries, and module independence.

### WS03 — Numerical Simulation

May define solver/integrator interfaces, internal iteration details, linear algebra integration, state/history representation, and numerical safeguards. Must preserve approved result semantics, convergence requirements, failure distinctions, and provisional status of tolerances/default diode parameters.

### WS04 — Verification & Benchmarks

Owns reproducible benchmark circuits, reference calculations, error metrics, acceptance thresholds, and evidence. Must distinguish proposed tolerances from validated thresholds and report any result that suggests a baseline change.

### WS05 — REST API & React

Release 2. May plan request/response mapping and frontend behavior, but must reuse the canonical model and engine. It must not invent parallel simulation semantics. Detailed implementation depends on stable engine contracts.

### WS00 — Project Charter & Workstream Control

Owns project governance, coordination, durable project status, scope control, release coordination, and cross-workstream status. WS01 owns requirements/SDS content and architecture contracts.

These handoffs communicate readiness and boundaries; they are not, by themselves, authorization to modify the repository or begin implementation.

## 15. Release and WS01 closure criteria

WS01 may be considered ready for closure when:

1. The project owner approves this consolidated SDS after reviewing it.
2. Approved decisions are represented without contradiction.
3. Provisional numerical defaults are clearly labeled.
4. All open decisions have an owner and an appropriate resolution point.
5. Acceptance criteria are traceable to requirements.
6. WS02–WS05 handoffs preserve the approved contracts and exclusions.
7. The Markdown file is placed at the owner-approved repository path, after explicit authorization to create or modify the file.

**Current status:** approved by the project owner on 2026-10-22. Repository placement is pending; no repository file has been created or modified in this conversation.