package id.xyz.parkease.db;

import org.junit.jupiter.api.Assumptions;
import org.testcontainers.DockerClientFactory;

final class PostgresIntegrationSupport {

    private static final String REQUIRE_POSTGRES = "parkease.requirePostgres";

    private PostgresIntegrationSupport() {
    }

    static void requireDockerOrSkip() {
        boolean dockerAvailable = DockerClientFactory.instance().isDockerAvailable();
        if (Boolean.getBoolean(REQUIRE_POSTGRES) && !dockerAvailable) {
            throw new IllegalStateException("Docker is required for PostgreSQL integration tests");
        }
        Assumptions.assumeTrue(dockerAvailable, "docker unavailable, skipping PostgreSQL integration tests");
    }
}
