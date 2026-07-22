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

public abstract class ZoneFabricConstants {
  public static final String TABLENAME_ZONE = "ZONE";
  public static final String TABLENAME_ENTITY = "ENTITY";
  public static final String TABLENAME_EFFECT_ENTITY = "EFFECT_ENTITY";
  public static final String TABLENAME_FANOUT_TARGET = "FANOUT_TARGET";

  // Entities per zone at scale factor 1. Scale factor is therefore "how many
  // zones", matching TPC-C's warehouse-count scale factor.
  public static final int ENTITIES_PER_ZONE = 200;

  public static final double WORLD_EXTENT = 10000.0;
  public static final double GHOST_RANGE = 150.0;

  public static final int AUTHORITY_CAPACITY = 256;
  public static final int INTEREST_CAPACITY = 512;

  // Zone split triggers once population^2 cost crosses this - mirrors the
  // real AV1-style maybeSplitZone cost model.
  public static final double SPLIT_COST_THRESHOLD = 40000.0;
}
