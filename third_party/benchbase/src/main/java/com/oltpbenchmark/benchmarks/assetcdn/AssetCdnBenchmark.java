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

package com.oltpbenchmark.benchmarks.assetcdn;

import com.oltpbenchmark.WorkloadConfiguration;
import com.oltpbenchmark.api.BenchmarkModule;
import com.oltpbenchmark.api.Loader;
import com.oltpbenchmark.api.Worker;
import com.oltpbenchmark.benchmarks.assetcdn.procedures.AssetFetch;
import java.util.ArrayList;
import java.util.List;

/**
 * TPC-C-style benchmark modeling zone-backend's 3D asset CDN scaling problem: a catalog of
 * versioned assets, a fleet of edge caches serving fetches for them, and per-user entitlement
 * checks gating access. See README.md for the full TPC-C-to-domain mapping.
 */
public final class AssetCdnBenchmark extends BenchmarkModule {

  protected final long numAssets;

  public AssetCdnBenchmark(WorkloadConfiguration workConf) {
    super(workConf);
    this.numAssets = Math.max(1, Math.round(workConf.getScaleFactor()));
  }

  @Override
  protected List<Worker<? extends BenchmarkModule>> makeWorkersImpl() {
    List<Worker<? extends BenchmarkModule>> workers = new ArrayList<>();
    for (int i = 0; i < workConf.getTerminals(); ++i) {
      workers.add(new AssetCdnWorker(this, i));
    }
    return workers;
  }

  @Override
  protected Loader<AssetCdnBenchmark> makeLoaderImpl() {
    return new AssetCdnLoader(this);
  }

  @Override
  protected Package getProcedurePackageImpl() {
    return AssetFetch.class.getPackage();
  }
}
