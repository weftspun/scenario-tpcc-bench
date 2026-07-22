# crdb-tpcc-bench

A TPC-C concurrency-scaling benchmark harness.

## What's here

- `third_party/benchbase` — BenchBase, vendored as a squashed `git subtree`,
  pinned to the **same commit** mvsqlite's harness uses, so both benchmarks
  run identical TPC-C transaction logic.
- `Dockerfile` — Debian bookworm + JDK 23 (Temurin) + Maven. BenchBase
  requires Java 23; nothing else CockroachDB-specific is needed (no native
  build, unlike mvsqlite's harness).
- `start-crdb.sh` — starts a single-node, insecure, in-memory CockroachDB
  container and creates the `benchbase` database.
- `build.sh` — builds BenchBase's `cockroachdb` profile.
- `sweep.sh` — loads N warehouses once, then runs a short execute-only pass
  at each of several terminal (concurrent client) counts, recording
  throughput/latency per level.

Single-node/in-memory CockroachDB is a deliberate choice: this harness is for
apples-to-apples comparison against another single-process system on the same
dev machine, not for reproducing CockroachDB's own published multi-node
numbers (their public benchmarks use 81-300 node clusters with dedicated
NVMe - see the comparison writeup this repo was created alongside).

## Usage

```
podman build -t localhost/crdb-tpcc-bench .
./start-crdb.sh
./build.sh
./sweep.sh
```

Override defaults via env vars: `WAREHOUSES`, `BATCHSIZE`, `TERMINALS_LIST`,
`RUN_SECONDS`, `CRDB_CONTAINER_NAME`, `CRDB_NETWORK`. Defaults
(`WAREHOUSES=5 BATCHSIZE=500 TERMINALS_LIST="1 4 8 16 32" RUN_SECONDS=15`)
match mvsqlite's sweep exactly.

Per-level results land in `target/benchbase-cockroachdb/results/*.summary.json`
(throughput, goodput, and full latency percentiles).
