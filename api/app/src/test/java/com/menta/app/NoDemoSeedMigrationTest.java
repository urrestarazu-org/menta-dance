package com.menta.app;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;

/** Demo data is seeded only by profile-gated runners, never by a Flyway migration of any module. */
class NoDemoSeedMigrationTest {

    @Test
    void no_migration_inserts_demo_data() throws IOException {
        Resource[] migrations = new PathMatchingResourcePatternResolver()
            .getResources("classpath*:db/migration/**/*.sql");

        assertThat(migrations).isNotEmpty();
        for (Resource migration : migrations) {
            try (InputStream content = migration.getInputStream()) {
                String sql = new String(content.readAllBytes(), StandardCharsets.UTF_8).toLowerCase(Locale.ROOT);
                assertThat(sql)
                    .as("demo data in migration %s", migration.getFilename())
                    .doesNotContain("@menta.dance", "de000000-");
            }
        }
    }
}
