package com.menta.app.integration.billing;

import static org.assertj.core.api.Assertions.assertThat;

import com.menta.testsupport.MySqlTestImage;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
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
 * Proves V31 (#767) replaces {@code idx_billing_payments_user_id} with the composite {@code
 * idx_billing_payments_user_created (user_id, created_at)} behind {@code GET
 * /api/v1/billing/payments/me}, and that the operator-only V31.1 rollback puts the single-column
 * index back.
 *
 * <p>Drives Flyway directly (not a {@code @SpringBootTest}) because the app's {@code
 * integration-test} profile replaces Flyway with Hibernate {@code create-drop}.</p>
 */
@Testcontainers
class PaymentsUserCreatedIndexMigrationIntegrationTest {

    private static final String COMPOSITE = "idx_billing_payments_user_created";
    private static final String SINGLE = "idx_billing_payments_user_id";

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

    private static void rollbackIndex() {
        // The rollbacks below 31 sit in db/rollback and are never applied, so the combined-location run must
        // ignore them as "ignored" rather than fail validation.
        Flyway.configure()
            .dataSource(MYSQL.getJdbcUrl(), MYSQL.getUsername(), MYSQL.getPassword())
            .locations("classpath:db/migration", "classpath:db/rollback")
            .ignoreMigrationPatterns("*:ignored")
            .target(MigrationVersion.fromVersion("31.1"))
            .load()
            .migrate();
    }

    /** The index's columns in key order; empty when the index does not exist. */
    private static List<String> indexColumns(String indexName) throws SQLException {
        try (Connection connection = connect(); PreparedStatement statement = connection.prepareStatement(
            "SELECT column_name FROM information_schema.statistics "
                + "WHERE table_schema = DATABASE() AND table_name = 'billing_payments' AND index_name = ? "
                + "ORDER BY seq_in_index"
        )) {
            statement.setString(1, indexName);
            List<String> columns = new ArrayList<>();
            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    columns.add(resultSet.getString(1));
                }
            }
            return columns;
        }
    }

    @Test
    void before_v31_only_the_single_column_user_index_exists() throws SQLException {
        migrateTo("30");

        assertThat(indexColumns(SINGLE)).containsExactly("user_id");
        assertThat(indexColumns(COMPOSITE)).isEmpty();
    }

    @Test
    void v31_replaces_the_user_index_with_user_and_created_at() throws SQLException {
        migrateTo("31");

        assertThat(indexColumns(COMPOSITE)).containsExactly("user_id", "created_at");
        assertThat(indexColumns(SINGLE)).isEmpty();
    }

    @Test
    void v31_1_rollback_restores_the_single_column_user_index() throws SQLException {
        migrateTo("31");

        rollbackIndex();

        assertThat(indexColumns(SINGLE)).containsExactly("user_id");
        assertThat(indexColumns(COMPOSITE)).isEmpty();
    }
}
