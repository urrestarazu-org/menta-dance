package com.menta.app.integration.auth;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationVersion;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Proves V28 adds the NOT NULL {@code demo} flag to {@code auth_users} (default false), leaves every existing row
 * unflagged (even one whose email is a demo seed email: no email is hardcoded in a migration), and that the operator-only V28.1 rollback removes
 * the column again.
 *
 * <p>Drives Flyway directly (not a {@code @SpringBootTest}) because the app's
 * {@code integration-test} profile replaces Flyway with Hibernate {@code create-drop}.</p>
 */
@Testcontainers
class AuthUserDemoFlagMigrationIntegrationTest {

    private static final String TABLE = "auth_users";
    /** Fixture only: emails the demo profile seeds. The migration must not treat them specially. */
    private static final List<String> SEED_EMAILS = List.of(
        "admin@menta.dance", "profesor@menta.dance", "alumno@menta.dance", "recepcion@menta.dance");

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

    private static void rollbackDemoFlag() {
        // V20.2, V21 and the earlier rollbacks sit below 28 in db/rollback and are never applied, so the
        // combined-location run must ignore them as "ignored" rather than fail validation.
        Flyway.configure()
            .dataSource(MYSQL.getJdbcUrl(), MYSQL.getUsername(), MYSQL.getPassword())
            .locations("classpath:db/migration", "classpath:db/rollback")
            .ignoreMigrationPatterns("*:ignored")
            .target(MigrationVersion.fromVersion("28.1"))
            .load()
            .migrate();
    }

    private static String columnInfo(Connection connection, String field) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
            "SELECT " + field + " FROM information_schema.columns "
                + "WHERE table_schema = DATABASE() AND table_name = ? AND column_name = 'demo'"
        )) {
            statement.setString(1, TABLE);
            try (ResultSet resultSet = statement.executeQuery()) {
                return resultSet.next() ? resultSet.getString(1) : null;
            }
        }
    }

    private static void insertUser(Connection connection, String email) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
            "INSERT INTO " + TABLE + " (id, email, password_hash, role, status, created_at, updated_at) "
                + "VALUES (UNHEX(REPLACE(UUID(),'-','')), ?, 'hash', 'STUDENT', 'ACTIVE', NOW(), NOW())"
        )) {
            statement.setString(1, email);
            statement.executeUpdate();
        }
    }

    private static Map<String, Boolean> demoByEmail(Connection connection) throws SQLException {
        Map<String, Boolean> flags = new HashMap<>();
        try (Statement statement = connection.createStatement();
             ResultSet resultSet = statement.executeQuery("SELECT email, demo FROM " + TABLE)) {
            while (resultSet.next()) {
                flags.put(resultSet.getString("email"), resultSet.getBoolean("demo"));
            }
        }
        return flags;
    }

    @Test
    void v28_adds_a_not_null_demo_column_defaulting_to_false() throws SQLException {
        migrateTo("28");

        try (Connection connection = connect()) {
            assertThat(columnInfo(connection, "is_nullable")).isEqualTo("NO");
            assertThat(columnInfo(connection, "column_default")).isEqualTo("0");
            assertThat(columnInfo(connection, "data_type")).isEqualTo("tinyint");

            insertUser(connection, "new@example.com");

            assertThat(demoByEmail(connection)).containsEntry("new@example.com", false);
        }
    }

    @Test
    void v28_never_infers_the_flag_from_the_email() throws SQLException {
        migrateTo("27");
        try (Connection connection = connect()) {
            for (String email : SEED_EMAILS) {
                insertUser(connection, email);
            }
            insertUser(connection, "student@example.com");
        }

        migrateTo("28");

        try (Connection connection = connect()) {
            Map<String, Boolean> flags = demoByEmail(connection);
            assertThat(flags).hasSize(5);
            assertThat(flags.values()).containsOnly(false);
        }
    }

    @Test
    void v28_1_rollback_removes_the_column_and_keeps_existing_users() throws SQLException {
        migrateTo("27");
        try (Connection connection = connect()) {
            insertUser(connection, "admin@menta.dance");
            insertUser(connection, "student@example.com");
        }
        migrateTo("28");

        rollbackDemoFlag();

        try (Connection connection = connect(); Statement statement = connection.createStatement()) {
            assertThat(columnInfo(connection, "column_name")).isNull();
            try (ResultSet resultSet = statement.executeQuery("SELECT COUNT(*) FROM " + TABLE)) {
                assertThat(resultSet.next()).isTrue();
                assertThat(resultSet.getInt(1)).isEqualTo(2);
            }
        }
    }
}
