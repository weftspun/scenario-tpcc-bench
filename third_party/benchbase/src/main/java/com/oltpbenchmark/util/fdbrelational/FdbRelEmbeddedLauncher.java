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

package com.oltpbenchmark.util.fdbrelational;

import com.oltpbenchmark.DBWorkload;

/**
 * Entry point for running BenchBase against FDB Relational Layer's EMBEDDED driver.
 *
 * <p>BenchBase's normal startup path only does {@code Class.forName(driverClassName)} before
 * connecting, which is enough to trigger driver self-registration for drivers that register
 * themselves in a static initializer (the JDBC convention every other {@code DatabaseType} in this
 * project relies on). {@code EmbeddedRelationalDriver} does not follow that convention - it has no
 * public no-arg constructor and must be built and registered explicitly (see {@link
 * FdbRelEmbeddedBootstrap}). This launcher does that registration once, then delegates straight
 * into BenchBase's own {@link DBWorkload#main}, unmodified, with the same CLI arguments - from that
 * point on BenchBase's connection pool calling {@code
 * DriverManager.getConnection("jdbc:embed:...")} resolves to the already-registered driver like any
 * other JDBC driver would.
 *
 * <p>Run as: {@code java -cp benchbase.jar
 * com.oltpbenchmark.util.fdbrelational.FdbRelEmbeddedLauncher <benchbase args>}
 */
public final class FdbRelEmbeddedLauncher {
  private FdbRelEmbeddedLauncher() {}

  public static void main(String[] args) throws Exception {
    FdbRelEmbeddedBootstrap.register();
    DBWorkload.main(args);
  }
}
