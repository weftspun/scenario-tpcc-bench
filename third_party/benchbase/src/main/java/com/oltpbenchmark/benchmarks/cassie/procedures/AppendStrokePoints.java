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
import java.sql.SQLException;

/**
 * NewOrder-analog: the most frequent, heaviest transaction. One completed freehand stroke's worth
 * of input arrives as a batch (client-side input batching means individual points never reach
 * durable storage one at a time): insert the STROKE header row, then a variable-count batch of
 * STROKE_POINT child rows - the same "one parent, N child rows" shape that makes NewOrder/
 * ORDER_LINE representative of real insert-fanout workloads.
 */
public class AppendStrokePoints extends Procedure {

  public final SQLStmt InsertStroke =
      new SQLStmt(
          "INSERT INTO "
              + CassieConstants.TABLENAME_STROKE
              + " (s_id, s_canvas_id, s_author_user_id, s_point_count, s_created_tick, s_deleted)"
              + " VALUES (?, ?, ?, ?, ?, false)");

  public final SQLStmt InsertStrokePoint =
      new SQLStmt(
          "INSERT INTO "
              + CassieConstants.TABLENAME_STROKE_POINT
              + " (sp_stroke_id, sp_seq, sp_x, sp_y, sp_pressure, sp_tick) VALUES (?, ?, ?, ?, ?, ?)");

  public void run(
      Connection conn,
      long strokeId,
      long canvasId,
      long authorUserId,
      double[] xs,
      double[] ys,
      long tick)
      throws SQLException {
    try (PreparedStatement stmt =
        this.getPreparedStatement(
            conn, InsertStroke, strokeId, canvasId, authorUserId, xs.length, tick)) {
      stmt.executeUpdate();
    }

    try (PreparedStatement stmt = this.getPreparedStatement(conn, InsertStrokePoint)) {
      for (int i = 0; i < xs.length; i++) {
        stmt.setLong(1, strokeId);
        stmt.setInt(2, i);
        stmt.setDouble(3, xs[i]);
        stmt.setDouble(4, ys[i]);
        stmt.setDouble(5, 0.5);
        stmt.setLong(6, tick);
        stmt.addBatch();
      }
      stmt.executeBatch();
    }
  }
}
