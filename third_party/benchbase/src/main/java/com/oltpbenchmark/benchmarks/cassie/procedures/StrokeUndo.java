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
 * Delivery-analog: the rare, deferred, write-heavy transaction. A user undoes their most recent
 * stroke: mark it deleted rather than physically removing it (undo history / redo support in a
 * real editor needs the row to still exist), the same "flag rather than delete" simplification
 * TPC-C's own Delivery makes for orders it can't yet fulfill.
 */
public class StrokeUndo extends Procedure {

  public final SQLStmt MarkStrokeDeleted =
      new SQLStmt(
          "UPDATE " + CassieConstants.TABLENAME_STROKE + " SET s_deleted = true WHERE s_id = ?");

  public boolean run(Connection conn, long strokeId) throws SQLException {
    try (PreparedStatement stmt = this.getPreparedStatement(conn, MarkStrokeDeleted, strokeId)) {
      return stmt.executeUpdate() > 0;
    }
  }
}
