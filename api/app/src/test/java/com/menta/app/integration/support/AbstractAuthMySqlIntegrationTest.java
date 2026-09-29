package com.menta.app.integration.support;

import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.utility.MountableFile;

/**
 * Base class for every {@code "integration-test"}-profile test under the auth domain (root
 * {@code integration} package, {@code integration.auth}, and {@code outbox}), providing one
 * {@link MySQLContainer} shared for the entire test run. See {@link MySqlSchemas} for why each
 * Spring context gets its own schema on this container instead of its own container, and why
 * this is one container per domain rather than one for the whole module.
 *
 * <p>Spring resolves {@code @DynamicPropertySource} customizer equality via {@code Method.equals()},
 * which includes the declaring class — subclasses must inherit this exact method rather than
 * redeclare their own, or they will never be cache-equal to a sibling with an
 * otherwise-identical profile/mock signature.</p>
 *
 * <p>Deliberately NOT {@code @Testcontainers}/{@code @Container}: that annotation pair stops the
 * container after the last test method of whichever concrete class currently owns it — per class,
 * not per JVM — even though the field is declared once on this shared abstract base. Every
 * subclass's own {@code afterAll} killed the inherited container and the next class recreated it
 * from scratch, so 30+ integration test classes never shared 4 containers, they paid for ~30+
 * fresh ones. This is Testcontainers' own documented singleton pattern instead: a plain static
 * field started once in a static initializer, with no JUnit 5 extension managing its lifecycle at
 * all — only Ryuk stops it, at JVM shutdown, once for the whole run.</p>
 */
public abstract class AbstractAuthMySqlIntegrationTest {

    static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.0")
        .withDatabaseName("menta_test")
        .withUsername("test")
        .withPassword("test")
        .withCommand("--max_connections=1000")
        .withCopyFileToContainer(
            MountableFile.forClasspathResource("testcontainers/grant-all-to-test-user.sql"),
            "/docker-entrypoint-initdb.d/grant-all-to-test-user.sql");

    static {
        MYSQL.start();
    }

    @DynamicPropertySource
    static void mysqlProperties(DynamicPropertyRegistry registry) {
        String schema = MySqlSchemas.createSchema(MYSQL);
        registry.add("spring.datasource.url", () -> MySqlSchemas.jdbcUrl(MYSQL, schema));
        registry.add("spring.datasource.username", MYSQL::getUsername);
        registry.add("spring.datasource.password", MYSQL::getPassword);
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "create-drop");
    }
}
