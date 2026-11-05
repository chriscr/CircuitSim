# CircuitSim WS06 — REST API Contract

Base path: `/api`; JSON is UTF-8. The API is a thin Spring MVC adapter over `circuitsim-engine`. It contains no circuit equations or numerical methods.

## Endpoints

- `GET /api/health` → `{ "service":"circuitsim-api", "status":"UP", "engine":"circuitsim-engine" }`
- `GET /api/examples` → `{ "examples":[{ "id":"resistor|rc|diode", "name":"…", "netlist":"…" }] }`. Files are read from `CIRCUITSIM_EXAMPLES` (default `examples`); bundled classpath examples are the fallback for JAR/WAR deployments.
- `POST /api/simulations` accepts `{ "netlist":"…", "method":"TRAPEZOIDAL|BACKWARD_EULER" }`; method defaults to `TRAPEZOIDAL`. Netlist is required and limited to 200,000 characters.

Successful simulations return `status`, `successful`, `analysis`, `method`, `step`, `stop`, `iterations`, `nodes`, `components`, `nodeVoltages`, `componentCurrents`, `samples`, `diagnostics`, and `units`, matching the frontend TypeScript contract. Invalid JSON/requests use HTTP 400; parser validation errors return HTTP 422 with an `error` object; unsuccessful numerical runs return HTTP 422 with the normal simulation payload. Unexpected service errors do not expose stack traces.

## Deployment

- Executable Spring Boot JAR: `circuitsim-api/target/circuitsim-api-1.0.0-SNAPSHOT-exec.jar` (`java -jar ...`), embedded Tomcat.
- External Tomcat WAR: `circuitsim-api-war/target/circuitsim-api.war`; deploy to a compatible Tomcat 10.1 / Jakarta Servlet 6 container. The same `CircuitSimApiApplication` extends `SpringBootServletInitializer` and is shared by both modes.
- Port: `CIRCUITSIM_PORT` (default 8080). Example directory: `CIRCUITSIM_EXAMPLES` (default `examples`). Allowed browser origin: `circuitsim.cors-origin` (default `http://localhost:5173`).

This is a local portfolio application, not a production-hardened public service. Authentication, authorization, rate limiting, and deployment-specific security remain out of scope.
