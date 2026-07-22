#!/bin/bash
# Runs a TPC-C concurrency sweep against CockroachDB: one load, then a
# short execute-only run at each of several terminal (concurrent client)
# counts, so throughput/latency can be plotted against concurrency.
#
# Mirrors weftspun/mvsqlite's res/ci/tpcc-benchbase.sh sweep exactly
# (same warehouse count, batch size, terminal levels, run duration) so the
# two are directly comparable - same benchmark tool, same config, only the
# database under test differs.
set -euo pipefail

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
BB_DIR="$REPO_ROOT/target/benchbase-cockroachdb"
CRDB_NAME="${CRDB_CONTAINER_NAME:-crdb-bench}"
CRDB_NETWORK="${CRDB_NETWORK:-crdb-bench-net}"

WAREHOUSES="${WAREHOUSES:-5}"
BATCHSIZE="${BATCHSIZE:-500}"
TERMINALS_LIST="${TERMINALS_LIST:-1 4 8 16 32}"
RUN_SECONDS="${RUN_SECONDS:-15}"

cd "$BB_DIR"
BASE=config/cockroachdb/sample_tpcc_config.xml
sed -e "s|<scalefactor>1</scalefactor>|<scalefactor>${WAREHOUSES}</scalefactor>|" \
    -e "s|<batchsize>128</batchsize>|<batchsize>${BATCHSIZE}</batchsize>|" \
    -e "s|localhost:26257|${CRDB_NAME}:26257|" \
    "$BASE" > config/cockroachdb/sweep_load.xml

RUN_ARGS=(--rm --network "$CRDB_NETWORK" -v "$BB_DIR":/bb:z -w /bb localhost/crdb-tpcc-bench)

echo "=== LOAD (${WAREHOUSES} warehouses) ==="
podman run "${RUN_ARGS[@]}" java -jar benchbase.jar -b tpcc \
  -c config/cockroachdb/sweep_load.xml --create=true --load=true --execute=false

for T in $TERMINALS_LIST; do
  sed -e "s|<terminals>1</terminals>|<terminals>${T}</terminals>|" \
      -e "s|<time>60</time>|<time>${RUN_SECONDS}</time>|" \
      config/cockroachdb/sweep_load.xml > "config/cockroachdb/sweep_t${T}.xml"
  echo "=== TERMINALS=$T ==="
  podman run "${RUN_ARGS[@]}" java -jar benchbase.jar -b tpcc \
    -c "config/cockroachdb/sweep_t${T}.xml" --create=false --load=false --execute=true \
    2>&1 | grep -E "requests/sec|state=ERROR" || true
done

echo "=== summaries ==="
ls -t results/*.summary.json | head -"$(echo "$TERMINALS_LIST" | wc -w)"
