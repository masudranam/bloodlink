package com.bloodlink;

import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.containers.PostgreSQLContainer;

/**
 * Base class for integration tests.
 *
 * <p>One PostgreSQL 16 container is shared by every subclass: it is started once
 * from a static initialiser and torn down by Ryuk when the JVM exits, so the
 * suite pays the startup cost a single time. {@code @ServiceConnection} points
 * the application's DataSource at it, so no test needs to know the JDBC URL.
 */
public abstract class AbstractIntegrationTest {

    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("bloodlink")
            .withUsername("bloodlink")
            .withPassword("bloodlink");

    static {
        POSTGRES.start();
    }
}
