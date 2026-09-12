package com.menta.app.integration.billing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

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
import org.flywaydb.core.api.FlywayException;
import org.flywaydb.core.api.MigrationVersion;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Proves V20 (#41, US-PHYSICAL-004, design A1) applies cleanly on top of the real migration
 * history and preserves every pre-existing single-session {@code billing_purchases} row with
 * exact row-count parity, and that the compensating V21 refuses to revert once a purchase covers
 * more than one session.
 *
 * <p>Deliberately not a {@code @SpringBootTest} — same rationale as {@link
 * SubscriptionTrialMigrationIntegrationTest}: the {@code integration-test} profile disables
 * Flyway in favor of Hibernate's {@code ddl-auto: create-drop}, so this test drives Flyway
 * directly against the real {@code classpath:db/migration} scripts.
 *
 * <p>V21 deliberately does NOT live under {@code classpath:db/migration} (the app's and this
 * test's default Flyway location): a normal "migrate to latest" would apply it automatically
 * right after V20, reverting the very feature this change ships. It lives under {@code
 * classpath:db/rollback} instead, and is only exercised here by explicitly adding that second
 * location — exactly how an operator would invoke the escape hatch.</p>
 */
@Testcontainers
class PurchaseSessionsMigrationIntegrationTest {

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

    /**
     * The MySQL container is {@code static} (shared across every test method in this class, to
     * avoid paying its ~8s startup cost per test) so each test resets the schema before migrating
     * — otherwise V20/V21 applied by an earlier test would leak into the next one.
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

    private static void migrateToV19(Connection ignored) {
        Flyway.configure()
            .dataSource(MYSQL.getJdbcUrl(), MYSQL.getUsername(), MYSQL.getPassword())
            .locations("classpath:db/migration")
            .target(MigrationVersion.fromVersion("19"))
            .load()
            .migrate();
    }

    private static void migrateToLatest() {
        Flyway.configure()
            .dataSource(MYSQL.getJdbcUrl(), MYSQL.getUsername(), MYSQL.getPassword())
            .locations("classpath:db/migration")
            .load()
            .migrate();
    }

    private static void migrateIncludingRollbackTo(String version) {
        Flyway.configure()
            .dataSource(MYSQL.getJdbcUrl(), MYSQL.getUsername(), MYSQL.getPassword())
            .locations("classpath:db/migration", "classpath:db/rollback")
            .target(MigrationVersion.fromVersion(version))
            .load()
            .migrate();
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
            statement.setBigDecimal(4, new BigDecimal("5000.00"));
            statement.setString(5, "ARS");
            statement.setString(6, "PHY-" + paymentId);
            statement.setString(7, "merchant-1");
            statement.setString(8, "PHYSICAL");
            statement.setString(9, "session-ref");
            statement.setString(10, "COMPLETED");
            statement.setNull(11, Types.VARCHAR);
            statement.setTimestamp(12, Timestamp.from(now));
            statement.setTimestamp(13, Timestamp.from(now));
            statement.executeUpdate();
        }
    }

    /**
     * Inserted against the pre-V20 shape of {@code billing_purchases}: a singular {@code
     * physical_session_id} column, no child table — exactly what every row written before this
     * change looks like.
     */
    private static void seedPreV20Purchase(
        Connection connection, UUID purchaseId, UUID paymentId, String physicalSessionId
    ) throws java.sql.SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
            "INSERT INTO billing_purchases (id, payment_id, physical_session_id, status) VALUES (?, ?, ?, ?)"
        )) {
            statement.setBytes(1, uuidToBytes(purchaseId));
            statement.setBytes(2, uuidToBytes(paymentId));
            statement.setString(3, physicalSessionId);
            statement.setString(4, "ASSIGNED");
            statement.executeUpdate();
        }
    }

    private static long count(Connection connection, String sql) throws java.sql.SQLException {
        try (PreparedStatement statement = connection.prepareStatement(sql);
             ResultSet resultSet = statement.executeQuery()) {
            resultSet.next();
            return resultSet.getLong(1);
        }
    }

    private static boolean columnExists(Connection connection, String table, String column)
        throws java.sql.SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
            "SELECT COUNT(*) FROM information_schema.columns "
                + "WHERE table_schema = DATABASE() AND table_name = ? AND column_name = ?"
        )) {
            statement.setString(1, table);
            statement.setString(2, column);
            try (ResultSet resultSet = statement.executeQuery()) {
                resultSet.next();
                return resultSet.getInt(1) == 1;
            }
        }
    }

    private static boolean tableExists(Connection connection, String table) throws java.sql.SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
            "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema = DATABASE() AND table_name = ?"
        )) {
            statement.setString(1, table);
            try (ResultSet resultSet = statement.executeQuery()) {
                resultSet.next();
                return resultSet.getInt(1) == 1;
            }
        }
    }

    @Test
    void v20_applies_cleanly_with_row_count_parity_for_every_pre_existing_single_session_purchase()
        throws java.sql.SQLException {
        UUID paymentId1 = UUID.randomUUID();
        UUID paymentId2 = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        UUID purchaseId1 = UUID.randomUUID();
        UUID purchaseId2 = UUID.randomUUID();
        Instant now = Instant.parse("2026-09-01T10:00:00Z");

        migrateToV19(null);

        try (Connection connection = connect()) {
            seedPayment(connection, paymentId1, userId, now);
            seedPayment(connection, paymentId2, userId, now);
            seedPreV20Purchase(connection, purchaseId1, paymentId1, "session-aaa");
            seedPreV20Purchase(connection, purchaseId2, paymentId2, "session-bbb");
        }

        migrateToLatest();

        try (Connection connection = connect()) {
            assertThat(columnExists(connection, "billing_purchases", "physical_session_id")).isFalse();
            assertThat(tableExists(connection, "billing_purchase_sessions")).isTrue();

            long purchaseCount = count(connection, "SELECT COUNT(*) FROM billing_purchases");
            long distinctPurchaseIdCount =
                count(connection, "SELECT COUNT(DISTINCT id) FROM billing_purchases");
            long sessionRowCount = count(connection, "SELECT COUNT(*) FROM billing_purchase_sessions");

            assertThat(purchaseCount).isEqualTo(2L);
            assertThat(purchaseCount).isEqualTo(distinctPurchaseIdCount);
            assertThat(sessionRowCount).isEqualTo(purchaseCount);

            try (PreparedStatement statement = connection.prepareStatement(
                "SELECT position, physical_session_id FROM billing_purchase_sessions WHERE purchase_id = ?"
            )) {
                statement.setBytes(1, uuidToBytes(purchaseId1));
                try (ResultSet resultSet = statement.executeQuery()) {
                    assertThat(resultSet.next()).isTrue();
                    assertThat(resultSet.getInt("position")).isZero();
                    assertThat(resultSet.getString("physical_session_id")).isEqualTo("session-aaa");
                    assertThat(resultSet.next()).isFalse();
                }
            }
        }
    }

    @Test
    void v21_reverse_migration_aborts_when_a_multi_session_purchase_exists() throws java.sql.SQLException {
        UUID paymentId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        UUID purchaseId = UUID.randomUUID();
        Instant now = Instant.parse("2026-09-01T10:00:00Z");

        migrateToV19(null);

        try (Connection connection = connect()) {
            seedPayment(connection, paymentId, userId, now);
            seedPreV20Purchase(connection, purchaseId, paymentId, "session-ccc");
        }

        migrateToLatest();

        // Simulate a MONTHLY purchase that ended up covering two sessions — something V20's
        // backfill never produces on its own, but a real MONTHLY checkout does once assignment
        // (PR3) exists. Insert a second child row directly to set up the guard's trigger case.
        try (Connection connection = connect();
             PreparedStatement statement = connection.prepareStatement(
                 "INSERT INTO billing_purchase_sessions (purchase_id, position, physical_session_id) "
                     + "VALUES (?, 1, ?)"
             )) {
            statement.setBytes(1, uuidToBytes(purchaseId));
            statement.setString(2, "session-ddd");
            statement.executeUpdate();
        }

        assertThatThrownBy(() -> migrateIncludingRollbackTo("21")).isInstanceOf(FlywayException.class);

        try (Connection connection = connect()) {
            // The guard failed the migration before any destructive DDL ran: original shape
            // survives untouched.
            assertThat(columnExists(connection, "billing_purchases", "physical_session_id")).isFalse();
            assertThat(tableExists(connection, "billing_purchase_sessions")).isTrue();
            assertThat(count(connection, "SELECT COUNT(*) FROM billing_purchase_sessions WHERE purchase_id = "
                + "UNHEX('" + bytesToHex(uuidToBytes(purchaseId)) + "')")).isEqualTo(2L);
        }
    }

    @Test
    void v21_reverse_migration_restores_the_singular_column_when_every_purchase_is_single_session()
        throws java.sql.SQLException {
        UUID paymentId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        UUID purchaseId = UUID.randomUUID();
        Instant now = Instant.parse("2026-09-01T10:00:00Z");

        migrateToV19(null);

        try (Connection connection = connect()) {
            seedPayment(connection, paymentId, userId, now);
            seedPreV20Purchase(connection, purchaseId, paymentId, "session-eee");
        }

        migrateToLatest();
        migrateIncludingRollbackTo("21");

        try (Connection connection = connect()) {
            assertThat(columnExists(connection, "billing_purchases", "physical_session_id")).isTrue();
            assertThat(tableExists(connection, "billing_purchase_sessions")).isFalse();

            try (PreparedStatement statement = connection.prepareStatement(
                "SELECT physical_session_id FROM billing_purchases WHERE id = ?"
            )) {
                statement.setBytes(1, uuidToBytes(purchaseId));
                try (ResultSet resultSet = statement.executeQuery()) {
                    assertThat(resultSet.next()).isTrue();
                    assertThat(resultSet.getString("physical_session_id")).isEqualTo("session-eee");
                }
            }
        }
    }

    private static String bytesToHex(byte[] bytes) {
        StringBuilder builder = new StringBuilder();
        for (byte b : bytes) {
            builder.append(String.format("%02x", b));
        }
        return builder.toString();
    }
}
