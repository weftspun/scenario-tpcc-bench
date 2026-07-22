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

import com.oltpbenchmark.api.Procedure;
import com.oltpbenchmark.api.Procedure.UserAbortException;
import com.oltpbenchmark.api.TransactionType;
import com.oltpbenchmark.api.Worker;
import com.oltpbenchmark.benchmarks.cassie.procedures.*;
import com.oltpbenchmark.types.TransactionStatus;
import com.oltpbenchmark.util.RandomDistribution.DiscreteRNG;
import com.oltpbenchmark.util.RandomDistribution.Flat;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.concurrent.atomic.AtomicLong;

public final class CassieWorker extends Worker<CassieBenchmark> {

  private final AppendStrokePoints procAppendStrokePoints;
  private final BeautifyStroke procBeautifyStroke;
  private final CanvasSyncQuery procCanvasSyncQuery;
  private final CanvasSnapshot procCanvasSnapshot;
  private final StrokeUndo procStrokeUndo;

  private final DiscreteRNG canvasRng;
  private final long numCanvases;

  // New stroke/point ids just need to be unique per worker, not globally
  // sequential - see ZoneFabricWorker's javadoc for the same reasoning.
  private static final AtomicLong nextGlobalId = new AtomicLong(1);
  private static final AtomicLong nextTick = new AtomicLong(1);

  public CassieWorker(CassieBenchmark benchmarkModule, int id) {
    super(benchmarkModule, id);
    this.procAppendStrokePoints = this.getProcedure(AppendStrokePoints.class);
    this.procBeautifyStroke = this.getProcedure(BeautifyStroke.class);
    this.procCanvasSyncQuery = this.getProcedure(CanvasSyncQuery.class);
    this.procCanvasSnapshot = this.getProcedure(CanvasSnapshot.class);
    this.procStrokeUndo = this.getProcedure(StrokeUndo.class);

    this.numCanvases = benchmarkModule.numCanvases;
    this.canvasRng = new Flat(rng(), 0, this.numCanvases - 1);
  }

  private long randomCanvasId() {
    return this.canvasRng.nextLong();
  }

  private long randomSubscriberIdForCanvas(long canvasId) {
    long offset = (long) (rng().nextDouble() * CassieConstants.SUBSCRIBERS_PER_CANVAS);
    return canvasId * 1_000_000L + offset;
  }

  private long randomSeedStrokeIdForCanvas(long canvasId) {
    long offset = (long) (rng().nextDouble() * CassieConstants.STROKES_PER_CANVAS_SEED);
    return canvasId * 1_000_000L + offset;
  }

  @Override
  protected TransactionStatus executeWork(Connection conn, TransactionType txnType)
      throws UserAbortException, SQLException {
    Class<? extends Procedure> procClass = txnType.getProcedureClass();
    long tick = nextTick.getAndIncrement();

    if (procClass.equals(AppendStrokePoints.class)) {
      long canvasId = randomCanvasId();
      long authorUserId = randomSubscriberIdForCanvas(canvasId);
      long strokeId = this.numCanvases * 1_000_000L + nextGlobalId.getAndIncrement();
      int n = CassieConstants.POINTS_PER_APPENDED_STROKE;
      double[] xs = new double[n];
      double[] ys = new double[n];
      for (int i = 0; i < n; i++) {
        xs[i] = rng().nextDouble() * CassieConstants.CANVAS_EXTENT;
        ys[i] = rng().nextDouble() * CassieConstants.CANVAS_EXTENT;
      }
      this.procAppendStrokePoints.run(conn, strokeId, canvasId, authorUserId, xs, ys, tick);

    } else if (procClass.equals(BeautifyStroke.class)) {
      long canvasId = randomCanvasId();
      long strokeId = randomSeedStrokeIdForCanvas(canvasId);
      this.procBeautifyStroke.run(conn, strokeId, 1, 1 + rng().nextInt(20));

    } else if (procClass.equals(CanvasSyncQuery.class)) {
      long canvasId = randomCanvasId();
      long userId = randomSubscriberIdForCanvas(canvasId);
      this.procCanvasSyncQuery.run(conn, canvasId, userId);

    } else if (procClass.equals(CanvasSnapshot.class)) {
      this.procCanvasSnapshot.run(conn, randomCanvasId());

    } else if (procClass.equals(StrokeUndo.class)) {
      long canvasId = randomCanvasId();
      long strokeId = randomSeedStrokeIdForCanvas(canvasId);
      this.procStrokeUndo.run(conn, strokeId);
    }

    return TransactionStatus.SUCCESS;
  }
}
