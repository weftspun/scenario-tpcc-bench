#!/bin/bash
# Starts a single-node, insecure, in-memory CockroachDB instance for local
# TPC-C benchmarking, and creates the "benchbase" database BenchBase's
# cockroachdb profile expects by default.
#
# Single-node/in-memory is a deliberate choice for this harness: it's meant
# for apples-to-apples comparison against another single-process system on
# the same dev machine (see weftspun/mvsqlite's res/ci/tpcc-benchbase.sh),
# not for reproducing CockroachDB's own published multi-node numbers.
set -euo pipefail

NETWORK="${CRDB_NETWORK:-crdb-bench-net}"
NAME="${CRDB_CONTAINER_NAME:-crdb-bench}"
IMAGE="${CRDB_IMAGE:-docker.io/cockroachdb/cockroach:latest-v24.3}"

podman network create "$NETWORK" 2>/dev/null || true
podman rm -f "$NAME" 2>/dev/null || true
podman run -d --name "$NAME" --network "$NETWORK" "$IMAGE" \
  start-single-node --insecure --store=type=mem,size=0.25

echo "waiting for CockroachDB to accept SQL connections..."
for i in $(seq 1 30); do
  if podman exec "$NAME" ./cockroach sql --insecure --execute="SELECT 1" >/dev/null 2>&1; then
    break
  fi
  sleep 1
done

podman exec "$NAME" ./cockroach sql --insecure --execute="CREATE DATABASE IF NOT EXISTS benchbase;"
echo "CockroachDB ready: container=$NAME network=$NETWORK db=benchbase"
