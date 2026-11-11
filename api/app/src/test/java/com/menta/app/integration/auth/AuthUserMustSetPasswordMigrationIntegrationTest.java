package com.menta.app.integration.auth;

import static org.assertj.core.api.Assertions.assertThat;

import com.menta.testsupport.MySqlTestImage;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.HashMap;
import java.util.Map;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationVersion;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Proves V30 adds the NOT NULL {@code must_set_password} flag to {@code auth_users} (default false), leaves every
 * existing row unflagged, and that the operator-only V30.1 rollback removes the column again.
 *
 * <p>Drives Flyway directly (not a {@code @SpringBootTest}) because the app's
 * {@code integration-test} profile replaces Flyway with Hibernate {@code create-drop}.</p>
 */
@Testcontainers
class AuthUserMustSetPasswordMigrationIntegrationTest {

    private static final String TABLE = "auth_users";

    @Container
    private static final MySQLContainer<?> MYSQL = new MySQLContainer<>(MySqlTestImage.NAME)
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

    private static void rollbackMustSetPassword() {
        // The rollbacks below 30 sit in db/rollback and are never applied, so the combined-location run must
        // ignore them as "ignored" rather than fail validation.
        Flyway.configure()
            .dataSource(MYSQL.getJdbcUrl(), MYSQL.getUsername(), MYSQL.getPassword())
            .locations("classpath:db/migration", "classpath:db/rollback")
            .ignoreMigrationPatterns("*:ignored")
            .target(MigrationVersion.fromVersion("30.1"))
            .load()
            .migrate();
    }

    private static String columnInfo(Connection connection, String field) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
            "SELECT " + field + " FROM information_schema.columns "
                + "WHERE table_schema = DATABASE() AND table_name = ? AND column_name = 'must_set_password'"
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

    private static Map<String, Boolean> flagByEmail(Connection connection) throws SQLException {
        Map<String, Boolean> flags = new HashMap<>();
        try (Statement statement = connection.createStatement();
             ResultSet resultSet = statement.executeQuery("SELECT email, must_set_password FROM " + TABLE)) {
            while (resultSet.next()) {
                flags.put(resultSet.getString("email"), resultSet.getBoolean("must_set_password"));
            }
        }
        return flags;
    }

    @Test
    void v30_adds_a_not_null_column_defaulting_to_false() throws SQLException {
        migrateTo("30");

        try (Connection connection = connect()) {
            assertThat(columnInfo(connection, "is_nullable")).isEqualTo("NO");
            assertThat(columnInfo(connection, "column_default")).isEqualTo("0");
            assertThat(columnInfo(connection, "data_type")).isEqualTo("tinyint");

            insertUser(connection, "new@example.com");

            assertThat(flagByEmail(connection)).containsEntry("new@example.com", false);
        }
    }

    @Test
    void v30_leaves_every_existing_account_unflagged() throws SQLException {
        migrateTo("29");
        try (Connection connection = connect()) {
            insertUser(connection, "admin@menta.dance");
            insertUser(connection, "student@example.com");
        }

        migrateTo("30");

        try (Connection connection = connect()) {
            Map<String, Boolean> flags = flagByEmail(connection);
            assertThat(flags).hasSize(2);
            assertThat(flags.values()).containsOnly(false);
        }
    }

    @Test
    void v30_1_rollback_removes_the_column_and_keeps_existing_users() throws SQLException {
        migrateTo("29");
        try (Connection connection = connect()) {
            insertUser(connection, "admin@menta.dance");
            insertUser(connection, "student@example.com");
        }
        migrateTo("30");

        rollbackMustSetPassword();

        try (Connection connection = connect(); Statement statement = connection.createStatement()) {
            assertThat(columnInfo(connection, "column_name")).isNull();
            try (ResultSet resultSet = statement.executeQuery("SELECT COUNT(*) FROM " + TABLE)) {
                assertThat(resultSet.next()).isTrue();
                assertThat(resultSet.getInt(1)).isEqualTo(2);
            }
        }
    }
}
