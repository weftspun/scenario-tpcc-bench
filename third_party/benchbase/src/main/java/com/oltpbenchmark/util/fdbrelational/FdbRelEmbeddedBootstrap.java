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

import com.apple.foundationdb.record.provider.foundationdb.APIVersion;
import com.apple.foundationdb.record.provider.foundationdb.FDBDatabase;
import com.apple.foundationdb.record.provider.foundationdb.FDBDatabaseFactory;
import com.apple.foundationdb.relational.api.EmbeddedRelationalDriver;
import com.apple.foundationdb.relational.api.Options;
import com.apple.foundationdb.relational.api.RelationalDriver;
import com.apple.foundationdb.relational.api.catalog.StoreCatalog;
import com.apple.foundationdb.relational.recordlayer.DirectFdbConnection;
import com.apple.foundationdb.relational.recordlayer.RecordLayerConfig;
import com.apple.foundationdb.relational.recordlayer.RecordLayerEngine;
import com.apple.foundationdb.relational.recordlayer.RelationalKeyspaceProvider;
import com.apple.foundationdb.relational.recordlayer.catalog.StoreCatalogProvider;
import com.apple.foundationdb.relational.recordlayer.ddl.RecordLayerMetadataOperationsFactory;
import com.apple.foundationdb.relational.recordlayer.query.cache.RelationalPlanCache;
import com.codahale.metrics.MetricRegistry;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.Collections;

/**
 * Builds and registers FDB Relational Layer's EMBEDDED (in-process) JDBC driver with {@link
 * DriverManager}, so ordinary {@code DriverManager.getConnection("jdbc:embed:...")} calls (as
 * BenchBase's own connection pool makes) resolve to it.
 *
 * <p>Unlike the network/gRPC driver ({@code fdb-relational-jdbc}'s {@code JDBCRelationalDriver}),
 * {@code EmbeddedRelationalDriver} is NOT auto-discoverable via {@code
 * META-INF/services/java.sql.Driver} - there is no such service file under {@code
 * fdb-relational-core}, and the driver has no public no-arg constructor. It must be constructed and
 * registered explicitly, once per JVM, before any {@code jdbc:embed:} connection is opened. This
 * mirrors the pattern in fdb-record-layer's own {@code JDBCEmbedDriverTest}.
 *
 * <p>Call {@link #register()} exactly once, as early as possible in the process (see {@link
 * FdbRelEmbeddedLauncher} and {@link FdbRelSchemaBootstrap}).
 */
public final class FdbRelEmbeddedBootstrap {
  private FdbRelEmbeddedBootstrap() {}

  public static void register() throws SQLException {
    RelationalKeyspaceProvider.instance()
        .registerDomainIfNotExists(FdbRelConstants.KEYSPACE_DOMAIN);

    RecordLayerConfig rlCfg = RecordLayerConfig.getDefault();

    // Must happen before the first call to factory.getDatabase().
    FDBDatabaseFactory.instance().setAPIVersion(APIVersion.API_VERSION_7_1);
    final FDBDatabase database = FDBDatabaseFactory.instance().getDatabase();

    StoreCatalog storeCatalog;
    try (var txn =
        new DirectFdbConnection(database).getTransactionManager().createTransaction(Options.NONE)) {
      storeCatalog =
          StoreCatalogProvider.getCatalog(txn, RelationalKeyspaceProvider.instance().getKeySpace());
      txn.commit();
    } catch (Exception e) {
      throw new SQLException("Failed to initialize FDB Relational store catalog", e);
    }

    RecordLayerMetadataOperationsFactory ddlFactory =
        RecordLayerMetadataOperationsFactory.defaultFactory()
            .setBaseKeySpace(RelationalKeyspaceProvider.instance().getKeySpace())
            .setRlConfig(rlCfg)
            .setStoreCatalog(storeCatalog)
            .build();

    RelationalDriver driver =
        new EmbeddedRelationalDriver(
            RecordLayerEngine.makeEngine(
                rlCfg,
                Collections.singletonList(database),
                RelationalKeyspaceProvider.instance().getKeySpace(),
                storeCatalog,
                new MetricRegistry(),
                ddlFactory,
                RelationalPlanCache.buildWithDefaults()));
    DriverManager.registerDriver(driver);
  }
}
