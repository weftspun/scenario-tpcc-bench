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

package com.oltpbenchmark.benchmarks.assetcdn.procedures;

import com.oltpbenchmark.api.Procedure;
import com.oltpbenchmark.api.SQLStmt;
import com.oltpbenchmark.benchmarks.assetcdn.AssetCdnConstants;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

/**
 * NewOrder-analog: the most frequent, heaviest transaction. An edge node looks up its cache entry
 * for an asset; a hit just bumps hit_count/last_access, a miss reads the asset's current version
 * from the catalog (simulating an origin fetch) and inserts a fresh cache entry, exactly the
 * lookup-then-maybe-insert shape that makes NewOrder representative of real fetch-or-populate
 * workloads.
 */
public class AssetFetch extends Procedure {

  public final SQLStmt GetCacheEntry =
      new SQLStmt(
          "SELECT version, hit_count FROM "
              + AssetCdnConstants.TABLENAME_EDGE_CACHE_ENTRY
              + " WHERE edge_node_id = ? AND asset_id = ?");

  public final SQLStmt TouchCacheEntry =
      new SQLStmt(
          "UPDATE "
              + AssetCdnConstants.TABLENAME_EDGE_CACHE_ENTRY
              + " SET hit_count = hit_count + 1, last_access_tick = ?"
              + " WHERE edge_node_id = ? AND asset_id = ?");

  public final SQLStmt GetCurrentVersion =
      new SQLStmt(
          "SELECT current_version FROM "
              + AssetCdnConstants.TABLENAME_ASSET
              + " WHERE asset_id = ?");

  public final SQLStmt InsertCacheEntry =
      new SQLStmt(
          "INSERT INTO "
              + AssetCdnConstants.TABLENAME_EDGE_CACHE_ENTRY
              + " (edge_node_id, asset_id, version, last_access_tick, hit_count)"
              + " VALUES (?, ?, ?, ?, 1)");

  public boolean run(Connection conn, int edgeNodeId, long assetId, long tick) throws SQLException {
    boolean hit;
    try (PreparedStatement stmt = this.getPreparedStatement(conn, GetCacheEntry, edgeNodeId, assetId)) {
      try (ResultSet r = stmt.executeQuery()) {
        hit = r.next();
      }
    }

    if (hit) {
      try (PreparedStatement stmt =
          this.getPreparedStatement(conn, TouchCacheEntry, tick, edgeNodeId, assetId)) {
        stmt.executeUpdate();
      }
      return true;
    }

    int currentVersion;
    try (PreparedStatement stmt = this.getPreparedStatement(conn, GetCurrentVersion, assetId)) {
      try (ResultSet r = stmt.executeQuery()) {
        if (!r.next()) {
          throw new UserAbortException("Unknown asset " + assetId);
        }
        currentVersion = r.getInt(1);
      }
    }

    try (PreparedStatement stmt =
        this.getPreparedStatement(
            conn, InsertCacheEntry, edgeNodeId, assetId, currentVersion, tick)) {
      stmt.executeUpdate();
    }
    return false;
  }
}
