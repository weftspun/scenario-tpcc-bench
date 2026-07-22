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

package com.oltpbenchmark.benchmarks.assetcdn;

public abstract class AssetCdnConstants {
  public static final String TABLENAME_ASSET = "ASSET";
  public static final String TABLENAME_ASSET_VERSION = "ASSET_VERSION";
  public static final String TABLENAME_EDGE_CACHE_ENTRY = "EDGE_CACHE_ENTRY";
  public static final String TABLENAME_USER_ENTITLEMENT = "USER_ENTITLEMENT";

  // Scale factor is "how many assets", the top-level partition unit here -
  // matches zonefabric's "scale factor = zone count" convention.

  // Fixed edge-node fleet size, independent of scale factor: adding more
  // assets doesn't add more edge locations in the real system, it adds more
  // objects those same edge locations cache.
  public static final int EDGE_NODE_COUNT = 8;

  // Users entitled to access a given asset at load time.
  public static final int ENTITLEMENTS_PER_ASSET = 50;

  public static final long DEFAULT_ASSET_SIZE_BYTES = 5_000_000L;

  // A cache entry not accessed in longer than this (in loader/worker "tick"
  // units) is eligible for CacheEviction - mirrors a real LRU/TTL edge policy.
  public static final long CACHE_STALE_TICKS = 10_000L;
}
