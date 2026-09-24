package com.menta.app.integration.physical;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationVersion;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Proves V20.1 (#208, US-PHYSICAL-004b, design B1) adds {@code payment_id} and {@code
 * converted_at} to {@code physical_capacity_holds} with the exact NOT NULL/unique/index shape the
 * design locks, and that V20.2 (the guarded, operator-only reverse migration) drops both columns
 * cleanly on the zero-row table every environment has today.
 *
 * <p>Numbered V20.1/V20.2, not the V21/V22 design.md sketches: {@code classpath:db/rollback}
 * already owns version 21 ({@code V21__revert_billing_purchase_sessions.sql}, #41), and {@code
 * PurchaseSessionsMigrationIntegrationTest} proves that revert by migrating {@code
 * classpath:db/migration} to latest and then targeting version 21 with {@code
 * classpath:db/rollback} added. A whole-number V21/V22 here would either collide with that
 * existing version 21 the moment both locations are scanned together, or push "migrate to latest"
 * past 21 and turn that test's later {@code target(21)} into an out-of-order validation failure —
 * exactly the "migrations have moved since the design doc was written" case this task's brief
 * warned about. A decimal version between V20 and V21 avoids both failure modes without touching
 * that unrelated file.
 *
 * <p>Deliberately not a {@code @SpringBootTest}: the app's {@code integration-test} profile
 * disables Flyway in favor of Hibernate's {@code ddl-auto: create-drop} (see {@code
 * application-integration-test.yml}), so every other integration test in this module never
 * actually runs a single migration script. This test drives Flyway directly against the real
 * {@code classpath:db/migration} scripts — the same ones the running application applies — so
 * V20.1/V20.2 are proven the way they will actually run in production, not against a schema
 * Hibernate invented from the current entity mappings.</p>
 */
@Testcontainers
class PhysicalCapacityHoldMigrationIntegrationTest {

    @Container
    private static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.0")
        .withDatabaseName("menta_migration_test")
        .withUsername("test")
        .withPassword("test");

    private static Connection connect() throws java.sql.SQLException {
        return DriverManager.getConnection(MYSQL.getJdbcUrl(), MYSQL.getUsername(), MYSQL.getPassword());
    }

    /**
     * The MySQL container is {@code static} (shared across every test method, to avoid paying
     * its startup cost per test) so each test resets the schema before migrating — otherwise
     * {@code v20_1_adds_payment_id_not_null_with_its_unique_key_and_index}'s migrate-to-latest
     * would leak a schema already at v22 into {@code v20_2_reverts_cleanly_on_the_zero_row_table},
     * defeating its own {@code target(20.1.5)} bound (mirrors {@code
     * PurchaseSessionsMigrationIntegrationTest}).
     */
    @BeforeEach
    void resetSchema() {
        Flyway.configure()
            .dataSource(MYSQL.getJdbcUrl(), MYSQL.getUsername(), MYSQL.getPassword())
            .locations("classpath:db/migration", "classpath:db/rollback")
            .cleanDisabled(false)
            .load()
            .clean();
    }

    private static boolean columnExists(Connection connection, String column) throws java.sql.SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
            "SELECT COUNT(*) FROM information_schema.columns "
                + "WHERE table_schema = DATABASE() AND table_name = 'physical_capacity_holds' AND column_name = ?"
        )) {
            statement.setString(1, column);
            try (ResultSet resultSet = statement.executeQuery()) {
                resultSet.next();
                return resultSet.getInt(1) == 1;
            }
        }
    }

    private static String columnNullability(Connection connection, String column) throws java.sql.SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
            "SELECT is_nullable FROM information_schema.columns "
                + "WHERE table_schema = DATABASE() AND table_name = 'physical_capacity_holds' "
                + "AND column_name = ?"
        )) {
            statement.setString(1, column);
            try (ResultSet resultSet = statement.executeQuery()) {
                resultSet.next();
                return resultSet.getString(1);
            }
        }
    }

    private static boolean indexExists(Connection connection, String indexName) throws java.sql.SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
            "SELECT COUNT(*) FROM information_schema.statistics "
                + "WHERE table_schema = DATABASE() AND table_name = 'physical_capacity_holds' AND index_name = ?"
        )) {
            statement.setString(1, indexName);
            try (ResultSet resultSet = statement.executeQuery()) {
                resultSet.next();
                return resultSet.getInt(1) > 0;
            }
        }
    }

    @Test
    void v20_1_adds_payment_id_not_null_with_its_unique_key_and_index() throws java.sql.SQLException {
        Flyway.configure()
            .dataSource(MYSQL.getJdbcUrl(), MYSQL.getUsername(), MYSQL.getPassword())
            .locations("classpath:db/migration")
            .load()
            .migrate();

        try (Connection connection = connect()) {
            assertThat(columnExists(connection, "payment_id")).isTrue();
            assertThat(columnNullability(connection, "payment_id")).isEqualTo("NO");
            assertThat(columnExists(connection, "converted_at")).isTrue();
            assertThat(columnNullability(connection, "converted_at")).isEqualTo("YES");
            assertThat(indexExists(connection, "uq_physical_holds_payment_session")).isTrue();
            assertThat(indexExists(connection, "idx_physical_holds_payment")).isTrue();
        }
    }

    @Test
    void v20_2_reverts_cleanly_on_the_zero_row_table() throws java.sql.SQLException {
        // Targets 20.1.5 (the highest real classpath:db/migration version below the reserved
        // V20.2/V21 rollback slots), not an unbounded "migrate to latest": any later real
        // migration (V22, #39, and beyond) applied here would sit above V20.2/V21, and the
        // combined-location target(20.2) below would then be an out-of-order downgrade — the
        // exact failure mode this class's own javadoc warned about.
        Flyway.configure()
            .dataSource(MYSQL.getJdbcUrl(), MYSQL.getUsername(), MYSQL.getPassword())
            .locations("classpath:db/migration")
            .target(MigrationVersion.fromVersion("20.1.5"))
            .load()
            .migrate();

        // V20.2 is a guarded, operator-only escape hatch (design "Migration / Rollout")
        // deliberately NOT under classpath:db/migration -- it must never auto-apply on a normal
        // "migrate to latest". Applying it here requires explicitly adding db/rollback to
        // Flyway's locations, targeted at exactly 20.2 so the unrelated, pre-existing V21 revert
        // (classpath:db/rollback/V21__revert_billing_purchase_sessions.sql, #41) is never pulled
        // in as a side effect.
        Flyway.configure()
            .dataSource(MYSQL.getJdbcUrl(), MYSQL.getUsername(), MYSQL.getPassword())
            .locations("classpath:db/migration", "classpath:db/rollback")
            .target(MigrationVersion.fromVersion("20.2"))
            .load()
            .migrate();

        try (Connection connection = connect()) {
            assertThat(columnExists(connection, "payment_id")).isFalse();
            assertThat(columnExists(connection, "converted_at")).isFalse();
        }
    }
}
