/*
 * Copyright 2020 by OLTPBenchmark Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */

package com.oltpbenchmark.benchmarks.cassie;

import com.oltpbenchmark.WorkloadConfiguration;
import com.oltpbenchmark.api.BenchmarkModule;
import com.oltpbenchmark.api.Loader;
import com.oltpbenchmark.api.Worker;
import com.oltpbenchmark.benchmarks.cassie.procedures.AppendStrokePoints;
import java.util.ArrayList;
import java.util.List;

/**
 * TPC-C-style benchmark modeling the scaling problem behind the "cassie" stroke-beautification
 * work (github.com/V-Sekai/cassie, em-yu.github.io/research/cassie): many concurrent in-world
 * sketch/annotation sessions, each appending freehand stroke input, beautifying completed
 * strokes, and syncing canvas state out to subscribers. See README.md for the full
 * TPC-C-to-domain mapping.
 */
public final class CassieBenchmark extends BenchmarkModule {

  protected final long numCanvases;

  public CassieBenchmark(WorkloadConfiguration workConf) {
    super(workConf);
    this.numCanvases = Math.max(1, Math.round(workConf.getScaleFactor()));
  }

  @Override
  protected List<Worker<? extends BenchmarkModule>> makeWorkersImpl() {
    List<Worker<? extends BenchmarkModule>> workers = new ArrayList<>();
    for (int i = 0; i < workConf.getTerminals(); ++i) {
      workers.add(new CassieWorker(this, i));
    }
    return workers;
  }

  @Override
  protected Loader<CassieBenchmark> makeLoaderImpl() {
    return new CassieLoader(this);
  }

  @Override
  protected Package getProcedurePackageImpl() {
    return AppendStrokePoints.class.getPackage();
  }
}
