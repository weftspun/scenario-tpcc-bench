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

package com.oltpbenchmark.benchmarks.zonefabric;

import com.oltpbenchmark.api.Loader;
import com.oltpbenchmark.api.LoaderThread;
import com.oltpbenchmark.catalog.Table;
import com.oltpbenchmark.util.RandomDistribution.DiscreteRNG;
import com.oltpbenchmark.util.RandomDistribution.Flat;
import com.oltpbenchmark.util.SQLUtil;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

/**
 * ZoneFabricBenchmark Loader: one zone per unit of scale factor, ENTITIES_PER_ZONE entities each.
 */
public final class ZoneFabricLoader extends Loader<ZoneFabricBenchmark> {
  private final Table catalogZone;
  private final Table catalogEntity;

  private final String sqlZone;
  private final String sqlEntity;

  private final long numZones;

  public ZoneFabricLoader(ZoneFabricBenchmark benchmark) {
    super(benchmark);

    this.catalogZone = this.benchmark.getCatalog().getTable(ZoneFabricConstants.TABLENAME_ZONE);
    this.catalogEntity = this.benchmark.getCatalog().getTable(ZoneFabricConstants.TABLENAME_ENTITY);

    this.sqlZone = SQLUtil.getInsertSQL(this.catalogZone, this.getDatabaseType());
    this.sqlEntity = SQLUtil.getInsertSQL(this.catalogEntity, this.getDatabaseType());

    this.numZones = benchmark.numZones;
  }

  @Override
  public List<LoaderThread> createLoaderThreads() {
    List<LoaderThread> threads = new ArrayList<>();
    for (long z = 0; z < this.numZones; z++) {
      threads.add(new Generator(z));
    }
    return threads;
  }

  private class Generator extends LoaderThread {
    private final long zoneId;
    private final DiscreteRNG randPos;
    private final DiscreteRNG randVel;
    private final DiscreteRNG randRtt;

    PreparedStatement stmtZone;
    PreparedStatement stmtEntity;

    public Generator(long zoneId) {
      super(benchmark);
      this.zoneId = zoneId;
      this.randPos = new Flat(benchmark.rng(), 0, (long) ZoneFabricConstants.WORLD_EXTENT);
      this.randVel = new Flat(benchmark.rng(), -10, 10);
      this.randRtt = new Flat(benchmark.rng(), 10, 200);
    }

    @Override
    public void load(Connection conn) throws SQLException {
      // Same reasoning as AssetCdnLoader: keep each zone's load in its own
      // small transaction rather than one giant uncommitted batch.
      conn.setAutoCommit(false);

      this.stmtZone = conn.prepareStatement(ZoneFabricLoader.this.sqlZone);
      stmtZone.setLong(1, this.zoneId);
      stmtZone.setString(2, "region-" + (this.zoneId % 4));
      stmtZone.setInt(3, ZoneFabricConstants.ENTITIES_PER_ZONE);
      stmtZone.setInt(4, ZoneFabricConstants.AUTHORITY_CAPACITY);
      stmtZone.setInt(5, ZoneFabricConstants.INTEREST_CAPACITY);
      stmtZone.setDouble(6, Math.pow(ZoneFabricConstants.ENTITIES_PER_ZONE, 2));
      stmtZone.executeUpdate();
      conn.commit();

      this.stmtEntity = conn.prepareStatement(ZoneFabricLoader.this.sqlEntity);
      int batchSize = 0;
      for (int i = 0; i < ZoneFabricConstants.ENTITIES_PER_ZONE; i++) {
        long entityId = this.zoneId * 1_000_000L + i;
        stmtEntity.setLong(1, entityId);
        stmtEntity.setLong(2, this.zoneId);
        stmtEntity.setDouble(3, this.randPos.nextLong());
        stmtEntity.setDouble(4, this.randPos.nextLong());
        stmtEntity.setDouble(5, this.randVel.nextLong());
        stmtEntity.setDouble(6, this.randVel.nextLong());
        stmtEntity.setInt(7, (int) this.randRtt.nextLong());
        stmtEntity.setLong(8, 0L);
        stmtEntity.addBatch();

        if (++batchSize >= workConf.getBatchSize()) {
          stmtEntity.executeBatch();
          conn.commit();
          batchSize = 0;
        }
      }
      if (batchSize > 0) {
        stmtEntity.executeBatch();
        conn.commit();
      }
    }
  }
}
