package com.menta.app.integration.support;

import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.MountableFile;

/**
 * One MySQLContainer shared by every "integration-test" profile class under
 * {@code integration.virtual} and {@code integration.catalog}. See {@link MySqlSchemas} for
 * why each context gets its own schema on this container, and why this is one container per
 * domain rather than one for the whole module.
 *
 * <p>Spring resolves {@code @DynamicPropertySource} customizer equality via {@code Method.equals()},
 * which includes the declaring class — subclasses must inherit this exact method rather than
 * redeclare their own, or they will never be cache-equal to a sibling with an
 * otherwise-identical profile/mock signature.</p>
 */
@Testcontainers
public abstract class AbstractVirtualMySqlIntegrationTest {

    @Container
    static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.0")
        .withDatabaseName("menta_test")
        .withUsername("test")
        .withPassword("test")
        .withCommand("--max_connections=1000")
        .withCopyFileToContainer(
            MountableFile.forClasspathResource("testcontainers/grant-all-to-test-user.sql"),
            "/docker-entrypoint-initdb.d/grant-all-to-test-user.sql");

    @DynamicPropertySource
    static void mysqlProperties(DynamicPropertyRegistry registry) {
        String schema = MySqlSchemas.createSchema(MYSQL);
        registry.add("spring.datasource.url", () -> MySqlSchemas.jdbcUrl(MYSQL, schema));
        registry.add("spring.datasource.username", MYSQL::getUsername);
        registry.add("spring.datasource.password", MYSQL::getPassword);
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "create-drop");
    }
}
