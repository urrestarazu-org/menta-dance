package com.menta.app.integration.support;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
import java.util.UUID;
import org.testcontainers.containers.MySQLContainer;

/**
 * Creates one isolated MySQL schema per Spring test context on a shared container, so that
 * {@code ddl-auto=create-drop} on one context can't drop tables out from under a sibling context
 * still live in the test context cache. Used by the domain-scoped
 * {@code Abstract*MySqlIntegrationTest} base classes, each of which gives its own package group
 * one dedicated container (splitting the module's integration-test classes across a handful of
 * containers instead of either one-per-class or one shared by all of them — sharing a single
 * server across every context in the module let their HikariCP pools collectively exhaust it
 * under the full suite).
 */
final class MySqlSchemas {

    private MySqlSchemas() {
    }

    /**
     * Creates a new, empty schema with a random name on the given container and returns it.
     *
     * <p>Connects with a raw JDBC {@link DriverManager} connection to the container's own default
     * database, not through Spring/Hikari — this runs from a {@code @DynamicPropertySource} method,
     * before any {@code DataSource} bean exists yet, since it is what supplies that bean's URL.</p>
     *
     * @param container the shared domain container to create the schema on
     * @return the new schema's name (e.g. {@code menta_test_3f9a1c...}), never {@code null}
     * @throws IllegalStateException if the {@code CREATE DATABASE} statement fails
     */
    static String createSchema(MySQLContainer<?> container) {
        String schema = "menta_test_" + UUID.randomUUID().toString().replace("-", "");
        try (
            Connection connection = DriverManager.getConnection(
                container.getJdbcUrl(), container.getUsername(), container.getPassword());
            Statement statement = connection.createStatement()
        ) {
            statement.execute("CREATE DATABASE IF NOT EXISTS `" + schema + "`");
        } catch (Exception e) {
            throw new IllegalStateException(
                "Could not create per-context test schema " + schema, e);
        }
        return schema;
    }

    /**
     * Builds the JDBC URL a Spring context should use to connect to one specific schema on the
     * given container, rather than the container's own default database.
     *
     * @param container the shared domain container the schema lives on
     * @param schema the schema name, as returned by {@link #createSchema(MySQLContainer)}
     * @return a {@code jdbc:mysql://...} URL pointing at that schema on that container
     */
    static String jdbcUrl(MySQLContainer<?> container, String schema) {
        return "jdbc:mysql://" + container.getHost() + ":"
            + container.getMappedPort(MySQLContainer.MYSQL_PORT)
            + "/" + schema + "?useSSL=false&allowPublicKeyRetrieval=true";
    }
}
