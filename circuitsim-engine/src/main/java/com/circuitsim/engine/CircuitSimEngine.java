package com.circuitsim.engine;

import java.util.*;

/** Public, transport-independent CircuitSim engine API. All component values are SI. */
public final class CircuitSimEngine {
    public static final double IS = 1e-14, IDEALITY = 1.0, TEMPERATURE_K = 300.15;
    public static final double VT = 8.617333262145e-5 * TEMPERATURE_K;
    public static final int MAX_ITERATIONS = 100;
    public static final double RELTOL = 1e-3, VNTOL = 1e-6, ABSTOL = 1e-12;
    public CircuitSimEngine() {}

    public enum Kind { R, C, L, V, I, D }
    public enum AnalysisKind { DC, TRANSIENT }
    public enum Method { BACKWARD_EULER, TRAPEZOIDAL }
    public enum Status { SUCCESS, FAILED }
    public record Waveform(String type, double a, double b, double delay, double rise, double fall, double width, double period) {
        public double value(double t) {
            if (type.equals("DC")) return a;
            if (type.equals("STEP")) return t < delay ? a : b;
            if (t < delay) return a;
            double phase = (t-delay) % period;
            if (phase < rise && rise > 0) return a + (b-a)*phase/rise;
            if (phase < rise + width) return b;
            if (phase < rise + width + fall && fall > 0) return b + (a-b)*(phase-rise-width)/fall;
            return a;
        }
        public List<Double> events(double stop) {
            TreeSet<Double> e = new TreeSet<>(); if (type.equals("DC")) return List.of();
            if (type.equals("STEP")) { if(delay>0&&delay<stop)e.add(delay); return List.copyOf(e); }
            for (long k=0; ; k++) { double s=delay+k*period; if(s>stop)break; if(s>=0&&s>0)e.add(s); for(double q:new double[]{rise,rise+width,rise+width+fall}){double x=s+q;if(x>0&&x<stop)e.add(x);} if(k>1_000_000)throw new IllegalArgumentException("PULSE event count exceeds safety limit"); }
            return List.copyOf(e);
        }
    }
    public record Component(String id, Kind kind, String n1, String n2, double value, Waveform waveform) {}
    public record AnalysisRequest(AnalysisKind kind, double step, double stop, Method method) {
        public static AnalysisRequest dc(){return new AnalysisRequest(AnalysisKind.DC,0,0,Method.TRAPEZOIDAL);}
        public static AnalysisRequest transientAnalysis(double step,double stop,Method method){return new AnalysisRequest(AnalysisKind.TRANSIENT,step,stop,method);}
    }
    public record CircuitModel(List<Component> components, Set<String> nodes) {
        public CircuitModel { components=List.copyOf(components); nodes=Collections.unmodifiableSet(new TreeSet<>(nodes)); }
    }
    public record Diagnostic(String code,String message,String stage,Integer line,Double time,Integer iteration) {}
    public record Sample(double time, Map<String,Double> nodeVoltages, Map<String,Double> componentCurrents) {
        public Sample { nodeVoltages=Map.copyOf(nodeVoltages); componentCurrents=Map.copyOf(componentCurrents); }
    }
    public record SimulationResult(Status status, AnalysisKind analysis, Map<String,Double> nodeVoltages,
                                   Map<String,Double> componentCurrents, List<Sample> samples,
                                   List<Diagnostic> diagnostics, int iterations) {
        public SimulationResult { nodeVoltages=Map.copyOf(nodeVoltages); componentCurrents=Map.copyOf(componentCurrents); samples=List.copyOf(samples); diagnostics=List.copyOf(diagnostics); }
        public boolean successful(){return status==Status.SUCCESS;}
    }
    public static final class SimulationException extends RuntimeException {
        private final String code; public SimulationException(String code,String message){super(message);this.code=code;} public String code(){return code;}
    }

