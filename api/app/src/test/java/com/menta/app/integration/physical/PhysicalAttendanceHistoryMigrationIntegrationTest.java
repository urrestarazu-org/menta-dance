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
 * Proves V22 (#39, US-PHYSICAL-002, design C5) adds
 * {@code idx_physical_assignments_student} cleanly, and that it does not collide with the
 * existing {@code classpath:db/rollback} version 21
 * ({@code V21__revert_billing_purchase_sessions.sql}, #41) when both locations are combined —
 * the exact collision {@code PhysicalCapacityHoldMigrationIntegrationTest} (V20.1's own
 * javadoc) already documents avoiding for the same reason. Mirrors that test's structure.
 *
 * <p>Deliberately not a {@code @SpringBootTest}: the app's {@code integration-test} profile
 * disables Flyway in favor of Hibernate's {@code ddl-auto: create-drop}, so this test drives
 * Flyway directly against the real {@code classpath:db/migration} scripts, the same ones the
 * running application applies.</p>
 */
@Testcontainers
class PhysicalAttendanceHistoryMigrationIntegrationTest {

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
     * its startup cost per test) so each test resets the schema before migrating — otherwise a
     * version applied by an earlier test leaks into the next one (mirrors
     * {@code PurchaseSessionsMigrationIntegrationTest}).
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

    @Test
    void v22_adds_the_student_index_on_physical_capacity_assignments() throws java.sql.SQLException {
        Flyway.configure()
            .dataSource(MYSQL.getJdbcUrl(), MYSQL.getUsername(), MYSQL.getPassword())
            .locations("classpath:db/migration")
            .load()
            .migrate();

        try (Connection connection = connect()) {
            assertThat(indexExists(connection, "physical_capacity_assignments", "idx_physical_assignments_student"))
                .isTrue();
            assertThat(indexExists(connection, "physical_attendances", "idx_physical_attendances_user")).isFalse();
        }
    }

    /**
     * Reproduces exactly the combined-location call {@code PurchaseSessionsMigrationIntegrationTest}
     * makes ({@code migrateIncludingRollbackTo("21")}): if V22 had instead been numbered V21 (the
     * design.md/tasks.md sketch), Flyway would see two scripts claiming version 21 the moment
     * {@code classpath:db/rollback} is added, and this call would throw. It does not — V22 is a
     * disjoint version from the pre-existing rollback version 21.
     */
    @Test
    void v22_does_not_collide_with_the_existing_rollback_version_21_in_the_combined_namespace()
        throws java.sql.SQLException {
        // Exactly PurchaseSessionsMigrationIntegrationTest's migrateIncludingRollbackTo("21")
        // call: both locations combined, targeted at version 21. If V22 had instead been
        // numbered V21 (the design.md/tasks.md sketch), Flyway would resolve two scripts
        // claiming version 21 here and this call would throw. It applies cleanly instead,
        // proving V22 is a disjoint version.
        Flyway.configure()
            .dataSource(MYSQL.getJdbcUrl(), MYSQL.getUsername(), MYSQL.getPassword())
            .locations("classpath:db/migration", "classpath:db/rollback")
            .target(MigrationVersion.fromVersion("21"))
            .load()
            .migrate();

        try (Connection connection = connect()) {
            // Version 21 is below V22 (this change's migration), so its index does not exist yet
            // at this target — proving the two versions are genuinely disjoint and ordered, not
            // just coincidentally non-throwing.
            assertThat(indexExists(connection, "physical_capacity_assignments", "idx_physical_assignments_student"))
                .isFalse();
        }
    }
}
