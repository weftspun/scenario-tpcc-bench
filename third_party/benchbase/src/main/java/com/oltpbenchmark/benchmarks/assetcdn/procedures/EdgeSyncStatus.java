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
 * StockLevel-analog: the read-heavy aggregate scan. Queries how many edge nodes currently cache a
 * given asset and at what version, and their aggregate hit count - a read that touches many rows
 * per invocation but writes nothing, the same shape as StockLevel's cross-item low-stock scan.
 */
public class EdgeSyncStatus extends Procedure {

  public final SQLStmt EdgeCoverage =
      new SQLStmt(
          "SELECT COUNT(*), MIN(version), MAX(version), SUM(hit_count) FROM "
              + AssetCdnConstants.TABLENAME_EDGE_CACHE_ENTRY
              + " WHERE asset_id = ?");

  public void run(Connection conn, long assetId) throws SQLException {
    try (PreparedStatement stmt = this.getPreparedStatement(conn, EdgeCoverage, assetId)) {
      try (ResultSet r = stmt.executeQuery()) {
        r.next();
      }
    }
  }
}
