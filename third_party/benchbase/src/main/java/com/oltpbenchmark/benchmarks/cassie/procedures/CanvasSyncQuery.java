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
 * OrderStatus-analog: a simple read-only point lookup, no writes. A subscriber checks how far
 * behind their last sync is versus the canvas's current activity level.
 */
public class CanvasSyncQuery extends Procedure {

  public final SQLStmt GetSubscriberSync =
      new SQLStmt(
          "SELECT cs_last_synced_tick FROM "
              + CassieConstants.TABLENAME_CANVAS_SUBSCRIBER
              + " WHERE cs_canvas_id = ? AND cs_user_id = ?");

  public final SQLStmt GetCanvasActivity =
      new SQLStmt(
          "SELECT c_active_sessions FROM "
              + CassieConstants.TABLENAME_CANVAS
              + " WHERE c_id = ?");

  public void run(Connection conn, long canvasId, long userId) throws SQLException {
    try (PreparedStatement stmt =
        this.getPreparedStatement(conn, GetSubscriberSync, canvasId, userId)) {
      try (ResultSet r = stmt.executeQuery()) {
        if (!r.next()) {
          throw new UserAbortException("Unknown subscriber " + userId + " for canvas " + canvasId);
        }
      }
    }
    try (PreparedStatement stmt = this.getPreparedStatement(conn, GetCanvasActivity, canvasId)) {
      try (ResultSet r = stmt.executeQuery()) {
        r.next();
      }
    }
  }
}
