package com.menta.testsupport;

/**
 * Single source of truth for the MySQL image used by every Testcontainers-backed test.
 *
 * <p>The tag mirrors the engine production runs ({@code infra/docker/prod/docker-compose.prod.yml})
 * so Flyway migrations and repository queries are exercised against the deployed version. This
 * file is compiled into the test source set of every {@code :api:*} module (see the root {@code
 * build.gradle.kts}); bump the tag here and in the compose files together. {@code
 * scripts/verify-local-infrastructure-contract.sh} fails the build if they drift apart.
 */
public final class MySqlTestImage {

    /** Pinned Docker image name, never {@code latest}. */
    public static final String NAME = "mysql:8.4.11";

    private MySqlTestImage() {
    }
}
