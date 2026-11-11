package com.menta.app.integration.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.menta.testsupport.MySqlTestImage;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationVersion;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Proves V29 creates the append-only {@code auth_user_audit} table (#378) with the agreed columns, a foreign key
 * to {@code auth_users} and an index by user, leaves existing users untouched, and that the operator-only V29.1
 * rollback drops the table again without touching users.
 *
 * <p>Drives Flyway directly (not a {@code @SpringBootTest}) because the app's
 * {@code integration-test} profile replaces Flyway with Hibernate {@code create-drop}.</p>
 */
@Testcontainers
class AuthUserAuditMigrationIntegrationTest {

    private static final String TABLE = "auth_user_audit";

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

    private static void rollbackUserAudit() {
        // The rollbacks below 29 in db/rollback are never applied, so the combined-location run must ignore
        // them as "ignored" rather than fail validation.
        Flyway.configure()
            .dataSource(MYSQL.getJdbcUrl(), MYSQL.getUsername(), MYSQL.getPassword())
            .locations("classpath:db/migration", "classpath:db/rollback")
            .ignoreMigrationPatterns("*:ignored")
            .target(MigrationVersion.fromVersion("29.1"))
            .load()
            .migrate();
    }

    private static List<String> columns(Connection connection) throws SQLException {
        List<String> columns = new ArrayList<>();
        try (PreparedStatement statement = connection.prepareStatement(
            "SELECT column_name, column_type, is_nullable FROM information_schema.columns "
                + "WHERE table_schema = DATABASE() AND table_name = ? ORDER BY ordinal_position"
        )) {
            statement.setString(1, TABLE);
            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    columns.add(resultSet.getString(1) + ":" + resultSet.getString(2) + ":" + resultSet.getString(3));
                }
            }
        }
        return columns;
    }

    private static void insertUser(Connection connection, String email) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
            "INSERT INTO auth_users (id, email, password_hash, role, status, created_at, updated_at) "
                + "VALUES (UNHEX(REPLACE(UUID(),'-','')), ?, 'hash', 'STUDENT', 'ACTIVE', NOW(), NOW())"
        )) {
            statement.setString(1, email);
            statement.executeUpdate();
        }
    }

    private static void insertAudit(Connection connection, String userEmail) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
            "INSERT INTO " + TABLE + " (id, user_id, actor_id, action, previous_value, new_value, created_at) "
                + "SELECT UNHEX(REPLACE(UUID(),'-','')), id, UNHEX(REPLACE(UUID(),'-','')), 'ROLE_CHANGED', "
                + "'STUDENT', 'INSTRUCTOR', NOW(3) FROM auth_users WHERE email = ?"
        )) {
            statement.setString(1, userEmail);
            statement.executeUpdate();
        }
    }

    private static int count(Connection connection, String table) throws SQLException {
        try (Statement statement = connection.createStatement();
             ResultSet resultSet = statement.executeQuery("SELECT COUNT(*) FROM " + table)) {
            resultSet.next();
            return resultSet.getInt(1);
        }
    }

    @Test
    void v29_creates_the_audit_table_with_the_agreed_columns() throws SQLException {
        migrateTo("29");

        try (Connection connection = connect()) {
            assertThat(columns(connection)).containsExactly(
                "id:binary(16):NO",
                "user_id:binary(16):NO",
                "actor_id:binary(16):NO",
                "action:varchar(50):NO",
                "previous_value:varchar(255):YES",
                "new_value:varchar(255):YES",
                "created_at:datetime(3):NO"
            );
        }
    }

    @Test
    void v29_indexes_the_audit_by_user_and_keeps_a_foreign_key_to_the_users() throws SQLException {
        migrateTo("29");

        try (Connection connection = connect()) {
            insertUser(connection, "student@example.com");
            insertAudit(connection, "student@example.com");
            assertThat(count(connection, TABLE)).isEqualTo(1);

            try (Statement statement = connection.createStatement()) {
                assertThatThrownBy(() -> statement.executeUpdate(
                    "INSERT INTO " + TABLE + " (id, user_id, actor_id, action, created_at) VALUES "
                        + "(UNHEX(REPLACE(UUID(),'-','')), UNHEX(REPLACE(UUID(),'-','')), "
                        + "UNHEX(REPLACE(UUID(),'-','')), 'ROLE_CHANGED', NOW(3))"
                )).isInstanceOf(SQLException.class).hasMessageContaining("foreign key");
            }

            try (PreparedStatement statement = connection.prepareStatement(
                "SELECT COUNT(*) FROM information_schema.statistics "
                    + "WHERE table_schema = DATABASE() AND table_name = ? AND column_name = 'user_id' "
                    + "AND seq_in_index = 1"
            )) {
                statement.setString(1, TABLE);
                try (ResultSet resultSet = statement.executeQuery()) {
                    resultSet.next();
                    assertThat(resultSet.getInt(1)).isPositive();
                }
            }
        }
    }

    @Test
    void v29_leaves_the_existing_users_untouched() throws SQLException {
        migrateTo("28");
        try (Connection connection = connect()) {
            insertUser(connection, "admin@example.com");
            insertUser(connection, "student@example.com");
        }

        migrateTo("29");

        try (Connection connection = connect()) {
            assertThat(count(connection, "auth_users")).isEqualTo(2);
            assertThat(count(connection, TABLE)).isZero();
        }
    }

    @Test
    void v29_1_rollback_drops_the_audit_table_and_keeps_the_users() throws SQLException {
        migrateTo("28");
        try (Connection connection = connect()) {
            insertUser(connection, "student@example.com");
        }
        migrateTo("29");
        try (Connection connection = connect()) {
            insertAudit(connection, "student@example.com");
        }

        rollbackUserAudit();

        try (Connection connection = connect()) {
            assertThat(columns(connection)).isEmpty();
            assertThat(count(connection, "auth_users")).isEqualTo(1);
        }
    }
}
