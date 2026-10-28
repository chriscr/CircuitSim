#!/usr/bin/env sh
set -eu
ROOT=$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)
TMP=$(mktemp -d)
trap 'rm -rf "$TMP"' EXIT
mkdir -p "$TMP/engine" "$TMP/cli"
javac -d "$TMP/engine" $(find "$ROOT/circuitsim-engine/src/main/java" -name '*.java')
javac -cp "$TMP/engine" -d "$TMP/cli" $(find "$ROOT/circuitsim-cli/src/main/java" -name '*.java')
CP="$TMP/engine:$TMP/cli"
java -cp "$CP" com.circuitsim.cli.Main --format json "$ROOT/examples/resistor.cir" > "$TMP/resistor.json"
python3 - "$TMP/resistor.json" <<'PY'
import json,sys
r=json.load(open(sys.argv[1])); assert r['status']=='SUCCESS'; assert abs(r['nodeVoltages']['IN']-5)<1e-10; assert abs(r['componentCurrents']['R1']-.005)<1e-10
PY
java -cp "$CP" com.circuitsim.cli.Main --format json "$ROOT/examples/diode.cir" > "$TMP/diode.json"
python3 - "$TMP/diode.json" <<'PY'
import json,sys
r=json.load(open(sys.argv[1])); assert r['status']=='SUCCESS'; assert .5<r['nodeVoltages']['OUT']<.9; assert r['componentCurrents']['D1']>.004
PY
java -cp "$CP" com.circuitsim.cli.Main --format json --output "$TMP/results.csv" "$ROOT/examples/rc.cir" > "$TMP/rc.json"
python3 - "$TMP/rc.json" "$TMP/results.csv" <<'PY'
import csv,json,sys
r=json.load(open(sys.argv[1])); assert r['status']=='SUCCESS'; assert abs(r['samples'][-1]['time']-.005)<1e-12; assert len(r['samples'])==51; assert abs(r['samples'][10]['nodeVoltages']['OUT']-5*(1-__import__('math').exp(-1)))<.15
rows=list(csv.reader(open(sys.argv[2]))); assert len(rows)==52
PY
java -cp "$CP" com.circuitsim.cli.Main --format json --method be "$ROOT/examples/rc.cir" > "$TMP/rc-be.json"
python3 - "$TMP/rc-be.json" <<'PY'
import json,math,sys
r=json.load(open(sys.argv[1])); assert r['status']=='SUCCESS'; assert abs(r['samples'][10]['nodeVoltages']['OUT']-5*(1-math.exp(-1)))<.25
PY
cat > "$TMP/floating.cir" <<'CIR'
R1 a b 1k
.END
CIR
set +e
java -cp "$CP" com.circuitsim.cli.Main --format json "$TMP/floating.cir" > "$TMP/floating.json"
status=$?
set -e
[ "$status" -eq 1 ]
python3 - "$TMP/floating.json" <<'PY'
import json,sys
r=json.load(open(sys.argv[1])); assert r['status']=='FAILED'; assert r['diagnostics'][0]['code']=='SINGULAR_SYSTEM'
PY
printf 'PASS: javac compilation, resistor DC, diode DC, RC transient (BE and trapezoidal), JSON/CSV, singular failure and CLI exit code.\n'
