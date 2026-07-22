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
import java.sql.SQLException;

/**
 * Delivery-analog: the rare, deferred, batch-multi-row transaction. A real CDN evicts cache
 * entries asynchronously in the background, not transactionally - this is a lightweight
 * transactional stand-in for that maintenance sweep (same simplification TPC-C itself makes for
 * Delivery), removing every entry at a given edge node that hasn't been accessed recently.
 */
public class CacheEviction extends Procedure {

  public final SQLStmt EvictStaleEntries =
      new SQLStmt(
          "DELETE FROM "
              + AssetCdnConstants.TABLENAME_EDGE_CACHE_ENTRY
              + " WHERE edge_node_id = ? AND last_access_tick < ?");

  public int run(Connection conn, int edgeNodeId, long staleBeforeTick) throws SQLException {
    try (PreparedStatement stmt =
        this.getPreparedStatement(conn, EvictStaleEntries, edgeNodeId, staleBeforeTick)) {
      return stmt.executeUpdate();
    }
  }
}
