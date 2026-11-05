# WS06 File Change Manifest

## Preserved from the supplied WS05 baseline
- `circuitsim-engine/**`
- `circuitsim-cli/**`
- `examples/**`
- `scripts/**`
- `docs/WS05-implementation-handoff.md`
- `docs/WS05-implementation-notes.md`
- `docs/verification-traceability.md`

These paths were extracted directly from `CircuitSim-WS05-initial-implementation.zip` before WS06 files were added. The parent `pom.xml` was extended with the API and WAR modules and dependency management; it is therefore a deliberate modification, not a byte-for-byte preserved file.

## Added
- `circuitsim-api/**`: Spring Boot application, MVC controller, error handler, CORS configuration, properties, and MockMvc tests. The executable JAR uses the `exec` classifier so the plain API JAR remains consumable by the WAR module.
- `circuitsim-api-war/**`: external-Tomcat WAR packaging module.
- `circuitsim-web/src/components/WorkbenchHeader.tsx`
- `circuitsim-web/src/components/NetlistEditor.tsx`
- `circuitsim-web/src/components/SimulationResults.tsx`
- `circuitsim-web/src/components/CircuitTopology.tsx`
- `circuitsim-web/src/lib/useSimulation.ts`
- `docs/WS06-api-contract.md`, `docs/WS06-progress-handoff.md`, `docs/WS06-file-change-manifest.md`

## Modified / integrated
- Root `pom.xml`: adds Spring Boot BOM/version and the two API modules; existing engine and CLI modules remain.
- `circuitsim-web/src/App.tsx`: now composes dedicated workbench components.
- `circuitsim-web/src/lib/api.ts`: uses same-origin `/api` by default, compatible with the Vite proxy.
- `circuitsim-web/vite.config.ts`: adds local API proxy configuration.
- Root `README.md`: documents integrated Release 1 + Release 2.

## Removed
- No Release 1 files are intentionally removed. The prior WS06 custom `HttpServer` and hand-written `Json` codec are not carried forward; they are replaced by Spring MVC/Jackson in the new `circuitsim-api` module.
