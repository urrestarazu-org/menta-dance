package com.menta.app.integration.auth;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationVersion;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Proves V27 (#652) adds the nullable profile columns to {@code auth_users} without touching rows that
 * already exist, and that the operator-only V27.1 rollback removes the columns again.
 *
 * <p>Drives Flyway directly (not a {@code @SpringBootTest}) because the app's
 * {@code integration-test} profile replaces Flyway with Hibernate {@code create-drop}.</p>
 */
@Testcontainers
class AuthUserProfileMigrationIntegrationTest {

    private static final String TABLE = "auth_users";
    private static final List<String> PROFILE_COLUMNS = List.of("first_name", "last_name", "phone", "birth_date");

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

    private static void rollbackProfile() {
        // V20.2 and V21 sit below 27 in db/rollback and are never applied, so the
        // combined-location run must ignore them as "ignored" rather than fail validation.
        Flyway.configure()
            .dataSource(MYSQL.getJdbcUrl(), MYSQL.getUsername(), MYSQL.getPassword())
            .locations("classpath:db/migration", "classpath:db/rollback")
            .ignoreMigrationPatterns("*:ignored")
            .target(MigrationVersion.fromVersion("27.1"))
            .load()
            .migrate();
    }

    private static String columnInfo(Connection connection, String column, String field) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
            "SELECT " + field + " FROM information_schema.columns "
                + "WHERE table_schema = DATABASE() AND table_name = ? AND column_name = ?"
        )) {
            statement.setString(1, TABLE);
            statement.setString(2, column);
            try (ResultSet resultSet = statement.executeQuery()) {
                return resultSet.next() ? resultSet.getString(1) : null;
            }
        }
    }

    private static void insertLegacyUser(Connection connection) throws SQLException {
        try (Statement statement = connection.createStatement()) {
            statement.execute("INSERT INTO " + TABLE + " (id, email, password_hash, role, status, created_at, "
                + "updated_at) VALUES (UNHEX(REPLACE(UUID(),'-','')), 'legacy@example.com', 'legacy-hash', "
                + "'ADMIN', 'ACTIVE', NOW(), NOW())");
        }
    }

    private static void assertLegacyUserIntact(Connection connection) throws SQLException {
        try (Statement statement = connection.createStatement();
             ResultSet resultSet = statement.executeQuery(
                 "SELECT email, password_hash, role, status, token_version FROM " + TABLE)) {
            assertThat(resultSet.next()).isTrue();
            assertThat(resultSet.getString("email")).isEqualTo("legacy@example.com");
            assertThat(resultSet.getString("password_hash")).isEqualTo("legacy-hash");
            assertThat(resultSet.getString("role")).isEqualTo("ADMIN");
            assertThat(resultSet.getString("status")).isEqualTo("ACTIVE");
            assertThat(resultSet.getLong("token_version")).isEqualTo(1L);
            assertThat(resultSet.next()).isFalse();
        }
    }

    @Test
    void v27_keeps_existing_users_with_a_null_profile() throws SQLException {
        migrateTo("26");
        try (Connection connection = connect()) {
            insertLegacyUser(connection);
        }

        migrateTo("27");

        try (Connection connection = connect(); Statement statement = connection.createStatement()) {
            assertLegacyUserIntact(connection);
            try (ResultSet resultSet = statement.executeQuery(
                "SELECT first_name, last_name, phone, birth_date FROM " + TABLE)) {
                assertThat(resultSet.next()).isTrue();
                assertThat(resultSet.getString("first_name")).isNull();
                assertThat(resultSet.getString("last_name")).isNull();
                assertThat(resultSet.getString("phone")).isNull();
                assertThat(resultSet.getDate("birth_date")).isNull();
            }
        }
    }

    @Test
    void v27_adds_nullable_columns_sized_for_the_profile() throws SQLException {
        migrateTo("27");

        try (Connection connection = connect()) {
            for (String column : PROFILE_COLUMNS) {
                assertThat(columnInfo(connection, column, "is_nullable")).as(column).isEqualTo("YES");
                assertThat(columnInfo(connection, column, "column_default")).as(column).isNull();
            }
            assertThat(columnInfo(connection, "first_name", "character_maximum_length")).isEqualTo("100");
            assertThat(columnInfo(connection, "last_name", "character_maximum_length")).isEqualTo("100");
            assertThat(columnInfo(connection, "phone", "character_maximum_length")).isEqualTo("20");
            assertThat(columnInfo(connection, "birth_date", "data_type")).isEqualTo("date");
        }
    }

    @Test
    void v27_stores_a_user_with_a_profile_and_without_a_birth_date() throws SQLException {
        migrateTo("27");

        try (Connection connection = connect(); Statement statement = connection.createStatement()) {
            statement.execute("INSERT INTO " + TABLE + " (id, email, password_hash, role, status, created_at, "
                + "updated_at, first_name, last_name, phone, birth_date) VALUES (UNHEX(REPLACE(UUID(),'-','')), "
                + "'student@example.com', 'hash', 'STUDENT', 'PENDING_ACTIVATION', NOW(), NOW(), "
                + "'Ada', 'Lovelace', '+5491100000000', '1990-05-17')");
            statement.execute("INSERT INTO " + TABLE + " (id, email, password_hash, role, status, created_at, "
                + "updated_at, first_name, last_name, phone) VALUES (UNHEX(REPLACE(UUID(),'-','')), "
                + "'other@example.com', 'hash', 'STUDENT', 'PENDING_ACTIVATION', NOW(), NOW(), "
                + "'Grace', 'Hopper', '+12025550100')");

            try (ResultSet resultSet = statement.executeQuery(
                "SELECT first_name, last_name, phone, birth_date FROM " + TABLE + " ORDER BY email DESC")) {
                assertThat(resultSet.next()).isTrue();
                assertThat(resultSet.getString("first_name")).isEqualTo("Ada");
                assertThat(resultSet.getString("last_name")).isEqualTo("Lovelace");
                assertThat(resultSet.getString("phone")).isEqualTo("+5491100000000");
                assertThat(resultSet.getDate("birth_date").toString()).isEqualTo("1990-05-17");
                assertThat(resultSet.next()).isTrue();
                assertThat(resultSet.getString("phone")).isEqualTo("+12025550100");
                assertThat(resultSet.getDate("birth_date")).isNull();
            }
        }
    }

    @Test
    void v27_1_rollback_removes_the_columns_and_keeps_existing_users() throws SQLException {
        migrateTo("26");
        try (Connection connection = connect()) {
            insertLegacyUser(connection);
        }
        migrateTo("27");

        rollbackProfile();

        try (Connection connection = connect()) {
            for (String column : PROFILE_COLUMNS) {
                assertThat(columnInfo(connection, column, "column_name")).as(column).isNull();
            }
            assertLegacyUserIntact(connection);
        }
    }
}
