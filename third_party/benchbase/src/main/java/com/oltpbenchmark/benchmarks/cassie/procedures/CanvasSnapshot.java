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
 * StockLevel-analog: the read-heavy aggregate scan. Periodically recomputes a full canvas's
 * summary (stroke count, total point count, how many strokes have been beautified) - a read that
 * touches many rows per invocation but writes nothing, the same shape as StockLevel's
 * cross-item low-stock scan.
 */
public class CanvasSnapshot extends Procedure {

  public final SQLStmt StrokeSummary =
      new SQLStmt(
          "SELECT COUNT(*), SUM(s_point_count) FROM "
              + CassieConstants.TABLENAME_STROKE
              + " WHERE s_canvas_id = ? AND s_deleted = false");

  public final SQLStmt BeautifiedCount =
      new SQLStmt(
          "SELECT COUNT(*) FROM "
              + CassieConstants.TABLENAME_BEAUTIFIED_STROKE
              + " bs JOIN "
              + CassieConstants.TABLENAME_STROKE
              + " s ON bs.bs_stroke_id = s.s_id"
              + " WHERE s.s_canvas_id = ?");

  public void run(Connection conn, long canvasId) throws SQLException {
    try (PreparedStatement stmt = this.getPreparedStatement(conn, StrokeSummary, canvasId)) {
      try (ResultSet r = stmt.executeQuery()) {
        r.next();
      }
    }
    try (PreparedStatement stmt = this.getPreparedStatement(conn, BeautifiedCount, canvasId)) {
      try (ResultSet r = stmt.executeQuery()) {
        r.next();
      }
    }
  }
}
