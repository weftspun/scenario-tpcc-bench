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

import com.oltpbenchmark.api.Loader;
import com.oltpbenchmark.api.LoaderThread;
import com.oltpbenchmark.catalog.Table;
import com.oltpbenchmark.util.SQLUtil;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

/**
 * AssetCdnBenchmark Loader: one asset per unit of scale factor, one initial version, one seed
 * edge-cache entry, and ENTITLEMENTS_PER_ASSET user entitlements each.
 */
public final class AssetCdnLoader extends Loader<AssetCdnBenchmark> {
  private final Table catalogAsset;
  private final Table catalogAssetVersion;
  private final Table catalogEdgeCacheEntry;
  private final Table catalogUserEntitlement;

  private final String sqlAsset;
  private final String sqlAssetVersion;
  private final String sqlEdgeCacheEntry;
  private final String sqlUserEntitlement;

  private final long numAssets;

  public AssetCdnLoader(AssetCdnBenchmark benchmark) {
    super(benchmark);

    this.catalogAsset = this.benchmark.getCatalog().getTable(AssetCdnConstants.TABLENAME_ASSET);
    this.catalogAssetVersion =
        this.benchmark.getCatalog().getTable(AssetCdnConstants.TABLENAME_ASSET_VERSION);
    this.catalogEdgeCacheEntry =
        this.benchmark.getCatalog().getTable(AssetCdnConstants.TABLENAME_EDGE_CACHE_ENTRY);
    this.catalogUserEntitlement =
        this.benchmark.getCatalog().getTable(AssetCdnConstants.TABLENAME_USER_ENTITLEMENT);

    this.sqlAsset = SQLUtil.getInsertSQL(this.catalogAsset, this.getDatabaseType());
    this.sqlAssetVersion = SQLUtil.getInsertSQL(this.catalogAssetVersion, this.getDatabaseType());
    this.sqlEdgeCacheEntry =
        SQLUtil.getInsertSQL(this.catalogEdgeCacheEntry, this.getDatabaseType());
    this.sqlUserEntitlement =
        SQLUtil.getInsertSQL(this.catalogUserEntitlement, this.getDatabaseType());

    this.numAssets = benchmark.numAssets;
  }

  @Override
  public List<LoaderThread> createLoaderThreads() {
    List<LoaderThread> threads = new ArrayList<>();
    for (long a = 0; a < this.numAssets; a++) {
      threads.add(new Generator(a));
    }
    return threads;
  }

  private class Generator extends LoaderThread {
    private final long assetId;

    public Generator(long assetId) {
      super(benchmark);
      this.assetId = assetId;
    }

    @Override
    public void load(Connection conn) throws SQLException {
      // Same reasoning as ZoneFabricLoader: keep each asset's load in its
      // own small transaction rather than one giant uncommitted batch.
      conn.setAutoCommit(false);

      String owner = "owner-" + (this.assetId % 500);
      String contentHash = "hash-" + this.assetId + "-v1";
      String storageUri = "s3://assets/" + this.assetId + "/v1";

      try (PreparedStatement stmt = conn.prepareStatement(AssetCdnLoader.this.sqlAsset)) {
        stmt.setLong(1, this.assetId);
        stmt.setString(2, owner);
        stmt.setInt(3, 1);
        stmt.setString(4, contentHash);
        stmt.setLong(5, AssetCdnConstants.DEFAULT_ASSET_SIZE_BYTES);
        stmt.setLong(6, 0L);
        stmt.setLong(7, 0L);
        stmt.executeUpdate();
      }

      try (PreparedStatement stmt = conn.prepareStatement(AssetCdnLoader.this.sqlAssetVersion)) {
        stmt.setLong(1, this.assetId);
        stmt.setInt(2, 1);
        stmt.setString(3, storageUri);
        stmt.setLong(4, AssetCdnConstants.DEFAULT_ASSET_SIZE_BYTES);
        stmt.setLong(5, 0L);
        stmt.executeUpdate();
      }

      try (PreparedStatement stmt = conn.prepareStatement(AssetCdnLoader.this.sqlEdgeCacheEntry)) {
        stmt.setInt(1, 0);
        stmt.setLong(2, this.assetId);
        stmt.setInt(3, 1);
        stmt.setLong(4, 0L);
        stmt.setLong(5, 0L);
        stmt.executeUpdate();
      }
      conn.commit();

      try (PreparedStatement stmt = conn.prepareStatement(AssetCdnLoader.this.sqlUserEntitlement)) {
        int batchSize = 0;
        for (int u = 0; u < AssetCdnConstants.ENTITLEMENTS_PER_ASSET; u++) {
          long userId = this.assetId * 1_000_000L + u;
          stmt.setLong(1, userId);
          stmt.setLong(2, this.assetId);
          stmt.setLong(3, 0L);
          stmt.addBatch();

          if (++batchSize >= workConf.getBatchSize()) {
            stmt.executeBatch();
            conn.commit();
            batchSize = 0;
          }
        }
        if (batchSize > 0) {
          stmt.executeBatch();
          conn.commit();
        }
      }
    }
  }
}
