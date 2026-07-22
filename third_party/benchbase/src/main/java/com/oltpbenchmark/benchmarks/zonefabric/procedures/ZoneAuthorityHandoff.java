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
 * Payment-analog: the cross-partition write. A player crosses a zone boundary and authority for
 * their entity hands off from the old zone to the new one - hysteresis-gated in the real system,
 * simplified here to an unconditional handoff since the benchmark's job is to stress the
 * cross-partition write path, not reproduce the hysteresis threshold logic itself. Touches two
 * different ZONE rows atomically, the same "two different partitions in one transaction" shape that
 * makes TPC-C's Payment (warehouse+district+customer) representative of real cross-shard
 * contention.
 */
public class ZoneAuthorityHandoff extends Procedure {

  public final SQLStmt GetEntityZone =
      new SQLStmt(
          "SELECT e_zone_id FROM " + ZoneFabricConstants.TABLENAME_ENTITY + " WHERE e_id = ?");

  public final SQLStmt UpdateEntityZone =
      new SQLStmt(
          "UPDATE " + ZoneFabricConstants.TABLENAME_ENTITY + " SET e_zone_id = ? WHERE e_id = ?");

  public final SQLStmt DecrementZonePopulation =
      new SQLStmt(
          "UPDATE "
              + ZoneFabricConstants.TABLENAME_ZONE
              + " SET z_population = z_population - 1,"
              + "     z_cost = (z_population - 1) * (z_population - 1)"
              + " WHERE z_id = ?");

  public final SQLStmt IncrementZonePopulation =
      new SQLStmt(
          "UPDATE "
              + ZoneFabricConstants.TABLENAME_ZONE
              + " SET z_population = z_population + 1,"
              + "     z_cost = (z_population + 1) * (z_population + 1)"
              + " WHERE z_id = ?");

  public void run(Connection conn, long entityId, long newZoneId) throws SQLException {
    long oldZoneId;
    try (PreparedStatement stmt = this.getPreparedStatement(conn, GetEntityZone, entityId)) {
      try (ResultSet r = stmt.executeQuery()) {
        if (!r.next()) {
          throw new UserAbortException("Unknown entity " + entityId);
        }
        oldZoneId = r.getLong(1);
      }
    }

    if (oldZoneId == newZoneId) {
      return;
    }

    try (PreparedStatement stmt =
        this.getPreparedStatement(conn, UpdateEntityZone, newZoneId, entityId)) {
      stmt.executeUpdate();
    }
    try (PreparedStatement stmt =
        this.getPreparedStatement(conn, DecrementZonePopulation, oldZoneId)) {
      stmt.executeUpdate();
    }
    try (PreparedStatement stmt =
        this.getPreparedStatement(conn, IncrementZonePopulation, newZoneId)) {
      stmt.executeUpdate();
    }
  }
}
