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
 * Pre-provisions BenchBase's standard {@code tpcc} benchmark schema against FDB Relational
 * Layer's EMBEDDED driver, using FRL's own schema-template DDL dialect. Must run BEFORE BenchBase
 * (via {@link FdbRelEmbeddedLauncher}), which is then invoked with {@code --create=false} -
 * BenchBase's own {@code createDatabase()} can't express FRL's dialect (schema templates, {@code
 * CREATE DATABASE /path/segments}) from a connect-then-DDL flow.
 *
 * <p>This is a deliberately literal, type-mapped port of {@code
 * benchmarks/tpcc/ddl-generic.sql}, not a hand-picked subset - the point of running the stock
 * {@code tpcc} benchmark (rather than a purpose-built one like zonefabric) is to see how much of
 * BenchBase's existing, unmodified TPCCLoader/procedures work as-is against FRL. Two mismatches
 * between TPC-C's ANSI-SQL schema and what FRL's grammar actually supports (see {@code
 * reference/sql_commands/DDL/CREATE/TABLE.html} and {@code reference/sql_types.html}):
 *
 * <ul>
 *   <li>Type mapping - FRL's primitive types are only {@code string}/{@code bigint}/{@code
 *       double}/{@code boolean}/{@code bytes}: no {@code DECIMAL}, {@code TIMESTAMP}, or {@code
 *       CHAR}/{@code VARCHAR(n)}. Mapped here as DECIMAL(x,y)->double, TIMESTAMP->bigint (expected
 *       to be epoch millis), CHAR(n)/VARCHAR(n)->string. This is a schema-only translation -
 *       TPCCLoader/the TPCC procedures are untouched, so whether their JDBC calls
 *       (setBigDecimal/setTimestamp) actually work against a double/bigint column under FRL's
 *       driver is exactly the open question this PR's CI run is meant to answer.
 *   <li>No {@code FOREIGN KEY}/{@code REFERENCES}/{@code UNIQUE} constraint clause exists in FRL's
 *       {@code CREATE TABLE} grammar (only {@code PRIMARY KEY(...)} or {@code SINGLE ROW ONLY}),
 *       so those are dropped entirely rather than approximated. This doesn't change TPC-C's actual
 *       behavior: NewOrder/Payment/etc. already look up parent rows (WAREHOUSE/DISTRICT/CUSTOMER)
 *       by ID before touching dependents, so referential integrity is already maintained
 *       procedurally by the transactions themselves - the FK constraints in the standard DDL are
 *       a redundant DB-side safety net TPC-C's own logic doesn't depend on, not a correctness
 *       requirement.
 * </ul>
 *
 * <p>HISTORY is the one table this port can't cleanly express: the standard schema gives it no
 * primary key at all (it's an append-only log), but FRL requires every table to declare either
 * {@code PRIMARY KEY(...)} or {@code SINGLE ROW ONLY}. Keyed here on all of its columns
 * (H_C_W_ID, H_C_D_ID, H_C_ID, H_D_ID, H_W_ID, H_DATE) as the least-bad option, but two Payment
 * transactions for the same customer/district in the same millisecond would collide on this key -
 * a real, currently-unresolved gap, not a hidden assumption.
 *
 * <p>Run as: {@code java -cp benchbase.jar com.oltpbenchmark.util.fdbrelational.FdbRelSchemaBootstrap}
 */
public final class FdbRelSchemaBootstrap {
  private FdbRelSchemaBootstrap() {}

  public static void main(String[] args) throws Exception {
    FdbRelEmbeddedBootstrap.register();

    String dbPath = FdbRelConstants.TPCC_DB_PATH;
    String schemaName = FdbRelConstants.TPCC_SCHEMA_NAME;
    String templateName = FdbRelConstants.TPCC_TEMPLATE_NAME;

    try (Connection sys = DriverManager.getConnection("jdbc:embed:/__SYS?schema=CATALOG")) {
      try (Statement st = sys.createStatement()) {
        st.execute("drop database if exists \"" + dbPath + "\"");
        st.execute("drop schema template if exists " + templateName);

        st.execute(
            "CREATE SCHEMA TEMPLATE "
                + templateName
                + """
                     CREATE TABLE WAREHOUSE (
                         w_id bigint,
                         w_ytd double,
                         w_tax double,
                         w_name string,
                         w_street_1 string,
                         w_street_2 string,
                         w_city string,
                         w_state string,
                         w_zip string,
                         PRIMARY KEY(w_id)
                     )
                     CREATE TABLE DISTRICT (
                         d_w_id bigint,
                         d_id bigint,
                         d_ytd double,
                         d_tax double,
                         d_next_o_id bigint,
                         d_name string,
                         d_street_1 string,
                         d_street_2 string,
                         d_city string,
                         d_state string,
                         d_zip string,
                         PRIMARY KEY(d_w_id, d_id)
                     )
                     CREATE TABLE CUSTOMER (
                         c_w_id bigint,
                         c_d_id bigint,
                         c_id bigint,
                         c_discount double,
                         c_credit string,
                         c_last string,
                         c_first string,
                         c_credit_lim double,
                         c_balance double,
                         c_ytd_payment double,
                         c_payment_cnt bigint,
                         c_delivery_cnt bigint,
                         c_street_1 string,
                         c_street_2 string,
                         c_city string,
                         c_state string,
                         c_zip string,
                         c_phone string,
                         c_since bigint,
                         c_middle string,
                         c_data string,
                         PRIMARY KEY(c_w_id, c_d_id, c_id)
                     )
                     CREATE TABLE OORDER (
                         o_w_id bigint,
                         o_d_id bigint,
                         o_id bigint,
                         o_c_id bigint,
                         o_carrier_id bigint,
                         o_ol_cnt bigint,
                         o_all_local bigint,
                         o_entry_d bigint,
                         PRIMARY KEY(o_w_id, o_d_id, o_id)
                     )
                     CREATE TABLE NEW_ORDER (
                         no_w_id bigint,
                         no_d_id bigint,
                         no_o_id bigint,
                         PRIMARY KEY(no_w_id, no_d_id, no_o_id)
                     )
                     CREATE TABLE HISTORY (
                         h_c_id bigint,
                         h_c_d_id bigint,
                         h_c_w_id bigint,
                         h_d_id bigint,
                         h_w_id bigint,
                         h_date bigint,
                         h_amount double,
                         h_data string,
                         PRIMARY KEY(h_c_w_id, h_c_d_id, h_c_id, h_d_id, h_w_id, h_date)
                     )
                     CREATE TABLE ITEM (
                         i_id bigint,
                         i_name string,
                         i_price double,
                         i_data string,
                         i_im_id bigint,
                         PRIMARY KEY(i_id)
                     )
                     CREATE TABLE STOCK (
                         s_w_id bigint,
                         s_i_id bigint,
                         s_quantity bigint,
                         s_ytd double,
                         s_order_cnt bigint,
                         s_remote_cnt bigint,
                         s_data string,
                         s_dist_01 string,
                         s_dist_02 string,
                         s_dist_03 string,
                         s_dist_04 string,
                         s_dist_05 string,
                         s_dist_06 string,
                         s_dist_07 string,
                         s_dist_08 string,
                         s_dist_09 string,
                         s_dist_10 string,
                         PRIMARY KEY(s_w_id, s_i_id)
                     )
                     CREATE TABLE ORDER_LINE (
                         ol_w_id bigint,
                         ol_d_id bigint,
                         ol_o_id bigint,
                         ol_number bigint,
                         ol_i_id bigint,
                         ol_delivery_d bigint,
                         ol_amount double,
                         ol_supply_w_id bigint,
                         ol_quantity double,
                         ol_dist_info string,
                         PRIMARY KEY(ol_w_id, ol_d_id, ol_o_id, ol_number)
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
            "insert into WAREHOUSE (w_id, w_ytd, w_tax, w_name, w_street_1, w_street_2, w_city,"
                + " w_state, w_zip) values (999999, 0.0, 0.0, 'bootstrap-verify', '', '', '', '',"
                + " '')");
        st.executeUpdate("delete from WAREHOUSE where w_id = 999999");
      }
      System.out.println("VERIFY ok: " + dbPath + "?schema=" + schemaName + " is immediately queryable");
    }

    System.out.println("BOOTSTRAP DONE: " + dbPath + "?schema=" + schemaName);
  }
}
