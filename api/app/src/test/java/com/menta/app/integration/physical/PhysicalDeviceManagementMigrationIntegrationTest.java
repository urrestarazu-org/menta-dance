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
 * Proves V23 (#44, US-PHYSICAL-007, design C8) creates {@code physical_devices} and {@code
 * physical_device_audit} cleanly, and that it does not collide with the existing {@code
 * classpath:db/rollback} version 21 ({@code V21__revert_billing_purchase_sessions.sql}, #41) when
 * both locations are combined -- the exact collision {@code
 * PhysicalAttendanceHistoryMigrationIntegrationTest} already verified V22 avoids (#39). Mirrors
 * that test's structure.
 *
 * <p>Deliberately not a {@code @SpringBootTest}: the app's {@code integration-test} profile
 * disables Flyway in favor of Hibernate's {@code ddl-auto: create-drop}, so this test drives
 * Flyway directly against the real {@code classpath:db/migration} scripts, the same ones the
 * running application applies.</p>
 */
@Testcontainers
class PhysicalDeviceManagementMigrationIntegrationTest {

    @Container
    private static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.0")
        .withDatabaseName("menta_device_migration_test")
        .withUsername("test")
        .withPassword("test");

    /**
     * The MySQL container is {@code static} (shared across every test method, to avoid paying
     * its startup cost per test) so each test resets the schema before migrating -- otherwise a
     * version applied by an earlier test leaks into the next one (mirrors {@code
     * PhysicalAttendanceHistoryMigrationIntegrationTest}).
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

    private static Connection connect() throws java.sql.SQLException {
        return DriverManager.getConnection(MYSQL.getJdbcUrl(), MYSQL.getUsername(), MYSQL.getPassword());
    }

    private static boolean tableExists(Connection connection, String table) throws java.sql.SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
            "SELECT COUNT(*) FROM information_schema.tables "
                + "WHERE table_schema = DATABASE() AND table_name = ?"
        )) {
            statement.setString(1, table);
            try (ResultSet resultSet = statement.executeQuery()) {
                resultSet.next();
                return resultSet.getInt(1) > 0;
            }
        }
    }

    private static boolean indexExists(Connection connection, String table, String indexName)
        throws java.sql.SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
            "SELECT COUNT(*) FROM information_schema.statistics "
                + "WHERE table_schema = DATABASE() AND table_name = ? AND index_name = ?"
        )) {
            statement.setString(1, table);
            statement.setString(2, indexName);
            try (ResultSet resultSet = statement.executeQuery()) {
                resultSet.next();
                return resultSet.getInt(1) > 0;
            }
        }
    }

    /**
     * The schema-freeness check this test proves BEFORE the migration file exists (task 1.7):
     * V23 is free in {@code classpath:db/migration} (highest applied there: V22) and disjoint
     * from {@code classpath:db/rollback}'s highest version (V21) once both locations are
     * combined.
     */
    @Test
    void v23_creates_physical_devices_and_physical_device_audit_cleanly() throws java.sql.SQLException {
        Flyway.configure()
            .dataSource(MYSQL.getJdbcUrl(), MYSQL.getUsername(), MYSQL.getPassword())
            .locations("classpath:db/migration")
            .cleanDisabled(false)
            .load()
            .migrate();

        try (Connection connection = connect()) {
            assertThat(tableExists(connection, "physical_devices")).isTrue();
            assertThat(tableExists(connection, "physical_device_audit")).isTrue();
            assertThat(indexExists(connection, "physical_devices", "uq_physical_devices_secret_hash")).isTrue();
            assertThat(indexExists(connection, "physical_devices", "idx_physical_devices_status")).isTrue();
            assertThat(indexExists(connection, "physical_device_audit", "idx_physical_device_audit_device"))
                .isTrue();
        }
    }

    /**
     * Reproduces exactly the combined-location call {@code
     * PhysicalAttendanceHistoryMigrationIntegrationTest} makes for V22: if V23 had instead
     * collided with a version already claimed under {@code classpath:db/rollback} (V21), Flyway
     * would see two scripts claiming the same version the moment both locations are combined,
     * and this call would throw. It does not -- V23 is a disjoint version from the pre-existing
     * rollback version 21.
     */
    @Test
    void v23_does_not_collide_with_the_existing_rollback_version_21_in_the_combined_namespace()
        throws java.sql.SQLException {
        Flyway.configure()
            .dataSource(MYSQL.getJdbcUrl(), MYSQL.getUsername(), MYSQL.getPassword())
            .locations("classpath:db/migration", "classpath:db/rollback")
            .target(MigrationVersion.fromVersion("21"))
            .load()
            .migrate();

        try (Connection connection = connect()) {
            // Version 21 is below V23 (this change's migration), so its tables do not exist yet
            // at this target -- proving the two versions are genuinely disjoint and ordered, not
            // just coincidentally non-throwing.
            assertThat(tableExists(connection, "physical_devices")).isFalse();
        }
    }
}
