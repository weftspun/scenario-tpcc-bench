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
import java.util.ArrayList;
import java.util.List;

/**
 * NewOrder-analog: the most frequent, heaviest transaction. A player casts a spell; the effect fans
 * out to every entity within ghost-range in the caster's zone, exactly like a player movement
 * update does today (see README.md) - except the effect itself must be durably logged (combat log /
 * anti-cheat / replay), which is why this is a database transaction rather than the in-memory
 * pub/sub the 60Hz movement stream uses.
 */
public class CastSpell extends Procedure {

  public final SQLStmt GetCaster =
      new SQLStmt(
          "SELECT e_zone_id, e_x, e_y FROM "
              + ZoneFabricConstants.TABLENAME_ENTITY
              + " WHERE e_id = ?");

  public final SQLStmt GetNearbyEntities =
      new SQLStmt(
          "SELECT e_id FROM "
              + ZoneFabricConstants.TABLENAME_ENTITY
              + " WHERE e_zone_id = ? AND e_x BETWEEN ? AND ? AND e_y BETWEEN ? AND ? AND e_id != ?");

  public final SQLStmt InsertEffect =
      new SQLStmt(
          "INSERT INTO "
              + ZoneFabricConstants.TABLENAME_EFFECT_ENTITY
              + " (ef_id, ef_caster_id, ef_zone_id, ef_kind, ef_magnitude, ef_duration_ticks, ef_created_tick)"
              + " VALUES (?, ?, ?, ?, ?, ?, ?)");

  public final SQLStmt InsertFanoutTarget =
      new SQLStmt(
          "INSERT INTO "
              + ZoneFabricConstants.TABLENAME_FANOUT_TARGET
              + " (ft_effect_id, ft_target_entity_id, ft_distance) VALUES (?, ?, ?)");

  public void run(
      Connection conn, long effectId, long casterId, String kind, double magnitude, long tick)
      throws SQLException {
    long zoneId;
    double cx;
    double cy;

    try (PreparedStatement stmt = this.getPreparedStatement(conn, GetCaster, casterId)) {
      try (ResultSet r = stmt.executeQuery()) {
        if (!r.next()) {
          throw new UserAbortException("Unknown caster entity " + casterId);
        }
        zoneId = r.getLong(1);
        cx = r.getDouble(2);
        cy = r.getDouble(3);
      }
    }

    List<Long> targets = new ArrayList<>();
    double range = ZoneFabricConstants.GHOST_RANGE;
    try (PreparedStatement stmt =
        this.getPreparedStatement(
            conn,
            GetNearbyEntities,
            zoneId,
            cx - range,
            cx + range,
            cy - range,
            cy + range,
            casterId)) {
      try (ResultSet r = stmt.executeQuery()) {
        while (r.next()) {
          targets.add(r.getLong(1));
        }
      }
    }

    try (PreparedStatement stmt =
        this.getPreparedStatement(
            conn, InsertEffect, effectId, casterId, zoneId, kind, magnitude, 20, tick)) {
      stmt.executeUpdate();
    }

    try (PreparedStatement stmt = this.getPreparedStatement(conn, InsertFanoutTarget)) {
      for (long targetId : targets) {
        stmt.setLong(1, effectId);
        stmt.setLong(2, targetId);
        stmt.setDouble(3, range / 2);
        stmt.addBatch();
      }
      if (!targets.isEmpty()) {
        stmt.executeBatch();
      }
    }
  }
}
