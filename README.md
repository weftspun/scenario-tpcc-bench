# crdb-tpcc-bench

A TPC-C concurrency-scaling benchmark harness.

## What's here

- `third_party/benchbase` — BenchBase, vendored as a squashed `git subtree`.
- `.github/workflows/tpcc-bench.yml` — CI sweep: starts a single-node,
  insecure, in-memory CockroachDB container, builds BenchBase's
  `cockroachdb` profile, loads N warehouses once, then runs a short
  execute-only pass at each of several terminal (concurrent client) counts,
  recording throughput/latency per level.

## Usage

The sweep runs on every push/PR via GitHub Actions
(`.github/workflows/tpcc-bench.yml`); see that workflow for the exact steps
to reproduce locally (start CockroachDB, build BenchBase's `cockroachdb`
profile, run `benchbase.jar` against `config/cockroachdb/sample_tpcc_config.xml`
at each terminal count).

Per-level results land in `results/*.summary.json` (throughput, goodput, and
full latency percentiles), uploaded as CI artifacts.
