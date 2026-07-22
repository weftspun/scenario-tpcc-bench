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

package com.oltpbenchmark.benchmarks.zonefabric.procedures;

import com.oltpbenchmark.api.Procedure;
import com.oltpbenchmark.api.SQLStmt;
import com.oltpbenchmark.benchmarks.zonefabric.ZoneFabricConstants;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

/**
 * OrderStatus-analog: a simple read-only point lookup, no writes. Models a reconnect/HUD/admin
 * status check of a single entity's last durably-persisted state.
 */
public class EntityStateQuery extends Procedure {

  public final SQLStmt GetEntity =
      new SQLStmt(
          "SELECT e_zone_id, e_x, e_y, e_vx, e_vy, e_rtt_ms, e_last_tick FROM "
              + ZoneFabricConstants.TABLENAME_ENTITY
              + " WHERE e_id = ?");

  public void run(Connection conn, long entityId) throws SQLException {
    try (PreparedStatement stmt = this.getPreparedStatement(conn, GetEntity, entityId)) {
      try (ResultSet r = stmt.executeQuery()) {
        if (!r.next()) {
          throw new UserAbortException("Unknown entity " + entityId);
        }
      }
    }
  }
}
