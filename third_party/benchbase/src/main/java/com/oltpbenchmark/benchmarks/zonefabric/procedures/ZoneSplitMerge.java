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
 * Delivery-analog: the rare, deferred, batch-multi-row transaction. Mirrors the real AV1-style
 * cost-driven maybeSplitZone: once a zone's population^2 cost crosses SPLIT_COST_THRESHOLD, half
 * its entities are reassigned to a freshly-created sibling zone in one batch UPDATE, exactly like
 * Delivery batch-processes multiple districts' oldest orders in one transaction. newZoneId is
 * caller-generated (not a DB sequence/autoincrement) so this stays portable across database
 * backends, same reasoning as TPC-C's own id-generation convention in this benchmark suite.
 */
public class ZoneSplitMerge extends Procedure {

  public final SQLStmt GetZoneCost =
      new SQLStmt(
          "SELECT z_population, z_cost, z_region, z_authority_cap, z_interest_cap FROM "
              + ZoneFabricConstants.TABLENAME_ZONE
              + " WHERE z_id = ?");

  public final SQLStmt InsertSiblingZone =
      new SQLStmt(
          "INSERT INTO "
              + ZoneFabricConstants.TABLENAME_ZONE
              + " (z_id, z_region, z_population, z_authority_cap, z_interest_cap, z_cost)"
              + " VALUES (?, ?, 0, ?, ?, 0)");

  public final SQLStmt ReassignHalfEntities =
      new SQLStmt(
          "UPDATE "
              + ZoneFabricConstants.TABLENAME_ENTITY
              + " SET e_zone_id = ? WHERE e_zone_id = ? AND (e_id % 2) = 0");

  public final SQLStmt UpdateSplitZones =
      new SQLStmt(
          "UPDATE "
              + ZoneFabricConstants.TABLENAME_ZONE
              + " SET z_population = ?, z_cost = ? WHERE z_id = ?");

  public boolean run(Connection conn, long zoneId, long newZoneId) throws SQLException {
    int population;
    double cost;
    String region;
    int authorityCap;
    int interestCap;

    try (PreparedStatement stmt = this.getPreparedStatement(conn, GetZoneCost, zoneId)) {
      try (ResultSet r = stmt.executeQuery()) {
        if (!r.next()) {
          throw new UserAbortException("Unknown zone " + zoneId);
        }
        population = r.getInt(1);
        cost = r.getDouble(2);
        region = r.getString(3);
        authorityCap = r.getInt(4);
        interestCap = r.getInt(5);
      }
    }

    if (cost < ZoneFabricConstants.SPLIT_COST_THRESHOLD) {
      // Nothing to do this round - matches Delivery's "no undelivered orders
      // for this district" no-op case rather than forcing an artificial split.
      return false;
    }

    try (PreparedStatement stmt =
        this.getPreparedStatement(
            conn, InsertSiblingZone, newZoneId, region, authorityCap, interestCap)) {
      stmt.executeUpdate();
    }

    int moved;
    try (PreparedStatement stmt =
        this.getPreparedStatement(conn, ReassignHalfEntities, newZoneId, zoneId)) {
      moved = stmt.executeUpdate();
    }

    int remaining = population - moved;
    try (PreparedStatement stmt =
        this.getPreparedStatement(
            conn, UpdateSplitZones, remaining, (double) remaining * remaining, zoneId)) {
      stmt.executeUpdate();
    }
    try (PreparedStatement stmt =
        this.getPreparedStatement(
            conn, UpdateSplitZones, moved, (double) moved * moved, newZoneId)) {
      stmt.executeUpdate();
    }

    return true;
  }
}
