# WS06 / Release 2 — Implementation Handoff

## Scope and preservation

Release 2 extends Release 1. This package was assembled by extracting the supplied WS05 baseline first, retaining `circuitsim-engine`, `circuitsim-cli`, the root Maven configuration lineage, examples, scripts, and WS05 documentation. WS06 adds `circuitsim-api`, `circuitsim-api-war`, and the React/TypeScript workbench. No Git commits or pushes were performed; WS07 owns remote integration.

## Architecture

- `circuitsim-engine`: transport- and framework-independent numerical engine.
- `circuitsim-cli`: Release 1 command-line interface, preserved.
- `circuitsim-api`: Spring Boot 3.5 / Spring Web REST adapter; Jackson handles JSON; embedded Tomcat in executable JAR.
- `circuitsim-api-war`: WAR packaging for deployment to external Tomcat; uses the same Spring Boot application class and controller code.
- `circuitsim-web`: React + TypeScript/Vite; workbench split into `WorkbenchHeader`, `NetlistEditor`, `SimulationResults`, `CircuitTopology`, and `useSimulation`.

## Build / run

Prerequisite: JDK 25 (the root Maven build targets Java 25), Maven 3.9+, Node.js 20+ and npm. From the project root:

```bash
mvn clean test package
java -jar circuitsim-api/target/circuitsim-api-1.0.0-SNAPSHOT-exec.jar
```

Then in another terminal:

```bash
cd circuitsim-web
npm install
npm test
npm run build
npm run dev
```

Vite proxies `/api` to `http://localhost:8080` by default; set `CIRCUITSIM_API_TARGET` to override. Set `VITE_CIRCUITSIM_API` only when using a direct API origin. Example netlists are bundled in the API artifact; set `CIRCUITSIM_EXAMPLES` to override the filesystem example directory.

External Tomcat artifact:

```bash
mvn -pl circuitsim-api-war -am package
```

Deploy `circuitsim-api-war/target/circuitsim-api.war` to a compatible Tomcat 10.1 installation. The WAR can also be run as a Spring Boot executable WAR where supported.

## Verification status

This regenerated package has **not been built or executed in this environment**. JDK 21 is available here, while the project targets Java 25; Maven is not available on PATH. The frontend build also has not been run. The source includes API MockMvc tests and retains Release 1 engine tests, but these tests are not reported as passing until run locally. The API/frontend behavior is designed to preserve the previous `/api/health`, `/api/examples`, and `/api/simulations` contract; verify with the documented tests before treating WS06 as closed.

## Known carry-forward limitations

The WS05 handoff's numerical verification gaps remain open, including diode-current discrepancy and broader nonlinear/convergence/transient verification. WS06 does not resolve or claim to resolve them. The frontend is a visualization workbench, not a certification of numerical accuracy.
