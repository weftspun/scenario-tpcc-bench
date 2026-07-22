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

import com.oltpbenchmark.api.Procedure;
import com.oltpbenchmark.api.Procedure.UserAbortException;
import com.oltpbenchmark.api.TransactionType;
import com.oltpbenchmark.api.Worker;
import com.oltpbenchmark.benchmarks.assetcdn.procedures.*;
import com.oltpbenchmark.types.TransactionStatus;
import com.oltpbenchmark.util.RandomDistribution.DiscreteRNG;
import com.oltpbenchmark.util.RandomDistribution.Flat;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.concurrent.atomic.AtomicLong;

public final class AssetCdnWorker extends Worker<AssetCdnBenchmark> {

  private final AssetFetch procAssetFetch;
  private final AssetUpload procAssetUpload;
  private final EntitlementCheck procEntitlementCheck;
  private final CacheEviction procCacheEviction;
  private final EdgeSyncStatus procEdgeSyncStatus;

  private final DiscreteRNG assetRng;
  private final DiscreteRNG edgeNodeRng;
  private final long numAssets;

  // Ticks are a monotonic per-worker counter, not wall-clock time - only
  // relative ordering (for staleness/eviction comparisons) matters here.
  private static final AtomicLong nextTick = new AtomicLong(1);

  public AssetCdnWorker(AssetCdnBenchmark benchmarkModule, int id) {
    super(benchmarkModule, id);
    this.procAssetFetch = this.getProcedure(AssetFetch.class);
    this.procAssetUpload = this.getProcedure(AssetUpload.class);
    this.procEntitlementCheck = this.getProcedure(EntitlementCheck.class);
    this.procCacheEviction = this.getProcedure(CacheEviction.class);
    this.procEdgeSyncStatus = this.getProcedure(EdgeSyncStatus.class);

    this.numAssets = benchmarkModule.numAssets;
    this.assetRng = new Flat(rng(), 0, this.numAssets - 1);
    this.edgeNodeRng = new Flat(rng(), 0, AssetCdnConstants.EDGE_NODE_COUNT - 1);
  }

  private long randomAssetId() {
    return this.assetRng.nextLong();
  }

  private int randomEdgeNodeId() {
    return (int) this.edgeNodeRng.nextLong();
  }

  private long randomUserIdForAsset(long assetId) {
    long offset = (long) (rng().nextDouble() * AssetCdnConstants.ENTITLEMENTS_PER_ASSET);
    return assetId * 1_000_000L + offset;
  }

  @Override
  protected TransactionStatus executeWork(Connection conn, TransactionType txnType)
      throws UserAbortException, SQLException {
    Class<? extends Procedure> procClass = txnType.getProcedureClass();
    long tick = nextTick.getAndIncrement();

    if (procClass.equals(AssetFetch.class)) {
      this.procAssetFetch.run(conn, randomEdgeNodeId(), randomAssetId(), tick);

    } else if (procClass.equals(AssetUpload.class)) {
      long assetId = randomAssetId();
      this.procAssetUpload.run(conn, assetId, AssetCdnConstants.DEFAULT_ASSET_SIZE_BYTES, tick);

    } else if (procClass.equals(EntitlementCheck.class)) {
      long assetId = randomAssetId();
      long userId = randomUserIdForAsset(assetId);
      this.procEntitlementCheck.run(conn, userId, assetId);

    } else if (procClass.equals(CacheEviction.class)) {
      int edgeNodeId = randomEdgeNodeId();
      long staleBefore = Math.max(0, tick - AssetCdnConstants.CACHE_STALE_TICKS);
      this.procCacheEviction.run(conn, edgeNodeId, staleBefore);

    } else if (procClass.equals(EdgeSyncStatus.class)) {
      this.procEdgeSyncStatus.run(conn, randomAssetId());
    }

    return TransactionStatus.SUCCESS;
  }
}
