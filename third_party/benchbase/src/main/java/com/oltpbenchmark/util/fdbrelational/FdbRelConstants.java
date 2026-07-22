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

public abstract class FdbRelConstants {
  // Matches the domain name used in fdb-record-layer's own JDBCEmbedDriverTest
  // (RelationalKeyspaceProvider.instance().registerDomainIfNotExists("FRL"), TESTDB =
  // "/FRL/jdbc_test_db") - keeping the same casing/convention this integration is modeled on,
  // rather than inventing a new one, since keyspace domain registration is case-sensitive.
  public static final String KEYSPACE_DOMAIN = "FRL";
  public static final String TPCC_DB_PATH = "/" + KEYSPACE_DOMAIN + "/tpcc";
  public static final String TPCC_SCHEMA_NAME = "PUBLIC";
  public static final String TPCC_TEMPLATE_NAME = "tpcc_template";
}
