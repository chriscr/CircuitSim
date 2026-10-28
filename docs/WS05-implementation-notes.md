# WS05 implementation notes and verification status

**Artifact state:** source and tests created in `/mnt/data/CircuitSim`; not placed in a Git repository and no Git operations performed.

## Architecture delivered

- `circuitsim-engine`: immutable component/circuit records, analysis request, structured result/diagnostic records, bounded netlist parser, dense MNA assembly, Gaussian elimination with partial pivoting, Newton iteration for built-in diode, fixed-step transient simulation, accepted sample history.
- `circuitsim-cli`: argument parsing, text/JSON reports, transient CSV output, exit codes.
- Root Maven aggregator and per-module POMs target Java 25 and JUnit 5.

## Implementation decisions

1. **Dense solver:** internal dense Gaussian elimination with partial pivoting avoids adding an external solver before Java-25 compatibility and licensing have been reviewed. This is suitable only for the bounded small-circuit scope.
2. **Diode defaults:** retains the SDS provisional Is, ideality factor, and temperature. Exponential evaluation is bounded at ±40 thermal voltages. The current voltage clamp is documented, but a robust iteration-to-iteration PN-junction limiting algorithm and diode benchmark verification remain incomplete; this is a material completion item.
3. **Convergence:** update and assembled residual checks are present. However, the residual check currently evaluates the linearized assembly, not a fully independent KCL/constitutive residual of the original physical equations. It must be strengthened before the nonlinear solver can be accepted as meeting the original-residual requirement.
4. **DC source semantics:** constant source value and the first value of STEP/PULSE are used in DC analysis. This is an implementation choice, not a validated SDS decision.
5. **Tolerances:** provisional SDS values are retained as defaults only; no claim of validation is made.
6. **Failure semantics:** explicit failure status and diagnostics are returned. Earlier accepted transient samples are retained when a later trial fails; failed trial samples are not appended.

## Build/execution environment

- Available runtime: OpenJDK 21.0.12.
- Required target: Java 25 LTS.
- Maven is not installed in the available container.
- Both production modules compile with `javac` on Java 21. This is useful syntax/type-check evidence but is not the required Java 25 Maven build.
- JUnit tests are written but could not be executed because Maven/JUnit artifacts are unavailable in this environment.

## Executed smoke checks

- A 5 V source with a 1 kΩ resistor produced 5 V and 5 mA with SUCCESS.
- A floating resistor-only network produced FAILED / `SINGULAR_SYSTEM` and a nonzero CLI exit code.
- A PULSE netlist parsed and executed, and transient timestamps landed on identified events and the final stop time.
- An RC transient exposed a companion-source sign error during smoke testing; that sign was corrected. Further transient analytical verification remains required, and the current implementation should not be considered benchmark-qualified.

## Known risks / incomplete items

- Independent original-physical-residual calculation for nonlinear convergence is incomplete.
- Diode limiting is not yet demonstrated robust, and diode analytical benchmarks/failure cases remain unexecuted.
- Automated tests are authored but unexecuted; the RC transient should be reviewed against the analytic curve using both methods.
- Some component result currents and source boundary conventions need systematic test coverage.
- Structured parser source-location fields are not yet populated separately from message text.
- Contradictory ideal-source and singular cases currently depend on pivot-based failure detection; dedicated inconsistency classification is not implemented.
- CSV output writes NaN for a missing map entry, which should be changed to explicit blank/unavailable representation rather than a numeric-looking placeholder.
- JSON numeric values are finite in current result maps by validation, but serialization hardening and escaping tests are still needed.
- No claim of Release 1 completion or release readiness is made.
