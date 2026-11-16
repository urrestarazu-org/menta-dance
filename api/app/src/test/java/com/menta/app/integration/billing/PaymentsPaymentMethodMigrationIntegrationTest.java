package com.menta.app.integration.billing;

import static org.assertj.core.api.Assertions.assertThat;

import com.menta.testsupport.MySqlTestImage;
import java.math.BigDecimal;
import java.nio.ByteBuffer;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.sql.Types;
import java.time.Instant;
import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationVersion;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Proves V32 (#771) adds {@code billing_payments.payment_method}, backfills every existing row with
 * the rule {@code PaymentRepositoryAdapter} used to derive it at read time, leaves the column
 * {@code NOT NULL}, and that the operator-only V32.1 rollback drops it again.
 *
 * <p>Drives Flyway directly (not a {@code @SpringBootTest}) because the app's {@code
 * integration-test} profile replaces Flyway with Hibernate {@code create-drop}.</p>
 */
@Testcontainers
class PaymentsPaymentMethodMigrationIntegrationTest {

    private static final Instant NOW = Instant.parse("2026-11-16T12:00:00Z");

    @Container
    private static final MySQLContainer<?> MYSQL = new MySQLContainer<>(MySqlTestImage.NAME)
        .withDatabaseName("menta_migration_test")
        .withUsername("test")
        .withPassword("test");

    private static byte[] uuidToBytes(UUID uuid) {
        ByteBuffer buffer = ByteBuffer.wrap(new byte[16]);
        buffer.putLong(uuid.getMostSignificantBits());
        buffer.putLong(uuid.getLeastSignificantBits());
        return buffer.array();
    }

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

    private static void rollbackColumn() {
        // The rollbacks below 32 sit in db/rollback and are never applied, so the combined-location run must
        // ignore them as "ignored" rather than fail validation.
        Flyway.configure()
            .dataSource(MYSQL.getJdbcUrl(), MYSQL.getUsername(), MYSQL.getPassword())
            .locations("classpath:db/migration", "classpath:db/rollback")
            .ignoreMigrationPatterns("*:ignored")
            .target(MigrationVersion.fromVersion("32.1"))
            .load()
            .migrate();
    }

    private static UUID seedPlan(Connection connection) throws SQLException {
        UUID planId = UUID.randomUUID();
        try (PreparedStatement statement = connection.prepareStatement(
            "INSERT INTO billing_plans (id, name, description, price, currency, duration_days, featured, "
                + "status, terms_and_conditions, cancellation_policy, created_at, updated_at) "
                + "VALUES (?, 'Plan', 'desc', 15000.00, 'ARS', 30, 0, 'ACTIVE', 't', 'p', ?, ?)"
        )) {
            statement.setBytes(1, uuidToBytes(planId));
            statement.setTimestamp(2, Timestamp.from(NOW));
            statement.setTimestamp(3, Timestamp.from(NOW));
            statement.executeUpdate();
        }
        return planId;
    }

    /** A pre-V32 payment row: the column list deliberately has no {@code payment_method}. */
    private static UUID seedPayment(Connection connection, String modality, String reference) throws SQLException {
        UUID paymentId = UUID.randomUUID();
        try (PreparedStatement statement = connection.prepareStatement(
            "INSERT INTO billing_payments (id, user_id, provider_payment_id, expected_amount, expected_currency, "
                + "expected_external_reference, expected_merchant_account_id, target_modality, target_reference, "
                + "status_type, status_reason, status_changed_at, created_at) "
                + "VALUES (?, ?, NULL, ?, 'ARS', ?, 'merchant-1', ?, 'ref-1', 'AWAITING_PROVIDER', NULL, NULL, ?)"
        )) {
            statement.setBytes(1, uuidToBytes(paymentId));
            statement.setBytes(2, uuidToBytes(UUID.randomUUID()));
            statement.setBigDecimal(3, new BigDecimal("15000.00"));
            statement.setString(4, reference);
            statement.setString(5, modality);
            statement.setTimestamp(6, Timestamp.from(NOW));
            statement.executeUpdate();
        }
        return paymentId;
    }