    public SimulationResult simulate(CircuitModel model, AnalysisRequest request) {
        List<Diagnostic> ds=new ArrayList<>();
        try {
            validate(model,request);
            Solver solver=new Solver(model);
            Solve dc=solver.solve(0,null,0,Method.TRAPEZOIDAL,null);
            if(!dc.ok) return failure(request,ds,dc.code,dc.message,dc.iterations,List.of());
            if(request.kind()==AnalysisKind.DC) return new SimulationResult(Status.SUCCESS,AnalysisKind.DC,dc.voltages,dc.currents,List.of(),ds,dc.iterations);
            List<Sample> samples=new ArrayList<>(); samples.add(new Sample(0,dc.voltages,dc.currents));
            State state=solver.stateFrom(dc); double t=0; int totalIterations=dc.iterations; TreeSet<Double> events=new TreeSet<>();
            for(Component c:model.components()) if(c.waveform()!=null) events.addAll(c.waveform().events(request.stop()));
            while(t < request.stop()-Math.max(1e-15,Math.ulp(request.stop())*4)) {
                double next=Math.min(request.stop(),t+request.step()); Double event=events.higher(t+Math.max(1e-15,Math.ulp(t)*4)); if(event!=null&&event<next)next=event;
                double h=next-t; if(!(h>0)||!Double.isFinite(h)) return failure(request,ds,"INVALID_TIMESTEP","Computed transient timestep is invalid",totalIterations,samples);
                Solve trial=solver.solve(next,state,h,request.method(),dc.voltages);
                totalIterations+=trial.iterations;
                if(!trial.ok) return failure(request,ds,trial.code,trial.message,totalIterations,samples,next);
                state=solver.advanceState(trial,next,h,request.method(),state); t=next;
                samples.add(new Sample(t,trial.voltages,trial.currents));
            }
            Sample last=samples.get(samples.size()-1);
            return new SimulationResult(Status.SUCCESS,AnalysisKind.TRANSIENT,last.nodeVoltages(),last.componentCurrents(),samples,ds,totalIterations);
        } catch(SimulationException e) { ds.add(new Diagnostic(e.code(),e.getMessage(),"VALIDATION",null,null,null)); return new SimulationResult(Status.FAILED,request.kind(),Map.of(),Map.of(),List.of(),ds,0); }
          catch(RuntimeException e) { ds.add(new Diagnostic("INTERNAL_NUMERICAL_ERROR",e.getMessage()==null?e.getClass().getSimpleName():e.getMessage(),"ENGINE",null,null,null)); return new SimulationResult(Status.FAILED,request.kind(),Map.of(),Map.of(),List.of(),ds,0); }
    }
    private SimulationResult failure(AnalysisRequest r,List<Diagnostic> ds,String code,String message,int it,List<Sample> samples){return failure(r,ds,code,message,it,samples,null);}
    private SimulationResult failure(AnalysisRequest r,List<Diagnostic> ds,String code,String message,int it,List<Sample> samples,Double time){ds.add(new Diagnostic(code,message,"SOLVE",null,time,it));return new SimulationResult(Status.FAILED,r.kind(),Map.of(),Map.of(),samples,ds,it);}
    private static void validate(CircuitModel m,AnalysisRequest r){
        if(m==null||m.components().isEmpty())throw new SimulationException("EMPTY_CIRCUIT","Circuit must contain at least one component");
        if(!m.nodes().contains("0"))throw new SimulationException("GROUND_REQUIRED","Circuit must reference ground node 0");
        if(r==null)throw new SimulationException("ANALYSIS_REQUIRED","Analysis request is required");
        if(r.kind()==AnalysisKind.TRANSIENT&&(!(r.step()>0)||!(r.stop()>0)||!Double.isFinite(r.step())||!Double.isFinite(r.stop())))throw new SimulationException("INVALID_TRAN_SETTINGS","Transient step and stop time must be positive and finite");
        Set<String> ids=new HashSet<>();
        for(Component c:m.components()) { if(c.id()==null||c.id().isBlank()||c.n1()==null||c.n2()==null)throw new SimulationException("INVALID_COMPONENT_IDENTITY","Component identity and terminals are required"); if(!m.nodes().contains(c.n1())||!m.nodes().contains(c.n2()))throw new SimulationException("UNKNOWN_NODE","Component "+c.id()+" references a node absent from the circuit model"); if((c.kind()==Kind.V||c.kind()==Kind.I)&&c.waveform()==null)throw new SimulationException("MISSING_WAVEFORM","Source "+c.id()+" requires a waveform"); if(!ids.add(c.id().toUpperCase(Locale.ROOT)))throw new SimulationException("DUPLICATE_COMPONENT","Duplicate component identifier: "+c.id()); if(c.n1().equals(c.n2())&&c.kind()!=Kind.V&&c.kind()!=Kind.I)throw new SimulationException("SAME_TERMINALS","Component "+c.id()+" has identical terminals"); if((c.kind()==Kind.R||c.kind()==Kind.C||c.kind()==Kind.L)&&(!(c.value()>0)||!Double.isFinite(c.value())))throw new SimulationException("INVALID_COMPONENT_VALUE",c.id()+" must have a positive finite value"); }
    }
    private static final class State { Map<String,Double> capV=new HashMap<>(),capI=new HashMap<>(),indI=new HashMap<>(),indV=new HashMap<>(); }
    private record Solve(boolean ok,String code,String message,Map<String,Double> voltages,Map<String,Double> currents,int iterations,double[] x) {}
    private static final class Solver {
        final CircuitModel model; final List<String> nodes; final Map<String,Integer> ni=new HashMap<>(),vi=new HashMap<>(); final int n;
        Solver(CircuitModel m){model=m;nodes=m.nodes().stream().filter(s->!s.equals("0")).sorted().toList();for(int i=0;i<nodes.size();i++)ni.put(nodes.get(i),i);int k=nodes.size();for(Component c:m.components())if(c.kind()==Kind.V||c.kind()==Kind.L)vi.put(c.id(),k++);n=k;}
        State stateFrom(Solve s){State z=new State();for(Component c:model.components()){double v=voltage(s.x,c.n1())-voltage(s.x,c.n2());double i=s.currents.getOrDefault(c.id(),0.0);if(c.kind()==Kind.C){z.capV.put(c.id(),v);z.capI.put(c.id(),0.0);}if(c.kind()==Kind.L){z.indI.put(c.id(),i);z.indV.put(c.id(),v);}}return z;}
        State advanceState(Solve s,double t,double h,Method method,State old){State z=new State();z.capV.putAll(old.capV);z.capI.putAll(old.capI);z.indI.putAll(old.indI);z.indV.putAll(old.indV);for(Component c:model.components()){double v=voltage(s.x,c.n1())-voltage(s.x,c.n2()),i=s.currents.getOrDefault(c.id(),0.0);if(c.kind()==Kind.C){z.capV.put(c.id(),v);z.capI.put(c.id(),i);}if(c.kind()==Kind.L){z.indI.put(c.id(),i);z.indV.put(c.id(),v);}}return z;}
        Solve solve(double t,State state,double h,Method method,Map<String,Double> initial){double[] guess=new double[n];if(initial!=null)for(int i=0;i<nodes.size();i++)guess[i]=initial.getOrDefault(nodes.get(i),0.0);if(state!=null)for(Component c:model.components())if(c.kind()==Kind.L&&vi.containsKey(c.id()))guess[vi.get(c.id())]=state.indI.getOrDefault(c.id(),0.0);
            boolean nonlinear=model.components().stream().anyMatch(c->c.kind()==Kind.D);int max=nonlinear?MAX_ITERATIONS:1;double[] x=guess;int iter=0;
            for(;iter<max;iter++){Assembly a=assemble(t,state,h,method,x);double[] next;try{next=linear(a.a,a.b);}catch(SimulationException e){return new Solve(false,e.code(),e.getMessage(),Map.of(),Map.of(),iter,x);}if(!finite(next))return new Solve(false,"NONFINITE_SOLUTION","Linear solve produced nonfinite values",Map.of(),Map.of(),iter,next);
                double[] previous=x; x=next;
                if(!nonlinear||converged(x,previous,t,state,h,method)){iter++;break;}
            }
            if(nonlinear&&iter>=max)return new Solve(false,"NONLINEAR_NONCONVERGENCE","Newton iteration limit reached without satisfying update and original-residual criteria",Map.of(),Map.of(),iter,x);
            if(nonlinear&&!physicalResidual(x,t,state,h,method))return new Solve(false,"PHYSICAL_RESIDUAL_FAILURE","Original circuit residual did not meet convergence criteria",Map.of(),Map.of(),iter,x);
            Map<String,Double> vv=new TreeMap<>(),cc=new TreeMap<>();for(String node:nodes)vv.put(node,x[ni.get(node)]);vv.put("0",0.0);
            for(Component c:model.components()){double v=voltage(x,c.n1())-voltage(x,c.n2()),i=0;switch(c.kind()){
                case R -> i=v/c.value(); case C -> {if(state!=null&&h>0){double g=method==Method.BACKWARD_EULER?c.value()/h:2*c.value()/h;double ie=method==Method.BACKWARD_EULER?-g*state.capV.getOrDefault(c.id(),0.0):-g*state.capV.getOrDefault(c.id(),0.0)-state.capI.getOrDefault(c.id(),0.0);i=g*v+ie;}}
                case L -> i=x[vi.get(c.id())]; case V -> i=x[vi.get(c.id())]; case I -> i=state==null?c.waveform().a():c.waveform().value(t); case D -> i=diodeCurrent(v); }
                if(!Double.isFinite(i))return new Solve(false,"NONFINITE_COMPONENT_CURRENT","Nonfinite current for "+c.id(),Map.of(),Map.of(),iter,x);cc.put(c.id(),i);
            }
            return new Solve(true,"","",vv,cc,iter,x);
        }
        private Assembly assemble(double t,State s,double h,Method m,double[] x){double[][] a=new double[n][n];double[] b=new double[n];for(Component c:model.components()){int p=idx(c.n1()),q=idx(c.n2());double v=voltage(x,c.n1())-voltage(x,c.n2());switch(c.kind()){
            case R -> conductance(a,p,q,1/c.value());
            case I -> current(b,p,q,s==null?c.waveform().a():c.waveform().value(t));
            case V -> {int k=vi.get(c.id());if(p>=0){a[p][k]+=1;a[k][p]+=1;}if(q>=0){a[q][k]-=1;a[k][q]-=1;}b[k]+=(s==null?c.waveform().a():c.waveform().value(t));}
            case L -> {int k=vi.get(c.id());if(p>=0){a[p][k]+=1;a[k][p]+=1;}if(q>=0){a[q][k]-=1;a[k][q]-=1;}if(s!=null){double g=m==Method.BACKWARD_EULER?h/c.value():h/(2*c.value());double ie=m==Method.BACKWARD_EULER?-s.indI.getOrDefault(c.id(),0.0):-s.indI.getOrDefault(c.id(),0.0)-g*s.indV.getOrDefault(c.id(),0.0);a[k][k]-=1/g;b[k]+=-ie/g;}}
            case C -> {if(s!=null){double g=m==Method.BACKWARD_EULER?c.value()/h:2*c.value()/h;double ie=m==Method.BACKWARD_EULER?-g*s.capV.getOrDefault(c.id(),0.0):-g*s.capV.getOrDefault(c.id(),0.0)-s.capI.getOrDefault(c.id(),0.0);conductance(a,p,q,g);current(b,p,q,ie);}}
            case D -> {double vl=limited(v,v),i=diodeCurrent(vl),g=diodeConductance(vl);conductance(a,p,q,g);current(b,p,q,i-g*vl);}
        }}return new Assembly(a,b);}
        private boolean converged(double[] x,double[] previous,double t,State s,double h,Method m){for(int i=0;i<x.length;i++){double tol=(i<nodes.size()?VNTOL:ABSTOL)+RELTOL*Math.max(Math.abs(x[i]),Math.abs(previous[i]));if(Math.abs(x[i]-previous[i])>tol)return false;}return physicalResidual(x,t,s,h,m);}
        private boolean physicalResidual(double[] x,double t,State s,double h,Method m){
            double[] kcl=new double[nodes.size()]; double currentScale=ABSTOL; double voltageScale=VNTOL;
            for(Component c:model.components()){
                double v=voltage(x,c.n1())-voltage(x,c.n2()),i=0;
                switch(c.kind()){
                    case R -> i=v/c.value();
                    case I -> i=s==null?c.waveform().a():c.waveform().value(t);
                    case V -> i=x[vi.get(c.id())];
                    case L -> i=x[vi.get(c.id())];
                    case C -> {if(s!=null&&h>0){double g=m==Method.BACKWARD_EULER?c.value()/h:2*c.value()/h;double ie=m==Method.BACKWARD_EULER?-g*s.capV.getOrDefault(c.id(),0.0):-g*s.capV.getOrDefault(c.id(),0.0)-s.capI.getOrDefault(c.id(),0.0);i=g*v+ie;}}
                    case D -> i=diodeCurrent(v);
                }
                if(!Double.isFinite(i))return false; currentScale=Math.max(currentScale,Math.abs(i));
                int p=idx(c.n1()),q=idx(c.n2());if(p>=0)kcl[p]+=i;if(q>=0)kcl[q]-=i;
                if(c.kind()==Kind.V){double target=s==null?c.waveform().a():c.waveform().value(t);double r=v-target;if(!Double.isFinite(r)||Math.abs(r)>VNTOL+RELTOL*Math.max(Math.abs(v),Math.abs(target)))return false;}
                if(c.kind()==Kind.L){double r;if(s==null)r=v;else{double g=m==Method.BACKWARD_EULER?h/c.value():h/(2*c.value());double ie=m==Method.BACKWARD_EULER?-s.indI.getOrDefault(c.id(),0.0):-s.indI.getOrDefault(c.id(),0.0)-g*s.indV.getOrDefault(c.id(),0.0);r=x[vi.get(c.id())]-(g*v+ie);}if(!Double.isFinite(r)||Math.abs(r)>ABSTOL+RELTOL*Math.max(Math.abs(i),1e-9))return false;}
            }
            for(double r:kcl)if(!Double.isFinite(r)||Math.abs(r)>ABSTOL+RELTOL*currentScale)return false;
            return true;
        }
        private static double limited(double v,double old){double vt=VT;if(!Double.isFinite(v))throw new SimulationException("NONFINITE_DIODE_VOLTAGE","Nonfinite diode voltage");double dv=v-old;double limit=0.5;if(dv>limit)return old+limit;if(dv< -limit)return old-limit;return Math.max(-40*vt,Math.min(40*vt,v));}
        private static double diodeCurrent(double v){double z=Math.max(-40,Math.min(40,v/(IDEALITY*VT)));return IS*Math.expm1(z);}
        private static double diodeConductance(double v){double z=Math.max(-40,Math.min(40,v/(IDEALITY*VT)));return IS*Math.exp(z)/(IDEALITY*VT);}
        private static double maxAbs(double[] x){double v=0;for(double q:x)v=Math.max(v,Math.abs(q));return v;}
        private static boolean finite(double[] x){for(double v:x)if(!Double.isFinite(v))return false;return true;}
        private int idx(String node){return node.equals("0")?-1:ni.get(node);} private double voltage(double[] x,String node){return node.equals("0")?0:x[ni.get(node)];}
        private void conductance(double[][] a,int p,int q,double g){if(p>=0)a[p][p]+=g;if(q>=0)a[q][q]+=g;if(p>=0&&q>=0){a[p][q]-=g;a[q][p]-=g;}}
        private void current(double[] b,int p,int q,double i){if(p>=0)b[p]-=i;if(q>=0)b[q]+=i;}
        private record Assembly(double[][] a,double[] b){}
        private static double[] linear(double[][] aa,double[] bb){int n=bb.length;double[][] a=new double[n][n];double[] b=bb.clone();for(int i=0;i<n;i++)a[i]=aa[i].clone();double norm=0;for(double[] row:a)for(double v:row)norm=Math.max(norm,Math.abs(v));double tol=Math.max(1e-15,norm*1e-13);
            for(int k=0;k<n;k++){int p=k;for(int i=k+1;i<n;i++)if(Math.abs(a[i][k])>Math.abs(a[p][k]))p=i;if(!Double.isFinite(a[p][k])||Math.abs(a[p][k])<=tol)throw new SimulationException("SINGULAR_SYSTEM","MNA matrix is singular or numerically rank deficient at pivot "+k);double[] tr=a[k];a[k]=a[p];a[p]=tr;double tv=b[k];b[k]=b[p];b[p]=tv;for(int i=k+1;i<n;i++){double f=a[i][k]/a[k][k];a[i][k]=0;for(int j=k+1;j<n;j++)a[i][j]-=f*a[k][j];b[i]-=f*b[k];}}
            double[] x=new double[n];for(int i=n-1;i>=0;i--){double z=b[i];for(int j=i+1;j<n;j++)z-=a[i][j]*x[j];x[i]=z/a[i][i];}if(!finite(x))throw new SimulationException("NONFINITE_SOLUTION","Linear solve generated nonfinite solution");return x;}
    }
}
