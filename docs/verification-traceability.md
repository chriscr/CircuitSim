# WS05 implementation-to-verification traceability

The following implementation-side identifiers are local labels, not replacements for any WS04 catalogue identifiers. The authoritative WS04 handoff was reviewed, but its case IDs are not consistently exposed in the retrieved excerpts; where an exact identifier could not be confirmed, this table uses requirement descriptions rather than inventing a WS04 ID.

| Local case | Coverage obligation | Test/evidence artifact | Current evidence |
|---|---|---|---|
| CS05-DC-01 | 5 V source + 1 kΩ resistor; voltage/current sign convention | `EngineTest.simpleResistorOperatingPoint`; smoke check | Executed standalone smoke check passed |
| CS05-DC-02 | Resistor divider | `EngineTest.dividerOperatingPoint`; smoke check | Executed standalone smoke check passed |
| CS05-FAIL-01 | Floating/underdetermined system must fail explicitly | `EngineTest.floatingCircuitFailsExplicitly`; smoke check | Executed standalone smoke check passed |
| CS05-PARSE-01 | Unsupported directives rejected | `EngineTest.parserRejectsUnsupportedDirective` | Authored; JUnit not executed |
| CS05-TR-01 | RC transient, final-time landing, analytical response | `EngineTest.transientShortensToFinalTime`; smoke check | Executed smoke check passed for RC analytical response and final time; JUnit not executed |
| CS05-TR-02 | Backward Euler transient response | `EngineTest.backwardEulerRcApproachesAnalyticalResponse` | Authored; JUnit not executed |
| CS05-PARSE-02 | PULSE syntax with no whitespace before `(` | `EngineTest.pulseGrammarParsesWithoutSpaceBeforeParenthesis`; CLI smoke run | Parser/CLI smoke run passed; JUnit not executed |
| CS05-DIODE-01 | Diode-resistor DC operating point | standalone smoke check | Executed; status SUCCESS and plausible forward operating point, but not an independent benchmark acceptance |
| CS05-CLI-01 | JSON report and process exit semantics | manual CLI runs | Executed for success and singular failure |
| CS05-CLI-02 | CSV export | manual CLI run planned/needed | Not yet executed |

## Required but not yet adequately verified

- Original nonlinear physical residual independent of the Newton linearization, especially under difficult cases.
- Robust iteration-to-iteration diode voltage limiting and exponential/derivative behavior at extreme voltages.
- Contradictory ideal voltage sources and classification of inconsistent versus singular systems.
- Inductor DC branch-current semantics and transient companion equations.
- Trapezoidal and backward-Euler RC/RL analytical error curves over multiple step sizes.
- Source-event alignment for STEP/PULSE ramps and coincident events; duplicate/near-coincident events.
- Failed transient trial-step history integrity and retained earlier samples on later failure.
- JSON escaping/schema assertions, CSV content assertions, and exact CLI exit-code tests.
- Nonfinite and underflow/overflow parsing edges; duplicate identifiers case-insensitively.
- Full Java 25 Maven build and automated JUnit suite.

A passing smoke check establishes only the behavior exercised by that check. It does not close the entire related requirement category.