    private static void seedSubscription(Connection connection, UUID paymentId, UUID planId, String preferenceId)
        throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
            "INSERT INTO billing_subscriptions (id, payment_id, user_id, plan_id, idempotency_key, "
                + "active_user_id, status, fulfillment_status, provider_preference_id, created_at) "
                + "VALUES (?, ?, ?, ?, ?, NULL, 'PENDING', 'ASSIGNED', ?, ?)"
        )) {
            statement.setBytes(1, uuidToBytes(UUID.randomUUID()));
            statement.setBytes(2, uuidToBytes(paymentId));
            statement.setBytes(3, uuidToBytes(UUID.randomUUID()));
            statement.setBytes(4, uuidToBytes(planId));
            statement.setString(5, "idem-" + paymentId);
            if (preferenceId == null) {
                statement.setNull(6, Types.VARCHAR);
            } else {
                statement.setString(6, preferenceId);
            }
            statement.setTimestamp(7, Timestamp.from(NOW));
            statement.executeUpdate();
        }
    }

    private static String paymentMethodOf(UUID paymentId) throws SQLException {
        try (Connection connection = connect(); PreparedStatement statement = connection.prepareStatement(
            "SELECT payment_method FROM billing_payments WHERE id = ?"
        )) {
            statement.setBytes(1, uuidToBytes(paymentId));
            try (ResultSet resultSet = statement.executeQuery()) {
                resultSet.next();
                return resultSet.getString(1);
            }
        }
    }

    /** {@code YES}/{@code NO}, or {@code null} when the column does not exist. */
    private static String paymentMethodNullability() throws SQLException {
        try (Connection connection = connect(); PreparedStatement statement = connection.prepareStatement(
            "SELECT is_nullable FROM information_schema.columns "
                + "WHERE table_schema = DATABASE() AND table_name = 'billing_payments' "
                + "AND column_name = 'payment_method'"
        ); ResultSet resultSet = statement.executeQuery()) {
            return resultSet.next() ? resultSet.getString(1) : null;
        }
    }

    @Test
    void before_v32_the_payments_table_has_no_payment_method_column() throws SQLException {
        migrateTo("31");

        assertThat(paymentMethodNullability()).isNull();
    }

    @Test
    void v32_backfills_every_existing_payment_with_the_rule_the_read_side_used_to_derive() throws SQLException {
        migrateTo("31");
        UUID subscriptionWithPreference;
        UUID subscriptionWithoutPreference;
        UUID physicalCheckoutPro;
        UUID physicalBankTransfer;
        UUID subscriptionWithoutRow;
        try (Connection connection = connect()) {
            UUID planId = seedPlan(connection);
            subscriptionWithPreference = seedPayment(connection, "VIRTUAL", "SUB-with-preference");
            seedSubscription(connection, subscriptionWithPreference, planId, "pref-123");
            subscriptionWithoutPreference = seedPayment(connection, "VIRTUAL", "SUB-without-preference");
            seedSubscription(connection, subscriptionWithoutPreference, planId, null);
            physicalCheckoutPro = seedPayment(connection, "PHYSICAL", "PHY-0b6c1f9e");
            physicalBankTransfer = seedPayment(connection, "PHYSICAL", "PHY-BT-0b6c1f9e");
            // The edge: a hand-loaded subscription payment with no subscription row at all. Today's read-side
            // rule has no preference to look at and answers BANK_TRANSFER; the backfill keeps that answer.
            subscriptionWithoutRow = seedPayment(connection, "VIRTUAL", "SUB-orphan");
        }

        migrateTo("32");

        assertThat(paymentMethodOf(subscriptionWithPreference)).isEqualTo("MERCADO_PAGO");
        assertThat(paymentMethodOf(subscriptionWithoutPreference)).isEqualTo("BANK_TRANSFER");
        assertThat(paymentMethodOf(physicalCheckoutPro)).isEqualTo("MERCADO_PAGO");
        assertThat(paymentMethodOf(physicalBankTransfer)).isEqualTo("BANK_TRANSFER");
        assertThat(paymentMethodOf(subscriptionWithoutRow)).isEqualTo("BANK_TRANSFER");
    }

    @Test
    void v32_leaves_the_column_not_null() throws SQLException {
        migrateTo("32");

        assertThat(paymentMethodNullability()).isEqualTo("NO");
    }

    @Test
    void v32_1_rollback_drops_the_column() throws SQLException {
        migrateTo("32");

        rollbackColumn();

        assertThat(paymentMethodNullability()).isNull();
    }
}
