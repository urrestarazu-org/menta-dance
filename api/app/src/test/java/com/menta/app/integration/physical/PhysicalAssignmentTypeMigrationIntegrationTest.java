package com.menta.app.integration.physical;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationVersion;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Proves V26 (#521) adds {@code assignment_type} to {@code physical_capacity_assignments}
 * as NOT NULL, labels pre-existing rows {@code MONTHLY}, drops the column default, and that
 * the operator-only V26.1 rollback removes the column again.
 *
 * <p>Drives Flyway directly (not a {@code @SpringBootTest}) because the app's
 * {@code integration-test} profile replaces Flyway with Hibernate {@code create-drop}.</p>
 */
@Testcontainers
class PhysicalAssignmentTypeMigrationIntegrationTest {

    private static final String TABLE = "physical_capacity_assignments";

    @Container
    private static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.0.46")
        .withDatabaseName("menta_migration_test")
        .withUsername("test")
        .withPassword("test");

    private static Connection connect() throws SQLException {
        return DriverManager.getConnection(MYSQL.getJdbcUrl(), MYSQL.getUsername(), MYSQL.getPassword());
    }

    @BeforeEach
    void resetSchema() {
        Flyway.configure()
            .dataSource(MYSQL.getJdbcUrl(), MYSQL.getUsername(), MYSQL.getPassword())
            .locations("classpath:db/migration", "classpath:db/rollback")
            .cleanDisabled(false)
            .load()
            .clean();
    }

    private static void migrateTo(String version) {
        Flyway.configure()
            .dataSource(MYSQL.getJdbcUrl(), MYSQL.getUsername(), MYSQL.getPassword())
            .locations("classpath:db/migration")
            .target(MigrationVersion.fromVersion(version))
            .load()
            .migrate();
    }

    private static String columnInfo(Connection connection, String field) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
            "SELECT " + field + " FROM information_schema.columns "
                + "WHERE table_schema = DATABASE() AND table_name = ? AND column_name = 'assignment_type'"
        )) {
            statement.setString(1, TABLE);
            try (ResultSet resultSet = statement.executeQuery()) {
                return resultSet.next() ? resultSet.getString(1) : null;
            }
        }
    }

    private static boolean columnExists(Connection connection) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
            "SELECT COUNT(*) FROM information_schema.columns "
                + "WHERE table_schema = DATABASE() AND table_name = ? AND column_name = 'assignment_type'"
        )) {
            statement.setString(1, TABLE);
            try (ResultSet resultSet = statement.executeQuery()) {
                resultSet.next();
                return resultSet.getInt(1) == 1;
            }
        }
    }

    @Test
    void v26_labels_existing_rows_monthly_and_drops_the_default() throws SQLException {
        migrateTo("25");
        try (Connection connection = connect(); Statement statement = connection.createStatement()) {
            statement.execute("SET FOREIGN_KEY_CHECKS=0");
            statement.execute("INSERT INTO " + TABLE + " (id, session_id, student_id, created_at) "
                + "VALUES (UNHEX(REPLACE(UUID(),'-','')), UNHEX(REPLACE(UUID(),'-','')), "
                + "UNHEX(REPLACE(UUID(),'-','')), NOW(3))");
            statement.execute("SET FOREIGN_KEY_CHECKS=1");
        }

        migrateTo("26");

        try (Connection connection = connect(); Statement statement = connection.createStatement()) {
            try (ResultSet resultSet = statement.executeQuery("SELECT assignment_type FROM " + TABLE)) {
                assertThat(resultSet.next()).isTrue();
                assertThat(resultSet.getString(1)).isEqualTo("MONTHLY");
            }
            assertThat(columnInfo(connection, "is_nullable")).isEqualTo("NO");
            assertThat(columnInfo(connection, "column_default")).isNull();
        }
    }

    @Test
    void v26_rejects_an_insert_that_omits_the_type() throws SQLException {
        migrateTo("26");
        try (Connection connection = connect(); Statement statement = connection.createStatement()) {
            statement.execute("SET FOREIGN_KEY_CHECKS=0");
            assertThatThrownBy(() -> statement.execute(
                "INSERT INTO " + TABLE + " (id, session_id, student_id, created_at) "
                    + "VALUES (UNHEX(REPLACE(UUID(),'-','')), UNHEX(REPLACE(UUID(),'-','')), "
                    + "UNHEX(REPLACE(UUID(),'-','')), NOW(3))"
            )).isInstanceOf(SQLException.class);
        }
    }

    @Test
    void v26_1_rollback_removes_the_column() throws SQLException {
        migrateTo("26");

        // V20.2 and V21 sit below 26 in db/rollback and are never applied, so the
        // combined-location run must ignore them as "ignored" rather than fail validation.
        Flyway.configure()
            .dataSource(MYSQL.getJdbcUrl(), MYSQL.getUsername(), MYSQL.getPassword())
            .locations("classpath:db/migration", "classpath:db/rollback")
            .ignoreMigrationPatterns("*:ignored")
            .target(MigrationVersion.fromVersion("26.1"))
            .load()
            .migrate();

        try (Connection connection = connect()) {
            assertThat(columnExists(connection)).isFalse();
        }
    }
}
