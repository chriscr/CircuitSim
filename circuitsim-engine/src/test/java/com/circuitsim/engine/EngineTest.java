package com.circuitsim.engine;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static com.circuitsim.engine.CircuitSimEngine.*;

class EngineTest {
  private final NetlistParser parser = new NetlistParser();
  private final CircuitSimEngine engine = new CircuitSimEngine();
  @Test void simpleResistorOperatingPoint(){var p=parser.parse("V1 in 0 DC 5\nR1 in 0 1k\n.END");var r=engine.simulate(p.circuit(),p.request());assertTrue(r.successful(),r.diagnostics().toString());assertEquals(5,r.nodeVoltages().get("IN"),1e-12);assertEquals(.005,r.componentCurrents().get("R1"),1e-12);assertEquals(-.005,r.componentCurrents().get("V1"),1e-12);}
  @Test void dividerOperatingPoint(){var p=parser.parse("V1 in 0 DC 10\nR1 in out 1k\nR2 out 0 1k\n.END");var r=engine.simulate(p.circuit(),p.request());assertTrue(r.successful(),r.diagnostics().toString());assertEquals(5,r.nodeVoltages().get("OUT"),1e-10);assertEquals(.005,r.componentCurrents().get("R1"),1e-10);}
  @Test void floatingCircuitFailsExplicitly(){var p=parser.parse("R1 a b 1k\n.END");var r=engine.simulate(p.circuit(),p.request());assertEquals(Status.FAILED,r.status());assertTrue(r.diagnostics().stream().anyMatch(d->d.code().equals("SINGULAR_SYSTEM")));}
  @Test void parserRejectsUnsupportedDirective(){var e=assertThrows(SimulationException.class,()->parser.parse(".AC 10\n.END"));assertEquals("UNSUPPORTED_DIRECTIVE",e.code());}
  @Test void transientShortensToFinalTime(){var p=parser.parse("V1 in 0 PULSE(0 5 0 0 0 2m 5m)\nR1 in out 1k\nC1 out 0 1u\n.TRAN 100u 1m\n.END");var r=engine.simulate(p.circuit(),p.request());assertTrue(r.successful(),r.diagnostics().toString());assertEquals(1e-3,r.samples().get(r.samples().size()-1).time(),1e-15);assertEquals(5*(1-Math.exp(-1)),r.nodeVoltages().get("OUT"),0.25);}
  @Test void backwardEulerRcApproachesAnalyticalResponse(){var p=parser.parse("V1 in 0 PULSE(0 5 0 0 0 2m 5m)\nR1 in out 1k\nC1 out 0 1u\n.TRAN 100u 1m\n.END");var q=p.request();var r=engine.simulate(p.circuit(),new AnalysisRequest(q.kind(),q.step(),q.stop(),Method.BACKWARD_EULER));assertTrue(r.successful(),r.diagnostics().toString());assertEquals(5*(1-Math.exp(-1)),r.nodeVoltages().get("OUT"),0.3);}
  @Test void pulseGrammarParsesWithoutSpaceBeforeParenthesis(){var p=parser.parse("V1 in 0 PULSE(0 5 1m 100u 100u 2m 5m)\nR1 in 0 1k\n.TRAN 1m 10m\n.END");assertEquals(AnalysisKind.TRANSIENT,p.request().kind());}
}
