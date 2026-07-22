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

package com.oltpbenchmark.benchmarks.cassie;

import com.oltpbenchmark.api.Loader;
import com.oltpbenchmark.api.LoaderThread;
import com.oltpbenchmark.catalog.Table;
import com.oltpbenchmark.types.DatabaseType;
import com.oltpbenchmark.util.SQLUtil;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

/**
 * CassieBenchmark Loader: one canvas per unit of scale factor, SUBSCRIBERS_PER_CANVAS
 * subscribers, and STROKES_PER_CANVAS_SEED seed strokes each with POINTS_PER_SEED_STROKE points.
 */
public final class CassieLoader extends Loader<CassieBenchmark> {
  private final Table catalogCanvas;
  private final Table catalogStroke;
  private final Table catalogStrokePoint;
  private final Table catalogCanvasSubscriber;

  private final String sqlCanvas;
  private final String sqlStroke;
  private final String sqlStrokePoint;
  private final String sqlCanvasSubscriber;

  private final long numCanvases;

  public CassieLoader(CassieBenchmark benchmark) {
    super(benchmark);

    this.catalogCanvas = this.benchmark.getCatalog().getTable(CassieConstants.TABLENAME_CANVAS);
    this.catalogStroke = this.benchmark.getCatalog().getTable(CassieConstants.TABLENAME_STROKE);
    this.catalogStrokePoint =
        this.benchmark.getCatalog().getTable(CassieConstants.TABLENAME_STROKE_POINT);
    this.catalogCanvasSubscriber =
        this.benchmark.getCatalog().getTable(CassieConstants.TABLENAME_CANVAS_SUBSCRIBER);

    this.sqlCanvas = SQLUtil.getInsertSQL(this.catalogCanvas, this.getDatabaseType());
    this.sqlStroke = SQLUtil.getInsertSQL(this.catalogStroke, this.getDatabaseType());
    this.sqlStrokePoint = SQLUtil.getInsertSQL(this.catalogStrokePoint, this.getDatabaseType());
    this.sqlCanvasSubscriber =
        SQLUtil.getInsertSQL(this.catalogCanvasSubscriber, this.getDatabaseType());

    this.numCanvases = benchmark.numCanvases;
  }

  @Override
  public List<LoaderThread> createLoaderThreads() {
    List<LoaderThread> threads = new ArrayList<>();
    for (long c = 0; c < this.numCanvases; c++) {
      threads.add(new Generator(c));
    }
    return threads;
  }

  private class Generator extends LoaderThread {
    private final long canvasId;

    public Generator(long canvasId) {
      super(benchmark);
      this.canvasId = canvasId;
    }

    @Override
    public void load(Connection conn) throws SQLException {
      // Same reasoning as ZoneFabricLoader/AssetCdnLoader: small
      // per-canvas transactions, and explicit transaction control skipped
      // for targets (like FDB Relational) whose JDBC driver doesn't
      // support it.
      boolean explicitTransactions =
          CassieLoader.this.getDatabaseType() != DatabaseType.FDBRELATIONAL;

      if (explicitTransactions) {
        conn.setAutoCommit(false);
      }

      try (PreparedStatement stmt = conn.prepareStatement(CassieLoader.this.sqlCanvas)) {
        stmt.setLong(1, this.canvasId);
        stmt.setString(2, "owner-" + (this.canvasId % 500));
        stmt.setLong(3, 0L);
        stmt.setInt(4, 0);
        stmt.executeUpdate();
      }

      try (PreparedStatement stmt = conn.prepareStatement(CassieLoader.this.sqlCanvasSubscriber)) {
        int batchSize = 0;
        for (int s = 0; s < CassieConstants.SUBSCRIBERS_PER_CANVAS; s++) {
          long userId = this.canvasId * 1_000_000L + s;
          stmt.setLong(1, this.canvasId);
          stmt.setLong(2, userId);
          stmt.setLong(3, 0L);
          stmt.addBatch();
          if (++batchSize >= workConf.getBatchSize()) {
            stmt.executeBatch();
            batchSize = 0;
          }
        }
        if (batchSize > 0) {
          stmt.executeBatch();
        }
      }
      if (explicitTransactions) {
        conn.commit();
      }

      for (int s = 0; s < CassieConstants.STROKES_PER_CANVAS_SEED; s++) {
        long strokeId = this.canvasId * 1_000_000L + s;
        long authorUserId = this.canvasId * 1_000_000L + (s % CassieConstants.SUBSCRIBERS_PER_CANVAS);

        try (PreparedStatement stmt = conn.prepareStatement(CassieLoader.this.sqlStroke)) {
          stmt.setLong(1, strokeId);
          stmt.setLong(2, this.canvasId);
          stmt.setLong(3, authorUserId);
          stmt.setInt(4, CassieConstants.POINTS_PER_SEED_STROKE);
          stmt.setLong(5, 0L);
          stmt.setBoolean(6, false);
          stmt.executeUpdate();
        }

        try (PreparedStatement stmt = conn.prepareStatement(CassieLoader.this.sqlStrokePoint)) {
          for (int p = 0; p < CassieConstants.POINTS_PER_SEED_STROKE; p++) {
            stmt.setLong(1, strokeId);
            stmt.setInt(2, p);
            stmt.setDouble(3, (p * 13) % CassieConstants.CANVAS_EXTENT);
            stmt.setDouble(4, (p * 29) % CassieConstants.CANVAS_EXTENT);
            stmt.setDouble(5, 0.5);
            stmt.setLong(6, 0L);
            stmt.addBatch();
          }
          stmt.executeBatch();
        }
        if (explicitTransactions) {
          conn.commit();
        }
      }
    }
  }
}
