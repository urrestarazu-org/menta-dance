package com.menta.app.integration.billing;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.nio.ByteBuffer;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Timestamp;
import java.sql.Types;
import java.time.Instant;
import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationVersion;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Proves V18 (US-BILLING-012, #131) applies cleanly on top of the real migration history, and
 * that a pre-existing row survives it exactly as design A17's rehydration-safety claim states.
 *
 * <p>Deliberately not a {@code @SpringBootTest}: the app's {@code integration-test} profile
 * disables Flyway in favor of Hibernate's {@code ddl-auto: create-drop} (see {@code
 * application-integration-test.yml}), so every other integration test in this module never
 * actually runs a single migration script. This test drives Flyway directly against the real
 * {@code classpath:db/migration} scripts — the same ones the running application applies — so
 * V18 is proven the way it will actually run in production, not against a schema Hibernate
 * invented from the current entity mappings.</p>
 */
@Testcontainers
class SubscriptionTrialMigrationIntegrationTest {

    @Container
    private static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.0")
        .withDatabaseName("menta_migration_test")
        .withUsername("test")
        .withPassword("test");

    private static byte[] uuidToBytes(UUID uuid) {
        ByteBuffer buffer = ByteBuffer.wrap(new byte[16]);
        buffer.putLong(uuid.getMostSignificantBits());
        buffer.putLong(uuid.getLeastSignificantBits());
        return buffer.array();
    }

    private static Connection connect() throws java.sql.SQLException {
        return DriverManager.getConnection(MYSQL.getJdbcUrl(), MYSQL.getUsername(), MYSQL.getPassword());
    }

