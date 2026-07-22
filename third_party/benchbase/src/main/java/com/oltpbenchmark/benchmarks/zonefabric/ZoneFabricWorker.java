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

import com.oltpbenchmark.api.Procedure;
import com.oltpbenchmark.api.Procedure.UserAbortException;
import com.oltpbenchmark.api.TransactionType;
import com.oltpbenchmark.api.Worker;
import com.oltpbenchmark.benchmarks.zonefabric.procedures.*;
import com.oltpbenchmark.types.TransactionStatus;
import com.oltpbenchmark.util.RandomDistribution.DiscreteRNG;
import com.oltpbenchmark.util.RandomDistribution.Flat;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.concurrent.atomic.AtomicLong;

public final class ZoneFabricWorker extends Worker<ZoneFabricBenchmark> {

  private final CastSpell procCastSpell;
  private final ZoneAuthorityHandoff procZoneAuthorityHandoff;
  private final EntityStateQuery procEntityStateQuery;
  private final ZoneSplitMerge procZoneSplitMerge;
  private final InterestFanoutScan procInterestFanoutScan;

  private final DiscreteRNG zoneRng;
  private final long numZones;
  private final long entitiesPerZone;

  // Effect/new-zone ids just need to be unique per worker, not globally
  // sequential - a per-worker counter offset by worker id is enough and
  // avoids needing a DB sequence (see ZoneSplitMerge's javadoc).
  private static final AtomicLong nextGlobalId = new AtomicLong(1);

  public ZoneFabricWorker(ZoneFabricBenchmark benchmarkModule, int id) {
    super(benchmarkModule, id);
    this.procCastSpell = this.getProcedure(CastSpell.class);
    this.procZoneAuthorityHandoff = this.getProcedure(ZoneAuthorityHandoff.class);
    this.procEntityStateQuery = this.getProcedure(EntityStateQuery.class);
    this.procZoneSplitMerge = this.getProcedure(ZoneSplitMerge.class);
    this.procInterestFanoutScan = this.getProcedure(InterestFanoutScan.class);

    this.numZones = benchmarkModule.numZones;
    this.entitiesPerZone = ZoneFabricConstants.ENTITIES_PER_ZONE;
    this.zoneRng = new Flat(rng(), 0, this.numZones - 1);
  }

  private long randomZoneId() {
    return this.zoneRng.nextLong();
  }

  private long randomEntityIdInZone(long zoneId) {
    long offset = (long) (rng().nextDouble() * this.entitiesPerZone);
    return zoneId * 1_000_000L + offset;
  }

  @Override
  protected TransactionStatus executeWork(Connection conn, TransactionType txnType)
      throws UserAbortException, SQLException {
    Class<? extends Procedure> procClass = txnType.getProcedureClass();

    if (procClass.equals(CastSpell.class)) {
      long zoneId = randomZoneId();
      long casterId = randomEntityIdInZone(zoneId);
      long effectId = nextGlobalId.getAndIncrement();
      String[] kinds = {"bolt", "heal", "shield"};
      String kind = kinds[rng().nextInt(kinds.length)];
      this.procCastSpell.run(conn, effectId, casterId, kind, rng().nextDouble() * 100, effectId);

    } else if (procClass.equals(ZoneAuthorityHandoff.class)) {
      long zoneId = randomZoneId();
      long entityId = randomEntityIdInZone(zoneId);
      long newZoneId = randomZoneId();
      this.procZoneAuthorityHandoff.run(conn, entityId, newZoneId);

    } else if (procClass.equals(EntityStateQuery.class)) {
      long zoneId = randomZoneId();
      long entityId = randomEntityIdInZone(zoneId);
      this.procEntityStateQuery.run(conn, entityId);

    } else if (procClass.equals(ZoneSplitMerge.class)) {
      long zoneId = randomZoneId();
      long newZoneId = this.numZones + nextGlobalId.getAndIncrement();
      this.procZoneSplitMerge.run(conn, zoneId, newZoneId);

    } else if (procClass.equals(InterestFanoutScan.class)) {
      long zoneId = randomZoneId();
      this.procInterestFanoutScan.run(conn, zoneId, 0L);
    }

    return TransactionStatus.SUCCESS;
  }
}
