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

package com.oltpbenchmark.benchmarks.cassie.procedures;

import com.oltpbenchmark.api.Procedure;
import com.oltpbenchmark.api.SQLStmt;
import com.oltpbenchmark.benchmarks.cassie.CassieConstants;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

/**
 * Payment-analog: the read-then-cross-table-write transaction. Reads a completed stroke's raw
 * point count, inserts the beautification result, then bumps the owning canvas's activity
 * counter - two different tables touched atomically, the same shape as Payment's
 * warehouse+customer update.
 */
public class BeautifyStroke extends Procedure {

  public final SQLStmt GetStroke =
      new SQLStmt(
          "SELECT s_canvas_id, s_point_count FROM "
              + CassieConstants.TABLENAME_STROKE
              + " WHERE s_id = ?");

  public final SQLStmt InsertBeautified =
      new SQLStmt(
          "INSERT INTO "
              + CassieConstants.TABLENAME_BEAUTIFIED_STROKE
              + " (bs_stroke_id, bs_algorithm_version, bs_point_count, bs_compute_ms)"
              + " VALUES (?, ?, ?, ?)");

  public final SQLStmt BumpCanvasActivity =
      new SQLStmt(
          "UPDATE "
              + CassieConstants.TABLENAME_CANVAS
              + " SET c_active_sessions = c_active_sessions + 1 WHERE c_id = ?");

  public void run(Connection conn, long strokeId, int algorithmVersion, long computeMs)
      throws SQLException {
    long canvasId;
    int pointCount;
    try (PreparedStatement stmt = this.getPreparedStatement(conn, GetStroke, strokeId)) {
      try (ResultSet r = stmt.executeQuery()) {
        if (!r.next()) {
          throw new UserAbortException("Unknown stroke " + strokeId);
        }
        canvasId = r.getLong(1);
        pointCount = r.getInt(2);
      }
    }

    // Beautification typically reduces point count (smoothing/simplification) -
    // approximate that here rather than reproducing the real algorithm, which
    // is out of scope for a durable-storage benchmark (see README).
    int beautifiedPointCount = Math.max(2, pointCount / 2);

    try (PreparedStatement stmt =
        this.getPreparedStatement(
            conn, InsertBeautified, strokeId, algorithmVersion, beautifiedPointCount, computeMs)) {
      stmt.executeUpdate();
    }
    try (PreparedStatement stmt = this.getPreparedStatement(conn, BumpCanvasActivity, canvasId)) {
      stmt.executeUpdate();
    }
  }
}
