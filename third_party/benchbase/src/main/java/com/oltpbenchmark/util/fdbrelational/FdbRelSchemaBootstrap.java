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

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;

/**
 * Pre-provisions zonefabric's schema against FDB Relational Layer's EMBEDDED driver, using FRL's
 * own schema-template DDL dialect. Must run BEFORE BenchBase (via {@link FdbRelEmbeddedLauncher}),
 * which is then invoked with {@code --create=false} - BenchBase's own {@code createDatabase()}
 * can't express FRL's dialect (schema templates, {@code CREATE DATABASE /path/segments}) from a
 * connect-then-DDL flow.
 *
 * <p>Run as: {@code java -cp benchbase.jar com.oltpbenchmark.util.fdbrelational.FdbRelSchemaBootstrap}
 */
public final class FdbRelSchemaBootstrap {
  private FdbRelSchemaBootstrap() {}

  public static void main(String[] args) throws Exception {
    FdbRelEmbeddedBootstrap.register();

    String dbPath = FdbRelConstants.ZONEFABRIC_DB_PATH;
    String schemaName = FdbRelConstants.ZONEFABRIC_SCHEMA_NAME;
    String templateName = FdbRelConstants.ZONEFABRIC_TEMPLATE_NAME;

    try (Connection sys = DriverManager.getConnection("jdbc:embed:/__SYS?schema=CATALOG")) {
      try (Statement st = sys.createStatement()) {
        st.execute("drop database if exists \"" + dbPath + "\"");
        st.execute("drop schema template if exists " + templateName);

        st.execute(
            "CREATE SCHEMA TEMPLATE "
                + templateName
                + """
                     CREATE TABLE ZONE (
                         z_id bigint,
                         z_region string,
                         z_population bigint,
                         z_authority_cap bigint,
                         z_interest_cap bigint,
                         z_cost double,
                         PRIMARY KEY(z_id)
                     )
                     CREATE TABLE ENTITY (
                         e_id bigint,
                         e_zone_id bigint,
                         e_x double,
                         e_y double,
                         e_vx double,
                         e_vy double,
                         e_rtt_ms bigint,
                         e_last_tick bigint,
                         PRIMARY KEY(e_id)
                     )
                     CREATE TABLE EFFECT_ENTITY (
                         ef_id bigint,
                         ef_caster_id bigint,
                         ef_zone_id bigint,
                         ef_kind string,
                         ef_magnitude double,
                         ef_duration_ticks bigint,
                         ef_created_tick bigint,
                         PRIMARY KEY(ef_id)
                     )
                     CREATE TABLE FANOUT_TARGET (
                         ft_effect_id bigint,
                         ft_target_entity_id bigint,
                         ft_distance double,
                         PRIMARY KEY(ft_effect_id, ft_target_entity_id)
                     )
                    """);
        System.out.println("CREATE SCHEMA TEMPLATE ok");

        st.execute("create database \"" + dbPath + "\"");
        System.out.println("CREATE DATABASE ok");

        st.execute("create schema \"" + dbPath + "/" + schemaName + "\" with template " + templateName);
        System.out.println("CREATE SCHEMA ok");
      }
    }

    // Immediate same-JVM verification: open a fresh embedded connection to the exact
    // path/schema BenchBase will use and confirm it resolves right away, rather than
    // discovering a resolution problem only once BenchBase's multi-threaded loader hits it -
    // see the network-mode "Database does not exist" bug this embedded rework is meant to get
    // past (weftspun/scenario-tpcc-bench#3).
    try (Connection verify =
        DriverManager.getConnection("jdbc:embed:" + dbPath + "?schema=" + schemaName)) {
      try (Statement st = verify.createStatement()) {
        st.executeUpdate(
            "insert into ZONE (z_id, z_region, z_population, z_authority_cap, z_interest_cap, z_cost)"
                + " values (999999, 'bootstrap-verify', 0, 0, 0, 0.0)");
        st.executeUpdate("delete from ZONE where z_id = 999999");
      }
      System.out.println("VERIFY ok: " + dbPath + "?schema=" + schemaName + " is immediately queryable");
    }

    System.out.println("BOOTSTRAP DONE: " + dbPath + "?schema=" + schemaName);
  }
}
