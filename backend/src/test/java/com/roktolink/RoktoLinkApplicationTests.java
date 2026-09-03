package com.roktolink;

import static org.assertj.core.api.Assertions.assertThat;

import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * The one trivial test for issue 1.
 *
 * <p>It is deliberately end-to-end for the plumbing: the Spring context starts
 * against a real PostgreSQL 16 container, Flyway runs (zero migrations so far),
 * and Hibernate validates the schema. If Docker, Testcontainers, Flyway or
 * ddl-auto=validate are misconfigured, this fails.
 *
 * <p>No acN_ prefix here because SPEC-001 has no acceptance criteria yet. Every
 * test added from issue 2 onwards must be named for the criterion it covers.
 */
@SpringBootTest
class RoktoLinkApplicationTests extends AbstractIntegrationTest {

    @Autowired
    private DataSource dataSource;

    @Test
    void contextLoads_withPostgresContainer() {
        assertThat(dataSource).isNotNull();
    }
}