    private static void seedPlan(Connection connection, UUID planId, Instant now) throws java.sql.SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
            "INSERT INTO billing_plans (id, name, description, price, currency, duration_days, featured, "
                + "status, terms_and_conditions, cancellation_policy, created_at, updated_at) "
                + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)"
        )) {
            statement.setBytes(1, uuidToBytes(planId));
            statement.setString(2, "Plan Mensual");
            statement.setString(3, "Acceso mensual");
            statement.setBigDecimal(4, new BigDecimal("15000.00"));
            statement.setString(5, "ARS");
            statement.setInt(6, 30);
            statement.setBoolean(7, false);
            statement.setString(8, "ACTIVE");
            statement.setString(9, "Términos");
            statement.setString(10, "Política");
            statement.setTimestamp(11, Timestamp.from(now));
            statement.setTimestamp(12, Timestamp.from(now));
            statement.executeUpdate();
        }
    }

    private static void seedPayment(Connection connection, UUID paymentId, UUID userId, Instant now)
        throws java.sql.SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
            "INSERT INTO billing_payments (id, user_id, provider_payment_id, expected_amount, expected_currency, "
                + "expected_external_reference, expected_merchant_account_id, target_modality, target_reference, "
                + "status_type, status_reason, status_changed_at, created_at) "
                + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)"
        )) {
            statement.setBytes(1, uuidToBytes(paymentId));
            statement.setBytes(2, uuidToBytes(userId));
            statement.setString(3, "mp-" + paymentId);
            statement.setBigDecimal(4, new BigDecimal("15000.00"));
            statement.setString(5, "ARS");
            statement.setString(6, "SUB-" + paymentId);
            statement.setString(7, "merchant-1");
            statement.setString(8, "VIRTUAL");
            statement.setString(9, "plan-ref");
            statement.setString(10, "COMPLETED");
            statement.setNull(11, Types.VARCHAR);
            statement.setTimestamp(12, Timestamp.from(now));
            statement.setTimestamp(13, Timestamp.from(now));
            statement.executeUpdate();
        }
    }

    /**
     * Inserted against the pre-V18 shape of {@code billing_subscriptions}: {@code payment_id}
     * NOT NULL, no {@code type}, no grant columns, no {@code version} — exactly what every row
     * written before this change looks like.
     */
    private static void seedPreV18Subscription(
        Connection connection, UUID subscriptionId, UUID paymentId, UUID userId, UUID planId, Instant now
    ) throws java.sql.SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
            "INSERT INTO billing_subscriptions (id, payment_id, user_id, plan_id, idempotency_key, "
                + "active_user_id, status, fulfillment_status, start_date, end_date, provider_preference_id, "
                + "checkout_url, created_at, cancelled_at, cancelled_by, cancellation_reason) "
                + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)"
        )) {
            statement.setBytes(1, uuidToBytes(subscriptionId));
            statement.setBytes(2, uuidToBytes(paymentId));
            statement.setBytes(3, uuidToBytes(userId));
            statement.setBytes(4, uuidToBytes(planId));
            statement.setString(5, "idem-" + subscriptionId);
            statement.setBytes(6, uuidToBytes(userId));
            statement.setString(7, "ACTIVE");
            statement.setString(8, "ASSIGNED");
            statement.setTimestamp(9, Timestamp.from(now));
            statement.setTimestamp(10, Timestamp.from(now.plusSeconds(30L * 86_400)));
            statement.setNull(11, Types.VARCHAR);
            statement.setNull(12, Types.VARCHAR);
            statement.setTimestamp(13, Timestamp.from(now));
            statement.setNull(14, Types.TIMESTAMP);
            statement.setNull(15, Types.BINARY);
            statement.setNull(16, Types.VARCHAR);
            statement.executeUpdate();
        }
    }

    private static boolean columnExists(Connection connection, String column) throws java.sql.SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
            "SELECT COUNT(*) FROM information_schema.columns "
                + "WHERE table_schema = DATABASE() AND table_name = 'billing_subscriptions' AND column_name = ?"
        )) {
            statement.setString(1, column);
            try (ResultSet resultSet = statement.executeQuery()) {
                resultSet.next();
                return resultSet.getInt(1) == 1;
            }
        }
    }

    private static String paymentIdNullability(Connection connection) throws java.sql.SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
            "SELECT is_nullable FROM information_schema.columns "
                + "WHERE table_schema = DATABASE() AND table_name = 'billing_subscriptions' "
                + "AND column_name = 'payment_id'"
        ); ResultSet resultSet = statement.executeQuery()) {
            resultSet.next();
            return resultSet.getString(1);
        }
    }

    private static boolean indexExists(Connection connection, String indexName) throws java.sql.SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
            "SELECT COUNT(*) FROM information_schema.statistics "
                + "WHERE table_schema = DATABASE() AND table_name = 'billing_subscriptions' AND index_name = ?"
        )) {
            statement.setString(1, indexName);
            try (ResultSet resultSet = statement.executeQuery()) {
                resultSet.next();
                return resultSet.getInt(1) > 0;
            }
        }
    }

    @Test
    void v18_applies_cleanly_and_a_pre_existing_row_survives_as_a_paid_subscription_at_version_zero()
        throws java.sql.SQLException {
        UUID planId = UUID.randomUUID();
        UUID paymentId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        UUID subscriptionId = UUID.randomUUID();
        Instant now = Instant.parse("2026-08-30T12:00:00Z");

        // Migrate only up to V17 — the exact schema every row was ever written against before
        // this change.
        Flyway.configure()
            .dataSource(MYSQL.getJdbcUrl(), MYSQL.getUsername(), MYSQL.getPassword())
            .locations("classpath:db/migration")
            .target(MigrationVersion.fromVersion("17"))
            .load()
            .migrate();

        try (Connection connection = connect()) {
            seedPlan(connection, planId, now);
            seedPayment(connection, paymentId, userId, now);
            seedPreV18Subscription(connection, subscriptionId, paymentId, userId, planId, now);
        }

        // Now migrate the rest of the way, applying V18 on top of the seeded pre-existing row.
        Flyway.configure()
            .dataSource(MYSQL.getJdbcUrl(), MYSQL.getUsername(), MYSQL.getPassword())
            .locations("classpath:db/migration")
            .load()
            .migrate();

        try (Connection connection = connect()) {
            assertThat(columnExists(connection, "type")).isTrue();
            assertThat(columnExists(connection, "granted_at")).isTrue();
            assertThat(columnExists(connection, "granted_by")).isTrue();
            assertThat(columnExists(connection, "grant_reason")).isTrue();
            assertThat(columnExists(connection, "grant_days")).isTrue();
            assertThat(columnExists(connection, "version")).isTrue();
            assertThat(paymentIdNullability(connection)).isEqualTo("YES");
            assertThat(indexExists(connection, "idx_billing_subscriptions_status_end_date")).isTrue();

            try (PreparedStatement statement = connection.prepareStatement(
                "SELECT payment_id, type, version, granted_at, granted_by, grant_reason, grant_days "
                    + "FROM billing_subscriptions WHERE id = ?"
            )) {
                statement.setBytes(1, uuidToBytes(subscriptionId));
                try (ResultSet resultSet = statement.executeQuery()) {
                    assertThat(resultSet.next()).isTrue();
                    assertThat(resultSet.getBytes("payment_id")).isEqualTo(uuidToBytes(paymentId));
                    assertThat(resultSet.wasNull()).isFalse();
                    assertThat(resultSet.getString("type")).isEqualTo("PAID");
                    assertThat(resultSet.getLong("version")).isEqualTo(0L);
                    resultSet.getTimestamp("granted_at");
                    assertThat(resultSet.wasNull()).isTrue();
                    resultSet.getBytes("granted_by");
                    assertThat(resultSet.wasNull()).isTrue();
                    resultSet.getString("grant_reason");
                    assertThat(resultSet.wasNull()).isTrue();
                    resultSet.getObject("grant_days");
                    assertThat(resultSet.wasNull()).isTrue();
                }
            }
        }
    }
}
