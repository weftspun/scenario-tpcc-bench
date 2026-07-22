# crdb-tpcc-bench

A TPC-C concurrency-scaling benchmark harness.

## What's here

- `third_party/benchbase` — BenchBase, vendored as a squashed `git subtree`.
- `Dockerfile` — Debian bookworm + JDK 23 (Temurin) + Maven. BenchBase
  requires Java 23.
- `start-crdb.sh` — starts a single-node, insecure, in-memory CockroachDB
  container and creates the `benchbase` database.
- `build.sh` — builds BenchBase's `cockroachdb` profile.
- `sweep.sh` — loads N warehouses once, then runs a short execute-only pass
  at each of several terminal (concurrent client) counts, recording
  throughput/latency per level.

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
