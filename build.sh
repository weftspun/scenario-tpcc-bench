#!/bin/bash
# Builds BenchBase's cockroachdb profile. Run once (or whenever
# third_party/benchbase changes).
set -euo pipefail
REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$REPO_ROOT/third_party/benchbase"
mvn -q clean package -P cockroachdb -DskipTests
mkdir -p "$REPO_ROOT/target"
rm -rf "$REPO_ROOT/target/benchbase-cockroachdb"
tar xzf target/benchbase-cockroachdb.tgz -C "$REPO_ROOT/target"
echo "built: $REPO_ROOT/target/benchbase-cockroachdb"
