import java.sql.*;

/**
 * Pre-provisions zonefabric's schema against a real fdb-relational-server, using FDB
 * Relational's own schema-template DDL dialect. Must run BEFORE benchbase.jar, which is then
 * invoked with --create=false (its own createDatabase() can't work here: it connects directly
 * to the target database path, which doesn't exist yet - a chicken-and-egg problem FDB
 * Relational's dialect doesn't let you work around from a single connection, unlike
 * Postgres/CockroachDB's "connect to a different existing db, issue CREATE DATABASE remotely"
 * pattern).
 *
 * Usage: java -cp fdb-relational-jdbc-<version>-driver.jar:. FdbRelBootstrap [host]
 * (host defaults to localhost; pass the fdb-relational-server's hostname/IP if running it in a
 * separate container/network from this tool.)
 */
public class FdbRelBootstrap {
    public static void main(String[] args) throws Exception {
        Class.forName("com.apple.foundationdb.relational.jdbc.JDBCRelationalDriver");

        String host = args.length > 0 ? args[0] : "localhost";
        String dbPath = "/frl/zonefabric";
        String schemaName = "PUBLIC";
        String templateName = "zonefabric_template";

        try (Connection sys = DriverManager.getConnection("jdbc:relational://" + host + ":1111/__SYS?schema=CATALOG")) {
            try (Statement st = sys.createStatement()) {
                st.execute("drop database if exists " + dbPath);
                st.execute("drop schema template if exists " + templateName);

                st.execute("""
                    CREATE SCHEMA TEMPLATE zonefabric_template
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

                st.execute("CREATE DATABASE " + dbPath);
                System.out.println("CREATE DATABASE ok");

                st.execute("CREATE SCHEMA " + dbPath + "/" + schemaName + " WITH TEMPLATE " + templateName);
                System.out.println("CREATE SCHEMA ok");
            }
        }

        System.out.println("BOOTSTRAP DONE: " + dbPath + "?schema=" + schemaName);
    }
}
