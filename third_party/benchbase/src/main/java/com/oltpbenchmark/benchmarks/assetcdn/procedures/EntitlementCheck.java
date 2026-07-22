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
 * OrderStatus-analog: a simple read-only point lookup, no writes. Models the access-control check
 * a fetch path runs before serving an asset to a given user.
 */
public class EntitlementCheck extends Procedure {

  public final SQLStmt GetEntitlement =
      new SQLStmt(
          "SELECT granted_tick FROM "
              + AssetCdnConstants.TABLENAME_USER_ENTITLEMENT
              + " WHERE user_id = ? AND asset_id = ?");

  public boolean run(Connection conn, long userId, long assetId) throws SQLException {
    try (PreparedStatement stmt = this.getPreparedStatement(conn, GetEntitlement, userId, assetId)) {
      try (ResultSet r = stmt.executeQuery()) {
        return r.next();
      }
    }
  }
}
