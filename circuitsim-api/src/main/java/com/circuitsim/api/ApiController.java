package com.circuitsim.api;

import com.circuitsim.engine.CircuitSimEngine;
import com.circuitsim.engine.CircuitSimEngine.*;
import com.circuitsim.engine.NetlistParser;
import com.circuitsim.engine.NetlistParser.Parsed;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.nio.file.*;
import java.util.*;

@RestController
@RequestMapping("/api")
public class ApiController {
    private static final int MAX_NETLIST_CHARS = 200_000;
    private final CircuitSimEngine engine = new CircuitSimEngine();
    private final NetlistParser parser = new NetlistParser();
    private final Path examplesDirectory;

    public ApiController(@Value("${circuitsim.examples-dir:examples}") String examplesDirectory) {
        this.examplesDirectory = Path.of(examplesDirectory).toAbsolutePath().normalize();
    }

    @GetMapping("/health")
    public Map<String, Object> health() {
        return Map.of("service", "circuitsim-api", "status", "UP", "engine", "circuitsim-engine");
    }

    @GetMapping("/examples")
    public Map<String, Object> examples() {
        List<Map<String, String>> result = new ArrayList<>();
        for (String id : List.of("resistor", "rc", "diode")) {
            Path file = examplesDirectory.resolve(id + ".cir").normalize();
            if (!file.startsWith(examplesDirectory))
                continue;
            try {
                String netlist;
                if (Files.isRegularFile(file)) {
                    netlist = Files.readString(file);
                } else {
                    try (var stream = getClass().getClassLoader().getResourceAsStream("examples/" + id + ".cir")) {
                        if (stream == null)
                            continue;
                        netlist = new String(stream.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
                    }
                }
                String name = switch (id) {
                    case "resistor" -> "Resistive DC";
                    case "rc" -> "RC transient";
                    default -> "Diode DC";
                };
                result.add(Map.of("id", id, "name", name, "netlist", netlist));
            } catch (IOException ex) {
                throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Unable to read example circuits");
            }
        }
        return Map.of("examples", result);
    }

    @PostMapping("/simulations")
    public Object simulate(@RequestBody SimulationRequest body) {
        if (body == null || body.netlist() == null || body.netlist().isBlank())
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Field 'netlist' must be a non-empty string");
        if (body.netlist().length() > MAX_NETLIST_CHARS)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Netlist exceeds 200000 characters");
        String methodText = body.method() == null ? "TRAPEZOIDAL" : body.method().toUpperCase(Locale.ROOT);
        Method method = switch (methodText) {
            case "TRAPEZOIDAL", "TRAP" -> Method.TRAPEZOIDAL;
            case "BACKWARD_EULER", "BE" -> Method.BACKWARD_EULER;
            default -> throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Unsupported integration method: " + methodText);
        };
        final Parsed parsed;
        try {
            parsed = parser.parse(body.netlist());
        } catch (SimulationException ex) {
            return org.springframework.http.ResponseEntity.unprocessableEntity().body(Map.of("error",
                    Map.of("code", ex.code(), "message", safe(ex.getMessage()), "stage", "VALIDATION")));
        }
        AnalysisRequest request = parsed.request();
        if (request.kind() == AnalysisKind.TRANSIENT)
            request = new AnalysisRequest(request.kind(), request.step(), request.stop(), method);
        SimulationResult result = engine.simulate(parsed.circuit(), request);
        Map<String, Object> payload = serialize(parsed.circuit(), request, result);
        return result.successful() ? payload
                : org.springframework.http.ResponseEntity.unprocessableEntity().body(payload);
    }

    private Map<String, Object> serialize(CircuitModel model, AnalysisRequest request, SimulationResult result) {
        List<Map<String, Object>> components = new ArrayList<>();
        for (Component c : model.components()) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", c.id());
            m.put("kind", c.kind().name());
            m.put("n1", c.n1());
            m.put("n2", c.n2());
            m.put("value", (c.kind() == Kind.V || c.kind() == Kind.I) ? null : c.value());
            m.put("waveformType", c.waveform() == null ? null : c.waveform().type());
            m.put("initialValue", c.waveform() == null ? null : c.waveform().a());
            m.put("unit", switch (c.kind()) {
                case R -> "\u03A9";
                case C -> "F";
                case L -> "H";
                case V -> "V";
                case I -> "A";
                case D -> "model";
            });
            components.add(m);
        }
        List<Map<String, Object>> samples = new ArrayList<>();
        for (Sample sample : result.samples())
            samples.add(Map.of("time", sample.time(), "nodeVoltages", sample.nodeVoltages(), "componentCurrents",
                    sample.componentCurrents()));
        List<Map<String, Object>> diagnostics = new ArrayList<>();
        for (Diagnostic d : result.diagnostics()) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("code", d.code());
            m.put("message", d.message());
            m.put("stage", d.stage());
            m.put("line", d.line());
            m.put("time", d.time());
            m.put("iteration", d.iteration());
            diagnostics.add(m);
        }
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("status", result.status().name());
        out.put("successful", result.successful());
        out.put("analysis", result.analysis().name());
        out.put("method", request.method().name());
        out.put("step", request.kind() == AnalysisKind.TRANSIENT ? request.step() : null);
        out.put("stop", request.kind() == AnalysisKind.TRANSIENT ? request.stop() : null);
        out.put("iterations", result.iterations());
        out.put("nodes", model.nodes());
        out.put("components", components);
        out.put("nodeVoltages", result.nodeVoltages());
        out.put("componentCurrents", result.componentCurrents());
        out.put("samples", samples);
        out.put("diagnostics", diagnostics);
        out.put("units", Map.of("time", "s", "voltage", "V", "current", "A"));
        return out;
    }

    private static String safe(String value) {
        return value == null ? "Request could not be processed" : value;
    }

    public record SimulationRequest(String netlist, String method) {
    }
}
