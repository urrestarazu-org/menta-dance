package com.menta.app.integration.virtual;

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
 * Proves V33 (#841) adds {@code idx_virtual_lessons_video_id} on {@code virtual_lessons.video_id}, that V34 (#841)
 * creates the append-only {@code virtual_video_audit} table, and that the operator-only V33.1 / V34.1 rollbacks
 * undo each of them.
 *
 * <p>Drives Flyway directly (not a {@code @SpringBootTest}) because the app's {@code integration-test} profile
 * replaces Flyway with Hibernate {@code create-drop}.</p>
 */
@Testcontainers
class VirtualVideoManagementMigrationIntegrationTest {

    private static final String VIDEO_INDEX = "idx_virtual_lessons_video_id";

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

    private static void rollbackTo(String version) {
        // The rollbacks below the current head sit in db/rollback and are never applied, so the combined-location
        // run must ignore them as "ignored" rather than fail validation.
        Flyway.configure()
            .dataSource(MYSQL.getJdbcUrl(), MYSQL.getUsername(), MYSQL.getPassword())
            .locations("classpath:db/migration", "classpath:db/rollback")
            .ignoreMigrationPatterns("*:ignored")
            .target(MigrationVersion.fromVersion(version))
            .load()
            .migrate();
    }

    private static List<String> indexColumns(String table, String indexName) throws SQLException {
        try (Connection connection = connect(); PreparedStatement statement = connection.prepareStatement(
            "SELECT column_name FROM information_schema.statistics "
                + "WHERE table_schema = DATABASE() AND table_name = ? AND index_name = ? ORDER BY seq_in_index"
        )) {
            statement.setString(1, table);
            statement.setString(2, indexName);
            List<String> columns = new ArrayList<>();
            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    columns.add(resultSet.getString(1));
                }
            }
            return columns;
        }
    }

    private static List<String> columns(String table) throws SQLException {
        try (Connection connection = connect(); PreparedStatement statement = connection.prepareStatement(
            "SELECT column_name FROM information_schema.columns "
                + "WHERE table_schema = DATABASE() AND table_name = ? ORDER BY ordinal_position"
        )) {
            statement.setString(1, table);
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
    void before_v33_the_video_id_column_has_no_index() throws SQLException {
        migrateTo("32");

        assertThat(indexColumns("virtual_lessons", VIDEO_INDEX)).isEmpty();
    }

    @Test
    void v33_indexes_video_id() throws SQLException {
        migrateTo("33");

        assertThat(indexColumns("virtual_lessons", VIDEO_INDEX)).containsExactly("video_id");
    }

    @Test
    void v33_1_rollback_drops_the_index() throws SQLException {
        migrateTo("33");

        rollbackTo("33.1");

        assertThat(indexColumns("virtual_lessons", VIDEO_INDEX)).isEmpty();
    }

    @Test
    void before_v34_there_is_no_video_audit_table() throws SQLException {
        migrateTo("33");

        assertThat(columns("virtual_video_audit")).isEmpty();
    }

    @Test
    void v34_creates_the_video_audit_table_indexed_by_video() throws SQLException {
        migrateTo("34");

        assertThat(columns("virtual_video_audit"))
            .containsExactly("id", "video_id", "actor_id", "action", "previous_value", "new_value", "created_at");
        assertThat(indexColumns("virtual_video_audit", "idx_virtual_video_audit_video_id"))
            .containsExactly("video_id");
    }

    @Test
    void v34_1_rollback_drops_the_video_audit_table_and_leaves_the_index() throws SQLException {
        migrateTo("34");

        rollbackTo("34.1");

        assertThat(columns("virtual_video_audit")).isEmpty();
        assertThat(indexColumns("virtual_lessons", VIDEO_INDEX)).containsExactly("video_id");
    }
}
